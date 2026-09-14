package com.sentinelflow.api;

import com.sentinelflow.ai.dto.InvestigationExplanation;
import com.sentinelflow.ai.dto.InvestigationExplanationRequest;
import com.sentinelflow.ai.model.AiInvestigationRun;
import com.sentinelflow.ai.service.AiInvestigationService;
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
 * AI investigation API. Generating an explanation invokes an external LLM with
 * a controlled, read-only tool surface; it is deliberately a POST because the
 * operation performs side-effecting work (persisting the audit run and using an
 * external quota). Responses are only produced after validation against the
 * persisted decision and the evidence universe. Errors are surfaced through the
 * stable AnalyticsException codes (AI_UNAVAILABLE 503, AI_RESPONSE_INVALID
 * 502).
 */
@RestController
@RequestMapping("/api/investigations")
public class AiInvestigationController {

    private final AiInvestigationService aiInvestigationService;

    public AiInvestigationController(AiInvestigationService aiInvestigationService) {
        this.aiInvestigationService = aiInvestigationService;
    }

    @PostMapping("/{investigationId}/explanations")
    public ResponseEntity<InvestigationExplanation> explain(
            @PathVariable UUID investigationId,
            @RequestBody InvestigationExplanationRequest request) {
        return ResponseEntity.ok(aiInvestigationService.explain(investigationId, request));
    }

    @GetMapping("/{investigationId}/explanations/runs")
    public ResponseEntity<List<AiInvestigationRun>> runs(
            @PathVariable UUID investigationId) {
        return ResponseEntity.ok(aiInvestigationService.runsFor(investigationId));
    }
}