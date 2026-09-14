package com.sentinelflow.risk;

import com.sentinelflow.transaction.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "risk_scores")
public class RiskScore {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_version_id", nullable = false)
    private ModelVersion modelVersion;

    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    @Column(name = "prediction", length = 16)
    private String prediction;

    @Column(name = "inference_timestamp")
    private Instant inferenceTimestamp;

    @Column(name = "inference_latency_ms")
    private Integer inferenceLatencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RiskScore() {
    }

    public RiskScore(Transaction transaction, ModelVersion modelVersion, Double riskScore,
                     String prediction, Instant inferenceTimestamp, Integer inferenceLatencyMs) {
        this.transaction = transaction;
        this.modelVersion = modelVersion;
        this.riskScore = riskScore;
        this.prediction = prediction;
        this.inferenceTimestamp = inferenceTimestamp;
        this.createdAt = Instant.now();
        this.inferenceLatencyMs = inferenceLatencyMs;
    }

    public UUID getId() {
        return id;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public ModelVersion getModelVersion() {
        return modelVersion;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public String getPrediction() {
        return prediction;
    }

    public Instant getInferenceTimestamp() {
        return inferenceTimestamp;
    }

    public Integer getInferenceLatencyMs() {
        return inferenceLatencyMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
