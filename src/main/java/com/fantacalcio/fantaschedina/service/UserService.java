package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.User;
import com.fantacalcio.fantaschedina.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public Long getUserId(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalStateException("Utente autenticato non trovato nel database: " + username))
            .getId();
    }

    public User getById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new IllegalStateException("Utente non trovato: " + id));
    }

    @Transactional
    public void updateNotificationPreference(Long userId, boolean enabled) {
        User user = getById(userId);
        user.setNotificationsEnabled(enabled);
        userRepository.save(user);
    }

    @Transactional
    public void adminUpdateUser(Long userId, String email, boolean notificationsEnabled) {
        User user = getById(userId);
        if (!user.getEmail().equalsIgnoreCase(email) && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Esiste già un utente con questa email.");
        }
        user.setEmail(email);
        user.setNotificationsEnabled(notificationsEnabled);
        userRepository.save(user);
    }
}