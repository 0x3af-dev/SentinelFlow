package com.sentinelflow.operations;

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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 7 operational surfaces: liveness/readiness health, the read-only
 * /api/operations endpoints, scheduled-backlog numbers and Micrometer metric
 * exposure. Phase 8 adds authorization: operations endpoints require
 * OPERATOR/ADMIN, internal enqueue requires the internal API key, and the
 * public pipeline trigger requires an authenticated business role.
 */
@SpringBootTest(properties = {
        "management.endpoint.health.show-details=always",
        "management.endpoint.health.probes.enabled=true",
        "management.endpoints.web.exposure.include=health,info,metrics"
})
@AutoConfigureWebTestClient
@Testcontainers
class OperationalApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired WebTestClient web;
    @Autowired UserRepository users;
    @Autowired MerchantRepository merchants;
    @Autowired TransactionRepository transactions;

    @MockitoBean
    MlInferenceClient mlClient;

    @Test
    void livenessAndReadinessStayUpWithoutKafkaOrAi() {
        web.get().uri("/actuator/health").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
        web.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
        web.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void unauthenticatedOperationsAreRejectedWith401() {
        web.get().uri("/api/operations/summary").exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void operationalSummaryReportsDependenciesAndCounters() {
        WebTestClient authed = TestAuth.asOperator(web);
        authed.get().uri("/api/operations/summary").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.dependencies.postgres.status").isEqualTo("HEALTHY")
                .jsonPath("$.dependencies.kafka.status").isEqualTo("DISABLED")
                .jsonPath("$.dependencies.ai.status").isEqualTo("DISABLED")
                .jsonPath("$.counters").exists()
                .jsonPath("$.counters.transactionProcessed").isNumber()
                .jsonPath("$.attempts.statusCounts").exists()
                .jsonPath("$.attempts.stuckProcessingCount").isNumber();
    }

    @Test
    void enqueuedTransactionAppearsInOutboxDashboard() {
        String tx = newTransaction();
        web.post().uri("/internal/kafka/transactions/{ref}/enqueue", tx)
                .header("X-Internal-Api-Key", "test-internal-key")
                .exchange()
                .expectStatus().isAccepted();

        WebTestClient authed = TestAuth.asOperator(web);
        authed.get().uri("/api/operations/outbox").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pending").isNumber();
    }

    @Test
    void integrityChecksAreHealthyOnCleanSchema() {
        WebTestClient authed = TestAuth.asOperator(web);
        authed.get().uri("/api/operations/integrity").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.healthyAll").isEqualTo(true)
                .jsonPath("$.checks").isArray();
    }

    @Test
    void dlqAndAttemptsEndpointsAreReadable() {
        WebTestClient authed = TestAuth.asOperator(web);
        authed.get().uri("/api/operations/dlq").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.count").isNumber();
        authed.get().uri("/api/operations/attempts").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.stuckThresholdMinutes").isNumber();
        authed.get().uri("/api/operations").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("available");
    }

    @Test
    void processingPublishesMicrometerMetrics() {
        when(mlClient.infer(any(MlInferenceRequest.class))).thenAnswer(inv -> new MlPrediction(
                "risk-model", "v1", "fs-v1", 0.5, "MEDIUM",
                List.of(new MlPrediction.RiskFactorDto("MODEL_ELEVATED_RISK", "elevated risk", "MEDIUM", Map.of("risk_score", 0.5))),
                Map.of("model_type", "mock"), 10, Instant.now()));

        WebTestClient analystClient = TestAuth.asAnalyst(web);
        analystClient.post().uri("/api/transactions/{ref}/process", newTransaction()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.decision").exists();

        WebTestClient operatorClient = TestAuth.asOperator(web);
        operatorClient.get().uri("/actuator/metrics/sentinelflow.transaction.processing.success.total").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.measurements[0].value").isNumber();
    }

    private String newTransaction() {
        User user = users.save(new User("USR-OPS-" + System.nanoTime(), "Ops User", null, UserStatus.ACTIVE));
        Merchant merchant = merchants.save(new Merchant(
                "MRC-OPS-" + System.nanoTime(), "Ops Mart", "GROCERY", "IN", MerchantStatus.ACTIVE));
        return transactions.save(new Transaction(
                "TXN-OPS-" + System.nanoTime(), user, merchant, null, null,
                new BigDecimal("50000.00"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-14T10:00:00Z"), TransactionStatus.RECEIVED)).getTransactionReference();
    }
}