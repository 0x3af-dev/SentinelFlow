package com.sentinelflow.investigation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sentinelflow.analytics.dto.AddInvestigationEventRequest;
import com.sentinelflow.analytics.dto.CreateInvestigationRequest;
import com.sentinelflow.analytics.dto.DecisionReplayResponse;
import com.sentinelflow.analytics.dto.EvidenceGraphDto;
import com.sentinelflow.analytics.dto.InvestigationMetadata;
import com.sentinelflow.analytics.dto.InvestigationSummary;
import com.sentinelflow.analytics.dto.PolicySimulationRequest;
import com.sentinelflow.analytics.dto.TimelineEntry;
import com.sentinelflow.analytics.exception.AnalyticsNotFoundException;
import com.sentinelflow.analytics.exception.AnalyticsValidationException;
import com.sentinelflow.analytics.policy.PolicyLabService;
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
import com.sentinelflow.investigation.InvestigationRepository;
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
import java.util.UUID;
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
class InvestigationApiTest {

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
    @Autowired InvestigationApplicationService investigationsApi;
    @Autowired PolicyLabService policyLab;

    private Transaction txn;

    @BeforeEach
    void setUp() {
        User user = users.save(new User("USR-INVAPI-" + System.nanoTime(), "Inv User", null, UserStatus.ACTIVE));
        Merchant merchant = merchants.save(new Merchant("MRC-INVAPI-" + System.nanoTime(), "Inv Mart", "GROCERY", "IN", MerchantStatus.ACTIVE));
        txn = transactions.save(new Transaction("TXN-INVAPI-" + System.nanoTime(), user, merchant,
                null, null, new BigDecimal("12000.00"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-11T10:00:00Z"), TransactionStatus.COMPLETED));

        DecisionPolicy policy = policies.findByPolicyNameAndStatus("fraud-policy", PolicyStatus.ACTIVE)
                .orElseThrow();
        ModelVersion model = modelVersions.findByModelNameAndVersion("risk-model", "v1").orElseThrow();
        snapshots.save(new FeatureSnapshot(txn, "fs-v1",
                Map.of("transaction_amount", 12000.0), Instant.now()));
        RiskScore riskScore = riskScores.save(new RiskScore(txn, model, 0.72, "MEDIUM", Instant.now(), 10));
        decisions.save(new DecisionRecord(txn, riskScore, policy, FinalDecision.REVIEW,
                Instant.now(), "Risk score 0.72 is within review range"));
    }

    @Test
    void createRecordsInvestigationAndInitialEvent() {
        InvestigationMetadata meta = investigationsApi.create(new CreateInvestigationRequest(
                txn.getTransactionReference(), "HIGH", "analyst-1", "review this REVIEW"));

        assertThat(meta.id()).isNotNull();
        assertThat(meta.investigationReference()).startsWith("INV-");
        assertThat(meta.transactionReference()).isEqualTo(txn.getTransactionReference());
        assertThat(meta.status()).isEqualTo("OPEN");
        assertThat(meta.priority()).isEqualTo("HIGH");
        assertThat(meta.assignedTo()).isEqualTo("analyst-1");
        assertThat(meta.eventCount()).isEqualTo(1);

        List<TimelineEntry> timeline = investigationsApi.timeline(meta.id());
        assertThat(timeline).hasSize(1);
        assertThat(timeline.get(0).eventType()).isEqualTo("INVESTIGATION_CREATED");
        assertThat(timeline.get(0).payload()).containsEntry("note", "review this REVIEW");
    }

    @Test
    void createRejectsUnknownTransactionAndPriority() {
        assertThatThrownBy(() -> investigationsApi.create(
                new CreateInvestigationRequest("TXN-MISSING", "HIGH", "a", null)))
                .isInstanceOf(AnalyticsNotFoundException.class);
        assertThatThrownBy(() -> investigationsApi.create(
                new CreateInvestigationRequest(txn.getTransactionReference(), "URGENT", "a", null)))
                .isInstanceOf(AnalyticsValidationException.class);
    }

    @Test
    void getReturnsMetadataWithEventCount() {
        InvestigationMetadata meta = investigationsApi.create(new CreateInvestigationRequest(
                txn.getTransactionReference(), null, null, null));
        InvestigationMetadata fetched = investigationsApi.get(meta.id());
        assertThat(fetched.id()).isEqualTo(meta.id());
        assertThat(fetched.priority()).isEqualTo("MEDIUM");
        assertThat(fetched.eventCount()).isEqualTo(1);
    }

    @Test
    void eventsAreAppendOnly() {
        InvestigationMetadata meta = investigationsApi.create(new CreateInvestigationRequest(
                txn.getTransactionReference(), "LOW", "analyst-1", null));
        investigationsApi.addEvent(meta.id(), new AddInvestigationEventRequest(
                "EVIDENCE_ADDED", "ANALYST", "analyst-1", Map.of("evidence_id", "ev-1")));
        investigationsApi.addEvent(meta.id(), new AddInvestigationEventRequest(
                "NOTE_ADDED", "ANALYST", "analyst-1", Map.of("text", "looks odd")));

        List<TimelineEntry> timeline = investigationsApi.timeline(meta.id());
        assertThat(timeline).hasSize(3);
        assertThat(timeline).extracting(TimelineEntry::eventType)
                .containsExactly("INVESTIGATION_CREATED", "EVIDENCE_ADDED", "NOTE_ADDED");
        // old entries intact
        assertThat(timeline.get(0).payload()).isEmpty();
    }

    @Test
    void decisionReplayAndEvidenceAvailableFromInvestigation() {
        InvestigationMetadata meta = investigationsApi.create(new CreateInvestigationRequest(
                txn.getTransactionReference(), "HIGH", "analyst-1", null));

        DecisionReplayResponse replay = investigationsApi.decisionReplay(meta.id());
        assertThat(replay.decision().finalDecision()).isEqualTo("REVIEW");
        assertThat(replay.riskScore().score()).isEqualTo(0.72);

        EvidenceGraphDto graph = investigationsApi.evidence(meta.id());
        assertThat(graph).isNotNull();
        // no evidence graph was built in this fixture, so it is empty but well-formed
        assertThat(graph.nodes()).isEmpty();
    }

    @Test
    void summaryIncludesDecisionAndRecordedSimulation() {
        InvestigationMetadata meta = investigationsApi.create(new CreateInvestigationRequest(
                txn.getTransactionReference(), "HIGH", "analyst-1", null));

        InvestigationSummary before = investigationsApi.summary(meta.id());
        assertThat(before.transactionReference()).isEqualTo(txn.getTransactionReference());
        assertThat(before.decision().finalDecision()).isEqualTo("REVIEW");
        assertThat(before.investigation().investigationReference()).isEqualTo(meta.investigationReference());
        assertThat(before.policySimulations()).isEmpty();

        policyLab.simulate(new PolicySimulationRequest(
                txn.getTransactionReference(), "fraud-policy", "sim-v1", 0.75, 0.9,
                "analyst-1", meta.id()));

        InvestigationSummary after = investigationsApi.summary(meta.id());
        assertThat(after.policySimulations()).hasSize(1);
        assertThat(after.policySimulations().get(0).simulatedDecision()).isEqualTo("ALLOW");
        assertThat(after.investigation().eventCount()).isEqualTo(2);
    }

    @Test
    void unknownInvestigationRejected() {
        assertThatThrownBy(() -> investigationsApi.get(UUID.randomUUID()))
                .isInstanceOf(AnalyticsNotFoundException.class);
        assertThatThrownBy(() -> investigationsApi.summary(UUID.randomUUID()))
                .isInstanceOf(AnalyticsNotFoundException.class);
    }
}