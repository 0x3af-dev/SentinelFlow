package com.sentinelflow.analytics.model;

import com.sentinelflow.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only record of a Counterfactual analysis.
 *
 * A counterfactual analysis re-scores a persisted feature snapshot with the
 * active model after applying bounded, validated numeric modifications. It
 * never mutates the snapshot, the risk score, or any production lineage row.
 */
@Entity
@Table(name = "counterfactual_analyses")
public class CounterfactualAnalysis extends BaseEntity {

    @Column(name = "transaction_reference", nullable = false, length = 64)
    private String transactionReference;

    @Column(name = "feature_snapshot_id", nullable = false)
    private UUID featureSnapshotId;

    @Column(name = "feature_schema_version", nullable = false, length = 32)
    private String featureSchemaVersion;

    @Column(name = "model_name", nullable = false, length = 64)
    private String modelName;

    @Column(name = "model_version", nullable = false, length = 32)
    private String modelVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "modifications", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> modifications;

    @Column(name = "original_risk_score", nullable = false)
    private Double originalRiskScore;

    @Column(name = "hypothetical_risk_score", nullable = false)
    private Double hypotheticalRiskScore;

    @Column(name = "score_delta", nullable = false)
    private Double scoreDelta;

    @Column(name = "original_decision", nullable = false, length = 16)
    private String originalDecision;

    @Column(name = "hypothetical_decision", nullable = false, length = 16)
    private String hypotheticalDecision;

    @Column(name = "decision_changed", nullable = false)
    private Boolean decisionChanged;

    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    protected CounterfactualAnalysis() {
    }

    public CounterfactualAnalysis(String transactionReference, UUID featureSnapshotId,
                                  String featureSchemaVersion, String modelName, String modelVersion,
                                  List<Map<String, Object>> modifications, Double originalRiskScore,
                                  Double hypotheticalRiskScore, Double scoreDelta,
                                  String originalDecision, String hypotheticalDecision,
                                  Boolean decisionChanged, String requestedBy) {
        this.transactionReference = transactionReference;
        this.featureSnapshotId = featureSnapshotId;
        this.featureSchemaVersion = featureSchemaVersion;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.modifications = modifications;
        this.originalRiskScore = originalRiskScore;
        this.hypotheticalRiskScore = hypotheticalRiskScore;
        this.scoreDelta = scoreDelta;
        this.originalDecision = originalDecision;
        this.hypotheticalDecision = hypotheticalDecision;
        this.decisionChanged = decisionChanged;
        this.requestedBy = requestedBy;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public UUID getFeatureSnapshotId() {
        return featureSnapshotId;
    }

    public String getFeatureSchemaVersion() {
        return featureSchemaVersion;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public List<Map<String, Object>> getModifications() {
        return modifications;
    }

    public Double getOriginalRiskScore() {
        return originalRiskScore;
    }

    public Double getHypotheticalRiskScore() {
        return hypotheticalRiskScore;
    }

    public Double getScoreDelta() {
        return scoreDelta;
    }

    public String getOriginalDecision() {
        return originalDecision;
    }

    public String getHypotheticalDecision() {
        return hypotheticalDecision;
    }

    public Boolean getDecisionChanged() {
        return decisionChanged;
    }

    public String getRequestedBy() {
        return requestedBy;
    }
}