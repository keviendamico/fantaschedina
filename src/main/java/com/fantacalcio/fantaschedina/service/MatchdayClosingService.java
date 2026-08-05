package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.League;
import com.fantacalcio.fantaschedina.domain.entity.Matchday;
import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import com.fantacalcio.fantaschedina.repository.LeagueRepository;
import com.fantacalcio.fantaschedina.repository.MatchdayRepository;
import com.fantacalcio.fantaschedina.scheduler.MatchdayCloseJob;
import com.fantacalcio.fantaschedina.util.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchdayClosingService {

    private final Scheduler scheduler;
    private final MatchdayRepository matchdayRepository;
    private final LeagueRepository leagueRepository;
    private final MatchdayService matchdayService;
    private final AutoSubmitService autoSubmitService;

    /**
     * Closes the matchday and runs auto-submit for missing slips.
     * Idempotent: does nothing if the matchday is not OPEN.
     */
    @Transactional
    public void closeAndAutoSubmit(Long matchdayId) {
        log.debug("closeAndAutoSubmit: called for matchday {}", matchdayId);
        Matchday matchday = matchdayRepository.findByIdForUpdate(matchdayId).orElseThrow();
        if (matchday.getStatus() != MatchdayStatus.OPEN) {
            log.info("closeAndAutoSubmit: matchday {} is not OPEN (status={}), skipping", matchdayId, matchday.getStatus());
            return;
        }
        matchday.setStatus(MatchdayStatus.CLOSED);
        matchdayRepository.save(matchday);
        log.info("Matchday {} closed", matchdayId);

        log.debug("closeAndAutoSubmit: invoking autoSubmitMissing for matchday {}", matchdayId);
        autoSubmitService.autoSubmitMissing(matchdayId);
        log.debug("closeAndAutoSubmit: autoSubmitMissing returned for matchday {}", matchdayId);
    }

    /**
     * Schedules a one-shot Quartz job to close the matchday at its effective deadline.
     * Replaces any existing job for the same matchday.
     */
    public void scheduleCloseJob(Matchday matchday) {
        League league = leagueRepository.findById(matchday.getLeagueId()).orElseThrow();
        var deadline = matchdayService.effectiveDeadline(matchday, league.getBetDeadlineMinutes());
        log.debug("scheduleCloseJob: matchday {} startAt={} betDeadlineMinutes={} -> deadline={}",
                matchday.getId(), matchday.getStartAt(), league.getBetDeadlineMinutes(), deadline);
        if (deadline == null) {
            log.warn("scheduleCloseJob: matchday {} has no deadline, skipping", matchday.getId());
            return;
        }

        JobKey jobKey = jobKey(matchday.getId());
        TriggerKey triggerKey = triggerKey(matchday.getId());

        JobDetail job = JobBuilder.newJob(MatchdayCloseJob.class)
                .withIdentity(jobKey)
                .usingJobData(MatchdayCloseJob.MATCHDAY_ID_KEY, matchday.getId())
                .storeDurably(false)
                .build();

        Date fireAt = Date.from(deadline.atZone(AppClock.ZONE).toInstant());
        log.debug("scheduleCloseJob: matchday {} zone={} fireAt(epoch)={} fireAt(local)={}",
                matchday.getId(), AppClock.ZONE, fireAt.getTime(), fireAt);

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .startAt(fireAt)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                .build();

        try {
            boolean replaced = scheduler.checkExists(jobKey);
            if (replaced) {
                scheduler.deleteJob(jobKey);
                log.debug("scheduleCloseJob: deleted pre-existing job {} before rescheduling", jobKey);
            }
            scheduler.scheduleJob(job, trigger);
            log.info("Quartz job scheduled for matchday {} at {} (replaced existing={})", matchday.getId(), deadline, replaced);
        } catch (SchedulerException e) {
            log.error("scheduleCloseJob: failed to schedule job for matchday {}", matchday.getId(), e);
            throw new RuntimeException("Failed to schedule close job for matchday " + matchday.getId(), e);
        }
    }

    private static JobKey jobKey(Long matchdayId) {
        return JobKey.jobKey("matchday-close-" + matchdayId, "matchday");
    }

    private static TriggerKey triggerKey(Long matchdayId) {
        return TriggerKey.triggerKey("matchday-close-trigger-" + matchdayId, "matchday");
    }
}