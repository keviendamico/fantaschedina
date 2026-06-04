package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.*;
import com.fantacalcio.fantaschedina.domain.enums.*;
import com.fantacalcio.fantaschedina.exception.BetValidationException;
import com.fantacalcio.fantaschedina.exception.SlipNotFoundException;
import com.fantacalcio.fantaschedina.dto.BetPickRequest;
import com.fantacalcio.fantaschedina.dto.BetSlipRequest;
import com.fantacalcio.fantaschedina.repository.*;
import com.fantacalcio.fantaschedina.util.OutcomeConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        League league = leagueRepository.findById(leagueId).orElseThrow();
        Matchday matchday = matchdayRepository.findById(matchdayId).orElseThrow();

        if (matchday.getStatus() != MatchdayStatus.OPEN) {
            throw new BetValidationException("La giornata non è aperta alle scommesse.", leagueId, matchdayId);
        }

        LocalDateTime deadline = matchdayService.effectiveDeadline(matchday, league.getBetDeadlineMinutes());
        if (deadline != null && LocalDateTime.now().isAfter(deadline)) {
            throw new BetValidationException("La scadenza per questa giornata è già passata.", leagueId, matchdayId);
        }

        LeagueMembership membership = leagueMembershipRepository
                .findByLeagueIdAndUserId(leagueId, userId)
                .orElseThrow(() -> new BetValidationException("Non sei membro di questa lega.", leagueId, matchdayId));
        FantaTeam fantaTeam = fantaTeamRepository
                .findByLeagueMembershipId(membership.getId())
                .orElseThrow(() -> new BetValidationException("Nessuna squadra trovata per il tuo account.", leagueId, matchdayId));

        if (betSlipRepository.existsByFantaTeamIdAndMatchdayId(fantaTeam.getId(), matchdayId)) {
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

        return slip;
    }

    private void validatePicks(List<BetPickRequest> picks, List<BetTemplate> templates,
                               Long leagueId, Long matchdayId) {
        if (picks == null || picks.isEmpty()) {
            throw new BetValidationException("La schedina non contiene nessun pronostico.", leagueId, matchdayId);
        }

        Set<Long> validFixtureIds = matchdayFixtureRepository.findByMatchdayId(matchdayId).stream()
                .map(MatchdayFixture::getId)
                .collect(Collectors.toSet());

        for (BetPickRequest pick : picks) {
            if (!validFixtureIds.contains(pick.getFixtureId())) {
                throw new BetValidationException("Una partita selezionata non appartiene a questa giornata.", leagueId, matchdayId);
            }
            Set<String> valid = OutcomeConstants.VALID_OUTCOMES_SET.get(pick.getOutcomeType());
            if (valid == null || !valid.contains(pick.getPickedOutcome())) {
                throw new BetValidationException("Esito non valido: " + pick.getPickedOutcome(), leagueId, matchdayId);
            }
        }

        Set<Long> pickedFixtureIds = picks.stream()
                .map(BetPickRequest::getFixtureId)
                .collect(Collectors.toSet());
        if (pickedFixtureIds.size() < picks.size()) {
            throw new BetValidationException("Ogni partita può essere giocata una sola volta.", leagueId, matchdayId);
        }
        if (!pickedFixtureIds.equals(validFixtureIds)) {
            throw new BetValidationException("Devi giocare tutte le partite della giornata, una per una.", leagueId, matchdayId);
        }

        Map<OutcomeType, Long> pickCounts = picks.stream()
                .collect(Collectors.groupingBy(BetPickRequest::getOutcomeType, Collectors.counting()));

        for (BetTemplate template : templates) {
            long actual = pickCounts.getOrDefault(template.getOutcomeType(), 0L);
            if (actual != template.getRequiredCount()) {
                throw new BetValidationException(
                        "Pronostici di tipo " + template.getOutcomeType().name() +
                        ": richiesti " + template.getRequiredCount() + ", inviati " + actual + ".",
                        leagueId, matchdayId);
            }
        }

        for (OutcomeType type : pickCounts.keySet()) {
            boolean inTemplate = templates.stream().anyMatch(t -> t.getOutcomeType() == type);
            if (!inTemplate) {
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
    public BetSlip findSlipForUser(Long slipId, Long leagueId, Long userId) {
        BetSlip slip = betSlipRepository.findById(slipId)
                .orElseThrow(() -> new SlipNotFoundException(leagueId));

        FantaTeam myTeam = matchdayService.getFantaTeam(leagueId, userId).orElse(null);
        if (myTeam == null || !slip.getFantaTeamId().equals(myTeam.getId())) {
            throw new SlipNotFoundException(leagueId);
        }
        return slip;
    }
}