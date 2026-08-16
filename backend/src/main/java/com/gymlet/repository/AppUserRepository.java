package com.gymlet.repository;

import com.gymlet.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    /** The pre-auth legacy single-user row (no credentials yet). */
    Optional<AppUser> findFirstByPinHashIsNullAndUsernameIsNotNull();

    /** Legacy accounts awaiting claim: no PIN set yet (covers partial migrations too). */
    List<AppUser> findAllByPinHashIsNull();
}
