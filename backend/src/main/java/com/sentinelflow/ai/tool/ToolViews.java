package com.sentinelflow.ai.tool;

import com.sentinelflow.analytics.dto.DecisionReplayResponse;
import com.sentinelflow.analytics.dto.EvidenceGraphDto;
import com.sentinelflow.analytics.dto.InvestigationSummary;
import com.sentinelflow.analytics.dto.TimelineEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Output DTOs for the read-only behavioral context tool. Data is minimized:
 * users are identified by external reference only (no display name, no contact
 * information, no credentials). Every entry carries a provenance token — the
 * persisted evidence node id that backs it, where one exists.
 */
public final class ToolViews {

    private ToolViews() {
    }

    /**
     * A structured error instead of an exception, so the model can reason about
     * the failure and abstain rather than hallucinate around it.
     */
    public record ToolError(String code, String message) {
    }

    /** Tool outputs are (result, error) pairs; exactly one is populated. */
    public record InvestigationContextResult(InvestigationToolContext context, ToolError error) {
    }

    public record DecisionResult(DecisionReplayResponse replay, ToolError error) {
    }

    public record EvidenceResult(EvidenceGraphDto graph, ToolError error) {
    }

    public record BehavioralResult(BehavioralContextView context, ToolError error) {
    }

    public record InvestigationToolContext(
            InvestigationSummary summary,
            List<TimelineEntry> timeline
    ) {
    }

    public record BehavioralContextView(
            String userExternalReference,
            Long accountAgeDays,
            List<RecentTransactionView> recentTransactions,
            List<DeviceHistoryView> devices,
            List<LocationHistoryView> locations
    ) {
    }

    public record RecentTransactionView(
            String transactionReference,
            Instant transactionTimestamp,
            BigDecimal amount,
            String currency,
            String merchantName,
            String merchantCategory,
            String channel,
            String recordedDecision,
            Double recordedRiskScore,
            String evidenceNodeId
    ) {
    }

    public record DeviceHistoryView(
            String deviceReference,
            String deviceType,
            String platform,
            Instant firstSeenAt,
            Instant lastSeenAt,
            long transactionCount,
            List<String> knownUsers,
            String evidenceNodeId
    ) {
    }

    public record LocationHistoryView(
            String country,
            String region,
            String city,
            Instant firstSeenAt,
            Instant lastSeenAt,
            long transactionCount,
            String evidenceNodeId
    ) {
    }
}