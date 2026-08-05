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
import com.fantacalcio.fantaschedina.util.AppClock;
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
        LocalDateTime now = AppClock.now();

        List<Matchday> openMatchdays = matchdayRepository.findByStatus(MatchdayStatus.OPEN);
        log.debug("Reminder tick: {} OPEN matchday(s), minutesBeforeDeadline={}", openMatchdays.size(), minutesBeforeDeadline);
        for (Matchday matchday : openMatchdays) {
            if (matchday.getReminderSentAt() != null) {
                log.debug("Reminder: matchday {} already sent at {}, skipping", matchday.getId(), matchday.getReminderSentAt());
                continue;
            }

            League league = leagueRepository.findById(matchday.getLeagueId()).orElse(null);
            if (league == null) {
                log.warn("Reminder: matchday {} references missing league {}, skipping", matchday.getId(), matchday.getLeagueId());
                continue;
            }

            LocalDateTime deadline = matchdayService.effectiveDeadline(matchday, league.getBetDeadlineMinutes());
            if (deadline == null) {
                log.debug("Reminder: matchday {} has no startAt yet, skipping", matchday.getId());
                continue;
            }

            LocalDateTime reminderAt = deadline.minusMinutes(minutesBeforeDeadline);
            log.debug("Reminder: matchday {} deadline={} reminderAt={} now={}", matchday.getId(), deadline, reminderAt, now);
            if (now.isBefore(reminderAt)) {
                log.debug("Reminder: matchday {} reminder window not reached yet ({} until reminder)",
                        matchday.getId(), java.time.Duration.between(now, reminderAt));
                continue;
            }

            log.info("Reminder: sending pre-deadline reminders for matchday {} (deadline {})", matchday.getId(), deadline);
            try {
                remindMissingTeams(league, matchday, deadline);
                matchday.setReminderSentAt(now);
                matchdayRepository.save(matchday);
                log.debug("Reminder: matchday {} marked reminderSentAt={}", matchday.getId(), now);
            } catch (Exception e) {
                log.error("Reminder: failed sending reminders for matchday {} — reminderSentAt NOT set, will retry next tick", matchday.getId(), e);
            }
        }
    }

    private void remindMissingTeams(League league, Matchday matchday, LocalDateTime deadline) {
        List<FantaTeam> teams = fantaTeamRepository.findByLeagueId(league.getId());
        log.debug("Reminder: {} team(s) in league {} to check for matchday {}", teams.size(), league.getId(), matchday.getId());
        int sent = 0;
        for (FantaTeam team : teams) {
            if (betSlipRepository.existsByFantaTeamIdAndMatchdayId(team.getId(), matchday.getId())) {
                log.debug("Reminder: team {} already submitted, skipping", team.getId());
                continue;
            }

            LeagueMembership membership = leagueMembershipRepository
                    .findById(team.getLeagueMembershipId()).orElse(null);
            if (membership == null) {
                log.warn("Reminder: team {} references missing membership {}, skipping", team.getId(), team.getLeagueMembershipId());
                continue;
            }

            User user = userRepository.findById(membership.getUserId()).orElse(null);
            if (user == null) {
                log.warn("Reminder: membership {} references missing user {}, skipping", membership.getId(), membership.getUserId());
                continue;
            }

            log.debug("Reminder: sending to user {} ({}) notificationsEnabled={}", user.getId(), user.getEmail(), user.getNotificationsEnabled());
            notificationService.sendReminderEmail(user, league, matchday, deadline);
            sent++;
        }
        log.info("Reminder: {} reminder(s) attempted for matchday {} ({} teams total)", sent, matchday.getId(), teams.size());
    }
}
