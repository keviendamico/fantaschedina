package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.*;
import com.fantacalcio.fantaschedina.domain.enums.*;
import com.fantacalcio.fantaschedina.dto.FixtureResultRequest;
import com.fantacalcio.fantaschedina.dto.MatchdayResultRequest;
import com.fantacalcio.fantaschedina.repository.*;
import com.fantacalcio.fantaschedina.util.OutcomeEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Transactional
    public Matchday loadResults(Long matchdayId, MatchdayResultRequest request) {
        Matchday matchday = matchdayRepository.findById(matchdayId).orElseThrow();

        if (matchday.getStatus() != MatchdayStatus.CLOSED) {
            throw new IllegalStateException("La giornata non è in stato CLOSED.");
        }

        for (FixtureResultRequest f : request.getFixtures()) {
            if (f.getHomeScore() == null || f.getAwayScore() == null) {
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

        process(matchdayId);

        return matchday;
    }

    @Transactional
    public void process(Long matchdayId) {
        Matchday matchday = matchdayRepository.findById(matchdayId).orElseThrow();
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

        // Jackpot distribution
        Jackpot jackpot = jackpotRepository.findByLeagueId(league.getId()).orElseThrow();
        List<BetSlip> winners = slips.stream()
                .filter(s -> s.getStatus() == BetSlipStatus.WON)
                .collect(Collectors.toList());

        if (!winners.isEmpty()) {
            int share = jackpot.getCurrentAmount() / winners.size();
            int remainder = jackpot.getCurrentAmount() % winners.size();
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
            }
            jackpot.setCurrentAmount(league.getJackpotStart() + remainder);
        }
        jackpot.setLastUpdatedMatchdayId(matchdayId);
        jackpotRepository.save(jackpot);

        // Matchday → PROCESSED
        matchday.setStatus(MatchdayStatus.PROCESSED);
        matchdayRepository.save(matchday);

        notifyResultsAvailable(league, matchday);

        // Trigger 2: open next matchday if startAt already set
        matchdayOpeningService.tryOpenNext(matchday.getLeagueId(), matchday.getNumber());
    }

    private void notifyResultsAvailable(League league, Matchday matchday) {
        List<LeagueMembership> members = leagueMembershipRepository.findByLeagueId(league.getId());
        for (LeagueMembership member : members) {
            userRepository.findById(member.getUserId()).ifPresent(user ->
                    notificationService.sendResultsAvailableEmail(user, league, matchday));
        }
    }
}