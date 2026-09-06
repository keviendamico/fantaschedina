package com.fantacalcio.fantaschedina.scheduler;

import com.fantacalcio.fantaschedina.domain.entity.League;
import com.fantacalcio.fantaschedina.domain.entity.Matchday;
import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import com.fantacalcio.fantaschedina.repository.LeagueRepository;
import com.fantacalcio.fantaschedina.repository.MatchdayRepository;
import com.fantacalcio.fantaschedina.service.MatchdayClosingService;
import com.fantacalcio.fantaschedina.service.MatchdayService;
import com.fantacalcio.fantaschedina.util.AppClock;
import com.fantacalcio.fantaschedina.util.CorrelationId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchdaySafetyNetScheduler {

    private final MatchdayRepository matchdayRepository;
    private final LeagueRepository leagueRepository;
    private final MatchdayService matchdayService;
    private final MatchdayClosingService matchdayClosingService;

    /**
     * Fallback: closes any OPEN matchday whose effective deadline has passed.
     * Handles the gap between app restart and Quartz job re-execution.
     */
    @Scheduled(fixedDelay = 60_000)
    public void closeOverdueMatchdays() {
        CorrelationId.runAsJob(this::doCloseOverdueMatchdays);
    }

    private void doCloseOverdueMatchdays() {
        List<Matchday> openMatchdays = matchdayRepository.findByStatus(MatchdayStatus.OPEN);
        log.debug("Safety net tick: {} OPEN matchday(s) found", openMatchdays.size());
        for (Matchday matchday : openMatchdays) {
            League league = leagueRepository.findById(matchday.getLeagueId()).orElse(null);
            if (league == null) {
                log.warn("Safety net: matchday {} references missing league {}, skipping", matchday.getId(), matchday.getLeagueId());
                continue;
            }

            LocalDateTime now = AppClock.now();
            LocalDateTime deadline = matchdayService.effectiveDeadline(matchday, league.getBetDeadlineMinutes());
            log.debug("Safety net: matchday {} (league {}) startAt={} betDeadlineMinutes={} deadline={} now={}",
                    matchday.getId(), league.getId(), matchday.getStartAt(), league.getBetDeadlineMinutes(), deadline, now);

            if (deadline == null) {
                log.debug("Safety net: matchday {} has no startAt yet, skipping", matchday.getId());
                continue;
            }
            if (!now.isAfter(deadline)) {
                log.debug("Safety net: matchday {} deadline not reached yet ({} until close)", matchday.getId(), java.time.Duration.between(now, deadline));
                continue;
            }

            log.info("Safety net: closing overdue matchday {} (deadline was {})", matchday.getId(), deadline);
            try {
                matchdayClosingService.closeAndAutoSubmit(matchday.getId());
                log.debug("Safety net: closeAndAutoSubmit completed for matchday {}", matchday.getId());
            } catch (Exception e) {
                log.error("Safety net: closeAndAutoSubmit FAILED for matchday {} - will retry next tick", matchday.getId(), e);
            }
        }
    }
}
