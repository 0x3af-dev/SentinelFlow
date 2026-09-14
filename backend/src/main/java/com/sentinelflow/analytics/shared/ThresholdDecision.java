package com.sentinelflow.analytics.shared;

import com.sentinelflow.analytics.exception.AnalyticsValidationException;

/**
 * Pure threshold-decision semantics shared by the Policy Lab and the
 * Counterfactual Engine.
 *
 * This replicates the production PolicyEvaluator decision rule exactly
 * (score &lt; review_threshold {@literal ->} ALLOW; score &lt; block_threshold
 * {@literal ->} REVIEW; otherwise BLOCK) so that a simulated or hypothetical
 * decision is directly comparable to the real one. The production evaluator is
 * intentionally left untouched; the identical rule is verified by tests that
 * cover the spec's acceptance cases.
 */
public final class ThresholdDecision {

    public static final double REVIEW_DEFAULT = 0.5;
    public static final double BLOCK_DEFAULT = 0.85;

    private ThresholdDecision() {
    }

    /**
     * Validates proposed thresholds the same way the production policy is
     * validated: 0 &lt;= review &lt; block &lt;= 1.
     */
    public static void validate(double reviewThreshold, double blockThreshold) {
        if (Double.isNaN(reviewThreshold) || Double.isInfinite(reviewThreshold)
                || Double.isNaN(blockThreshold) || Double.isInfinite(blockThreshold)) {
            throw new AnalyticsValidationException("Thresholds must be finite numbers");
        }
        if (reviewThreshold < 0 || reviewThreshold > 1) {
            throw new AnalyticsValidationException("Invalid review_threshold: " + reviewThreshold);
        }
        if (blockThreshold < 0 || blockThreshold > 1) {
            throw new AnalyticsValidationException("Invalid block_threshold: " + blockThreshold);
        }
        if (reviewThreshold >= blockThreshold) {
            throw new AnalyticsValidationException("review_threshold must be less than block_threshold");
        }
    }

    public static String evaluate(double riskScore, double reviewThreshold, double blockThreshold) {
        if (riskScore < reviewThreshold) {
            return "ALLOW";
        }
        if (riskScore < blockThreshold) {
            return "REVIEW";
        }
        return "BLOCK";
    }

    public static String reason(double riskScore, double reviewThreshold, double blockThreshold, String decision) {
        return switch (decision) {
            case "ALLOW" -> String.format("Risk score %.2f is below review threshold %.2f",
                    riskScore, reviewThreshold);
            case "REVIEW" -> String.format("Risk score %.2f falls within review threshold range [%.2f, %.2f)",
                    riskScore, reviewThreshold, blockThreshold);
            case "BLOCK" -> String.format("Risk score %.2f meets or exceeds block threshold %.2f",
                    riskScore, blockThreshold);
            default -> "Decision not derived from thresholds";
        };
    }

    /**
     * Classifies how a simulated decision relates to the actual one according
     * to strictness. Severity order is ALLOW &lt; REVIEW &lt; BLOCK.
     */
    public static String changeType(String actualDecision, String simulatedDecision) {
        if (actualDecision.equals(simulatedDecision)) {
            return "UNCHANGED";
        }
        int actualRank = rank(actualDecision);
        int simulatedRank = rank(simulatedDecision);
        return simulatedRank < actualRank ? "MORE_PERMISSIVE" : "MORE_RESTRICTIVE";
    }

    private static int rank(String decision) {
        return switch (decision) {
            case "ALLOW" -> 0;
            case "REVIEW" -> 1;
            case "BLOCK" -> 2;
            default -> throw new AnalyticsValidationException("Unknown decision: " + decision);
        };
    }
}