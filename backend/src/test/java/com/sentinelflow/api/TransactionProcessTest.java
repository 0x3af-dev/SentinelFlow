package com.sentinelflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserRepository;
import com.sentinelflow.identity.UserStatus;
import com.sentinelflow.ml.MlInferenceClient;
import com.sentinelflow.ml.MlInferenceRequest;
import com.sentinelflow.shared.dto.MlPrediction;
import com.sentinelflow.shared.dto.PipelineResult;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class TransactionProcessTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired TransactionRepository transactions;
    @Autowired UserRepository users;
    @Autowired MerchantRepository merchants;
    @Autowired TransactionProcessController controller;

    @MockitoBean
    MlInferenceClient mlClient;

    private String reference;

    @BeforeEach
    void setUp() {
        User user = users.save(new User("USR-PR-" + System.nanoTime(), "Process User", null, UserStatus.ACTIVE));
        Merchant merchant = merchants.save(new Merchant("MRC-PR-" + System.nanoTime(), "Process Mart", "GROCERY", "IN", MerchantStatus.ACTIVE));
        Transaction txn = transactions.save(new Transaction(
                "TXN-PR-" + System.nanoTime(), user, merchant, null, null,
                new BigDecimal("12000.00"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-14T10:00:00Z"), TransactionStatus.RECEIVED));
        reference = txn.getTransactionReference();
    }

    @Test
    void processRunsPipelineAndReturnsResult() {
        when(mlClient.infer(any(MlInferenceRequest.class))).thenAnswer(inv -> new MlPrediction(
                "risk-model", "v1", "fs-v1", 0.6, "MEDIUM",
                List.of(new MlPrediction.RiskFactorDto("MODEL_ELEVATED_RISK", "elevated risk", "MEDIUM", Map.of("risk_score", 0.6))),
                Map.of("model_type", "mock"), 10, Instant.now()));

        PipelineResult result = (PipelineResult) controller.process(reference).getBody();
        assertThat(result).isNotNull();
        assertThat(result.transactionReference()).isEqualTo(reference);
        assertThat(result.decision()).isEqualTo("REVIEW");
        assertThat(result.riskScore()).isEqualTo(0.6);
    }

    @Test
    void unknownTransactionIsNotFound() {
        var response = controller.process("TXN-MISSING-PR");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("code")).isEqualTo("NOT_FOUND");
    }

    @Test
    void pipelineFailureIsServiceUnavailable() {
        when(mlClient.infer(any(MlInferenceRequest.class)))
                .thenThrow(new MlInferenceClient.MlInferenceException("refused"));

        var response = controller.process(reference);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("code")).isEqualTo("ML_UNAVAILABLE");
    }
}