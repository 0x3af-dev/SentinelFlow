package com.sentinelflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sentinelflow.ai.dto.InvestigationExplanation;
import com.sentinelflow.ai.dto.InvestigationExplanationRequest;
import com.sentinelflow.ai.dto.InvestigationRequestType;
import com.sentinelflow.ai.exception.AiResponseInvalidException;
import com.sentinelflow.ai.exception.AiUnavailableException;
import com.sentinelflow.ai.gateway.AiGateway;
import com.sentinelflow.ai.model.AiInvestigationRun;
import com.sentinelflow.ai.repo.AiInvestigationRunRepository;
import com.sentinelflow.ai.service.AiInvestigationService;
import com.sentinelflow.ai.tool.InvestigationAiTools;
import com.sentinelflow.analytics.dto.CreateInvestigationRequest;
import com.sentinelflow.analytics.dto.DecisionReplayResponse;
import com.sentinelflow.analytics.dto.InvestigationMetadata;
import com.sentinelflow.decision.DecisionRecordRepository;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration coverage for the evidence-grounded AI investigator with a
 * scriptable fake gateway (no real provider). The fake behaves like a model:
 * it is only allowed the read-only tool surface, its output is validated
 * against the persisted decision and evidence universe, and every run is
 * recorded for audit.
 */
@SpringBootTest(properties = "sentinelflow.ai.enabled=true")
@Testcontainers
class AiInvestigationServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    MlInferenceClient mlClient;

    @Autowired AiInvestigationService aiService;
    @Autowired AiInvestigationRunRepository runs;
    @Autowired TransactionRepository transactions;
    @Autowired UserRepository users;
    @Autowired MerchantRepository merchants;
    @Autowired DeviceRepository devices;
    @Autowired LocationRepository locations;
    @Autowired TransactionIntelligencePipeline pipeline;
    @Autowired InvestigationApplicationService investigations;
    @Autowired DecisionRecordRepository decisionRecords;
    @Autowired RiskScoreRepository riskScores;
    @Autowired EvidenceNodeRepository evidenceNodes;

    private UUID investigationId;
    private String transactionReference;

    private static volatile GatewayScript script;

    @BeforeEach
    void setUp() {
        transactionReference = "txn-ai-e2e-" + UUID.randomUUID();
        script = defaultScript(transactionReference);
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

        User user = users.save(new User("USR-AI-" + System.nanoTime(), "AI User", null, UserStatus.ACTIVE));
        Merchant merchant = merchants.save(new Merchant("MRC-AI-" + System.nanoTime(), "AI Mart", "GROCERY", "IN",
                MerchantStatus.ACTIVE));
        Device device = devices.save(new Device(user, "DEV-AI-" + System.nanoTime(), "MOBILE", "ANDROID",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.now()));
        Location location = locations.save(new Location(user, "IN", "Karnataka", "Bengaluru",
                12.97, 77.59, Instant.parse("2026-01-01T00:00:00Z"), Instant.now()));
        transactions.save(new Transaction(transactionReference, user, merchant, device, location,
                new BigDecimal("12000.00"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-14T10:00:00Z"), TransactionStatus.RECEIVED));

        pipeline.process(transactionReference);
        InvestigationMetadata inv = investigations.create(new CreateInvestigationRequest(
                transactionReference, "HIGH", "analyst-1", "AI trade study"));
        investigationId = inv.id();
    }

    @Test
    void validExplanationIsReturnedAndAuditedWithoutMutatingProductionData() {
        int decisionsBefore = decisionRecords.findByTransactionId(getTxnId()).size();
        int riskBefore = riskScores.findByTransactionId(getTxnId()).size();
        long evidenceBefore = evidenceNodes.count();

        InvestigationExplanation answer = aiService.explain(investigationId,
                new InvestigationExplanationRequest(InvestigationRequestType.SUMMARIZE, null));

        assertThat(answer.summary()).isNotBlank();
        assertThat(answer.riskAssessment().recordedDecision()).isEqualTo("REVIEW");
        assertThat(answer.riskAssessment().recordedRiskScore()).isEqualTo(0.6);
        assertThat(answer.riskAssessment().evidenceIds()).isNotEmpty();
        assertThat(answer.evidenceReferences()).anyMatch(r -> answer.riskAssessment().evidenceIds().contains(r.evidenceId()));

        List<AiInvestigationRun> saved = runs.findByInvestigationIdOrderByCreatedAtDesc(investigationId);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStatus()).isEqualTo(AiInvestigationRun.Status.SUCCEEDED);
        assertThat(saved.get(0).getProvider()).isEqualTo("fake-gateway");
        assertThat(saved.get(0).getResponse()).isNotNull();
        assertThat(saved.get(0).getLatencyMs()).isNotNull();
        assertThat(saved.get(0).getCorrelationId()).isNotBlank();

        assertThat(decisionRecords.findByTransactionId(getTxnId())).hasSize(decisionsBefore);
        assertThat(riskScores.findByTransactionId(getTxnId())).hasSize(riskBefore);
        assertThat(evidenceNodes.count()).isEqualTo(evidenceBefore);
    }

    @Test
    void providerFailureMapsToAiUnavailableAndRecordsFailedRun() {
        script = request -> {
            throw new AiUnavailableException("provider timeout");
        };

        assertThatThrownBy(() -> aiService.explain(investigationId,
                new InvestigationExplanationRequest(InvestigationRequestType.SUMMARIZE, null)))
                .isInstanceOf(AiUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", "AI_UNAVAILABLE");

        List<AiInvestigationRun> saved = runs.findByInvestigationIdOrderByCreatedAtDesc(investigationId);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStatus()).isEqualTo(AiInvestigationRun.Status.FAILED);
        assertThat(saved.get(0).getErrorCode()).isEqualTo("AI_UNAVAILABLE");
        assertThat(saved.get(0).getResponse()).isNull();
    }

    @Test
    void fabricatedEvidenceAndWrongDecisionAreRejected() {
        String madeUp = UUID.randomUUID().toString();
        script = request -> {
            var tools = (InvestigationAiTools) request.tools();
            DecisionReplayResponse replay = tools.getRiskDecision(transactionReference).replay();
            return canned("ALLOW", replay.riskScore().score(), policyRef(replay), madeUp, replay);
        };

        assertThatThrownBy(() -> aiService.explain(investigationId,
                new InvestigationExplanationRequest(InvestigationRequestType.SUMMARIZE, null)))
                .isInstanceOf(AiResponseInvalidException.class)
                .hasFieldOrPropertyWithValue("code", "AI_RESPONSE_INVALID");

        List<AiInvestigationRun> saved = runs.findByInvestigationIdOrderByCreatedAtDesc(investigationId);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStatus()).isEqualTo(AiInvestigationRun.Status.FAILED);
        assertThat(saved.get(0).getErrorCode()).isEqualTo("AI_RESPONSE_INVALID");
        assertThat(saved.get(0).getResponse()).isNull();
    }

    @Test
    void toolBudgetExhaustionIsSurfacedAsControlledBadGateway() {
        script = request -> {
            var tools = (InvestigationAiTools) request.tools();
            try {
                for (int i = 0; i < 100; i++) {
                    tools.getRiskDecision(transactionReference);
                }
                throw new IllegalStateException("budget was not enforced");
            } catch (com.sentinelflow.ai.exception.ToolBudgetExceededException e) {
                throw new com.sentinelflow.ai.exception.AiProviderResponseException("tool budget exceeded", e);
            }
        };

        assertThatThrownBy(() -> aiService.explain(investigationId,
                new InvestigationExplanationRequest(InvestigationRequestType.SUMMARIZE, null)))
                .isInstanceOf(AiResponseInvalidException.class)
                .hasFieldOrPropertyWithValue("code", "AI_RESPONSE_INVALID");

        List<AiInvestigationRun> saved = runs.findByInvestigationIdOrderByCreatedAtDesc(investigationId);
        assertThat(saved.get(0).getStatus()).isEqualTo(AiInvestigationRun.Status.FAILED);
        assertThat(saved.get(0).getErrorCode()).isEqualTo("AI_RESPONSE_INVALID");
    }

    @Test
    void missingSimulationReferenceIsRejected() {
        script = request -> {
            var tools = (InvestigationAiTools) request.tools();
            DecisionReplayResponse replay = tools.getRiskDecision(transactionReference).replay();
            String evId = replay.evidence().nodes().get(0).id().toString();
            InvestigationExplanation bad = canned(replay.decision().finalDecision(),
                    replay.riskScore().score(), policyRef(replay), evId, replay);
            InvestigationExplanation withFakeSim = new InvestigationExplanation(
                    bad.investigationId(), bad.transactionReference(), bad.requestType(), bad.summary(),
                    bad.observations(), bad.riskAssessment(), bad.modelFindings(), bad.ruleFindings(),
                    bad.behavioralFindings(), bad.evidenceConflicts(),
                    List.of(new InvestigationExplanation.SimulationExplanation(
                            UUID.randomUUID(), "fraud-policy", "vX", "ALLOW",
                            "the simulation indicates ALLOW",
                            "Hypothetical; the persisted decision stays REVIEW.", List.of(evId))),
                    bad.counterfactuals(), bad.uncertainty(), bad.recommendedNextEvidence(),
                    bad.evidenceReferences(), bad.generatedAt(), bad.modelMetadata());
            return withFakeSim;
        };

        assertThatThrownBy(() -> aiService.explain(investigationId,
                new InvestigationExplanationRequest(InvestigationRequestType.CONFLICTS, null)))
                .isInstanceOf(AiResponseInvalidException.class);
    }

    private UUID getTxnId() {
        return transactions.findByTransactionReference(transactionReference)
                .orElseThrow().getId();
    }

    private static String policyRef(DecisionReplayResponse replay) {
        return replay.policy().name() + "/" + replay.policy().version();
    }

    private static InvestigationExplanation canned(String decision, double score, String policy,
                                                   String evidenceId, DecisionReplayResponse replay) {
        return new InvestigationExplanation(
                UUID.randomUUID(), replay.transactionReference(), "SUMMARIZE",
                "The transaction was scored " + score + " and the persisted decision is " + decision + ".",
                List.of(new InvestigationExplanation.Observation("Risk score elevated", List.of(evidenceId))),
                new InvestigationExplanation.RiskAssessment(score, decision, policy,
                        "Persisted policy maps this score to " + decision + ".", List.of(evidenceId)),
                List.of(new InvestigationExplanation.Finding("MODEL_ELEVATED_RISK", List.of(evidenceId))),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.<InvestigationExplanation.Uncertainty>of(),
                List.of(new InvestigationExplanation.RecommendedEvidence("review device history", "device is new")),
                List.of(new InvestigationExplanation.EvidenceReference(
                        evidenceId, "DECISION", replay.transactionReference(), "persisted decision")),
                Instant.now(),
                new InvestigationExplanation.ModelMetadata("fake-gateway", "gpt-4o-mini", 1, "c1"));
    }

    private static GatewayScript defaultScript(String txnRef) {
        return request -> {
            var tools = (InvestigationAiTools) request.tools();
            var decision = tools.getRiskDecision(txnRef);
            if (decision.error() != null) {
                throw new AiUnavailableException("tool failed: " + decision.error().message());
            }
            DecisionReplayResponse replay = decision.replay();
            String evidenceId = replay.evidence().nodes().get(0).id().toString();
            return canned(replay.decision().finalDecision(), replay.riskScore().score(),
                    policyRef(replay), evidenceId, replay);
        };
    }

    @FunctionalInterface
    interface GatewayScript {
        InvestigationExplanation run(AiGateway.GenerationRequest request);
    }

    @TestConfiguration
    static class FakeGatewayConfig {

        @Bean
        @Primary
        AiGateway fakeAiGateway() {
            return new AiGateway() {
                @Override
                public String provider() {
                    return "fake-gateway";
                }

                @Override
                public String model() {
                    return "gpt-4o-mini";
                }

                @Override
                public InvestigationExplanation generate(GenerationRequest request) {
                    return script.run(request);
                }
            };
        }
    }
}