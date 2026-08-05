package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.Invite;
import com.fantacalcio.fantaschedina.domain.entity.Jackpot;
import com.fantacalcio.fantaschedina.domain.entity.League;
import com.fantacalcio.fantaschedina.domain.entity.Matchday;
import com.fantacalcio.fantaschedina.domain.enums.InviteStatus;
import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import com.fantacalcio.fantaschedina.dto.AdminLeagueCard;
import com.fantacalcio.fantaschedina.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
        return leagueRepository.findByCreatedByUserId(adminUserId).stream()
                .map(this::buildCard)
                .toList();
    }

    public List<Invite> getExpiringInvites() {
        return inviteRepository.findByStatusAndExpiresAtBefore(InviteStatus.PENDING, LocalDateTime.now().plusDays(3));
    }

    private AdminLeagueCard buildCard(League league) {
        List<Matchday> matchdays = matchdayRepository.findByLeagueIdOrderByNumberAsc(league.getId());

        // Current matchday: prefer OPEN or CLOSED, fallback to first SCHEDULED
        Matchday current = matchdays.stream()
                .filter(md -> md.getStatus() == MatchdayStatus.OPEN || md.getStatus() == MatchdayStatus.CLOSED)
                .findFirst()
                .or(() -> matchdays.stream()
                        .filter(md -> md.getStatus() == MatchdayStatus.SCHEDULED)
                        .min(Comparator.comparingInt(Matchday::getNumber)))
                .orElse(null);

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
