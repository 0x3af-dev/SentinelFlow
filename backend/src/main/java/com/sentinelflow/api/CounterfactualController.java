package com.sentinelflow.api;

import com.sentinelflow.analytics.counterfactual.CounterfactualFeatureRegistry;
import com.sentinelflow.analytics.counterfactual.CounterfactualService;
import com.sentinelflow.analytics.dto.CounterfactualRequest;
import com.sentinelflow.analytics.dto.CounterfactualResponse;
import com.sentinelflow.security.AuthPrincipal;
import com.sentinelflow.security.AuditEventService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/counterfactuals")
public class CounterfactualController {

    private final CounterfactualService counterfactualService;
    private final CounterfactualFeatureRegistry featureRegistry;
    private final AuditEventService audit;

    public CounterfactualController(CounterfactualService counterfactualService,
                                     CounterfactualFeatureRegistry featureRegistry,
                                     AuditEventService audit) {
        this.counterfactualService = counterfactualService;
        this.featureRegistry = featureRegistry;
        this.audit = audit;
    }

    @GetMapping("/features")
    public List<CounterfactualFeatureRegistry.FeatureSpec> features() {
        return featureRegistry.list();
    }

    @PostMapping
    public CounterfactualResponse analyze(
            @RequestBody CounterfactualRequest request,
            @AuthenticationPrincipal AuthPrincipal actor) {
        // Override client-supplied requestedBy with authenticated identity
        var secureRequest = new CounterfactualRequest(
                request.transactionReference(), request.modifications(),
                actor == null ? request.requestedBy() : actor.username(),
                request.investigationId());
        CounterfactualResponse response = counterfactualService.analyze(secureRequest);
        audit.record("COUNTERFACTUAL_ANALYSIS", "CounterfactualAnalysis",
                response.analysisId(), actor,
                Map.of("transactionReference", request.transactionReference(),
                        "decisionChanged", response.decisionChanged()));
        return response;
    }

    @GetMapping("/transactions/{transactionReference}")
    public List<CounterfactualResponse> listForTransaction(
            @PathVariable String transactionReference) {
        return counterfactualService.listCounterfactuals(transactionReference);
    }
}
