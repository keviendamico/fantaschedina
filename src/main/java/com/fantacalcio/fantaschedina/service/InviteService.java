package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.*;
import com.fantacalcio.fantaschedina.domain.enums.*;
import com.fantacalcio.fantaschedina.exception.InvalidInviteException;
import com.fantacalcio.fantaschedina.exception.InviteActionException;
import com.fantacalcio.fantaschedina.exception.RegistrationException;
import com.fantacalcio.fantaschedina.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class InviteService {

    private final InviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final LeagueRepository leagueRepository;
    private final LeagueMembershipRepository leagueMembershipRepository;
    private final FantaTeamRepository fantaTeamRepository;
    private final MatchdayRepository matchdayRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    public void createInvite(Long leagueId, String email) {
        League league = leagueRepository.findById(leagueId)
            .orElseThrow(() -> new IllegalArgumentException("Lega non trovata"));

        Long existingUserId = userRepository.findByEmail(email)
            .map(User::getId)
            .orElse(null);

        Invite invite = Invite.builder()
            .leagueId(leagueId)
            .userId(existingUserId)
            .token(UUID.randomUUID().toString())
            .email(email)
            .expiresAt(LocalDateTime.now().plusDays(7))
            .status(InviteStatus.PENDING)
            .build();

        invite = inviteRepository.save(invite);
        notificationService.sendInviteEmail(invite, league);
    }

    @Transactional(readOnly = true)
    public List<Invite> findAll() {
        return inviteRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Invite findValidInvite(String token) {
        Invite invite = inviteRepository.findByToken(token)
            .orElseThrow(() -> new InvalidInviteException("Token non valido o inesistente"));

        if (invite.getStatus() == InviteStatus.USED) {
            throw new InvalidInviteException("Questo invito è già stato utilizzato");
        }
        if (invite.getStatus() == InviteStatus.EXPIRED || invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidInviteException("Questo invito è scaduto");
        }
        return invite;
    }

    public void acceptForExistingUser(String token, Long userId, String fantaTeamName) {
        Invite invite;
        try {
            invite = findValidInvite(token);
        } catch (InvalidInviteException e) {
            throw new InviteActionException(e.getMessage());
        }

        if (!userId.equals(invite.getUserId())) {
            throw new InviteActionException("Questo invito non è destinato a te");
        }
        if (leagueMembershipRepository.existsByLeagueIdAndUserId(invite.getLeagueId(), userId)) {
            throw new InviteActionException("Sei già membro di questa lega");
        }

        createMembershipAndTeam(invite.getLeagueId(), userId, fantaTeamName);
        markAsUsed(invite, userId);
    }

    public void acceptForNewUser(String token, String username, String password, String fantaTeamName) {
        Invite invite = findValidInvite(token);

        if (userRepository.existsByUsername(username)) {
            throw new RegistrationException("Username già in uso", token);
        }
        if (userRepository.existsByEmail(invite.getEmail())) {
            throw new RegistrationException("Esiste già un account con questa email", token);
        }

        User user = User.builder()
            .username(username)
            .email(invite.getEmail())
            .passwordHash(passwordEncoder.encode(password))
            .role(Role.USER)
            .enabled(true)
            .build();
        user = userRepository.save(user);

        createMembershipAndTeam(invite.getLeagueId(), user.getId(), fantaTeamName);
        markAsUsed(invite, user.getId());
    }

    private void createMembershipAndTeam(Long leagueId, Long userId, String fantaTeamName) {
        League league = leagueRepository.findById(leagueId).orElseThrow();

        long remaining = matchdayRepository.countByLeagueIdAndStatusIn(
            leagueId, List.of(MatchdayStatus.SCHEDULED, MatchdayStatus.OPEN)
        );
        int initialBalance = league.getMatchdayCost() * (int) remaining;

        LeagueMembership membership = LeagueMembership.builder()
            .leagueId(leagueId)
            .userId(userId)
            .balance(initialBalance)
            .joinedAt(LocalDateTime.now())
            .build();
        membership = leagueMembershipRepository.save(membership);

        CreditTransaction deposit = CreditTransaction.builder()
            .leagueMembershipId(membership.getId())
            .type(TransactionType.INITIAL_DEPOSIT)
            .amount(initialBalance)
            .balanceAfter(initialBalance)
            .createdAt(LocalDateTime.now())
            .note("Deposito iniziale all'iscrizione alla lega")
            .build();
        creditTransactionRepository.save(deposit);

        FantaTeam team = FantaTeam.builder()
            .leagueId(leagueId)
            .leagueMembershipId(membership.getId())
            .name(fantaTeamName)
            .build();
        fantaTeamRepository.save(team);
    }

    public void revokeInvite(Long inviteId) {
        Invite invite = inviteRepository.findById(inviteId)
            .orElseThrow(() -> new IllegalArgumentException("Invito non trovato"));
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new IllegalStateException("Solo gli inviti PENDING possono essere revocati");
        }
        invite.setStatus(InviteStatus.EXPIRED);
        inviteRepository.save(invite);
    }

    private void markAsUsed(Invite invite, Long userId) {
        invite.setStatus(InviteStatus.USED);
        invite.setUsedAt(LocalDateTime.now());
        invite.setUserId(userId);
        inviteRepository.save(invite);
    }
}
