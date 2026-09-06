package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.User;
import com.fantacalcio.fantaschedina.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    // Called on nearly every request via ControllerAdvice-free resolution - no per-call debug log
    // to avoid flooding; the failure branch below is the actually interesting signal.
    public Long getUserId(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> {
                log.error("getUserId: authenticated user \"{}\" not found in database - inconsistent security state", username);
                return new IllegalStateException("Utente autenticato non trovato nel database: " + username);
            })
            .getId();
    }

    public User getById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> {
                log.error("getById: user {} not found in database - inconsistent state", id);
                return new IllegalStateException("Utente non trovato: " + id);
            });
    }

    @Transactional
    public void updateNotificationPreference(Long userId, boolean enabled) {
        log.debug("updateNotificationPreference: user {} enabled={}", userId, enabled);
        User user = getById(userId);
        user.setNotificationsEnabled(enabled);
        userRepository.save(user);
        log.info("updateNotificationPreference: user {} notificationsEnabled={}", userId, enabled);
    }

    @Transactional
    public void adminUpdateUser(Long userId, String email, boolean notificationsEnabled) {
        log.debug("adminUpdateUser: user {} email {}", userId, email);
        User user = getById(userId);
        if (!user.getEmail().equalsIgnoreCase(email) && userRepository.existsByEmail(email)) {
            log.warn("adminUpdateUser: rejected - email \"{}\" already in use", email);
            throw new IllegalArgumentException("Esiste già un utente con questa email.");
        }
        user.setEmail(email);
        user.setNotificationsEnabled(notificationsEnabled);
        userRepository.save(user);
        log.info("adminUpdateUser: user {} updated, email={} notificationsEnabled={}", userId, email, notificationsEnabled);
    }
}
