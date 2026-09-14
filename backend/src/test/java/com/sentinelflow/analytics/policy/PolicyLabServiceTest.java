package com.sentinelflow.analytics.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sentinelflow.analytics.dto.BatchPolicySimulationRequest;
import com.sentinelflow.analytics.dto.BatchPolicySimulationResponse;
import com.sentinelflow.analytics.dto.PolicySimulationRequest;
import com.sentinelflow.analytics.dto.PolicySimulationResponse;
import com.sentinelflow.analytics.exception.AnalyticsNotFoundException;
import com.sentinelflow.analytics.exception.AnalyticsValidationException;
import com.sentinelflow.decision.DecisionPolicy;
import com.sentinelflow.decision.DecisionPolicyRepository;
import com.sentinelflow.decision.DecisionRecord;
import com.sentinelflow.decision.DecisionRecordRepository;
import com.sentinelflow.decision.FinalDecision;
import com.sentinelflow.decision.PolicyStatus;
import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserRepository;
import com.sentinelflow.identity.UserStatus;
import com.sentinelflow.investigation.Investigation;
import com.sentinelflow.investigation.InvestigationEvent;
import com.sentinelflow.investigation.InvestigationEventRepository;
import com.sentinelflow.investigation.InvestigationPriority;
import com.sentinelflow.investigation.InvestigationRepository;
import com.sentinelflow.investigation.InvestigationStatus;
import com.sentinelflow.risk.FeatureSnapshot;
import com.sentinelflow.risk.FeatureSnapshotRepository;
import com.sentinelflow.risk.ModelVersion;
import com.sentinelflow.risk.ModelVersionRepository;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Transactional
class PolicyLabServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired TransactionRepository transactions;
    @Autowired UserRepository users;
    @Autowired MerchantRepository merchants;
    @Autowired ModelVersionRepository modelVersions;
    @Autowired FeatureSnapshotRepository snapshots;
    @Autowired RiskScoreRepository riskScores;
    @Autowired DecisionRecordRepository decisions;
    @Autowired DecisionPolicyRepository policies;
    @Autowired InvestigationRepository investigations;
    @Autowired InvestigationEventRepository investigationEvents;
    @Autowired PolicyLabService policyLab;

    private Transaction txn;
    private RiskScore riskScore;
    private DecisionRecord decision;
    private User user;
    private Merchant merchant;
    private DecisionPolicy policy;

    @BeforeEach
    void setUp() {
        user = users.save(new User("USR-P4-" + System.nanoTime(), "P4 User", null, UserStatus.ACTIVE));
        merchant = merchants.save(new Merchant("MRC-P4-" + System.nanoTime(), "P4 Mart", "GROCERY", "IN", MerchantStatus.ACTIVE));
        txn = transactions.save(new Transaction("TXN-P4-" + System.nanoTime(), user, merchant,
                null, null, new BigDecimal("12000.00"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-10T10:00:00Z"), TransactionStatus.COMPLETED));

        policy = policies.findByPolicyNameAndStatus("fraud-policy", PolicyStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Seeded fraud-policy v1 not found"));
        ModelVersion model = modelVersions.findByModelNameAndVersion("risk-model", "v1")
                .orElseThrow(() -> new IllegalStateException("Seeded risk-model v1 not found"));

        snapshots.save(new FeatureSnapshot(txn, "fs-v1",
                Map.of("transaction_amount", 12000.0), Instant.now()));
        riskScore = riskScores.save(new RiskScore(txn, model, 0.72, "MEDIUM", Instant.now(), 10));
        decision = decisions.save(new DecisionRecord(txn, riskScore, policy, FinalDecision.REVIEW,
                Instant.now(), "Risk score 0.72 is within review range"));
    }

    @Test
    void sameThresholdsKeepOutcomeUnchanged() {
        PolicySimulationResponse result = policyLab.simulate(new PolicySimulationRequest(
                txn.getTransactionReference(), "fraud-policy", "sim-baseline", 0.5, 0.85, "analyst-1", null));

        assertThat(result.actualDecision()).isEqualTo("REVIEW");
        assertThat(result.simulatedDecision()).isEqualTo("REVIEW");
        assertThat(result.baseRiskScore()).isEqualTo(0.72);
        assertThat(result.decisionChanged()).isFalse();
        assertThat(result.changeType()).isEqualTo("UNCHANGED");
        assertThat(result.simulationId()).isNotNull();
    }

    @Test
    void raisedReviewThresholdUpgradesOutcomeToAllow() {
        PolicySimulationResponse result = policyLab.simulate(new PolicySimulationRequest(
                txn.getTransactionReference(), "fraud-policy", "sim-v1", 0.75, 0.9, "analyst-1", null));

        assertThat(result.actualDecision()).isEqualTo("REVIEW");
        assertThat(result.simulatedDecision()).isEqualTo("ALLOW");
        assertThat(result.decisionChanged()).isTrue();
        assertThat(result.changeType()).isEqualTo("MORE_PERMISSIVE");
        // persisted
        assertThat(policyLab.listSimulations(txn.getTransactionReference())).hasSize(1);
    }

    @Test
    void invalidThresholdsRejected() {
        assertThatThrownBy(() -> policyLab.simulate(new PolicySimulationRequest(
                txn.getTransactionReference(), "fraud-policy", "sim-bad", 0.9, 0.5, "analyst-1", null)))
                .isInstanceOf(AnalyticsValidationException.class);

        assertThatThrownBy(() -> policyLab.simulate(new PolicySimulationRequest(
                txn.getTransactionReference(), "fraud-policy", "sim-bad", -0.1, 0.5, "a", null)))
                .isInstanceOf(AnalyticsValidationException.class);

        assertThatThrownBy(() -> policyLab.simulate(new PolicySimulationRequest(
                txn.getTransactionReference(), "fraud-policy", "sim-bad", 0.5, 1.1, "a", null)))
                .isInstanceOf(AnalyticsValidationException.class);
    }

    @Test
    void unknownTransactionRejected() {
        assertThatThrownBy(() -> policyLab.simulate(new PolicySimulationRequest(
                "TXN-DOES-NOT-EXIST", "fraud-policy", "sim-v1", 0.75, 0.9, "analyst-1", null)))
                .isInstanceOf(AnalyticsNotFoundException.class);
    }

    @Test
    void batchSimulationAggregatesOutcomes() {
        BatchPolicySimulationResponse batch = policyLab.simulateBatch(new BatchPolicySimulationRequest(
                List.of(txn.getTransactionReference()), "fraud-policy", "sim-v1", 0.75, 0.9, "analyst-1"));

        assertThat(batch.transactionsSimulated()).isEqualTo(1);
        assertThat(batch.changedCount()).isEqualTo(1);
        assertThat(batch.unchangedCount()).isZero();
        assertThat(batch.outcomeBreakdown()).containsEntry("REVIEW->ALLOW", 1);
        assertThat(batch.results()).hasSize(1);
        assertThat(batch.results().get(0).simulatedDecision()).isEqualTo("ALLOW");
        // each simulated row is persisted
        assertThat(policyLab.listSimulations(txn.getTransactionReference())).hasSize(1);
    }

    @Test
    void simulationDoesNotMutateProductionLineageOrPolicy() {
        int simulationCountBefore = riskScores.findByTransactionId(txn.getId()).size();
        int decisionCountBefore = decisions.findByTransactionId(txn.getId()).size();

        policyLab.simulate(new PolicySimulationRequest(
                txn.getTransactionReference(), "fraud-policy", "sim-v1", 0.75, 0.9, "analyst-1", null));

        List<RiskScore> scores = riskScores.findByTransactionId(txn.getId());
        assertThat(scores).hasSize(simulationCountBefore);
        assertThat(scores.get(0).getRiskScore()).isEqualTo(0.72);
        assertThat(decisions.findByTransactionId(txn.getId())).hasSize(decisionCountBefore);

        DecisionPolicy reloaded = policies.findByPolicyNameAndStatus("fraud-policy", PolicyStatus.ACTIVE)
                .orElseThrow();
        assertThat(reloaded.getConfiguration())
                .containsEntry("review_threshold", 0.5)
                .containsEntry("block_threshold", 0.85);
    }

    @Test
    void simulationAgainstInvestigationRecordsEvent() {
        Investigation inv = investigations.save(new Investigation(
                "INV-P4-" + System.nanoTime(), txn, InvestigationStatus.OPEN,
                InvestigationPriority.HIGH, "analyst-1", Instant.now()));
        investigationEvents.save(new InvestigationEvent(inv, "INVESTIGATION_CREATED", "SYSTEM",
                "analyst-1", Instant.now(), Map.of()));

        PolicySimulationResponse result = policyLab.simulate(new PolicySimulationRequest(
                txn.getTransactionReference(), "fraud-policy", "sim-v1", 0.75, 0.9,
                "analyst-1", inv.getId()));

        assertThat(result.simulationId()).isNotNull();
        List<InvestigationEvent> events = investigationEvents.findByInvestigationId(inv.getId());
        assertThat(events).hasSize(2);
        assertThat(events.get(1).getEventType()).isEqualTo("POLICY_SIMULATION_EXECUTED");
        assertThat(events.get(1).getPayload()).containsEntry("simulationId", result.simulationId().toString());
    }

    @Test
    void simulationRejectsInvestigationBelongingToAnotherTransaction() {
        Transaction other = transactions.save(new Transaction("TXN-P4-OTHER-" + System.nanoTime(),
                user, merchant, null, null, new BigDecimal("500.00"), "INR", "PURCHASE", "ONLINE",
                Instant.now(), TransactionStatus.COMPLETED));
        ModelVersion model = modelVersions.findByModelNameAndVersion("risk-model", "v1").orElseThrow();
        RiskScore otherScore = riskScores.save(new RiskScore(other, model, 0.2, "LOW", Instant.now(), 5));
        decisions.save(new DecisionRecord(other, otherScore, policy, FinalDecision.ALLOW,
                Instant.now(), "low risk"));
        Investigation inv = investigations.save(new Investigation(
                "INV-P4-OTHER-" + System.nanoTime(), other, InvestigationStatus.OPEN,
                InvestigationPriority.LOW, "analyst-1", Instant.now()));

        assertThatThrownBy(() -> policyLab.simulate(new PolicySimulationRequest(
                txn.getTransactionReference(), "fraud-policy", "sim-v1", 0.75, 0.9,
                "analyst-1", inv.getId())))
                .isInstanceOf(AnalyticsValidationException.class);
    }

    @Test
    void missingSimulationVersionRejected() {
        assertThatThrownBy(() -> policyLab.simulate(new PolicySimulationRequest(
                txn.getTransactionReference(), "fraud-policy", null, 0.75, 0.9, "a", null)))
                .isInstanceOf(AnalyticsValidationException.class);
    }
}