package com.fantacalcio.fantaschedina.repository;

import com.fantacalcio.fantaschedina.domain.entity.Matchday;
import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchdayRepository extends JpaRepository<Matchday, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Matchday m WHERE m.id = :id")
    Optional<Matchday> findByIdForUpdate(@Param("id") Long id);

    List<Matchday> findByLeagueIdOrderByNumberAsc(Long leagueId);

    List<Matchday> findByLeagueIdAndStatus(Long leagueId, MatchdayStatus status);

    List<Matchday> findByStatus(MatchdayStatus status);

    Optional<Matchday> findByLeagueIdAndNumber(Long leagueId, Integer number);

    long countByLeagueIdAndStatusIn(Long leagueId, List<MatchdayStatus> statuses);
}
