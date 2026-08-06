package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.PasswordResetToken;
import com.fantacalcio.fantaschedina.domain.entity.User;
import com.fantacalcio.fantaschedina.domain.enums.PasswordResetStatus;
import com.fantacalcio.fantaschedina.exception.InvalidPasswordResetException;
import com.fantacalcio.fantaschedina.repository.PasswordResetTokenRepository;
import com.fantacalcio.fantaschedina.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    public void requestReset(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Do not log a "user not found" outcome: the caller's response is intentionally
            // identical either way, to prevent user enumeration via logs or timing.
            log.debug("requestReset: no user found for {}", email);
            return;
        }

        passwordResetTokenRepository.findByUserIdAndStatus(user.getId(), PasswordResetStatus.PENDING)
            .forEach(old -> {
                old.setStatus(PasswordResetStatus.EXPIRED);
                passwordResetTokenRepository.save(old);
            });

        PasswordResetToken resetToken = PasswordResetToken.builder()
            .userId(user.getId())
            .token(UUID.randomUUID().toString())
            .expiresAt(LocalDateTime.now().plusHours(1))
            .status(PasswordResetStatus.PENDING)
            .build();
        resetToken = passwordResetTokenRepository.save(resetToken);
        log.info("requestReset: reset token created for user {}", user.getId());

        notificationService.sendPasswordResetEmail(user, resetToken.getToken());
    }

    @Transactional(readOnly = true)
    public PasswordResetToken findValidToken(String token) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
            .orElseThrow(() -> new InvalidPasswordResetException("Link non valido o inesistente"));

        if (resetToken.getStatus() == PasswordResetStatus.USED) {
            log.warn("findValidToken: rejected — token for user {} already used", resetToken.getUserId());
            throw new InvalidPasswordResetException("Questo link è già stato utilizzato");
        }
        if (resetToken.getStatus() == PasswordResetStatus.EXPIRED || resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("findValidToken: rejected — token for user {} expired", resetToken.getUserId());
            throw new InvalidPasswordResetException("Questo link è scaduto");
        }
        return resetToken;
    }

    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = findValidToken(token);

        User user = userRepository.findById(resetToken.getUserId())
            .orElseThrow(() -> new InvalidPasswordResetException("Utente non trovato"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setStatus(PasswordResetStatus.USED);
        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);
        log.info("resetPassword: password reset for user {}", user.getId());
    }
}
