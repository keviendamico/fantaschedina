package com.fantacalcio.fantaschedina.repository;

import com.fantacalcio.fantaschedina.domain.entity.Invite;
import com.fantacalcio.fantaschedina.domain.enums.InviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InviteRepository extends JpaRepository<Invite, Long> {

    Optional<Invite> findByToken(String token);

    List<Invite> findByLeagueId(Long leagueId);

    List<Invite> findByStatusAndExpiresAtBefore(InviteStatus status, LocalDateTime now);

    boolean existsByLeagueIdAndEmailAndStatus(Long leagueId, String email, InviteStatus status);
}
