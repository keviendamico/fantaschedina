package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.Matchday;
import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import com.fantacalcio.fantaschedina.repository.MatchdayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchdayOpeningService {

    private static final Set<MatchdayStatus> RESOLVED_STATUSES = EnumSet.of(
            MatchdayStatus.PROCESSED,
            MatchdayStatus.AWAITING_RECOVERY,
            MatchdayStatus.RESULTS_LOADED);

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
        if (isPreviousResolved(matchday)) {
            open(matchday);
        } else {
            log.debug("tryOpen: matchday {} previous matchday not resolved yet, staying SCHEDULED", matchday.getId());
        }
    }

    /**
     * Trigger 2: called whenever a matchday stops accepting bets — when it is processed, and when its
     * results are loaded (fully or partially) but processing is still pending.
     * Opens the next SCHEDULED matchday with startAt set, if present.
     */
    @Transactional
    public void tryOpenNext(Long leagueId, int fromNumber) {
        List<Matchday> candidates = matchdayRepository.findByLeagueIdAndStatus(leagueId, MatchdayStatus.SCHEDULED);
        log.debug("tryOpenNext: league {} fromNumber={} -> {} SCHEDULED candidate(s)", leagueId, fromNumber, candidates.size());
        var next = candidates.stream()
                .filter(md -> md.getNumber() > fromNumber && md.getStartAt() != null)
                .min(Comparator.comparingInt(Matchday::getNumber));
        if (next.isEmpty()) {
            log.debug("tryOpenNext: league {} no eligible next matchday (needs number > {} and startAt set)", leagueId, fromNumber);
            return;
        }
        // Guards the "at most one OPEN matchday" invariant: with a matchday awaiting a postponed
        // match, the one right after it may already be open.
        if (!isPreviousResolved(next.get())) {
            log.debug("tryOpenNext: league {} next matchday {} not eligible, its previous is still in play",
                    leagueId, next.get().getId());
            return;
        }
        open(next.get());
    }

    private void open(Matchday matchday) {
        matchday.setStatus(MatchdayStatus.OPEN);
        matchdayRepository.save(matchday);
        log.info("Matchday {} opened", matchday.getId());

        matchdayClosingService.scheduleCloseJob(matchday);
    }

    /**
     * A matchday is "resolved" once it no longer accepts bets and its results have been (at least
     * partially) loaded - so the next one can be played even while it waits for a postponed match
     * or for its turn in the processing queue.
     */
    private boolean isPreviousResolved(Matchday matchday) {
        return matchdayRepository
                .findByLeagueIdAndNumber(matchday.getLeagueId(), matchday.getNumber() - 1)
                .map(prev -> RESOLVED_STATUSES.contains(prev.getStatus()))
                .orElse(true); // no previous matchday → can open
    }
}