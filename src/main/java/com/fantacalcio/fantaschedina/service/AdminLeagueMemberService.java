package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.*;
import com.fantacalcio.fantaschedina.domain.enums.TransactionType;
import com.fantacalcio.fantaschedina.dto.LeagueMemberRow;
import com.fantacalcio.fantaschedina.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminLeagueMemberService {

    private final LeagueMembershipRepository leagueMembershipRepository;
    private final FantaTeamRepository fantaTeamRepository;
    private final UserRepository userRepository;
    private final CreditTransactionRepository creditTransactionRepository;

    @Transactional(readOnly = true)
    public List<LeagueMemberRow> getMembers(Long leagueId) {
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
        if (delta == 0) throw new IllegalArgumentException("Il delta non può essere zero.");

        LeagueMembership membership = leagueMembershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Iscrizione non trovata."));

        int newBalance = membership.getBalance() + delta;
        membership.setBalance(newBalance);
        leagueMembershipRepository.save(membership);

        creditTransactionRepository.save(CreditTransaction.builder()
                .leagueMembershipId(membershipId)
                .type(TransactionType.ADMIN_ADJUST)
                .amount(delta)
                .balanceAfter(newBalance)
                .createdAt(LocalDateTime.now())
                .note(note != null && !note.isBlank() ? note : "Rettifica manuale admin")
                .build());
    }

}