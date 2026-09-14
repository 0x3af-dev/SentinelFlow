package com.sentinelflow.policy;

import com.sentinelflow.decision.DecisionPolicy;
import com.sentinelflow.decision.DecisionPolicyRepository;
import com.sentinelflow.decision.PolicyStatus;
import com.sentinelflow.risk.ModelVersion;
import com.sentinelflow.risk.ModelStatus;
import com.sentinelflow.risk.RiskScore;
import com.sentinelflow.rule.RuleEngine;
import com.sentinelflow.transaction.Merchant;
import com.sentinelflow.transaction.MerchantStatus;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.transaction.TransactionStatus;
import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class PolicyEvaluatorTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired DecisionPolicyRepository policies;
    @Autowired PolicyEvaluator evaluator;

    private RiskScore score(double value) {
        User user = new User("USR-POL-" + System.nanoTime(), "Pol", null, UserStatus.ACTIVE);
        Merchant m = new Merchant("MRC-POL-" + System.nanoTime(), "Mart", "GROCERY", "IN", MerchantStatus.ACTIVE);
        Transaction t = new Transaction("TXN-POL-" + System.nanoTime(), user, m, null, null, new BigDecimal("100"), "INR", "PURCHASE", "ONLINE", Instant.now(), TransactionStatus.SCORING);
        ModelVersion mv = new ModelVersion("risk-model", "v-pol-" + System.nanoTime(), "xgb", "fs-v1", null, null, ModelStatus.ACTIVE);
        return new RiskScore(t, mv, value, value >= 0.85 ? "HIGH" : value >= 0.5 ? "MEDIUM" : "LOW", Instant.now(), 10);
    }

    @Test
    void policyThresholds() {
        // Uses seeded fraud-policy/v1 with 0.5 and 0.85
        RuleEngine.RuleEvaluationResult empty = new RuleEngine.RuleEvaluationResult(List.of(), List.of());
        assertThat(evaluator.evaluate(score(0.0), empty).decision()).isEqualTo("ALLOW");
        assertThat(evaluator.evaluate(score(0.49), empty).decision()).isEqualTo("ALLOW");
        assertThat(evaluator.evaluate(score(0.50), empty).decision()).isEqualTo("REVIEW");
        assertThat(evaluator.evaluate(score(0.51), empty).decision()).isEqualTo("REVIEW");
        assertThat(evaluator.evaluate(score(0.84), empty).decision()).isEqualTo("REVIEW");
        assertThat(evaluator.evaluate(score(0.85), empty).decision()).isEqualTo("BLOCK");
        assertThat(evaluator.evaluate(score(0.86), empty).decision()).isEqualTo("BLOCK");
        assertThat(evaluator.evaluate(score(1.0), empty).decision()).isEqualTo("BLOCK");
    }

    @Test
    void invalidPolicyRejected() {
        // Deactivate existing active to avoid duplicate ACTIVE, then test invalid
        var existingOpt = policies.findByPolicyNameAndStatus("fraud-policy", PolicyStatus.ACTIVE);
        DecisionPolicy existing = existingOpt.orElse(null);
        if (existing != null) {
            existing.setStatus(PolicyStatus.RETIRED);
            policies.save(existing);
        }
        DecisionPolicy invalid = policies.save(new DecisionPolicy("fraud-policy", "v-invalid-" + System.nanoTime(), "bad", Map.of("review_threshold", 0.9, "block_threshold", 0.5), PolicyStatus.ACTIVE));
        RuleEngine.RuleEvaluationResult empty = new RuleEngine.RuleEvaluationResult(List.of(), List.of());
        try {
            assertThatThrownBy(() -> evaluator.evaluate(score(0.6), empty))
                    .isInstanceOf(PolicyEvaluator.PolicyException.class);
        } finally {
            policies.delete(invalid);
            if (existing != null) {
                existing.setStatus(PolicyStatus.ACTIVE);
                policies.save(existing);
            }
        }
    }
}
