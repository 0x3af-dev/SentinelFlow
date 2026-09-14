package com.sentinelflow.investigation;

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
@Table(name = "investigations")
public class Investigation {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "investigation_reference", nullable = false, unique = true, length = 64)
    private String investigationReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InvestigationStatus status = InvestigationStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 16)
    private InvestigationPriority priority = InvestigationPriority.MEDIUM;

    @Column(name = "assigned_to", length = 128)
    private String assignedTo;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", length = 32)
    private InvestigationResolution resolution;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    protected Investigation() {
    }

    public Investigation(String investigationReference, Transaction transaction,
                         InvestigationStatus status, InvestigationPriority priority,
                         String assignedTo, Instant openedAt) {
        this.investigationReference = investigationReference;
        this.transaction = transaction;
        this.status = status == null ? InvestigationStatus.OPEN : status;
        this.priority = priority == null ? InvestigationPriority.MEDIUM : priority;
        this.assignedTo = assignedTo;
        this.openedAt = openedAt == null ? Instant.now() : openedAt;
        this.updatedAt = this.openedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getInvestigationReference() {
        return investigationReference;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public InvestigationStatus getStatus() {
        return status;
    }

    public void setStatus(InvestigationStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public InvestigationPriority getPriority() {
        return priority;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public InvestigationResolution getResolution() {
        return resolution;
    }

    public void setResolution(InvestigationResolution resolution) {
        this.resolution = resolution;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }
}