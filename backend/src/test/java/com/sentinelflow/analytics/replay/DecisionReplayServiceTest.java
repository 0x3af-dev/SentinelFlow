package com.sentinelflow.analytics.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sentinelflow.analytics.dto.DecisionReplayResponse;
import com.sentinelflow.analytics.exception.AnalyticsNotFoundException;
import com.sentinelflow.decision.DecisionRecordRepository;
import com.sentinelflow.identity.Device;
import com.sentinelflow.identity.DeviceRepository;
import com.sentinelflow.identity.Location;
import com.sentinelflow.identity.LocationRepository;
import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserRepository;
import com.sentinelflow.identity.UserStatus;
import com.sentinelflow.ml.MlInferenceClient;
import com.sentinelflow.ml.MlInferenceRequest;
import com.sentinelflow.pipeline.TransactionIntelligencePipeline;
import com.sentinelflow.risk.FeatureSnapshot;
import com.sentinelflow.risk.FeatureSnapshotRepository;
import com.sentinelflow.risk.RiskScore;
import com.sentinelflow.risk.RiskScoreRepository;
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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Transactional
class DecisionReplayServiceTest {

    private static final Set<String> DISAGREEMENT_CATEGORIES = Set.of(
            "ML_HIGH_RULE_HIGH", "ML_HIGH_RULE_LOW", "ML_LOW_RULE_HIGH", "ML_LOW_RULE_LOW");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired TransactionRepository transactions;
    @Autowired UserRepository users;
    @Autowired DeviceRepository devices;
    @Autowired LocationRepository locations;
    @Autowired MerchantRepository merchants;
    @Autowired FeatureSnapshotRepository snapshots;
    @Autowired RiskScoreRepository riskScores;
    @Autowired DecisionRecordRepository decisions;
    @Autowired TransactionIntelligencePipeline pipeline;
    @Autowired DecisionReplayService replay;

    @MockitoBean
    MlInferenceClient mlClient;

    private User user;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        user = users.save(new User("USR-REPLAY-" + System.nanoTime(), "Replay User", null, UserStatus.ACTIVE));
        merchant = merchants.save(new Merchant("MRC-REPLAY-" + System.nanoTime(), "Replay Mart", "GROCERY", "IN", MerchantStatus.ACTIVE));

        when(mlClient.infer(any(MlInferenceRequest.class))).thenAnswer(inv -> {
            MlInferenceRequest req = inv.getArgument(0);
            double score = req.transactionAmount() > 50000 ? 0.9 : req.transactionAmount() > 10000 ? 0.6 : 0.2;
            String pred = score >= 0.85 ? "HIGH" : score >= 0.5 ? "MEDIUM" : "LOW";
            return new MlPrediction("risk-model", "v1", "fs-v1", score, pred,
                    List.of(), Map.of(), 8, Instant.now());
        });
    }

    private Transaction createTxn(String prefix, BigDecimal amount) {
        return transactions.save(new Transaction(prefix + "-" + System.nanoTime(), user, merchant,
                null, null, amount, "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-12T10:00:00Z"), TransactionStatus.RECEIVED));
    }

    @Test
    void replayReconstructsDecisionLineageWithoutMutation() {
        Transaction txn = createTxn("TXN-REPLAY", new BigDecimal("12000.00"));
        PipelineResult res = pipeline.process(txn.getTransactionReference());
        assertThat(res.decision()).isEqualTo("REVIEW");

        int snapshotsBefore = snapshots.findByTransactionId(txn.getId()).size();
        int riskScoresBefore = riskScores.findByTransactionId(txn.getId()).size();
        int decisionsBefore = decisions.findByTransactionId(txn.getId()).size();
        Instant snapshotCreatedBefore = snapshots.findByTransactionId(txn.getId()).get(0).getCreatedAt();

        DecisionReplayResponse replayResult = replay.replay(txn.getTransactionReference());

        assertThat(replayResult.transactionReference()).isEqualTo(txn.getTransactionReference());
        assertThat(replayResult.decision().finalDecision()).isEqualTo("REVIEW");
        assertThat(replayResult.riskScore().score()).isEqualTo(res.riskScore());
        assertThat(replayResult.featureSnapshot()).isNotNull();
        assertThat(replayResult.featureSnapshot().features()).containsKey("transaction_amount");
        assertThat(replayResult.model().name()).isEqualTo("risk-model");
        assertThat(replayResult.model().version()).isEqualTo("v1");
        assertThat(replayResult.policy().name()).isEqualTo("fraud-policy");
        assertThat(replayResult.policy().version()).isEqualTo("v1");
        assertThat(replayResult.policy().reviewThreshold()).isEqualTo(0.5);
        assertThat(replayResult.disagreement().category()).isIn(DISAGREEMENT_CATEGORIES);
        assertThat(replayResult.evidence().nodeCount()).isGreaterThan(0);

        // no mutation across a replay
        assertThat(snapshots.findByTransactionId(txn.getId()).size()).isEqualTo(snapshotsBefore);
        assertThat(riskScores.findByTransactionId(txn.getId()).size()).isEqualTo(riskScoresBefore);
        assertThat(snapshots.findByTransactionId(txn.getId()).get(0).getCreatedAt())
                .isEqualTo(snapshotCreatedBefore);
        assertThat(decisions.findByTransactionId(txn.getId()).size()).isEqualTo(decisionsBefore);
    }

    @Test
    void replayUsesEvidenceRulesWhenPresent() {
        Device newDevice = devices.save(new Device(user, "DEV-REPLAY-" + System.nanoTime(),
                "MOBILE", "ANDROID", Instant.now(), Instant.now()));
        Location knownLoc = locations.save(new Location(user, "IN", "Karnataka", "Bengaluru",
                12.97, 77.59, Instant.parse("2026-01-01T00:00:00Z"), Instant.now()));
        Transaction txn = transactions.save(new Transaction("TXN-REPLAY-RULE-" + System.nanoTime(), user,
                merchant, newDevice, knownLoc, new BigDecimal("1500.00"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-13T10:00:00Z"), TransactionStatus.RECEIVED));
        pipeline.process(txn.getTransactionReference());

        DecisionReplayResponse replayResult = replay.replay(txn.getTransactionReference());
        // evidence RULE_RESULT nodes are present in the graph
        assertThat(replayResult.evidence().nodes().stream()
                .anyMatch(n -> n.nodeType().equals("RULE_RESULT"))).isTrue();
    }

    @Test
    void unknownTransactionRejected() {
        assertThatThrownBy(() -> replay.replay("TXN-NOT-PROCESSED"))
                .isInstanceOf(AnalyticsNotFoundException.class);
    }
}