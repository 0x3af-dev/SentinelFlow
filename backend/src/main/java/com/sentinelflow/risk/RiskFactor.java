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
@Table(name = "risk_factors")
public class RiskFactor {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(name = "factor_type", nullable = false, length = 64)
    private String factorType;

    @Column(name = "description")
    private String description;

    @Column(name = "severity", length = 16)
    private String severity;

    @Column(name = "source", length = 64)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RiskFactor() {
    }

    public RiskFactor(Transaction transaction, String factorType, String description,
                      String severity, String source) {
        this.transaction = transaction;
        this.factorType = factorType;
        this.description = description;
        this.severity = severity;
        this.source = source;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public String getFactorType() {
        return factorType;
    }

    public String getDescription() {
        return description;
    }

    public String getSeverity() {
        return severity;
    }

    public String getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
