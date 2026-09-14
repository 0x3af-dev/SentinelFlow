package com.sentinelflow.api;

import com.sentinelflow.analytics.dto.AddInvestigationEventRequest;
import com.sentinelflow.analytics.dto.CreateInvestigationRequest;
import com.sentinelflow.analytics.dto.DecisionReplayResponse;
import com.sentinelflow.analytics.dto.EvidenceGraphDto;
import com.sentinelflow.analytics.dto.InvestigationMetadata;
import com.sentinelflow.analytics.dto.InvestigationSummary;
import com.sentinelflow.analytics.dto.TimelineEntry;
import com.sentinelflow.investigation.service.InvestigationApplicationService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Investigation API: creates and reads investigations (append-only events) and
 * serves the decision replay, evidence subgraph, and consolidated summary for
 * an investigation. Read-only with respect to production lineage.
 */
@RestController
@RequestMapping("/api/investigations")
public class InvestigationController {

    private final InvestigationApplicationService investigations;

    public InvestigationController(InvestigationApplicationService investigations) {
        this.investigations = investigations;
    }

    @PostMapping
    public ResponseEntity<InvestigationMetadata> create(
            @RequestBody CreateInvestigationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(investigations.create(request));
    }

    @GetMapping("/{investigationId}")
    public ResponseEntity<InvestigationMetadata> get(
            @PathVariable UUID investigationId) {
        return ResponseEntity.ok(investigations.get(investigationId));
    }

    @PostMapping("/{investigationId}/events")
    public ResponseEntity<TimelineEntry> addEvent(
            @PathVariable UUID investigationId,
            @RequestBody AddInvestigationEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(investigations.addEvent(investigationId, request));
    }

    @GetMapping("/{investigationId}/timeline")
    public ResponseEntity<List<TimelineEntry>> timeline(
            @PathVariable UUID investigationId) {
        return ResponseEntity.ok(investigations.timeline(investigationId));
    }

    @GetMapping("/{investigationId}/decision-replay")
    public ResponseEntity<DecisionReplayResponse> decisionReplay(
            @PathVariable UUID investigationId) {
        return ResponseEntity.ok(investigations.decisionReplay(investigationId));
    }

    @GetMapping("/{investigationId}/evidence")
    public ResponseEntity<EvidenceGraphDto> evidence(
            @PathVariable UUID investigationId) {
        return ResponseEntity.ok(investigations.evidence(investigationId));
    }

    @GetMapping("/{investigationId}/summary")
    public ResponseEntity<InvestigationSummary> summary(
            @PathVariable UUID investigationId) {
        return ResponseEntity.ok(investigations.summary(investigationId));
    }
}