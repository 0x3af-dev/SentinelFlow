package com.sentinelflow.security;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityUserRepository extends JpaRepository<SecurityUser, java.util.UUID> {

    Optional<SecurityUser> findByUsername(String username);

    boolean existsByUsername(String username);
}
