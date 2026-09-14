package com.sentinelflow.api;

import com.sentinelflow.analytics.dto.BatchPolicySimulationRequest;
import com.sentinelflow.analytics.dto.BatchPolicySimulationResponse;
import com.sentinelflow.analytics.dto.PolicySimulationRequest;
import com.sentinelflow.analytics.dto.PolicySimulationResponse;
import com.sentinelflow.analytics.policy.PolicyLabService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Policy Lab: evaluates proposed policy thresholds over persisted risk scores.
 * It never alters the production policy and never invokes the model.
 */
@RestController
@RequestMapping("/api/policy-lab")
public class PolicyLabController {

    private final PolicyLabService policyLabService;

    public PolicyLabController(PolicyLabService policyLabService) {
        this.policyLabService = policyLabService;
    }

    @PostMapping("/simulate")
    public ResponseEntity<PolicySimulationResponse> simulate(
            @RequestBody PolicySimulationRequest request) {
        return ResponseEntity.ok(policyLabService.simulate(request));
    }

    @PostMapping("/simulate-batch")
    public ResponseEntity<BatchPolicySimulationResponse> simulateBatch(
            @RequestBody BatchPolicySimulationRequest request) {
        return ResponseEntity.ok(policyLabService.simulateBatch(request));
    }

    @GetMapping("/transactions/{transactionReference}/simulations")
    public ResponseEntity<List<PolicySimulationResponse>> listSimulations(
            @PathVariable String transactionReference) {
        return ResponseEntity.ok(policyLabService.listSimulations(transactionReference));
    }
}