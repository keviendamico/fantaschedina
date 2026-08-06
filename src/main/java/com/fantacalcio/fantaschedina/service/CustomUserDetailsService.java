package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.User;
import com.fantacalcio.fantaschedina.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("loadUserByUsername: {}", username);
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> {
                log.warn("loadUserByUsername: rejected — no user found for \"{}\"", username);
                return new UsernameNotFoundException("User not found: " + username);
            });

        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(user.getPasswordHash())
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
            .disabled(!user.getEnabled())
            .build();
    }
}
