package com.sentinelflow.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserRepository;
import com.sentinelflow.identity.UserStatus;
import com.sentinelflow.risk.ModelVersion;
import com.sentinelflow.risk.ModelVersionRepository;
import com.sentinelflow.risk.ModelStatus;
import com.sentinelflow.risk.RiskScore;
import com.sentinelflow.risk.RiskScoreRepository;
import com.sentinelflow.transaction.Merchant;
import com.sentinelflow.transaction.MerchantRepository;
import com.sentinelflow.transaction.MerchantStatus;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.transaction.TransactionRepository;
import com.sentinelflow.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
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

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Testcontainers
@Transactional
class DecisionRepositoryTest {

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
    RiskScoreRepository riskScores;

    @Autowired
    DecisionPolicyRepository policies;

    @Autowired
    DecisionRecordRepository decisions;

    private Transaction transaction;
    private RiskScore riskScore;

    @BeforeEach
    void setUp() {
        User user = users.save(new User("USR-DEC-" + System.nanoTime(), "Decision User", null, UserStatus.ACTIVE));
        Merchant merchant = merchants.save(new Merchant("MRC-DEC-" + System.nanoTime(), "Decision Mart", "GROCERY", "IN", MerchantStatus.ACTIVE));
        transaction = transactions.save(new Transaction("TXN-DEC-" + System.nanoTime(), user, merchant,
                null, null, new BigDecimal("999.00"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-01T10:00:00Z"), TransactionStatus.DECIDED));
        ModelVersion mv = modelVersions.save(new ModelVersion("risk-model", "v-dec-" + System.nanoTime(),
                "xgb", "fs-v1", null, null, ModelStatus.ACTIVE));
        riskScore = riskScores.save(new RiskScore(transaction, mv, 0.91, "HIGH",
                Instant.parse("2026-09-01T10:00:02Z"), 42));
    }

    @Test
    void policyVersionUniquenessAndHistoryPreserved() {
        String name = "fraud-policy-uq-" + System.nanoTime();
        policies.saveAndFlush(new DecisionPolicy(name, "v1", "initial", Map.of("review_threshold", 0.5), PolicyStatus.RETIRED));
        policies.saveAndFlush(new DecisionPolicy(name, "v2", "tightened", Map.of("review_threshold", 0.4), PolicyStatus.ACTIVE));

        assertThat(policies.findByPolicyName(name)).hasSize(2);
        assertThatThrownBy(() -> policies.saveAndFlush(
                new DecisionPolicy(name, "v1", "dup", Map.of(), PolicyStatus.DRAFT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void decisionReferencesTransactionRiskScoreAndPolicy() {
        DecisionPolicy policy = policies.save(new DecisionPolicy("fraud-policy", "v-dec-" + System.nanoTime(),
                "test policy", Map.of("review_threshold", 0.5, "block_threshold", 0.85), PolicyStatus.ACTIVE));

        DecisionRecord record = decisions.saveAndFlush(new DecisionRecord(transaction, riskScore, policy,
                FinalDecision.BLOCK, Instant.parse("2026-09-01T10:00:03Z"),
                "score 0.91 >= block_threshold 0.85 (risk-model, fraud-policy)"));

        assertThat(record.getId()).isNotNull();
        assertThat(decisions.findByTransactionId(transaction.getId())).hasSize(1);

        DecisionRecord reloaded = decisions.findById(record.getId()).orElseThrow();
        assertThat(reloaded.getTransaction().getId()).isEqualTo(transaction.getId());
        assertThat(reloaded.getRiskScore().getId()).isEqualTo(riskScore.getId());
        assertThat(reloaded.getPolicy().getId()).isEqualTo(policy.getId());
        assertThat(reloaded.getFinalDecision()).isEqualTo(FinalDecision.BLOCK);
    }

    @Test
    void historicalDecisionsPreserveHistoricalReferences() {
        DecisionPolicy v1 = policies.save(new DecisionPolicy("fraud-policy-hist-" + System.nanoTime(), "v1",
                "v1", Map.of("block_threshold", 0.9), PolicyStatus.RETIRED));
        DecisionRecord old = decisions.save(new DecisionRecord(transaction, riskScore, v1,
                FinalDecision.REVIEW, Instant.parse("2026-09-01T10:00:03Z"), "v1 thresholds"));

        DecisionPolicy v2 = policies.save(new DecisionPolicy(v1.getPolicyName(), "v2",
                "v2", Map.of("block_threshold", 0.85), PolicyStatus.ACTIVE));

        // Old decision still points at v1, not v2
        DecisionRecord reloaded = decisions.findById(old.getId()).orElseThrow();
        assertThat(reloaded.getPolicy().getVersion()).isEqualTo("v1");
        assertThat(policies.findByPolicyName(v1.getPolicyName())).hasSize(2);
    }
}
