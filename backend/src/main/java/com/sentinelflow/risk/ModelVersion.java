package com.sentinelflow.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "model_versions")
public class ModelVersion {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "version", nullable = false, length = 32)
    private String version;

    @Column(name = "algorithm")
    private String algorithm;

    @Column(name = "feature_schema_version", length = 32)
    private String featureSchemaVersion;

    @Column(name = "training_dataset_reference")
    private String trainingDatasetReference;

    @Column(name = "artifact_reference")
    private String artifactReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ModelStatus status = ModelStatus.CANDIDATE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    protected ModelVersion() {
    }

    public ModelVersion(String modelName, String version, String algorithm,
                        String featureSchemaVersion, String trainingDatasetReference,
                        String artifactReference, ModelStatus status) {
        this.modelName = modelName;
        this.version = version;
        this.algorithm = algorithm;
        this.featureSchemaVersion = featureSchemaVersion;
        this.trainingDatasetReference = trainingDatasetReference;
        this.artifactReference = artifactReference;
        this.status = status == null ? ModelStatus.CANDIDATE : status;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getModelName() {
        return modelName;
    }

    public String getVersion() {
        return version;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getFeatureSchemaVersion() {
        return featureSchemaVersion;
    }

    public String getTrainingDatasetReference() {
        return trainingDatasetReference;
    }

    public String getArtifactReference() {
        return artifactReference;
    }

    public ModelStatus getStatus() {
        return status;
    }

    public void setStatus(ModelStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(Instant activatedAt) {
        this.activatedAt = activatedAt;
    }

    public Instant getRetiredAt() {
        return retiredAt;
    }

    public void setRetiredAt(Instant retiredAt) {
        this.retiredAt = retiredAt;
    }
}
