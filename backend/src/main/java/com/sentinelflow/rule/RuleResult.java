package com.sentinelflow.rule;

import java.util.Map;

public record RuleResult(
    String ruleId,
    String ruleVersion,
    boolean triggered,
    String severity,
    String description,
    Map<String, Object> observedValues
) {
    public static RuleResult notTriggered(Rule rule) {
        return new RuleResult(rule.getRuleId(), rule.getVersion(), false, null, null, Map.of());
    }
}