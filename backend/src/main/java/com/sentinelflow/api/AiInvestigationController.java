package com.sentinelflow.api;

import com.sentinelflow.ai.dto.InvestigationExplanation;
import com.sentinelflow.ai.dto.InvestigationExplanationRequest;
import com.sentinelflow.ai.model.AiInvestigationRun;
import com.sentinelflow.ai.service.AiInvestigationService;
import com.sentinelflow.security.AuthPrincipal;
import com.sentinelflow.security.AuditEventService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investigations")
public class AiInvestigationController {

    private final AiInvestigationService aiInvestigationService;
    private final AuditEventService audit;

    public AiInvestigationController(AiInvestigationService aiInvestigationService, AuditEventService audit) {
        this.aiInvestigationService = aiInvestigationService;
        this.audit = audit;
    }

    @PostMapping("/{investigationId}/explanations")
    public InvestigationExplanation explain(
            @PathVariable UUID investigationId,
            @RequestBody InvestigationExplanationRequest request,
            @AuthenticationPrincipal AuthPrincipal actor) {
        String actorUsername = actor == null ? null : actor.username();
        // Audit BEFORE invoking the service so AI attempts are recorded even when
        // the AI is frozen (AiUnavailableException) or rejected.
        audit.record("AI_EXPLANATION_REQUESTED", "Investigation", investigationId, actor,
                Map.of("requestType", request.requestType().name()));
        return aiInvestigationService.explain(investigationId, request, actorUsername);
    }

    @GetMapping("/{investigationId}/explanations/runs")
    public List<AiInvestigationRun> runs(@PathVariable UUID investigationId) {
        return aiInvestigationService.runsFor(investigationId);
    }
}
