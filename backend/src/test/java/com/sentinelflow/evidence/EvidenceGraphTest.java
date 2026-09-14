package com.sentinelflow.evidence;

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
import com.sentinelflow.risk.RiskFactor;
import com.sentinelflow.risk.RiskFactorRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Transactional
class EvidenceGraphTest {

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
    RiskFactorRepository riskFactors;

    @Autowired
    EvidenceNodeRepository nodes;

    @Autowired
    EvidenceEdgeRepository edges;

    private Transaction transaction;
    private RiskScore riskScore;
    private RiskFactor riskFactor;

    @BeforeEach
    void setUp() {
        User user = users.save(new User("USR-EVD-" + System.nanoTime(), "Evidence User", null, UserStatus.ACTIVE));
        Merchant merchant = merchants.save(new Merchant("MRC-EVD-" + System.nanoTime(), "EVD Mart", "GROCERY", "IN", MerchantStatus.ACTIVE));
        transaction = transactions.save(new Transaction("TXN-EVD-" + System.nanoTime(), user, merchant,
                null, null, new BigDecimal("750.00"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-01T10:00:00Z"), TransactionStatus.SCORING));
        ModelVersion mv = modelVersions.save(new ModelVersion("risk-model", "v-evd-" + System.nanoTime(),
                "xgb", "fs-v1", null, null, ModelStatus.ACTIVE));
        riskScore = riskScores.save(new RiskScore(transaction, mv, 0.88, "HIGH",
                Instant.parse("2026-09-01T10:00:02Z"), 35));
        riskFactor = riskFactors.save(new RiskFactor(transaction, "NEW_DEVICE", "First seen device", "HIGH", "RULE_ENGINE"));
    }

    @Test
    void createNodesAndEdges() {
        EvidenceNode txnNode = nodes.saveAndFlush(new EvidenceNode(
                EvidenceNodeType.TRANSACTION, EvidenceSourceType.TRANSACTION_RECORD,
                "Transaction", transaction.getId(), transaction.getTransactionTimestamp(),
                Map.of("amount", 750.0, "currency", "INR"), 1.0, Map.of()));

        EvidenceNode deviceNode = nodes.saveAndFlush(new EvidenceNode(
                EvidenceNodeType.DEVICE, EvidenceSourceType.DEVICE_HISTORY,
                "Device", null, Instant.now(),
                Map.of("platform", "ANDROID", "first_seen", "2026-09-01T10:00:00Z"), 0.9, Map.of()));

        EvidenceNode modelNode = nodes.saveAndFlush(new EvidenceNode(
                EvidenceNodeType.MODEL_PREDICTION, EvidenceSourceType.MODEL_OUTPUT,
                "RiskScore", riskScore.getId(), riskScore.getInferenceTimestamp(),
                Map.of("score", 0.88, "prediction", "HIGH"), 0.95, Map.of()));

        EvidenceNode factorNode = nodes.saveAndFlush(new EvidenceNode(
                EvidenceNodeType.RISK_FACTOR, EvidenceSourceType.RULE_ENGINE,
                "RiskFactor", riskFactor.getId(), riskFactor.getCreatedAt(),
                Map.of("type", "NEW_DEVICE", "severity", "HIGH"), 0.9, Map.of()));

        edges.saveAndFlush(new EvidenceEdge(txnNode, deviceNode, EvidenceRelationshipType.USED_DEVICE, Map.of()));
        edges.saveAndFlush(new EvidenceEdge(txnNode, modelNode, EvidenceRelationshipType.GENERATED_FEATURE, Map.of()));
        edges.saveAndFlush(new EvidenceEdge(modelNode, factorNode, EvidenceRelationshipType.CONTRIBUTES_TO, Map.of()));

        // Traverse outgoing edges from transaction
        List<EvidenceEdge> outgoing = edges.findBySourceNodeId(txnNode.getId());
        assertThat(outgoing).hasSize(2);

        // Traverse incoming edges to model prediction
        List<EvidenceEdge> incoming = edges.findByTargetNodeId(modelNode.getId());
        assertThat(incoming).hasSize(1);
        assertThat(incoming.get(0).getRelationshipType()).isEqualTo(EvidenceRelationshipType.GENERATED_FEATURE);
    }

    @Test
    void invalidSourceNodeFails() {
        EvidenceNode valid = nodes.save(new EvidenceNode(
                EvidenceNodeType.TRANSACTION, EvidenceSourceType.TRANSACTION_RECORD,
                "Transaction", transaction.getId(), Instant.now(), Map.of(), 1.0, Map.of()));
        nodes.flush();

        EvidenceNode ghost = new EvidenceNode(
                EvidenceNodeType.DEVICE, EvidenceSourceType.DEVICE_HISTORY,
                "Device", UUID.randomUUID(), Instant.now(), Map.of(), 1.0, Map.of());
        // ghost is transient, not persisted -> FK violation
        assertThatThrownBy(() -> edges.saveAndFlush(new EvidenceEdge(ghost, valid, EvidenceRelationshipType.USED_DEVICE, Map.of())))
                .isInstanceOf(Exception.class);
    }

    @Test
    void invalidTargetNodeFails() {
        EvidenceNode valid = nodes.save(new EvidenceNode(
                EvidenceNodeType.TRANSACTION, EvidenceSourceType.TRANSACTION_RECORD,
                "Transaction", transaction.getId(), Instant.now(), Map.of(), 1.0, Map.of()));
        nodes.flush();

        EvidenceNode ghost = new EvidenceNode(
                EvidenceNodeType.DEVICE, EvidenceSourceType.DEVICE_HISTORY,
                "Device", UUID.randomUUID(), Instant.now(), Map.of(), 1.0, Map.of());

        assertThatThrownBy(() -> edges.saveAndFlush(new EvidenceEdge(valid, ghost, EvidenceRelationshipType.USED_DEVICE, Map.of())))
                .isInstanceOf(Exception.class);
    }

    @Test
    void duplicateEdgeRejected() {
        EvidenceNode a = nodes.saveAndFlush(new EvidenceNode(
                EvidenceNodeType.TRANSACTION, EvidenceSourceType.TRANSACTION_RECORD,
                "Transaction", transaction.getId(), Instant.now(), Map.of(), 1.0, Map.of()));
        EvidenceNode b = nodes.saveAndFlush(new EvidenceNode(
                EvidenceNodeType.DEVICE, EvidenceSourceType.DEVICE_HISTORY,
                "Device", null, Instant.now(), Map.of(), 1.0, Map.of()));

        edges.saveAndFlush(new EvidenceEdge(a, b, EvidenceRelationshipType.USED_DEVICE, Map.of()));

        assertThatThrownBy(() -> edges.saveAndFlush(new EvidenceEdge(a, b, EvidenceRelationshipType.USED_DEVICE, Map.of())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void fullSyntheticChainTraversable() {
        // Transaction -> Device -> Behavior -> Risk Factor -> Decision (conceptual chain via evidence graph)
        EvidenceNode txn = nodes.saveAndFlush(new EvidenceNode(
                EvidenceNodeType.TRANSACTION, EvidenceSourceType.TRANSACTION_RECORD,
                "Transaction", transaction.getId(), transaction.getTransactionTimestamp(),
                Map.of("ref", transaction.getTransactionReference()), 1.0, Map.of()));

        EvidenceNode dev = nodes.saveAndFlush(new EvidenceNode(
                EvidenceNodeType.DEVICE, EvidenceSourceType.DEVICE_HISTORY, "Device", null, Instant.now(),
                Map.of("type", "MOBILE"), 0.95, Map.of()));

        EvidenceNode beh = nodes.saveAndFlush(new EvidenceNode(
                EvidenceNodeType.BEHAVIOR, EvidenceSourceType.USER_ACTIVITY, "Behavior", null, Instant.now(),
                Map.of("velocity_1h", 5), 0.85, Map.of()));

        EvidenceNode factor = nodes.saveAndFlush(new EvidenceNode(
                EvidenceNodeType.RISK_FACTOR, EvidenceSourceType.RULE_ENGINE,
                "RiskFactor", riskFactor.getId(), riskFactor.getCreatedAt(),
                Map.of("type", "HIGH_VELOCITY"), 0.9, Map.of()));

        EvidenceNode decision = nodes.saveAndFlush(new EvidenceNode(
                EvidenceNodeType.DECISION, EvidenceSourceType.POLICY_ENGINE,
                "DecisionRecord", null, Instant.now(),
                Map.of("final", "BLOCK"), 1.0, Map.of()));

        edges.saveAndFlush(new EvidenceEdge(txn, dev, EvidenceRelationshipType.USED_DEVICE, Map.of()));
        edges.saveAndFlush(new EvidenceEdge(dev, beh, EvidenceRelationshipType.GENERATED_FEATURE, Map.of()));
        edges.saveAndFlush(new EvidenceEdge(beh, factor, EvidenceRelationshipType.CONTRIBUTES_TO, Map.of()));
        edges.saveAndFlush(new EvidenceEdge(factor, decision, EvidenceRelationshipType.CONTRIBUTES_TO, Map.of()));

        // Walk the chain: txn -> dev -> beh -> factor -> decision
        UUID current = txn.getId();
        for (int i = 0; i < 4; i++) {
            List<EvidenceEdge> out = edges.findBySourceNodeId(current);
            assertThat(out).isNotEmpty();
            current = out.get(0).getTargetNode().getId();
        }
        assertThat(current).isEqualTo(decision.getId());
    }
}