package com.sentinelflow.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sentinelflow.analytics.counterfactual.CounterfactualService;
import com.sentinelflow.analytics.dto.CounterfactualRequest;
import com.sentinelflow.analytics.dto.CounterfactualRequest.CounterfactualFeatureModification;
import com.sentinelflow.analytics.dto.CounterfactualResponse;
import com.sentinelflow.analytics.dto.CreateInvestigationRequest;
import com.sentinelflow.analytics.dto.DecisionReplayResponse;
import com.sentinelflow.analytics.dto.InvestigationMetadata;
import com.sentinelflow.analytics.dto.InvestigationSummary;
import com.sentinelflow.analytics.dto.PolicySimulationRequest;
import com.sentinelflow.analytics.dto.PolicySimulationResponse;
import com.sentinelflow.analytics.policy.PolicyLabService;
import com.sentinelflow.analytics.replay.DecisionReplayService;
import com.sentinelflow.decision.DecisionRecord;
import com.sentinelflow.decision.DecisionRecordRepository;
import com.sentinelflow.decision.FinalDecision;
import com.sentinelflow.evidence.EvidenceEdgeRepository;
import com.sentinelflow.evidence.EvidenceNodeRepository;
import com.sentinelflow.identity.Device;
import com.sentinelflow.identity.DeviceRepository;
import com.sentinelflow.identity.Location;
import com.sentinelflow.identity.LocationRepository;
import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserRepository;
import com.sentinelflow.identity.UserStatus;
import com.sentinelflow.investigation.service.InvestigationApplicationService;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The spec's end-to-end story (txn-demo-001):
 *   production decision REVIEW (score 0.72)
 *   policy simulation with review threshold 0.75 flips it to ALLOW
 *   counterfactual raising the amount flips the hypothetical decision to BLOCK
 * All while the production records stay untouched.
 */
@SpringBootTest
@Testcontainers
class Phase4EndToEndTest {

    private static final String DEMO_REF = "txn-demo-001";
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
    @Autowired EvidenceNodeRepository evidenceNodes;
    @Autowired EvidenceEdgeRepository evidenceEdges;
    @Autowired TransactionIntelligencePipeline pipeline;
    @Autowired PolicyLabService policyLab;
    @Autowired CounterfactualService counterfactuals;
    @Autowired InvestigationApplicationService investigations;
    @Autowired DecisionReplayService replay;

    @MockitoBean
    MlInferenceClient mlClient;

    @BeforeEach
    void setUp() {
        when(mlClient.infer(any(MlInferenceRequest.class))).thenAnswer(inv -> {
            MlInferenceRequest req = inv.getArgument(0);
            double score = req.transactionAmount() > 50000 ? 0.9
                    : req.transactionAmount() > 10000 ? 0.6 : 0.2;
            String pred = score >= 0.85 ? "HIGH" : score >= 0.5 ? "MEDIUM" : "LOW";
            List<MlPrediction.RiskFactorDto> factors = List.of();
            if (score >= 0.5) {
                factors = List.of(new MlPrediction.RiskFactorDto(
                        "MODEL_ELEVATED_RISK", "elevated risk", "MEDIUM", Map.of("risk_score", score)));
            }
            return new MlPrediction("risk-model", "v1", "fs-v1", score, pred,
                    factors, Map.of("model_type", "mock"), 10, Instant.now());
        });
    }

    @Test
    void endToEndDemoStory() {
        User user = users.save(new User("USR-DEMO-" + System.nanoTime(), "Demo User", null, UserStatus.ACTIVE));
        Merchant merchant = merchants.save(new Merchant("MRC-DEMO-" + System.nanoTime(), "Demo Mart", "GROCERY", "IN", MerchantStatus.ACTIVE));
        Device knownDevice = devices.save(new Device(user, "DEV-DEMO-" + System.nanoTime(),
                "MOBILE", "ANDROID", Instant.parse("2026-01-01T00:00:00Z"), Instant.now()));
        Location knownLocation = locations.save(new Location(user, "IN", "Karnataka", "Bengaluru",
                12.97, 77.59, Instant.parse("2026-01-01T00:00:00Z"), Instant.now()));

        Transaction txn = transactions.save(new Transaction(DEMO_REF, user, merchant,
                knownDevice, knownLocation, new BigDecimal("12000.00"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-14T10:00:00Z"), com.sentinelflow.transaction.TransactionStatus.RECEIVED));

        // 1. Produce the real decision. Amount 12000 -> mock score 0.6 (MEDIUM) -> REVIEW.
        PipelineResult result = pipeline.process(DEMO_REF);
        assertThat(result.decision()).isEqualTo("REVIEW");
        double productionScore = result.riskScore();

        // Baseline production state for immutability verification.
        long snapshotRows = snapshots.findByTransactionId(txn.getId()).size();
        long riskScoreRows = riskScores.findByTransactionId(txn.getId()).size();
        long decisionRows = decisions.findByTransactionId(txn.getId()).size();
        long evidenceNodeRows = evidenceNodes.count();
        long evidenceEdgeRows = evidenceEdges.count();
        FeatureSnapshot snapshotBefore = snapshots.findByTransactionId(txn.getId()).get(0);

        // 2. Policy Lab: proposed review threshold 0.75 -> ALLOW, but production stays REVIEW.
        PolicySimulationResponse sim = policyLab.simulate(new PolicySimulationRequest(
                DEMO_REF, "fraud-policy", "sim-v1", 0.75, 0.9, "analyst-1", null));
        assertThat(sim.actualDecision()).isEqualTo("REVIEW");
        assertThat(sim.simulatedDecision()).isEqualTo("ALLOW");
        assertThat(sim.decisionChanged()).isTrue();
        assertThat(sim.changeType()).isEqualTo("MORE_PERMISSIVE");

        // 3. Counterfactual: raise amount to 60000 -> hypothetical score 0.9 -> BLOCK.
        CounterfactualResponse count = counterfactuals.analyze(new CounterfactualRequest(
                DEMO_REF, List.of(new CounterfactualFeatureModification("transaction_amount", 60000.0)),
                "analyst-1", null));
        assertThat(count.originalRiskScore()).isEqualTo(productionScore);
        assertThat(count.hypotheticalRiskScore()).isEqualTo(0.9);
        assertThat(count.originalDecision()).isEqualTo("REVIEW");
        assertThat(count.hypotheticalDecision()).isEqualTo("BLOCK");
        assertThat(count.decisionChanged()).isTrue();
        assertThat(count.changeType()).isEqualTo("MORE_RESTRICTIVE");

        // 4. Production records are unchanged.
        assertThat(snapshots.findByTransactionId(txn.getId()).size()).isEqualTo(snapshotRows);
        assertThat(riskScores.findByTransactionId(txn.getId()).size()).isEqualTo(riskScoreRows);
        assertThat(decisions.findByTransactionId(txn.getId()).size()).isEqualTo(decisionRows);
        assertThat(evidenceNodes.count()).isEqualTo(evidenceNodeRows);
        assertThat(evidenceEdges.count()).isEqualTo(evidenceEdgeRows);
        RiskScore riskScoreAfter = riskScores.findByTransactionId(txn.getId()).get(0);
        assertThat(riskScoreAfter.getRiskScore()).isEqualTo(productionScore);
        DecisionRecord decisionAfter = decisions.findByTransactionId(txn.getId()).get(0);
        assertThat(decisionAfter.getFinalDecision()).isEqualTo(FinalDecision.REVIEW);
        FeatureSnapshot snapshotAfter = snapshots.findByTransactionId(txn.getId()).get(0);
        assertThat(snapshotAfter.getId()).isEqualTo(snapshotBefore.getId());
        assertThat(snapshotAfter.getFeatures()).containsEntry("transaction_amount", 12000.0);

        // 5. The investigation can surface the whole analytical picture.
        InvestigationMetadata inv = investigations.create(new CreateInvestigationRequest(
                DEMO_REF, "HIGH", "analyst-1", "Review the REVIEW"));
        InvestigationSummary summary = investigations.summary(inv.id());
        assertThat(summary.decision().finalDecision()).isEqualTo("REVIEW");
        assertThat(summary.investigation().investigationReference()).isEqualTo(inv.investigationReference());
        assertThat(summary.policySimulations()).hasSize(1);
        assertThat(summary.policySimulations().get(0).simulatedDecision()).isEqualTo("ALLOW");
        assertThat(summary.counterfactuals()).hasSize(1);
        assertThat(summary.counterfactuals().get(0).hypotheticalDecision()).isEqualTo("BLOCK");
        assertThat(summary.evidenceNodeCount()).isGreaterThan(0);
        assertThat(summary.disagreement().category()).isIn(DISAGREEMENT_CATEGORIES);

        // 6. Decision replay reconstructs the lineage read-only.
        DecisionReplayResponse replayResult = replay.replay(DEMO_REF);
        assertThat(replayResult.decision().finalDecision()).isEqualTo("REVIEW");
        assertThat(replayResult.riskScore().score()).isEqualTo(productionScore);
        assertThat(replayResult.featureSnapshot().features()).containsEntry("transaction_amount", 12000.0);
        assertThat(replayResult.policy().name()).isEqualTo("fraud-policy");
        assertThat(replayResult.evidence().nodeCount()).isGreaterThan(0);
    }
}