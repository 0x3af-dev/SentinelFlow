package com.sentinelflow.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserRepository;
import com.sentinelflow.identity.UserStatus;
import com.sentinelflow.ml.MlInferenceClient;
import com.sentinelflow.ml.MlInferenceRequest;
import com.sentinelflow.security.TestAuth;
import com.sentinelflow.shared.dto.MlPrediction;
import com.sentinelflow.transaction.Merchant;
import com.sentinelflow.transaction.MerchantRepository;
import com.sentinelflow.transaction.MerchantStatus;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.transaction.TransactionRepository;
import com.sentinelflow.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 7 resilience: a PostgreSQL outage (simulated by exhausting the single
 * connection in a size-1 pool so acquisition times out) must surface as a clean
 * 503 DEPENDENCY_UNAVAILABLE, must NOT produce a partial pipeline side-effect,
 * and the system must transparently recover once the connection is released.
 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.connection-timeout=1000",
        "spring.datasource.hikari.maximum-pool-size=1"
})
@AutoConfigureWebTestClient
@Testcontainers
class DatabaseOutageResilienceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired WebTestClient web;
    @Autowired UserRepository users;
    @Autowired MerchantRepository merchants;
    @Autowired TransactionRepository transactions;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean
    MlInferenceClient mlClient;

    @Test
    void postgresOutageSurfacesCleanFailureAndRecovers() throws Exception {
        when(mlClient.infer(any(MlInferenceRequest.class))).thenAnswer(inv -> new MlPrediction(
                "risk-model", "v1", "fs-v1", 0.5, "MEDIUM",
                List.of(new MlPrediction.RiskFactorDto("MODEL_ELEVATED_RISK", "elevated risk", "MEDIUM", Map.of("risk_score", 0.5))),
                Map.of("model_type", "mock"), 10, Instant.now()));
        String reference = newTransaction();
        long decisionsBefore = decisions();
        WebTestClient analyst = TestAuth.asAnalyst(web);

        try (Connection held = dataSource.getConnection()) {
            // The pool is exhausted: every further acquisition times out after 1s,
            // mimicking an unavailable database without touching the container.
            analyst.post().uri("/api/transactions/{ref}/process", reference).exchange()
                    .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("DEPENDENCY_UNAVAILABLE");
        }

        // The outage must not have created any partial decision side-effect.
        assertThat(decisions()).isEqualTo(decisionsBefore);

        // Once the connection is back, the same request succeeds and persists a decision.
        analyst.post().uri("/api/transactions/{ref}/process", reference).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.decision").exists();
        assertThat(decisions()).isEqualTo(decisionsBefore + 1);
    }

    private long decisions() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM decision_records WHERE transaction_id IN "
                + "(SELECT id FROM transactions WHERE transaction_reference = ?)", Long.class, txReference);
        return count == null ? 0 : count;
    }

    private String txReference;

    private String newTransaction() {
        User user = users.save(new User("USR-RES-" + System.nanoTime(), "Res User", null, UserStatus.ACTIVE));
        Merchant merchant = merchants.save(new Merchant(
                "MRC-RES-" + System.nanoTime(), "Res Mart", "GROCERY", "IN", MerchantStatus.ACTIVE));
        Transaction txn = transactions.save(new Transaction(
                "TXN-RES-" + System.nanoTime(), user, merchant, null, null,
                new BigDecimal("50000.00"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-14T10:00:00Z"), TransactionStatus.RECEIVED));
        txReference = txn.getTransactionReference();
        return txn.getTransactionReference();
    }
}