package com.fantacalcio.fantaschedina.scheduler;

import com.fantacalcio.fantaschedina.service.MatchdayClosingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchdayCloseJob implements Job {

    public static final String MATCHDAY_ID_KEY = "matchdayId";

    private final MatchdayClosingService matchdayClosingService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap data = context.getMergedJobDataMap();
        Long matchdayId = data.getLong(MATCHDAY_ID_KEY);
        log.debug("MatchdayCloseJob: firing for matchday {} (trigger={})", matchdayId, context.getTrigger().getKey());
        try {
            matchdayClosingService.closeAndAutoSubmit(matchdayId);
            log.debug("MatchdayCloseJob: completed for matchday {}", matchdayId);
        } catch (Exception e) {
            log.error("MatchdayCloseJob: closeAndAutoSubmit FAILED for matchday {} — safety net will retry", matchdayId, e);
            throw new JobExecutionException(e);
        }
    }
}