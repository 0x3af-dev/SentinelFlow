package com.sentinelflow.ai.model;

import com.sentinelflow.ai.dto.InvestigationRequestType;
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

/**
 * Immutable audit trail of every AI investigation run: what was asked, which
 * provider/model produced the answer, the tool budget consumed, latency, and
 * either the validated structured response or the controlled error that
 * replaced it. The full, persisted response is stored so a human can prove
 * exactly what the system returned. On failure the error code/message is
 * recorded; raw provider output is never stored.
 */
@Entity
@Table(name = "ai_investigation_runs")
public class AiInvestigationRun {

    public enum Status {
        SUCCEEDED,
        FAILED
    }

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "investigation_id", nullable = false)
    private UUID investigationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 32)
    private InvestigationRequestType requestType;

    @Column(name = "free_form_question")
    private String freeFormQuestion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "provider", length = 64)
    private String provider;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "tool_call_count", nullable = false)
    private int toolCallCount;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response", columnDefinition = "jsonb")
    private Map<String, Object> response;

    @Column(name = "error_code", length = 32)
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiInvestigationRun() {
    }

    private AiInvestigationRun(Builder builder) {
        this.investigationId = builder.investigationId;
        this.requestType = builder.requestType;
        this.freeFormQuestion = builder.freeFormQuestion;
        this.status = builder.status;
        this.provider = builder.provider;
        this.model = builder.model;
        this.toolCallCount = builder.toolCallCount;
        this.latencyMs = builder.latencyMs;
        this.correlationId = builder.correlationId;
        this.response = builder.response;
        this.errorCode = builder.errorCode;
        this.errorMessage = builder.errorMessage;
        this.createdAt = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvestigationId() {
        return investigationId;
    }

    public InvestigationRequestType getRequestType() {
        return requestType;
    }

    public String getFreeFormQuestion() {
        return freeFormQuestion;
    }

    public Status getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Map<String, Object> getResponse() {
        return response;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static final class Builder {
        private UUID investigationId;
        private InvestigationRequestType requestType;
        private String freeFormQuestion;
        private Status status;
        private String provider;
        private String model;
        private int toolCallCount;
        private Long latencyMs;
        private String correlationId;
        private Map<String, Object> response;
        private String errorCode;
        private String errorMessage;

        public Builder investigationId(UUID investigationId) {
            this.investigationId = investigationId;
            return this;
        }

        public Builder requestType(InvestigationRequestType requestType) {
            this.requestType = requestType;
            return this;
        }

        public Builder freeFormQuestion(String freeFormQuestion) {
            this.freeFormQuestion = freeFormQuestion;
            return this;
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder toolCallCount(int toolCallCount) {
            this.toolCallCount = toolCallCount;
            return this;
        }

        public Builder latencyMs(Long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder response(Map<String, Object> response) {
            this.response = response;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public AiInvestigationRun build() {
            return new AiInvestigationRun(this);
        }
    }
}