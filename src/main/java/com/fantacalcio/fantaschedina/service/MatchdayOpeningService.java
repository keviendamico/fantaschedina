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
        if (matchday.getStatus() != MatchdayStatus.SCHEDULED || matchday.getStartAt() == null) {
            return;
        }
        if (isPreviousProcessedOrAbsent(matchday)) {
            open(matchday);
        }
    }

    /**
     * Trigger 2: called at the end of MatchdayProcessingService.process().
     * Opens the next SCHEDULED matchday with startAt set, if present.
     */
    @Transactional
    public void tryOpenNext(Long leagueId, int processedNumber) {
        List<Matchday> candidates = matchdayRepository.findByLeagueIdAndStatus(leagueId, MatchdayStatus.SCHEDULED);
        candidates.stream()
                .filter(md -> md.getNumber() > processedNumber && md.getStartAt() != null)
                .min(Comparator.comparingInt(Matchday::getNumber))
                .ifPresent(this::open);
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