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
 * Idempotent via {@code Matchday.reminderSentAt}: once the reminder instant has
 * passed for a matchday it fires on the next tick and is never repeated, so a
 * delayed or skipped tick (GC pause, slow email send, etc.) doesn't lose the reminder.
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
        LocalDateTime now = LocalDateTime.now();

        List<Matchday> openMatchdays = matchdayRepository.findByStatus(MatchdayStatus.OPEN);
        for (Matchday matchday : openMatchdays) {
            if (matchday.getReminderSentAt() != null) continue;

            League league = leagueRepository.findById(matchday.getLeagueId()).orElse(null);
            if (league == null) continue;

            LocalDateTime deadline = matchdayService.effectiveDeadline(matchday, league.getBetDeadlineMinutes());
            if (deadline == null) continue;

            LocalDateTime reminderAt = deadline.minusMinutes(minutesBeforeDeadline);
            if (now.isBefore(reminderAt)) continue;

            remindMissingTeams(league, matchday, deadline);
            matchday.setReminderSentAt(now);
            matchdayRepository.save(matchday);
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
