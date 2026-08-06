package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.*;
import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import com.fantacalcio.fantaschedina.exception.MatchdayNotFoundException;
import com.fantacalcio.fantaschedina.exception.NotLeagueMemberException;
import com.fantacalcio.fantaschedina.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchdayService {

    private final MatchdayRepository matchdayRepository;
    private final MatchdayFixtureRepository matchdayFixtureRepository;
    private final LeagueMembershipRepository leagueMembershipRepository;
    private final FantaTeamRepository fantaTeamRepository;
    private final LeagueRepository leagueRepository;

    /**
     * Returns the league, validating that the user is a member.
     * Throws IllegalArgumentException if not a member or league not found.
     */
    public League getLeagueForMember(Long leagueId, Long userId) {
        if (leagueMembershipRepository.findByLeagueIdAndUserId(leagueId, userId).isEmpty()) {
            log.warn("getLeagueForMember: rejected — user {} is not a member of league {}", userId, leagueId);
            throw new NotLeagueMemberException();
        }
        return leagueRepository.findById(leagueId)
                .orElseThrow(() -> {
                    log.warn("getLeagueForMember: rejected — league {} not found", leagueId);
                    return new IllegalArgumentException("Lega non trovata");
                });
    }

    public List<Matchday> getMatchdays(Long leagueId) {
        return matchdayRepository.findByLeagueIdOrderByNumberAsc(leagueId);
    }

    public Matchday getMatchday(Long matchdayId, Long leagueId) {
        Matchday matchday = matchdayRepository.findById(matchdayId)
                .orElseThrow(() -> {
                    log.warn("getMatchday: rejected — matchday {} not found", matchdayId);
                    return new MatchdayNotFoundException(matchdayId, leagueId);
                });
        if (!matchday.getLeagueId().equals(leagueId)) {
            log.warn("getMatchday: rejected — matchday {} does not belong to league {}", matchdayId, leagueId);
            throw new MatchdayNotFoundException(matchdayId, leagueId);
        }
        return matchday;
    }

    public List<MatchdayFixture> getFixtures(Long matchdayId) {
        log.debug("getFixtures: matchday {}", matchdayId);
        return matchdayFixtureRepository.findByMatchdayId(matchdayId);
    }

    public Map<Long, String> getTeamNames(Long leagueId) {
        log.debug("getTeamNames: league {}", leagueId);
        return fantaTeamRepository.findByLeagueId(leagueId).stream()
                .collect(Collectors.toMap(FantaTeam::getId, FantaTeam::getName));
    }

    public LocalDateTime effectiveDeadline(Matchday matchday, int betDeadlineMinutes) {
        if (matchday.getStartAt() != null) return matchday.getStartAt().minusMinutes(betDeadlineMinutes);
        return null;
    }

    public Map<Long, LocalDateTime> getDeadlines(List<Matchday> matchdays, int betDeadlineMinutes) {
        return matchdays.stream()
                .filter(m -> effectiveDeadline(m, betDeadlineMinutes) != null)
                .collect(Collectors.toMap(Matchday::getId, m -> effectiveDeadline(m, betDeadlineMinutes)));
    }

    public Optional<Matchday> getNextOpenMatchday(List<Matchday> matchdays) {
        return matchdays.stream()
                .filter(m -> m.getStatus() == MatchdayStatus.OPEN)
                .findFirst();
    }

    public Optional<FantaTeam> getFantaTeam(Long leagueId, Long userId) {
        log.debug("getFantaTeam: league {} user {}", leagueId, userId);
        return leagueMembershipRepository.findByLeagueIdAndUserId(leagueId, userId)
                .flatMap(m -> fantaTeamRepository.findByLeagueMembershipId(m.getId()));
    }

}