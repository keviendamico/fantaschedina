package com.fantacalcio.fantaschedina.repository;

import com.fantacalcio.fantaschedina.domain.entity.LeagueAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeagueAuditLogRepository extends JpaRepository<LeagueAuditLog, Long> {

    List<LeagueAuditLog> findByLeagueIdOrderByCreatedAtDesc(Long leagueId);
}