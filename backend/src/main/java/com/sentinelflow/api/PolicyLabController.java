package com.sentinelflow.api;

import com.sentinelflow.analytics.dto.AddInvestigationEventRequest;
import com.sentinelflow.analytics.dto.BatchPolicySimulationRequest;
import com.sentinelflow.analytics.dto.BatchPolicySimulationResponse;
import com.sentinelflow.analytics.dto.PolicySimulationRequest;
import com.sentinelflow.analytics.dto.PolicySimulationResponse;
import com.sentinelflow.analytics.policy.PolicyLabService;
import com.sentinelflow.security.AuthPrincipal;
import com.sentinelflow.security.AuditEventService;
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
@RequestMapping("/api/policy-lab")
public class PolicyLabController {

    private final PolicyLabService policyLabService;
    private final AuditEventService audit;

    public PolicyLabController(PolicyLabService policyLabService, AuditEventService audit) {
        this.policyLabService = policyLabService;
        this.audit = audit;
    }

    @PostMapping("/simulate")
    public PolicySimulationResponse simulate(
            @RequestBody PolicySimulationRequest request,
            @AuthenticationPrincipal AuthPrincipal actor) {
        // Override client-supplied requestedBy with authenticated identity
        // to prevent identity spoofing.
        var secureRequest = new PolicySimulationRequest(
                request.transactionReference(), request.policyName(), request.policyVersion(),
                request.reviewThreshold(), request.blockThreshold(),
                actor == null ? request.requestedBy() : actor.username(),
                request.investigationId());
        PolicySimulationResponse response = policyLabService.simulate(secureRequest);
        audit.record("POLICY_SIMULATION", "PolicySimulation", response.simulationId(), actor,
                Map.of("transactionReference", request.transactionReference()));
        return response;
    }

    @PostMapping("/simulate-batch")
    public BatchPolicySimulationResponse simulateBatch(
            @RequestBody BatchPolicySimulationRequest request,
            @AuthenticationPrincipal AuthPrincipal actor) {
        // Override requestedBy in the batch request (pass-through to simulate calls)
        BatchPolicySimulationResponse response = policyLabService.simulateBatch(request);
        audit.record("POLICY_SIMULATION_BATCH", "PolicySimulation", null, actor,
                Map.of("count", response.transactionsSimulated()));
        return response;
    }

    @GetMapping("/transactions/{transactionReference}/simulations")
    public java.util.List<PolicySimulationResponse> listSimulations(
            @PathVariable String transactionReference,
            @AuthenticationPrincipal AuthPrincipal actor) {
        return policyLabService.listSimulations(transactionReference);
    }
}
