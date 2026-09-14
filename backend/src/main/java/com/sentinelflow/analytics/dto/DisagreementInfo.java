package com.sentinelflow.analytics.dto;

import com.sentinelflow.analytics.dto.DecisionReplayResponse.RuleInfo;
import java.util.List;

/**
 * Descriptive model-vs-rule disagreement classification. Strictly descriptive:
 * it reports what the model and the rule engine each indicated for an already
 * produced decision. It makes no causal claim.
 */
public record DisagreementInfo(
        String modelLevel,
        String ruleLevel,
        String category,
        String summary
) {

    private static final String HIGH = "HIGH";
    private static final String LOW = "LOW";

    /**
     * Model level is HIGH when the model either labelled the transaction HIGH
     * or scored it &gt;= 0.6 with the active model; otherwise LOW. Rule level is
     * HIGH when at least one triggered rule carried HIGH severity.
     */
    public static DisagreementInfo of(String prediction, double riskScore, List<RuleInfo> triggeredRules) {
        String modelLevel = isModelHigh(prediction, riskScore) ? HIGH : LOW;
        String ruleLevel = triggeredRules.stream()
                .anyMatch(r -> HIGH.equalsIgnoreCase(String.valueOf(r.severity()))) ? HIGH : LOW;

        String category = switch (modelLevel + "_" + ruleLevel) {
            case "HIGH_HIGH" -> "ML_HIGH_RULE_HIGH";
            case "HIGH_LOW" -> "ML_HIGH_RULE_LOW";
            case "LOW_HIGH" -> "ML_LOW_RULE_HIGH";
            default -> "ML_LOW_RULE_LOW";
        };

        String summary = switch (category) {
            case "ML_HIGH_RULE_HIGH" ->
                "The model indicated high risk and the rule engine also triggered HIGH severity rules";
            case "ML_HIGH_RULE_LOW" ->
                "The model indicated high risk while no HIGH severity rule triggered";
            case "ML_LOW_RULE_HIGH" ->
                "The model indicated low risk while HIGH severity rules triggered";
            default ->
                "The model indicated low risk and no HIGH severity rule triggered";
        };

        return new DisagreementInfo(modelLevel, ruleLevel, category, summary);
    }

    private static boolean isModelHigh(String prediction, double riskScore) {
        if (prediction != null && HIGH.equalsIgnoreCase(prediction)) {
            return true;
        }
        return riskScore >= 0.6;
    }
}