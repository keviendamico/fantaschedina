package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.*;
import com.fantacalcio.fantaschedina.domain.enums.*;
import com.fantacalcio.fantaschedina.repository.*;
import com.fantacalcio.fantaschedina.util.OutcomeConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoSubmitService {

    private final MatchdayRepository matchdayRepository;
    private final MatchdayFixtureRepository matchdayFixtureRepository;
    private final BetTemplateRepository betTemplateRepository;
    private final BetSlipRepository betSlipRepository;
    private final BetPickRepository betPickRepository;
    private final FantaTeamRepository fantaTeamRepository;
    private final LeagueMembershipRepository leagueMembershipRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final LeagueRepository leagueRepository;
    private final JackpotRepository jackpotRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public void autoSubmitMissing(Long matchdayId) {
        log.debug("autoSubmitMissing: called for matchday {}", matchdayId);
        Matchday matchday = matchdayRepository.findById(matchdayId).orElseThrow();
        League league = leagueRepository.findById(matchday.getLeagueId()).orElseThrow();

        List<MatchdayFixture> fixtures = matchdayFixtureRepository.findByMatchdayId(matchdayId);
        List<BetTemplate> templates = betTemplateRepository.findByLeagueIdOrderByOrderIndexAsc(league.getId());
        List<FantaTeam> allTeams = fantaTeamRepository.findByLeagueId(league.getId());
        log.debug("autoSubmitMissing: matchday {} -> {} fixtures, {} bet templates, {} teams in league",
                matchdayId, fixtures.size(), templates.size(), allTeams.size());

        int totalRequiredPicks = templates.stream().mapToInt(BetTemplate::getRequiredCount).sum();
        if (fixtures.size() != totalRequiredPicks) {
            log.error("Auto-submit skipped for matchday {}: fixture count ({}) != required picks ({})",
                    matchdayId, fixtures.size(), totalRequiredPicks);
            return;
        }

        Random random = new Random();
        int autoSubmitted = 0;

        for (FantaTeam team : allTeams) {
            if (betSlipRepository.existsByFantaTeamIdAndMatchdayId(team.getId(), matchdayId)) {
                log.debug("autoSubmitMissing: team {} already has a slip for matchday {}, skipping", team.getId(), matchdayId);
                continue;
            }
            log.debug("autoSubmitMissing: generating auto-submit slip for team {} on matchday {}", team.getId(), matchdayId);

            LeagueMembership membership = leagueMembershipRepository
                    .findById(team.getLeagueMembershipId()).orElseThrow();

            BetSlip slip = betSlipRepository.save(BetSlip.builder()
                    .matchdayId(matchdayId)
                    .fantaTeamId(team.getId())
                    .submittedAt(LocalDateTime.now())
                    .isAutoSubmitted(true)
                    .isAdminModified(false)
                    .status(BetSlipStatus.PENDING)
                    .amountCharged(league.getMatchdayCost())
                    .build());

            List<MatchdayFixture> shuffled = new ArrayList<>(fixtures);
            Collections.shuffle(shuffled, random);
            int fixtureIdx = 0;

            for (BetTemplate template : templates) {
                List<String> validOutcomes = OutcomeConstants.VALID_OUTCOMES.get(template.getOutcomeType());
                for (int i = 0; i < template.getRequiredCount(); i++) {
                    MatchdayFixture fixture = shuffled.get(fixtureIdx);
                    fixtureIdx++;
                    String picked = validOutcomes.get(random.nextInt(validOutcomes.size()));
                    betPickRepository.save(BetPick.builder()
                            .betSlipId(slip.getId())
                            .matchdayFixtureId(fixture.getId())
                            .outcomeType(template.getOutcomeType())
                            .pickedOutcome(picked)
                            .build());
                }
            }

            int newBalance = membership.getBalance() - league.getMatchdayCost();
            log.debug("autoSubmitMissing: team {} membership {} balance {} -> {} (charge {})",
                    team.getId(), membership.getId(), membership.getBalance(), newBalance, league.getMatchdayCost());
            membership.setBalance(newBalance);
            leagueMembershipRepository.save(membership);

            creditTransactionRepository.save(CreditTransaction.builder()
                    .leagueMembershipId(membership.getId())
                    .matchdayId(matchdayId)
                    .type(TransactionType.AUTO_CHARGE)
                    .amount(-league.getMatchdayCost())
                    .balanceAfter(newBalance)
                    .createdAt(LocalDateTime.now())
                    .note("Auto-submit giornata " + matchday.getNumber())
                    .build());

            Jackpot jackpot = jackpotRepository.findByLeagueId(league.getId()).orElseThrow();
            int jackpotBefore = jackpot.getCurrentAmount();
            jackpot.setCurrentAmount(jackpotBefore + league.getMatchdayCost());
            jackpotRepository.save(jackpot);
            log.debug("autoSubmitMissing: jackpot for league {} {} -> {}", league.getId(), jackpotBefore, jackpot.getCurrentAmount());

            userRepository.findById(membership.getUserId()).ifPresent(user ->
                    notificationService.sendAutoSubmitEmail(user, league, matchday, league.getMatchdayCost()));
            autoSubmitted++;
        }
        log.info("autoSubmitMissing: matchday {} -> {} auto-submitted slip(s) generated (out of {} teams)",
                matchdayId, autoSubmitted, allTeams.size());
    }
}