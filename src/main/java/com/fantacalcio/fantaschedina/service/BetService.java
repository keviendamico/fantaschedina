package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.*;
import com.fantacalcio.fantaschedina.domain.enums.*;
import com.fantacalcio.fantaschedina.exception.BetValidationException;
import com.fantacalcio.fantaschedina.exception.SlipNotFoundException;
import com.fantacalcio.fantaschedina.dto.BetPickRequest;
import com.fantacalcio.fantaschedina.dto.BetSlipRequest;
import com.fantacalcio.fantaschedina.repository.*;
import com.fantacalcio.fantaschedina.util.AppClock;
import com.fantacalcio.fantaschedina.util.OutcomeConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BetService {

    private final BetSlipRepository betSlipRepository;
    private final BetPickRepository betPickRepository;
    private final BetTemplateRepository betTemplateRepository;
    private final MatchdayRepository matchdayRepository;
    private final MatchdayFixtureRepository matchdayFixtureRepository;
    private final LeagueMembershipRepository leagueMembershipRepository;
    private final FantaTeamRepository fantaTeamRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final LeagueRepository leagueRepository;
    private final JackpotRepository jackpotRepository;
    private final MatchdayService matchdayService;

    /**
     * Submits a bet slip for the given user on the given matchday.
     * Throws BetValidationException on any validation failure.
     */
    @Transactional
    public BetSlip submit(Long leagueId, Long matchdayId, Long userId, BetSlipRequest request) {
        log.debug("submit: user {} league {} matchday {}", userId, leagueId, matchdayId);
        League league = leagueRepository.findById(leagueId).orElseThrow();
        Matchday matchday = matchdayRepository.findById(matchdayId).orElseThrow();

        if (matchday.getStatus() != MatchdayStatus.OPEN) {
            log.warn("submit: rejected — matchday {} is not OPEN (status={})", matchdayId, matchday.getStatus());
            throw new BetValidationException("La giornata non è aperta alle scommesse.", leagueId, matchdayId);
        }

        LocalDateTime deadline = matchdayService.effectiveDeadline(matchday, league.getBetDeadlineMinutes());
        if (deadline != null && AppClock.now().isAfter(deadline)) {
            log.warn("submit: rejected — deadline {} already passed for matchday {}", deadline, matchdayId);
            throw new BetValidationException("La scadenza per questa giornata è già passata.", leagueId, matchdayId);
        }

        LeagueMembership membership = leagueMembershipRepository
                .findByLeagueIdAndUserId(leagueId, userId)
                .orElseThrow(() -> {
                    log.warn("submit: rejected — user {} is not a member of league {}", userId, leagueId);
                    return new BetValidationException("Non sei membro di questa lega.", leagueId, matchdayId);
                });
        FantaTeam fantaTeam = fantaTeamRepository
                .findByLeagueMembershipId(membership.getId())
                .orElseThrow(() -> {
                    log.warn("submit: rejected — membership {} has no fanta team", membership.getId());
                    return new BetValidationException("Nessuna squadra trovata per il tuo account.", leagueId, matchdayId);
                });

        if (betSlipRepository.existsByFantaTeamIdAndMatchdayId(fantaTeam.getId(), matchdayId)) {
            log.warn("submit: rejected — team {} already submitted a slip for matchday {}", fantaTeam.getId(), matchdayId);
            throw new BetValidationException("Hai già inviato una schedina per questa giornata.", leagueId, matchdayId);
        }

        List<BetTemplate> templates = betTemplateRepository.findByLeagueIdOrderByOrderIndexAsc(leagueId);
        validatePicks(request.getPicks(), templates, leagueId, matchdayId);

        // Save BetSlip
        BetSlip slip = BetSlip.builder()
                .matchdayId(matchdayId)
                .fantaTeamId(fantaTeam.getId())
                .submittedAt(LocalDateTime.now())
                .isAutoSubmitted(false)
                .isAdminModified(false)
                .status(BetSlipStatus.PENDING)
                .amountCharged(league.getMatchdayCost())
                .build();
        slip = betSlipRepository.save(slip);

        // Save BetPicks
        for (BetPickRequest pick : request.getPicks()) {
            betPickRepository.save(BetPick.builder()
                    .betSlipId(slip.getId())
                    .matchdayFixtureId(pick.getFixtureId())
                    .outcomeType(pick.getOutcomeType())
                    .pickedOutcome(pick.getPickedOutcome())
                    .build());
        }

        // Debit credits
        int newBalance = membership.getBalance() - league.getMatchdayCost();
        membership.setBalance(newBalance);
        leagueMembershipRepository.save(membership);

        creditTransactionRepository.save(CreditTransaction.builder()
                .leagueMembershipId(membership.getId())
                .matchdayId(matchdayId)
                .type(TransactionType.BET_CHARGE)
                .amount(-league.getMatchdayCost())
                .balanceAfter(newBalance)
                .createdAt(LocalDateTime.now())
                .note("Schedina giornata " + matchday.getNumber())
                .build());

        Jackpot jackpot = jackpotRepository.findByLeagueId(leagueId).orElseThrow();
        jackpot.setCurrentAmount(jackpot.getCurrentAmount() + league.getMatchdayCost());
        jackpotRepository.save(jackpot);

        log.info("submit: slip {} submitted for team {} matchday {}, balance {} -> {}, jackpot -> {}",
                slip.getId(), fantaTeam.getId(), matchdayId, newBalance + league.getMatchdayCost(), newBalance, jackpot.getCurrentAmount());
        return slip;
    }

    private void validatePicks(List<BetPickRequest> picks, List<BetTemplate> templates,
                               Long leagueId, Long matchdayId) {
        if (picks == null || picks.isEmpty()) {
            log.warn("validatePicks: rejected — no picks submitted (league {} matchday {})", leagueId, matchdayId);
            throw new BetValidationException("La schedina non contiene nessun pronostico.", leagueId, matchdayId);
        }

        Set<Long> validFixtureIds = matchdayFixtureRepository.findByMatchdayId(matchdayId).stream()
                .map(MatchdayFixture::getId)
                .collect(Collectors.toSet());

        for (BetPickRequest pick : picks) {
            if (!validFixtureIds.contains(pick.getFixtureId())) {
                log.warn("validatePicks: rejected — fixture {} does not belong to matchday {}", pick.getFixtureId(), matchdayId);
                throw new BetValidationException("Una partita selezionata non appartiene a questa giornata.", leagueId, matchdayId);
            }
            Set<String> valid = OutcomeConstants.VALID_OUTCOMES_SET.get(pick.getOutcomeType());
            if (valid == null || !valid.contains(pick.getPickedOutcome())) {
                log.warn("validatePicks: rejected — invalid outcome \"{}\" for type {} (fixture {})",
                        pick.getPickedOutcome(), pick.getOutcomeType(), pick.getFixtureId());
                throw new BetValidationException("Esito non valido: " + pick.getPickedOutcome(), leagueId, matchdayId);
            }
        }

        Set<Long> pickedFixtureIds = picks.stream()
                .map(BetPickRequest::getFixtureId)
                .collect(Collectors.toSet());
        if (pickedFixtureIds.size() < picks.size()) {
            log.warn("validatePicks: rejected — duplicate fixture picks (matchday {})", matchdayId);
            throw new BetValidationException("Ogni partita può essere giocata una sola volta.", leagueId, matchdayId);
        }
        if (!pickedFixtureIds.equals(validFixtureIds)) {
            log.warn("validatePicks: rejected — not all fixtures of matchday {} covered (picked {}, expected {})",
                    matchdayId, pickedFixtureIds.size(), validFixtureIds.size());
            throw new BetValidationException("Devi giocare tutte le partite della giornata, una per una.", leagueId, matchdayId);
        }

        Map<OutcomeType, Long> pickCounts = picks.stream()
                .collect(Collectors.groupingBy(BetPickRequest::getOutcomeType, Collectors.counting()));

        for (BetTemplate template : templates) {
            long actual = pickCounts.getOrDefault(template.getOutcomeType(), 0L);
            if (actual != template.getRequiredCount()) {
                log.warn("validatePicks: rejected — type {} requires {} pick(s), got {} (matchday {})",
                        template.getOutcomeType(), template.getRequiredCount(), actual, matchdayId);
                throw new BetValidationException(
                        "Pronostici di tipo " + template.getOutcomeType().name() +
                        ": richiesti " + template.getRequiredCount() + ", inviati " + actual + ".",
                        leagueId, matchdayId);
            }
        }

        for (OutcomeType type : pickCounts.keySet()) {
            boolean inTemplate = templates.stream().anyMatch(t -> t.getOutcomeType() == type);
            if (!inTemplate) {
                log.warn("validatePicks: rejected — outcome type {} not in league {} template", type, leagueId);
                throw new BetValidationException("Tipo di pronostico non previsto: " + type.name(), leagueId, matchdayId);
            }
        }
    }

    @Transactional(readOnly = true)
    public BetSlip findSlip(Long fantaTeamId, Long matchdayId) {
        return betSlipRepository.findByFantaTeamIdAndMatchdayId(fantaTeamId, matchdayId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<BetSlip> findSlipsForTeam(Long fantaTeamId) {
        return betSlipRepository.findByFantaTeamId(fantaTeamId);
    }

    @Transactional(readOnly = true)
    public List<BetPick> findPicks(Long betSlipId) {
        return betPickRepository.findByBetSlipId(betSlipId);
    }

    @Transactional(readOnly = true)
    public Map<Long, BetPick> findPicksByFixture(Long betSlipId) {
        return indexByFixture(findPicks(betSlipId));
    }

    public Map<Long, BetPick> indexByFixture(List<BetPick> picks) {
        return picks.stream()
                .collect(Collectors.toMap(BetPick::getMatchdayFixtureId, p -> p));
    }

    @Transactional(readOnly = true)
    public Map<Long, BetSlip> findSlipsByMatchday(Long fantaTeamId) {
        return findSlipsForTeam(fantaTeamId).stream()
                .collect(Collectors.toMap(BetSlip::getMatchdayId, s -> s));
    }

    @Transactional(readOnly = true)
    public BetSlip findSlipForUser(Long slipId, Long leagueId, Long userId) {
        BetSlip slip = betSlipRepository.findById(slipId)
                .orElseThrow(() -> new SlipNotFoundException(leagueId));

        FantaTeam myTeam = matchdayService.getFantaTeam(leagueId, userId).orElse(null);
        if (myTeam == null || !slip.getFantaTeamId().equals(myTeam.getId())) {
            log.warn("findSlipForUser: rejected — slip {} does not belong to user {} (league {})", slipId, userId, leagueId);
            throw new SlipNotFoundException(leagueId);
        }
        return slip;
    }
}