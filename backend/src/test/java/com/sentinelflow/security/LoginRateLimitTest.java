package com.sentinelflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 8: the login rate limiter locks a username out after a small number of
 * failed attempts within the window, forcing a 429 even with correct
 * credentials, and records the lock-out in the audit log.
 *
 * Uses its own context with max-attempts=3 so the limiter is observable.
 */
@SpringBootTest(properties = {
        "sentinelflow.security.rate-limit.max-attempts=3",
        "sentinelflow.security.rate-limit.window-seconds=600",
        "sentinelflow.security.rate-limit.cooldown-seconds=600"
})
@AutoConfigureWebTestClient
@Testcontainers
class LoginRateLimitTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    WebTestClient web;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void usernameIsLockedOutAfterRepeatedFailures() {
        for (int i = 0; i < 3; i++) {
            web.post().uri("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"username\":\"admin\",\"password\":\"wrong\"}")
                    .exchange().expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        }

        // Fourth attempt — correct password included — is rate-limited.
        web.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"admin\",\"password\":\"admin-demo\"}")
                .exchange().expectStatus().isEqualTo(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                .expectBody().jsonPath("$.code").isEqualTo("TOO_MANY_ATTEMPTS");

        // The lock-out is recorded in the append-only audit log.
        Long lockedOut = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'AUTHENTICATION_LOCKED_OUT'", Long.class);
        assertThat(lockedOut).isGreaterThanOrEqualTo(1L);
    }
}