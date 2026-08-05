package com.fantacalcio.fantaschedina.scheduler;

import com.fantacalcio.fantaschedina.domain.entity.FantaTeam;
import com.fantacalcio.fantaschedina.domain.entity.League;
import com.fantacalcio.fantaschedina.domain.entity.LeagueMembership;
import com.fantacalcio.fantaschedina.domain.entity.Matchday;
import com.fantacalcio.fantaschedina.domain.entity.User;
import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import com.fantacalcio.fantaschedina.repository.BetSlipRepository;
import com.fantacalcio.fantaschedina.repository.FantaTeamRepository;
import com.fantacalcio.fantaschedina.repository.LeagueMembershipRepository;
import com.fantacalcio.fantaschedina.repository.LeagueRepository;
import com.fantacalcio.fantaschedina.repository.MatchdayRepository;
import com.fantacalcio.fantaschedina.repository.UserRepository;
import com.fantacalcio.fantaschedina.service.MatchdayService;
import com.fantacalcio.fantaschedina.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sends a one-time reminder to team owners who haven't submitted a bet slip yet,
 * a configurable number of minutes before the matchday's effective deadline.
 * Fires exactly once per matchday: it only matches when the reminder instant
 * falls within the current minute-tick of this @Scheduled poll.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchdayReminderScheduler {

    private final MatchdayRepository matchdayRepository;
    private final LeagueRepository leagueRepository;
    private final MatchdayService matchdayService;
    private final FantaTeamRepository fantaTeamRepository;
    private final BetSlipRepository betSlipRepository;
    private final LeagueMembershipRepository leagueMembershipRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Value("${app.reminder.minutes-before-deadline:60}")
    private int minutesBeforeDeadline;

    @Scheduled(cron = "0 * * * * *")
    public void sendPreDeadlineReminders() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);

        List<Matchday> openMatchdays = matchdayRepository.findByStatus(MatchdayStatus.OPEN);
        for (Matchday matchday : openMatchdays) {
            League league = leagueRepository.findById(matchday.getLeagueId()).orElse(null);
            if (league == null) continue;

            LocalDateTime deadline = matchdayService.effectiveDeadline(matchday, league.getBetDeadlineMinutes());
            if (deadline == null) continue;

            LocalDateTime reminderAt = deadline.minusMinutes(minutesBeforeDeadline).withSecond(0).withNano(0);
            if (!reminderAt.equals(now)) continue;

            remindMissingTeams(league, matchday, deadline);
        }
    }

    private void remindMissingTeams(League league, Matchday matchday, LocalDateTime deadline) {
        List<FantaTeam> teams = fantaTeamRepository.findByLeagueId(league.getId());
        int sent = 0;
        for (FantaTeam team : teams) {
            if (betSlipRepository.existsByFantaTeamIdAndMatchdayId(team.getId(), matchday.getId())) continue;

            LeagueMembership membership = leagueMembershipRepository
                    .findById(team.getLeagueMembershipId()).orElse(null);
            if (membership == null) continue;

            User user = userRepository.findById(membership.getUserId()).orElse(null);
            if (user == null) continue;

            notificationService.sendReminderEmail(user, league, matchday, deadline);
            sent++;
        }
        if (sent > 0) {
            log.info("Sent {} pre-deadline reminders for matchday {}", sent, matchday.getId());
        }
    }
}
