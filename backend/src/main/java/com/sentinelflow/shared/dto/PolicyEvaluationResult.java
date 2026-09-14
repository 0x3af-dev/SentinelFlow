package com.sentinelflow.shared.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PolicyEvaluationResult(
    String policyName,
    String policyVersion,
    Instant evaluatedAt,
    String decision,
    String reason,
    Map<String, Object> configuration,
    List<String> triggeredRuleIds
) {
    public static PolicyEvaluationResult allow(String policyName, String policyVersion) {
        return new PolicyEvaluationResult(
            policyName, policyVersion, Instant.now(), "ALLOW",
            "Risk below review threshold", Map.of(), List.of()
        );
    }

    public static PolicyEvaluationResult review(String policyName, String policyVersion) {
        return new PolicyEvaluationResult(
            policyName, policyVersion, Instant.now(), "REVIEW",
            "Risk within review threshold range", Map.of(), List.of()
        );
    }

    public static PolicyEvaluationResult block(String policyName, String policyVersion) {
        return new PolicyEvaluationResult(
            policyName, policyVersion, Instant.now(), "BLOCK",
            "Risk at or above block threshold", Map.of(), List.of()
        );
    }
}