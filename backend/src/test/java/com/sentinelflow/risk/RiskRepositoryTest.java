package com.sentinelflow.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserRepository;
import com.sentinelflow.identity.UserStatus;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class RiskRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    UserRepository users;

    @Autowired
    MerchantRepository merchants;

    @Autowired
    TransactionRepository transactions;

    @Autowired
    ModelVersionRepository modelVersions;

    @Autowired
    FeatureSnapshotRepository snapshots;

    @Autowired
    RiskScoreRepository riskScores;

    @Autowired
    RiskFactorRepository riskFactors;

    private Transaction transaction;

    @BeforeEach
    void setUp() {
        User user = users.save(new User("USR-RISK-" + System.nanoTime(), "Risk User", null, UserStatus.ACTIVE));
        Merchant merchant = merchants.save(new Merchant("MRC-RISK-" + System.nanoTime(), "Risk Mart", "GROCERY", "IN", MerchantStatus.ACTIVE));
        transaction = transactions.save(new Transaction("TXN-RISK-" + System.nanoTime(), user, merchant,
                null, null, new BigDecimal("500.00"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-01T10:00:00Z"), TransactionStatus.SCORING));
    }

    @Test
    void modelVersionUniquenessWorks() {
        modelVersions.saveAndFlush(new ModelVersion("risk-model", "v1-test-" + System.nanoTime(),
                "xgboost", "fs-v1", "ds-2026-01", "s3://models/risk/v1", ModelStatus.ACTIVE));

        String dup = "v1-dup-" + System.nanoTime();
        modelVersions.saveAndFlush(new ModelVersion("risk-model-dup-" + System.nanoTime(), dup,
                "xgboost", "fs-v1", null, null, ModelStatus.CANDIDATE));

        ModelVersion first = modelVersions.save(new ModelVersion("risk-model-uq-" + System.nanoTime(), "v9",
                "xgb", "fs-v1", null, null, ModelStatus.CANDIDATE));
        String name = first.getModelName();
        assertThatThrownBy(() -> modelVersions.saveAndFlush(
                new ModelVersion(name, "v9", "xgb", "fs-v1", null, null, ModelStatus.CANDIDATE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void historicalModelVersionsPreserved() {
        String base = "risk-model-hist-" + System.nanoTime();
        ModelVersion v1 = modelVersions.save(new ModelVersion(base, "v1", "xgb", "fs-v1", null, null, ModelStatus.RETIRED));
        ModelVersion v2 = modelVersions.save(new ModelVersion(base, "v2", "xgb", "fs-v2", null, null, ModelStatus.ACTIVE));

        List<ModelVersion> all = modelVersions.findByModelName(base);
        assertThat(all).hasSize(2);
        assertThat(all).extracting(ModelVersion::getVersion).containsExactlyInAnyOrder("v1", "v2");
    }

    @Test
    void featureSnapshotSupportsFlexibleData() {
        FeatureSnapshot snap = snapshots.saveAndFlush(new FeatureSnapshot(transaction, "fs-v1",
                Map.of("amount", 500.0, "velocity_1h", 3, "new_device", true,
                        "merchant_category", "GROCERY"),
                Instant.parse("2026-09-01T10:00:01Z")));

        FeatureSnapshot reloaded = snapshots.findById(snap.getId()).orElseThrow();
        assertThat(reloaded.getFeatures()).containsEntry("velocity_1h", 3);
        assertThat(reloaded.getFeatures()).containsEntry("new_device", true);

        // Historical snapshot remains available after a new one is added
        snapshots.save(new FeatureSnapshot(transaction, "fs-v2",
                Map.of("amount", 500.0, "velocity_1h", 4), Instant.now()));
        assertThat(snapshots.findByTransactionId(transaction.getId())).hasSize(2);
    }

    @Test
    void riskScoreReferencesTransactionAndModel() {
        ModelVersion mv = modelVersions.save(new ModelVersion("risk-model", "v-score-" + System.nanoTime(),
                "xgb", "fs-v1", null, null, ModelStatus.ACTIVE));
        RiskScore score = riskScores.saveAndFlush(new RiskScore(transaction, mv, 0.82, "HIGH",
                Instant.parse("2026-09-01T10:00:02Z"), null));

        assertThat(score.getId()).isNotNull();
        assertThat(riskScores.findByTransactionId(transaction.getId())).hasSize(1);
        assertThat(score.getRiskScore()).isEqualTo(0.82);
    }

    @Test
    void invalidRiskScoreRejected() {
        ModelVersion mv = modelVersions.save(new ModelVersion("risk-model", "v-bad-" + System.nanoTime(),
                "xgb", "fs-v1", null, null, ModelStatus.ACTIVE));

        assertThatThrownBy(() -> riskScores.saveAndFlush(
                new RiskScore(transaction, mv, 1.5, "HIGH", Instant.now(), null)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> riskScores.saveAndFlush(
                new RiskScore(transaction, mv, -0.1, "LOW", Instant.now(), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void riskFactorsExistIndependently() {
        riskFactors.save(new RiskFactor(transaction, "NEW_DEVICE", "First seen device", "HIGH", "RULE_ENGINE"));
        riskFactors.save(new RiskFactor(transaction, "HIGH_VELOCITY", "4 txns in 10 min", "MEDIUM", "RULE_ENGINE"));

        assertThat(riskFactors.findByTransactionId(transaction.getId())).hasSize(2);
    }
}
