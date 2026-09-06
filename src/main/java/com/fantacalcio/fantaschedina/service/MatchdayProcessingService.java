package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.*;
import com.fantacalcio.fantaschedina.domain.enums.*;
import com.fantacalcio.fantaschedina.dto.FixtureResultRequest;
import com.fantacalcio.fantaschedina.dto.MatchdayResultRequest;
import com.fantacalcio.fantaschedina.repository.*;
import com.fantacalcio.fantaschedina.util.OutcomeEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchdayProcessingService {

    private final MatchdayRepository matchdayRepository;
    private final MatchdayFixtureRepository matchdayFixtureRepository;
    private final BetSlipRepository betSlipRepository;
    private final BetPickRepository betPickRepository;
    private final BetTemplateRepository betTemplateRepository;
    private final LeagueMembershipRepository leagueMembershipRepository;
    private final FantaTeamRepository fantaTeamRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final JackpotRepository jackpotRepository;
    private final LeagueRepository leagueRepository;
    private final MatchdayOpeningService matchdayOpeningService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    private Matchday requireResultLoadable(Long matchdayId) {
        Matchday matchday = matchdayRepository.findById(matchdayId).orElseThrow();
        if (matchday.getStatus() != MatchdayStatus.CLOSED && matchday.getStatus() != MatchdayStatus.AWAITING_RECOVERY) {
            log.warn("requireResultLoadable: rejected - matchday {} does not accept results (status={})", matchdayId, matchday.getStatus());
            throw new IllegalStateException("La giornata non accetta il caricamento dei risultati.");
        }
        return matchday;
    }

    /**
     * Marks a matchday as awaiting a postponed match: the next matchday opens right away, while this
     * one's slips stay unprocessed and the jackpot frozen until its results are loaded. No score is
     * required here - the admin loads them later with {@link #loadResults}.
     */
    @Transactional
    public Matchday markAwaitingRecovery(Long matchdayId) {
        log.debug("markAwaitingRecovery: matchday {}", matchdayId);
        Matchday matchday = requireResultLoadable(matchdayId);

        matchday.setStatus(MatchdayStatus.AWAITING_RECOVERY);
        matchdayRepository.save(matchday);
        log.info("markAwaitingRecovery: matchday {} -> AWAITING_RECOVERY", matchdayId);

        // The next matchday can be played even while this one waits for a postponed match.
        matchdayOpeningService.tryOpenNext(matchday.getLeagueId(), matchday.getNumber());
        return matchday;
    }

    @Transactional
    public Matchday loadResults(Long matchdayId, MatchdayResultRequest request) {
        log.debug("loadResults: matchday {}", matchdayId);
        Matchday matchday = requireResultLoadable(matchdayId);

        for (FixtureResultRequest f : request.getFixtures()) {
            if (f.getHomeScore() == null || f.getAwayScore() == null) {
                log.warn("loadResults: rejected - missing score for fixture {} in matchday {}", f.getFixtureId(), matchdayId);
                throw new IllegalArgumentException("Inserisci tutti i risultati prima di confermare.");
            }
        }

        Map<Long, MatchdayFixture> fixtureMap = matchdayFixtureRepository.findByMatchdayId(matchdayId)
                .stream().collect(Collectors.toMap(MatchdayFixture::getId, f -> f));

        for (FixtureResultRequest f : request.getFixtures()) {
            MatchdayFixture fixture = fixtureMap.get(f.getFixtureId());
            if (fixture == null) continue;
            fixture.setHomeScore(f.getHomeScore());
            fixture.setAwayScore(f.getAwayScore());
            fixture.setResultLoaded(true);
            matchdayFixtureRepository.save(fixture);
        }

        matchday.setStatus(MatchdayStatus.RESULTS_LOADED);
        matchdayRepository.save(matchday);
        log.info("loadResults: matchday {} results loaded ({} fixture(s)), status -> RESULTS_LOADED",
                matchdayId, request.getFixtures().size());

        // A matchday resolved after later ones must still let them proceed once it is processed.
        matchdayOpeningService.tryOpenNext(matchday.getLeagueId(), matchday.getNumber());

        processPending(matchday.getLeagueId());

        return matchday;
    }

    /**
     * Processes every matchday with results loaded, in matchday-number order, stopping at the first
     * one whose predecessor is not PROCESSED yet. A matchday awaiting a postponed match therefore
     * freezes the jackpot: later matchdays stay queued until its results are completed.
     */
    @Transactional
    public void processPending(Long leagueId) {
        List<Matchday> queued = matchdayRepository.findByLeagueIdAndStatus(leagueId, MatchdayStatus.RESULTS_LOADED)
                .stream()
                .sorted(Comparator.comparingInt(Matchday::getNumber))
                .toList();
        log.debug("processPending: league {} has {} matchday(s) with results loaded", leagueId, queued.size());

        for (Matchday matchday : queued) {
            if (!isPreviousProcessed(matchday)) {
                log.info("processPending: matchday {} (number {}) stays queued - a previous matchday is not processed yet",
                        matchday.getId(), matchday.getNumber());
                break;
            }
            process(matchday.getId());
        }
    }

    @Transactional
    public void process(Long matchdayId) {
        log.debug("process: matchday {}", matchdayId);
        Matchday matchday = matchdayRepository.findById(matchdayId).orElseThrow();

        if (matchday.getStatus() != MatchdayStatus.RESULTS_LOADED) {
            log.warn("process: matchday {} is not RESULTS_LOADED (status={}), skipping", matchdayId, matchday.getStatus());
            return;
        }
        if (!isPreviousProcessed(matchday)) {
            log.warn("process: matchday {} skipped - a previous matchday is not processed yet", matchdayId);
            return;
        }

        League league = leagueRepository.findById(matchday.getLeagueId()).orElseThrow();

        // Build fixture map and threshold map
        Map<Long, MatchdayFixture> fixtureMap = matchdayFixtureRepository.findByMatchdayId(matchdayId)
                .stream().collect(Collectors.toMap(MatchdayFixture::getId, f -> f));

        Map<OutcomeType, Double> thresholds = betTemplateRepository
                .findByLeagueIdOrderByOrderIndexAsc(league.getId()).stream()
                .filter(t -> t.getOutcomeType() == OutcomeType.OVER_UNDER)
                .collect(Collectors.toMap(BetTemplate::getOutcomeType, BetTemplate::getOverUnderThreshold));

        // Evaluate all slips
        List<BetSlip> slips = betSlipRepository.findByMatchdayId(matchdayId);
        for (BetSlip slip : slips) {
            if (slip.getIsAutoSubmitted()) {
                slip.setStatus(BetSlipStatus.VOID);
                betSlipRepository.save(slip);
                continue;
            }
            List<BetPick> picks = betPickRepository.findByBetSlipId(slip.getId());
            boolean allCorrect = true;
            for (BetPick pick : picks) {
                MatchdayFixture fixture = fixtureMap.get(pick.getMatchdayFixtureId());
                boolean correct = OutcomeEvaluator.evaluate(
                        pick.getOutcomeType(),
                        pick.getPickedOutcome(),
                        fixture.getHomeScore(),
                        fixture.getAwayScore(),
                        thresholds.get(OutcomeType.OVER_UNDER)
                );
                pick.setIsCorrect(correct);
                betPickRepository.save(pick);
                if (!correct) allCorrect = false;
            }
            slip.setStatus(allCorrect ? BetSlipStatus.WON : BetSlipStatus.LOST);
            betSlipRepository.save(slip);
        }
        log.debug("process: matchday {} evaluated {} slip(s)", matchdayId, slips.size());

        // Jackpot distribution
        Jackpot jackpot = jackpotRepository.findByLeagueId(league.getId()).orElseThrow();
        List<BetSlip> winners = slips.stream()
                .filter(s -> s.getStatus() == BetSlipStatus.WON)
                .toList();

        // Pot of this matchday: the jackpot as it was at closing time, so a matchday resolved late
        // does not collect the stakes of the matchdays played meanwhile. Capped at what is actually
        // in the jackpot: snapshots of queued matchdays overlap (each one includes the stakes still
        // unpaid at its own closing), so an earlier payout shrinks the pot of the ones behind it.
        int snapshot = matchday.getJackpotAtClose() != null ? matchday.getJackpotAtClose() : jackpot.getCurrentAmount();
        int pot = Math.min(snapshot, jackpot.getCurrentAmount());
        if (pot < snapshot) {
            log.info("process: matchday {} pot capped at {} (snapshot {}) - an earlier matchday has been paid out meanwhile",
                    matchdayId, pot, snapshot);
        }

        if (!winners.isEmpty()) {
            int share = pot / winners.size();
            int remainder = pot % winners.size();
            log.info("process: matchday {} has {} winner(s), pot {} (jackpot {}) -> share {} each, remainder {}",
                    matchdayId, winners.size(), pot, jackpot.getCurrentAmount(), share, remainder);
            List<String> winnerTeamNames = new ArrayList<>();
            for (BetSlip winner : winners) {
                FantaTeam team = fantaTeamRepository.findById(winner.getFantaTeamId()).orElseThrow();
                LeagueMembership membership = leagueMembershipRepository.findById(team.getLeagueMembershipId()).orElseThrow();
                int newBalance = membership.getBalance() + share;
                membership.setBalance(newBalance);
                leagueMembershipRepository.save(membership);
                creditTransactionRepository.save(CreditTransaction.builder()
                        .leagueMembershipId(membership.getId())
                        .matchdayId(matchdayId)
                        .type(TransactionType.WIN_CREDIT)
                        .amount(share)
                        .balanceAfter(newBalance)
                        .createdAt(LocalDateTime.now())
                        .note("Vincita giornata " + matchday.getNumber())
                        .build());
                winnerTeamNames.add(team.getName());
            }
            // What is left in the pot are the stakes of the matchdays played meanwhile, if any.
            int leftover = jackpot.getCurrentAmount() - pot;
            jackpot.setCurrentAmount(leftover + league.getJackpotStart() + remainder);
            log.info("process: matchday {} jackpot reset to {}", matchdayId, jackpot.getCurrentAmount());
            notifyJackpotWon(league, matchday, winnerTeamNames, share);
        } else {
            log.debug("process: matchday {} has no winners, jackpot unchanged at {}", matchdayId, jackpot.getCurrentAmount());
        }
        jackpot.setLastUpdatedMatchdayId(matchdayId);
        jackpotRepository.save(jackpot);

        // Matchday → PROCESSED
        matchday.setStatus(MatchdayStatus.PROCESSED);
        matchdayRepository.save(matchday);
        log.info("process: matchday {} processed, status -> PROCESSED", matchdayId);

        notifyResultsAvailable(league, matchday);

        // Trigger 2: open next matchday if startAt already set
        matchdayOpeningService.tryOpenNext(matchday.getLeagueId(), matchday.getNumber());
    }

    private boolean isPreviousProcessed(Matchday matchday) {
        return matchdayRepository
                .findByLeagueIdAndNumber(matchday.getLeagueId(), matchday.getNumber() - 1)
                .map(prev -> prev.getStatus() == MatchdayStatus.PROCESSED)
                .orElse(true); // no previous matchday → nothing to wait for
    }

    private void notifyResultsAvailable(League league, Matchday matchday) {
        List<LeagueMembership> members = leagueMembershipRepository.findByLeagueId(league.getId());
        for (LeagueMembership member : members) {
            userRepository.findById(member.getUserId()).ifPresent(user ->
                    notificationService.sendResultsAvailableEmail(user, league, matchday));
        }
    }

    private void notifyJackpotWon(League league, Matchday matchday, List<String> winnerTeamNames, int amountPerWinner) {
        List<LeagueMembership> members = leagueMembershipRepository.findByLeagueId(league.getId());
        for (LeagueMembership member : members) {
            userRepository.findById(member.getUserId()).ifPresent(user ->
                    notificationService.sendJackpotWonEmail(user, league, matchday, winnerTeamNames, amountPerWinner));
        }
    }
}
