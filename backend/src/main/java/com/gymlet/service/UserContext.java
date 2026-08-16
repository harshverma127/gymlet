package com.gymlet.service;

import com.gymlet.domain.AppUser;
import com.gymlet.repository.AppUserRepository;
import org.springframework.stereotype.Component;

/** Single-user personal app: resolves the one AppUser record (seeded at startup). */
@Component
public class UserContext {

    private final AppUserRepository userRepository;

    public UserContext(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AppUser getUser() {
        return userRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No user profile found"));
    }
}
