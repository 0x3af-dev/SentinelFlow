package com.sentinelflow.analytics.model;

import com.sentinelflow.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Append-only record of a Policy Lab simulation.
 *
 * A simulation evaluates the risk score of an already-produced decision against
 * proposed thresholds. It never modifies the production policy, the risk score,
 * or the decision record it references.
 */
@Entity
@Table(name = "policy_simulations")
public class PolicySimulation extends BaseEntity {

    @Column(name = "transaction_reference", nullable = false, length = 64)
    private String transactionReference;

    @Column(name = "base_decision_id")
    private UUID baseDecisionId;

    @Column(name = "policy_name", nullable = false, length = 64)
    private String policyName;

    @Column(name = "policy_version", nullable = false, length = 32)
    private String policyVersion;

    @Column(name = "review_threshold", nullable = false)
    private Double reviewThreshold;

    @Column(name = "block_threshold", nullable = false)
    private Double blockThreshold;

    @Column(name = "base_risk_score", nullable = false)
    private Double baseRiskScore;

    @Column(name = "actual_decision", nullable = false, length = 16)
    private String actualDecision;

    @Column(name = "simulated_decision", nullable = false, length = 16)
    private String simulatedDecision;

    @Column(name = "decision_changed", nullable = false)
    private Boolean decisionChanged;

    @Column(name = "change_type", nullable = false, length = 24)
    private String changeType;

    @Column(name = "explanation", length = 512)
    private String explanation;

    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    protected PolicySimulation() {
    }

    public PolicySimulation(String transactionReference, UUID baseDecisionId,
                            String policyName, String policyVersion,
                            Double reviewThreshold, Double blockThreshold,
                            Double baseRiskScore, String actualDecision,
                            String simulatedDecision, Boolean decisionChanged,
                            String changeType, String explanation, String requestedBy) {
        this.transactionReference = transactionReference;
        this.baseDecisionId = baseDecisionId;
        this.policyName = policyName;
        this.policyVersion = policyVersion;
        this.reviewThreshold = reviewThreshold;
        this.blockThreshold = blockThreshold;
        this.baseRiskScore = baseRiskScore;
        this.actualDecision = actualDecision;
        this.simulatedDecision = simulatedDecision;
        this.decisionChanged = decisionChanged;
        this.changeType = changeType;
        this.explanation = explanation;
        this.requestedBy = requestedBy;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public UUID getBaseDecisionId() {
        return baseDecisionId;
    }

    public String getPolicyName() {
        return policyName;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public Double getReviewThreshold() {
        return reviewThreshold;
    }

    public Double getBlockThreshold() {
        return blockThreshold;
    }

    public Double getBaseRiskScore() {
        return baseRiskScore;
    }

    public String getActualDecision() {
        return actualDecision;
    }

    public String getSimulatedDecision() {
        return simulatedDecision;
    }

    public Boolean getDecisionChanged() {
        return decisionChanged;
    }

    public String getChangeType() {
        return changeType;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getRequestedBy() {
        return requestedBy;
    }
}