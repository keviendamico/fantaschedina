package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.*;
import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import com.fantacalcio.fantaschedina.dto.UserLeagueCard;
import com.fantacalcio.fantaschedina.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDashboardService {

    private final LeagueMembershipRepository leagueMembershipRepository;
    private final LeagueRepository leagueRepository;
    private final MatchdayRepository matchdayRepository;
    private final FantaTeamRepository fantaTeamRepository;
    private final BetSlipRepository betSlipRepository;
    private final JackpotRepository jackpotRepository;
    private final MatchdayService matchdayService;

    public List<UserLeagueCard> buildCards(Long userId) {
        log.debug("buildCards: user {}", userId);
        return leagueMembershipRepository.findByUserId(userId).stream()
                .map(membership -> buildCard(membership, userId))
                .toList();
    }

    private UserLeagueCard buildCard(LeagueMembership membership, Long userId) {
        League league = leagueRepository.findById(membership.getLeagueId()).orElseThrow();
        List<Matchday> matchdays = matchdayRepository.findByLeagueIdOrderByNumberAsc(league.getId());

        Matchday current = matchdays.stream()
                .filter(md -> md.getStatus() == MatchdayStatus.OPEN || md.getStatus() == MatchdayStatus.CLOSED)
                .findFirst()
                .or(() -> matchdays.stream()
                        .filter(md -> md.getStatus() == MatchdayStatus.SCHEDULED)
                        .min(Comparator.comparingInt(Matchday::getNumber)))
                .orElse(null);

        LocalDateTime deadline = current != null
                ? matchdayService.effectiveDeadline(current, league.getBetDeadlineMinutes())
                : null;

        BetSlip slip = null;
        if (current != null && current.getStatus() == MatchdayStatus.OPEN) {
            FantaTeam team = fantaTeamRepository.findByLeagueMembershipId(membership.getId()).orElse(null);
            if (team != null) {
                slip = betSlipRepository.findByFantaTeamIdAndMatchdayId(team.getId(), current.getId()).orElse(null);
            }
        }

        Integer jackpot = jackpotRepository.findByLeagueId(league.getId())
                .map(Jackpot::getCurrentAmount)
                .orElse(null);

        return new UserLeagueCard(league, membership, current, slip, jackpot, deadline);
    }
}