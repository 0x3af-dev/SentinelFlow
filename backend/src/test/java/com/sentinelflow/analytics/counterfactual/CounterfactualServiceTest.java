package com.sentinelflow.analytics.counterfactual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sentinelflow.analytics.dto.CounterfactualRequest;
import com.sentinelflow.analytics.dto.CounterfactualRequest.CounterfactualFeatureModification;
import com.sentinelflow.analytics.dto.CounterfactualResponse;
import com.sentinelflow.analytics.exception.AnalyticsMlUnavailableException;
import com.sentinelflow.analytics.exception.AnalyticsValidationException;
import com.sentinelflow.analytics.model.CounterfactualAnalysisRepository;
import com.sentinelflow.decision.DecisionPolicy;
import com.sentinelflow.decision.DecisionPolicyRepository;
import com.sentinelflow.decision.DecisionRecord;
import com.sentinelflow.decision.DecisionRecordRepository;
import com.sentinelflow.decision.FinalDecision;
import com.sentinelflow.decision.PolicyStatus;
import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserRepository;
import com.sentinelflow.identity.UserStatus;
import com.sentinelflow.ml.MlInferenceClient;
import com.sentinelflow.ml.MlInferenceRequest;
import com.sentinelflow.risk.FeatureSnapshot;
import com.sentinelflow.risk.FeatureSnapshotRepository;
import com.sentinelflow.risk.ModelVersion;
import com.sentinelflow.risk.ModelVersionRepository;
import com.sentinelflow.risk.RiskScore;
import com.sentinelflow.risk.RiskScoreRepository;
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
class CounterfactualServiceTest {

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
    @Autowired CounterfactualAnalysisRepository analyses;
    @Autowired CounterfactualService counterfactual;

    @MockitoBean
    MlInferenceClient mlClient;

    private Transaction txn;

    @BeforeEach
    void setUp() {
        User user = users.save(new User("USR-CF-" + System.nanoTime(), "CF User", null, UserStatus.ACTIVE));
        Merchant merchant = merchants.save(new Merchant("MRC-CF-" + System.nanoTime(), "CF Mart", "GROCERY", "IN", MerchantStatus.ACTIVE));
        txn = transactions.save(new Transaction("TXN-CF-" + System.nanoTime(), user, merchant,
                null, null, new BigDecimal("12000.00"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-10T10:00:00Z"), TransactionStatus.COMPLETED));

        DecisionPolicy policy = policies.findByPolicyNameAndStatus("fraud-policy", PolicyStatus.ACTIVE)
                .orElseThrow();
        ModelVersion model = modelVersions.findByModelNameAndVersion("risk-model", "v1").orElseThrow();

        snapshots.save(new FeatureSnapshot(txn, "fs-v1", Map.of(
                "transaction_amount", 12000.0,
                "user_avg_transaction_amount", 5000.0,
                "transactions_last_1_hour", 2,
                "transactions_last_10_minutes", 1,
                "transactions_last_24_hours", 5), Instant.now()));
        RiskScore riskScore = riskScores.save(new RiskScore(txn, model, 0.72, "MEDIUM", Instant.now(), 10));
        decisions.save(new DecisionRecord(txn, riskScore, policy, FinalDecision.REVIEW,
                Instant.now(), "Risk score 0.72 is within review range"));

        when(mlClient.infer(any(MlInferenceRequest.class))).thenAnswer(inv -> {
            MlInferenceRequest req = inv.getArgument(0);
            double score = req.transactionAmount() >= 60000 ? 0.9 : 0.72;
            String pred = score >= 0.85 ? "HIGH" : "MEDIUM";
            return new MlPrediction("risk-model", "v1", "fs-v1", score, pred,
                    List.of(), Map.of(), 12, Instant.now());
        });
    }

    private CounterfactualRequest request(String feature, Object value) {
        return new CounterfactualRequest(txn.getTransactionReference(),
                List.of(new CounterfactualFeatureModification(feature, value)), "analyst-1", null);
    }

    @Test
    void hypotheticalAmountFlipFlipsDecisionToBlock() {
        CounterfactualResponse result = counterfactual.analyze(request("transaction_amount", 60000.0));

        assertThat(result.originalRiskScore()).isEqualTo(0.72);
        assertThat(result.hypotheticalRiskScore()).isEqualTo(0.9);
        assertThat(result.originalDecision()).isEqualTo("REVIEW");
        assertThat(result.hypotheticalDecision()).isEqualTo("BLOCK");
        assertThat(result.decisionChanged()).isTrue();
        assertThat(result.changeType()).isEqualTo("MORE_RESTRICTIVE");
        assertThat(result.modelVersion()).isEqualTo("v1");
        assertThat(result.analysisId()).isNotNull();
        assertThat(counterfactual.listCounterfactuals(txn.getTransactionReference())).hasSize(1);
    }

    @Test
    void noDecisionChangeWhenScoreStable() {
        CounterfactualResponse result = counterfactual.analyze(request("transactions_last_1_hour", 4));

        assertThat(result.originalDecision()).isEqualTo("REVIEW");
        assertThat(result.hypotheticalDecision()).isEqualTo("REVIEW");
        assertThat(result.decisionChanged()).isFalse();
        assertThat(result.changeType()).isEqualTo("UNCHANGED");
        assertThat(counterfactual.listCounterfactuals(txn.getTransactionReference())).hasSize(1);
    }

    @Test
    void snapshotRemainsUntouched() {
        counterfactual.analyze(request("transaction_amount", 60000.0));

        FeatureSnapshot snapshot = snapshots.findFirstByTransactionIdOrderByCreatedAtDesc(txn.getId())
                .orElseThrow();
        assertThat(snapshot.getFeatures()).containsEntry("transaction_amount", 12000.0);
        assertThat(riskScores.findByTransactionId(txn.getId()).get(0).getRiskScore()).isEqualTo(0.72);
        assertThat(decisions.findByTransactionId(txn.getId()).get(0).getFinalDecision())
                .isEqualTo(FinalDecision.REVIEW);
    }

    @Test
    void unsupportedFeatureRejectedBeforeModelCall() {
        assertThatThrownBy(() -> counterfactual.analyze(request("bogus_feature", 5.0)))
                .isInstanceOf(AnalyticsValidationException.class);
        verify(mlClient, never()).infer(any(MlInferenceRequest.class));
    }

    @Test
    void nonNumericValueRejectedBeforeModelCall() {
        assertThatThrownBy(() -> counterfactual.analyze(request("transaction_amount", "lots")))
                .isInstanceOf(AnalyticsValidationException.class);
        verify(mlClient, never()).infer(any(MlInferenceRequest.class));
    }

    @Test
    void nonFiniteValueRejected() {
        assertThatThrownBy(() -> counterfactual.analyze(request("transaction_amount", Double.NaN)))
                .isInstanceOf(AnalyticsValidationException.class);
        assertThatThrownBy(() -> counterfactual.analyze(request("transaction_amount", Double.POSITIVE_INFINITY)))
                .isInstanceOf(AnalyticsValidationException.class);
        verify(mlClient, never()).infer(any(MlInferenceRequest.class));
    }

    @Test
    void negativeValueOutOfDomainRejected() {
        assertThatThrownBy(() -> counterfactual.analyze(request("transaction_amount", -5.0)))
                .isInstanceOf(AnalyticsValidationException.class);
        verify(mlClient, never()).infer(any(MlInferenceRequest.class));
    }

    @Test
    void fractionalValueOnIntegralFeatureRejected() {
        assertThatThrownBy(() -> counterfactual.analyze(request("transactions_last_1_hour", 2.5)))
                .isInstanceOf(AnalyticsValidationException.class);
        verify(mlClient, never()).infer(any(MlInferenceRequest.class));
    }

    @Test
    void duplicateFeatureModificationRejected() {
        CounterfactualRequest dup = new CounterfactualRequest(txn.getTransactionReference(),
                List.of(new CounterfactualFeatureModification("transaction_amount", 5000.0),
                        new CounterfactualFeatureModification("transaction_amount", 9000.0)),
                "analyst-1", null);
        assertThatThrownBy(() -> counterfactual.analyze(dup))
                .isInstanceOf(AnalyticsValidationException.class);
        verify(mlClient, never()).infer(any(MlInferenceRequest.class));
    }

    @Test
    void mlFailureReportedAndNothingPersisted() {
        when(mlClient.infer(any(MlInferenceRequest.class)))
                .thenThrow(new MlInferenceClient.MlInferenceException("ML down for counterfactual"));
        assertThatThrownBy(() -> counterfactual.analyze(request("transaction_amount", 70000.0)))
                .isInstanceOf(AnalyticsMlUnavailableException.class);
        assertThat(analyses.findAll()).isEmpty();
    }
}