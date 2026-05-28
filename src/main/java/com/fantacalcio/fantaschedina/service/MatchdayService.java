package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.*;
import com.fantacalcio.fantaschedina.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
        leagueMembershipRepository.findByLeagueIdAndUserId(leagueId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Non sei membro di questa lega"));
        return leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("Lega non trovata"));
    }

    public List<Matchday> getMatchdays(Long leagueId) {
        return matchdayRepository.findByLeagueIdOrderByNumberAsc(leagueId);
    }

    public Matchday getMatchday(Long matchdayId, Long leagueId) {
        Matchday matchday = matchdayRepository.findById(matchdayId)
                .orElseThrow(() -> new IllegalArgumentException("Giornata non trovata"));
        if (!matchday.getLeagueId().equals(leagueId)) {
            throw new IllegalArgumentException("La giornata non appartiene a questa lega");
        }
        return matchday;
    }

    public List<MatchdayFixture> getFixtures(Long matchdayId) {
        return matchdayFixtureRepository.findByMatchdayId(matchdayId);
    }

    public Map<Long, String> getTeamNames(Long leagueId) {
        return fantaTeamRepository.findByLeagueId(leagueId).stream()
                .collect(Collectors.toMap(FantaTeam::getId, FantaTeam::getName));
    }

    public LocalDateTime effectiveDeadline(Matchday matchday, int betDeadlineMinutes) {
        if (matchday.getStartAt() != null) return matchday.getStartAt().minusMinutes(betDeadlineMinutes);
        return null;
    }

    public Optional<FantaTeam> getFantaTeam(Long leagueId, Long userId) {
        return leagueMembershipRepository.findByLeagueIdAndUserId(leagueId, userId)
                .flatMap(m -> fantaTeamRepository.findByLeagueMembershipId(m.getId()));
    }

}