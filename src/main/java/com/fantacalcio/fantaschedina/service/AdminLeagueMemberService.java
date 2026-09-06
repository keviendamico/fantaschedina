package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.*;
import com.fantacalcio.fantaschedina.domain.enums.AdminLogType;
import com.fantacalcio.fantaschedina.domain.enums.TransactionType;
import com.fantacalcio.fantaschedina.dto.LeagueMemberRow;
import com.fantacalcio.fantaschedina.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLeagueMemberService {

    private static final int MAX_TEAM_NAME_LENGTH = 255;

    private final LeagueMembershipRepository leagueMembershipRepository;
    private final FantaTeamRepository fantaTeamRepository;
    private final UserRepository userRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final LeagueAuditLogRepository leagueAuditLogRepository;

    @Transactional(readOnly = true)
    public List<LeagueMemberRow> getMembers(Long leagueId) {
        log.debug("getMembers: league {}", leagueId);
        return leagueMembershipRepository.findByLeagueId(leagueId).stream()
                .map(membership -> {
                    User user = userRepository.findById(membership.getUserId()).orElseThrow();
                    FantaTeam team = fantaTeamRepository
                            .findByLeagueMembershipId(membership.getId()).orElse(null);
                    return new LeagueMemberRow(membership, user, team);
                })
                .sorted((a, b) -> a.user().getUsername().compareToIgnoreCase(b.user().getUsername()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void adjustMemberBalance(Long membershipId, int delta, String note) {
        log.debug("adjustMemberBalance: membership {} delta {}", membershipId, delta);
        if (delta == 0) {
            log.warn("adjustMemberBalance: rejected - delta is zero for membership {}", membershipId);
            throw new IllegalArgumentException("Il delta non può essere zero.");
        }

        LeagueMembership membership = leagueMembershipRepository.findById(membershipId)
                .orElseThrow(() -> {
                    log.warn("adjustMemberBalance: rejected - membership {} not found", membershipId);
                    return new IllegalArgumentException("Iscrizione non trovata.");
                });

        int newBalance = membership.getBalance() + delta;
        membership.setBalance(newBalance);
        leagueMembershipRepository.save(membership);

        String resolvedNote = note != null && !note.isBlank() ? note : "Rettifica manuale admin";

        creditTransactionRepository.save(CreditTransaction.builder()
                .leagueMembershipId(membershipId)
                .type(TransactionType.ADMIN_ADJUST)
                .amount(delta)
                .balanceAfter(newBalance)
                .createdAt(LocalDateTime.now())
                .note(resolvedNote)
                .build());

        leagueAuditLogRepository.save(LeagueAuditLog.builder()
                .leagueId(membership.getLeagueId())
                .type(AdminLogType.CREDIT_ADJUST)
                .targetMembershipId(membershipId)
                .amount(delta)
                .note(resolvedNote)
                .createdAt(LocalDateTime.now())
                .build());

        log.info("adjustMemberBalance: membership {} balance {} -> {} (delta {}), note=\"{}\"",
                membershipId, newBalance - delta, newBalance, delta, resolvedNote);
    }

    @Transactional
    public void renameTeam(Long leagueId, Long membershipId, String newName) {
        log.debug("renameTeam: league {} membership {}", leagueId, membershipId);
        String resolvedName = newName != null ? newName.trim() : "";

        if (resolvedName.isEmpty()) {
            log.warn("renameTeam: rejected - empty name for membership {}", membershipId);
            throw new IllegalArgumentException("Il nome della squadra non può essere vuoto.");
        }
        if (resolvedName.length() > MAX_TEAM_NAME_LENGTH) {
            log.warn("renameTeam: rejected - name too long ({} chars) for membership {}", resolvedName.length(), membershipId);
            throw new IllegalArgumentException("Il nome della squadra non può superare i " + MAX_TEAM_NAME_LENGTH + " caratteri.");
        }

        LeagueMembership membership = leagueMembershipRepository.findById(membershipId)
                .orElseThrow(() -> {
                    log.warn("renameTeam: rejected - membership {} not found", membershipId);
                    return new IllegalArgumentException("Iscrizione non trovata.");
                });
        if (!membership.getLeagueId().equals(leagueId)) {
            log.warn("renameTeam: rejected - membership {} does not belong to league {}", membershipId, leagueId);
            throw new IllegalArgumentException("Iscrizione non appartenente a questa lega.");
        }

        FantaTeam team = fantaTeamRepository.findByLeagueMembershipId(membershipId)
                .orElseThrow(() -> {
                    log.warn("renameTeam: rejected - no team for membership {}", membershipId);
                    return new IllegalArgumentException("Squadra non trovata.");
                });

        String previousName = team.getName();
        if (previousName.equals(resolvedName)) {
            log.debug("renameTeam: team {} name unchanged, skipping", team.getId());
            return;
        }

        fantaTeamRepository.findByLeagueIdAndNameIgnoreCase(leagueId, resolvedName)
                .filter(other -> !other.getId().equals(team.getId()))
                .ifPresent(other -> {
                    log.warn("renameTeam: rejected - name \"{}\" already used by team {} in league {}", resolvedName, other.getId(), leagueId);
                    throw new IllegalArgumentException("Esiste già una squadra con questo nome in questa lega.");
                });

        team.setName(resolvedName);
        fantaTeamRepository.save(team);

        String note = "Nome squadra: " + previousName + " → " + resolvedName;
        leagueAuditLogRepository.save(LeagueAuditLog.builder()
                .leagueId(leagueId)
                .type(AdminLogType.TEAM_RENAME)
                .targetMembershipId(membershipId)
                .note(note)
                .createdAt(LocalDateTime.now())
                .build());

        log.info("renameTeam: team {} (league {}) renamed \"{}\" -> \"{}\"", team.getId(), leagueId, previousName, resolvedName);
    }

    @Transactional(readOnly = true)
    public List<LeagueAuditLog> getAuditLog(Long leagueId) {
        log.debug("getAuditLog: league {}", leagueId);
        return leagueAuditLogRepository.findByLeagueIdOrderByCreatedAtDesc(leagueId);
    }

    @Transactional(readOnly = true)
    public LeagueMembership getMembershipForLeague(Long leagueId, Long membershipId) {
        log.debug("getMembershipForLeague: league {} membership {}", leagueId, membershipId);
        LeagueMembership membership = leagueMembershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Iscrizione non trovata."));
        if (!membership.getLeagueId().equals(leagueId)) {
            log.warn("getMembershipForLeague: rejected - membership {} does not belong to league {}", membershipId, leagueId);
            throw new IllegalArgumentException("Iscrizione non appartenente a questa lega.");
        }
        return membership;
    }
}
