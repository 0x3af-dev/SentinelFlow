package com.sentinelflow.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserRepository;
import com.sentinelflow.identity.UserStatus;
import com.sentinelflow.ml.MlInferenceClient;
import com.sentinelflow.ml.MlInferenceRequest;
import com.sentinelflow.shared.dto.MlPrediction;
import com.sentinelflow.pipeline.TransactionIntelligencePipeline;
import com.sentinelflow.transaction.Merchant;
import com.sentinelflow.transaction.MerchantRepository;
import com.sentinelflow.transaction.MerchantStatus;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.transaction.TransactionRepository;
import com.sentinelflow.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Phase 7 performance baseline. Excluded from the default suite
 * (maven-surefire excludes {@code benchmark}); run with -Dgroups=benchmark to
 * produce the p50/p95 latency numbers quoted in the Phase 7 report. Numbers on
 * a developer laptop are noise, so this is a baseline, not a benchmark gate.
 */
@Tag("benchmark")
@SpringBootTest
@Testcontainers
class PipelineLatencyBaselineTest {

    private static final Logger log = LoggerFactory.getLogger(PipelineLatencyBaselineTest.class);
    private static final int SAMPLES = 25;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired UserRepository users;
    @Autowired MerchantRepository merchants;
    @Autowired TransactionRepository transactions;
    @Autowired TransactionIntelligencePipeline pipeline;

    @MockitoBean
    MlInferenceClient mlClient;

    @Test
    void pipelineLatencyP50AndP95UnderGenerousCeiling() {
        when(mlClient.infer(any(MlInferenceRequest.class))).thenAnswer(inv -> new MlPrediction(
                "risk-model", "v1", "fs-v1", 0.35, "LOW",
                List.of(new MlPrediction.RiskFactorDto("MODEL_ELEVATED_RISK", "elevated risk", "LOW", Map.of("risk_score", 0.35))),
                Map.of("model_type", "mock"), 10, Instant.now()));
        List<Long> durations = new ArrayList<>(SAMPLES);
        for (int i = 0; i < SAMPLES; i++) {
            String reference = newTransaction();
            long start = System.nanoTime();
            var result = pipeline.process(reference);
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            durations.add(tookMs);
            assertThat(result.decision()).isIn("ALLOW", "REVIEW", "BLOCK");
        }
        durations.sort(Comparator.naturalOrder());
        long p50 = durations.get(SAMPLES / 2);
        long p95 = durations.get((int) (SAMPLES * 0.95));
        log.info("PIPELINE_BASELINE samples={} p50={}ms p95={}ms min={}ms max={}ms",
                SAMPLES, p50, p95, durations.get(0), durations.get(durations.size() - 1));

        assertThat(p50).isLessThan(3000);
        assertThat(p95).isLessThan(6000);
    }

    private String newTransaction() {
        User user = users.save(new User("USR-BENCH-" + System.nanoTime(), "Bench User", null, UserStatus.ACTIVE));
        Merchant merchant = merchants.save(new Merchant(
                "MRC-BENCH-" + System.nanoTime(), "Bench Mart", "GROCERY", "IN", MerchantStatus.ACTIVE));
        Transaction txn = transactions.save(new Transaction(
                "TXN-BENCH-" + System.nanoTime(), user, merchant, null, null,
                new BigDecimal("50000.00"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-14T10:00:00Z"), TransactionStatus.RECEIVED));
        return txn.getTransactionReference();
    }
}