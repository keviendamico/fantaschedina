package com.fantacalcio.fantaschedina.repository;

import com.fantacalcio.fantaschedina.domain.entity.FantaTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FantaTeamRepository extends JpaRepository<FantaTeam, Long> {

    List<FantaTeam> findByLeagueId(Long leagueId);

    Optional<FantaTeam> findByLeagueMembershipId(Long leagueMembershipId);

    Optional<FantaTeam> findByLeagueIdAndName(Long leagueId, String name);

    Optional<FantaTeam> findByLeagueIdAndNameIgnoreCase(Long leagueId, String name);
}
