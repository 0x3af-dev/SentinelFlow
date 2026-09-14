package com.sentinelflow.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "decision_policies")
public class DecisionPolicy {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "policy_name", nullable = false)
    private String policyName;

    @Column(name = "version", nullable = false, length = 32)
    private String version;

    @Column(name = "description")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", columnDefinition = "jsonb")
    private Map<String, Object> configuration;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PolicyStatus status = PolicyStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    protected DecisionPolicy() {
    }

    public DecisionPolicy(String policyName, String version, String description,
                          Map<String, Object> configuration, PolicyStatus status) {
        this.policyName = policyName;
        this.version = version;
        this.description = description;
        this.configuration = configuration;
        this.status = status == null ? PolicyStatus.DRAFT : status;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getPolicyName() {
        return policyName;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public PolicyStatus getStatus() {
        return status;
    }

    public void setStatus(PolicyStatus status) {
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
