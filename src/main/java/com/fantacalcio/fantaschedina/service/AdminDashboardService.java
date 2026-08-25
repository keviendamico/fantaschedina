package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.Invite;
import com.fantacalcio.fantaschedina.domain.entity.Jackpot;
import com.fantacalcio.fantaschedina.domain.entity.League;
import com.fantacalcio.fantaschedina.domain.entity.Matchday;
import com.fantacalcio.fantaschedina.domain.enums.InviteStatus;
import com.fantacalcio.fantaschedina.dto.AdminLeagueCard;
import com.fantacalcio.fantaschedina.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final LeagueRepository leagueRepository;
    private final MatchdayRepository matchdayRepository;
    private final BetSlipRepository betSlipRepository;
    private final FantaTeamRepository fantaTeamRepository;
    private final JackpotRepository jackpotRepository;
    private final InviteRepository inviteRepository;
    private final MatchdayService matchdayService;

    public List<AdminLeagueCard> buildLeagueCards(Long adminUserId) {
        log.debug("buildLeagueCards: admin {}", adminUserId);
        return leagueRepository.findByCreatedByUserId(adminUserId).stream()
                .map(this::buildCard)
                .toList();
    }

    public List<Invite> getExpiringInvites() {
        List<Invite> invites = inviteRepository.findByStatusAndExpiresAtBefore(InviteStatus.PENDING, LocalDateTime.now().plusDays(3));
        log.debug("getExpiringInvites: {} invite(s) expiring within 3 days", invites.size());
        return invites;
    }

    private AdminLeagueCard buildCard(League league) {
        List<Matchday> matchdays = matchdayRepository.findByLeagueIdOrderByNumberAsc(league.getId());

        Matchday current = matchdayService.pickCurrent(matchdays);

        long slipsSubmitted = current != null
                ? betSlipRepository.findByMatchdayId(current.getId()).size()
                : 0;
        long totalTeams = fantaTeamRepository.findByLeagueId(league.getId()).size();

        Integer jackpot = jackpotRepository.findByLeagueId(league.getId())
                .map(Jackpot::getCurrentAmount)
                .orElse(null);

        LocalDateTime deadline = current != null
                ? matchdayService.effectiveDeadline(current, league.getBetDeadlineMinutes())
                : null;

        return new AdminLeagueCard(league, current, jackpot, slipsSubmitted, totalTeams, deadline);
    }
}
