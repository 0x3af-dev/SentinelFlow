package com.sentinelflow.decision;

import com.sentinelflow.risk.RiskScore;
import com.sentinelflow.transaction.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "decision_records")
public class DecisionRecord {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "risk_score_id")
    private RiskScore riskScore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id")
    private DecisionPolicy policy;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_decision", nullable = false, length = 16)
    private FinalDecision finalDecision;

    @Column(name = "decision_timestamp", nullable = false)
    private Instant decisionTimestamp;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DecisionRecord() {
    }

    public DecisionRecord(Transaction transaction, RiskScore riskScore, DecisionPolicy policy,
                          FinalDecision finalDecision, Instant decisionTimestamp, String decisionReason) {
        this.transaction = transaction;
        this.riskScore = riskScore;
        this.policy = policy;
        this.finalDecision = finalDecision;
        this.decisionTimestamp = decisionTimestamp == null ? Instant.now() : decisionTimestamp;
        this.decisionReason = decisionReason;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public RiskScore getRiskScore() {
        return riskScore;
    }

    public DecisionPolicy getPolicy() {
        return policy;
    }

    public FinalDecision getFinalDecision() {
        return finalDecision;
    }

    public Instant getDecisionTimestamp() {
        return decisionTimestamp;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
