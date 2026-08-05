package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.Matchday;
import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import com.fantacalcio.fantaschedina.repository.MatchdayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchdayOpeningService {

    private final MatchdayRepository matchdayRepository;
    private final MatchdayClosingService matchdayClosingService;

    /**
     * Trigger 1: called after admin sets startAt on a SCHEDULED matchday.
     * Opens the matchday immediately if the previous one is PROCESSED (or none exists).
     */
    @Transactional
    public void tryOpen(Matchday matchday) {
        log.debug("tryOpen: matchday {} status={} startAt={}", matchday.getId(), matchday.getStatus(), matchday.getStartAt());
        if (matchday.getStatus() != MatchdayStatus.SCHEDULED || matchday.getStartAt() == null) {
            log.debug("tryOpen: matchday {} not eligible (status={}, startAt={}), skipping", matchday.getId(), matchday.getStatus(), matchday.getStartAt());
            return;
        }
        if (isPreviousProcessedOrAbsent(matchday)) {
            open(matchday);
        } else {
            log.debug("tryOpen: matchday {} previous matchday not PROCESSED yet, staying SCHEDULED", matchday.getId());
        }
    }

    /**
     * Trigger 2: called at the end of MatchdayProcessingService.process().
     * Opens the next SCHEDULED matchday with startAt set, if present.
     */
    @Transactional
    public void tryOpenNext(Long leagueId, int processedNumber) {
        List<Matchday> candidates = matchdayRepository.findByLeagueIdAndStatus(leagueId, MatchdayStatus.SCHEDULED);
        log.debug("tryOpenNext: league {} processedNumber={} -> {} SCHEDULED candidate(s)", leagueId, processedNumber, candidates.size());
        var next = candidates.stream()
                .filter(md -> md.getNumber() > processedNumber && md.getStartAt() != null)
                .min(Comparator.comparingInt(Matchday::getNumber));
        if (next.isPresent()) {
            open(next.get());
        } else {
            log.debug("tryOpenNext: league {} no eligible next matchday (needs number > {} and startAt set)", leagueId, processedNumber);
        }
    }

    private void open(Matchday matchday) {
        matchday.setStatus(MatchdayStatus.OPEN);
        matchdayRepository.save(matchday);
        log.info("Matchday {} opened", matchday.getId());

        matchdayClosingService.scheduleCloseJob(matchday);
    }

    private boolean isPreviousProcessedOrAbsent(Matchday matchday) {
        return matchdayRepository
                .findByLeagueIdAndNumber(matchday.getLeagueId(), matchday.getNumber() - 1)
                .map(prev -> prev.getStatus() == MatchdayStatus.PROCESSED)
                .orElse(true); // no previous matchday → can open
    }
}