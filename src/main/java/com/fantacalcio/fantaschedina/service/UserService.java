package com.fantacalcio.fantaschedina.service;

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
}
