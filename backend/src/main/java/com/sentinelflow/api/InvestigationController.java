package com.sentinelflow.api;

import com.sentinelflow.analytics.dto.AddInvestigationEventRequest;
import com.sentinelflow.analytics.dto.CreateInvestigationRequest;
import com.sentinelflow.analytics.dto.DecisionReplayResponse;
import com.sentinelflow.analytics.dto.EvidenceGraphDto;
import com.sentinelflow.analytics.dto.InvestigationMetadata;
import com.sentinelflow.analytics.dto.InvestigationSummary;
import com.sentinelflow.analytics.dto.TimelineEntry;
import com.sentinelflow.investigation.service.InvestigationApplicationService;
import com.sentinelflow.security.AuthPrincipal;
import com.sentinelflow.security.AuditEventService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investigations")
public class InvestigationController {

    private final InvestigationApplicationService investigations;
    private final AuditEventService audit;

    public InvestigationController(InvestigationApplicationService investigations, AuditEventService audit) {
        this.investigations = investigations;
        this.audit = audit;
    }

    @PostMapping
    public InvestigationMetadata create(
            @RequestBody CreateInvestigationRequest request,
            @AuthenticationPrincipal AuthPrincipal actor) {
        InvestigationMetadata meta = investigations.create(request);
        audit.record("INVESTIGATION_CREATED", "Investigation", meta.id(), actor,
                Map.of("transactionReference", request.transactionReference()));
        return meta;
    }

    @GetMapping
    public List<InvestigationMetadata> list(
            @RequestParam(required = false) String transactionReference) {
        return investigations.list(transactionReference);
    }

    @GetMapping("/{investigationId}")
    public InvestigationMetadata get(@PathVariable UUID investigationId) {
        return investigations.get(investigationId);
    }

    @PostMapping("/{investigationId}/events")
    public TimelineEntry addEvent(
            @PathVariable UUID investigationId,
            @RequestBody AddInvestigationEventRequest request,
            @AuthenticationPrincipal AuthPrincipal actor) {
        // Override client-supplied actorReference with authenticated identity
        // to prevent identity spoofing. Actor type remains as requested (ANALYST etc).
        AddInvestigationEventRequest secureRequest = request;
        if (actor != null) {
            secureRequest = new AddInvestigationEventRequest(
                    request.eventType(),
                    request.actorType() == null || request.actorType().isBlank()
                            ? "ANALYST" : request.actorType(),
                    actor.username(),
                    request.payload());
        }
        TimelineEntry entry = investigations.addEvent(investigationId, secureRequest);
        audit.record("INVESTIGATION_EVENT_ADDED", "InvestigationEvent", entry.eventId(), actor,
                Map.of("investigationId", investigationId.toString(),
                        "eventType", request.eventType()));
        return entry;
    }

    @GetMapping("/{investigationId}/timeline")
    public List<TimelineEntry> timeline(@PathVariable UUID investigationId) {
        return investigations.timeline(investigationId);
    }

    @GetMapping("/{investigationId}/decision-replay")
    public DecisionReplayResponse decisionReplay(@PathVariable UUID investigationId) {
        return investigations.decisionReplay(investigationId);
    }

    @GetMapping("/{investigationId}/evidence")
    public EvidenceGraphDto evidence(@PathVariable UUID investigationId) {
        return investigations.evidence(investigationId);
    }

    @GetMapping("/{investigationId}/summary")
    public InvestigationSummary summary(@PathVariable UUID investigationId) {
        return investigations.summary(investigationId);
    }
}
