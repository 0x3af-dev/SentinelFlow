package com.sentinelflow.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserRepository;
import com.sentinelflow.identity.UserStatus;
import com.sentinelflow.investigation.AuditLogRepository;
import com.sentinelflow.investigation.InvestigationEventRepository;
import com.sentinelflow.investigation.InvestigationRepository;
import com.sentinelflow.ml.MlInferenceClient;
import com.sentinelflow.ml.MlInferenceRequest;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared infrastructure for the Phase 8 security integration suite: a fresh
 * PostgreSQL container per test class, real JWT auth via the seeded demo
 * users, and a stubbed ML client for pipeline runs. Every request in these
 * tests travels through the real security filter chain — nothing is bypassed.
 */
@Testcontainers
@SpringBootTest(properties = {
        "management.endpoint.health.show-details=always",
        "management.endpoints.web.exposure.include=health,info,metrics"
})
@AutoConfigureWebTestClient
abstract class SecurityIntegrationTestBase {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    protected WebTestClient web;
    @Autowired
    protected SecurityUserRepository securityUsers;
    @Autowired
    protected AuditLogRepository auditLogs;
    @Autowired
    protected InvestigationRepository investigations;
    @Autowired
    protected InvestigationEventRepository investigationEvents;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected MerchantRepository merchants;
    @Autowired
    protected TransactionRepository transactions;
    @Autowired
    protected JdbcTemplate jdbc;

    @MockitoBean
    protected MlInferenceClient mlClient;

    protected String newTransaction() {
        User user = users.save(new User("USR-SEC-" + System.nanoTime(), "Sec User", null, UserStatus.ACTIVE));
        Merchant merchant = merchants.save(new Merchant(
                "MRC-SEC-" + System.nanoTime(), "Sec Mart", "GROCERY", "IN", MerchantStatus.ACTIVE));
        return transactions.save(new Transaction(
                "TXN-SEC-" + System.nanoTime(), user, merchant, null, null,
                new BigDecimal("50000.00"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-14T10:00:00Z"), TransactionStatus.RECEIVED)).getTransactionReference();
    }

    protected void stubMl() {
        when(mlClient.infer(any(MlInferenceRequest.class))).thenAnswer(inv -> new MlPrediction(
                "risk-model", "v1", "fs-v1", 0.5, "MEDIUM",
                List.of(new MlPrediction.RiskFactorDto("MODEL_ELEVATED_RISK", "elevated risk", "MEDIUM", Map.of("risk_score", 0.5))),
                Map.of("model_type", "mock"), 10, Instant.now()));
    }
}