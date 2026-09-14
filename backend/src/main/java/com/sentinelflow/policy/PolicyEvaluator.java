package com.sentinelflow.policy;

import com.sentinelflow.decision.DecisionPolicy;
import com.sentinelflow.decision.DecisionPolicyRepository;
import com.sentinelflow.decision.PolicyStatus;
import com.sentinelflow.risk.RiskScore;
import com.sentinelflow.rule.RuleEngine;
import com.sentinelflow.shared.dto.PolicyEvaluationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PolicyEvaluator {

    private static final Logger log = LoggerFactory.getLogger(PolicyEvaluator.class);

    private final DecisionPolicyRepository policyRepository;
    private final RuleEngine ruleEngine;

    public PolicyEvaluator(DecisionPolicyRepository policyRepository, RuleEngine ruleEngine) {
        this.policyRepository = policyRepository;
        this.ruleEngine = ruleEngine;
    }

    @Transactional(readOnly = true)
    public PolicyEvaluationResult evaluate(RiskScore riskScore, RuleEngine.RuleEvaluationResult ruleResult) {
        DecisionPolicy policy = getActivePolicy();
        validatePolicy(policy);

        Map<String, Object> config = policy.getConfiguration();
        double reviewThreshold = getDouble(config, "review_threshold", 0.5);
        double blockThreshold = getDouble(config, "block_threshold", 0.85);

        double riskScoreValue = riskScore.getRiskScore();
        String decision;
        String reason;
        List<String> triggeredRuleIds = ruleResult.triggeredRules().stream()
                .map(r -> r.ruleId() + ":" + r.ruleVersion())
                .toList();

        if (riskScoreValue < reviewThreshold) {
            decision = "ALLOW";
            reason = String.format("Risk score %.2f is below review threshold %.2f", riskScoreValue, reviewThreshold);
        } else if (riskScoreValue < blockThreshold) {
            decision = "REVIEW";
            reason = String.format("Risk score %.2f falls within review threshold range [%.2f, %.2f)",
                    riskScoreValue, reviewThreshold, blockThreshold);
        } else {
            decision = "BLOCK";
            reason = String.format("Risk score %.2f meets or exceeds block threshold %.2f", riskScoreValue, blockThreshold);
        }

        log.info("Policy evaluation: policy={}, version={}, risk_score={}, decision={}, triggered_rules={}",
                policy.getPolicyName(), policy.getVersion(), riskScoreValue, decision, triggeredRuleIds.size());

        return new PolicyEvaluationResult(
                policy.getPolicyName(),
                policy.getVersion(),
                Instant.now(),
                decision,
                reason,
                config,
                triggeredRuleIds
        );
    }

    @Transactional(readOnly = true)
    public DecisionPolicy getActivePolicy() {
        return policyRepository.findByPolicyNameAndStatus("fraud-policy", PolicyStatus.ACTIVE)
                .orElseThrow(() -> new PolicyException("No active fraud-policy found"));
    }

    private void validatePolicy(DecisionPolicy policy) {
        Map<String, Object> config = policy.getConfiguration();
        if (config == null) {
            throw new PolicyException("Policy configuration is null");
        }

        Double reviewThreshold = getDouble(config, "review_threshold", null);
        Double blockThreshold = getDouble(config, "block_threshold", null);

        if (reviewThreshold == null || blockThreshold == null) {
            throw new PolicyException("Policy missing required thresholds");
        }

        if (reviewThreshold < 0 || reviewThreshold > 1) {
            throw new PolicyException("Invalid review_threshold: " + reviewThreshold);
        }
        if (blockThreshold < 0 || blockThreshold > 1) {
            throw new PolicyException("Invalid block_threshold: " + blockThreshold);
        }
        if (reviewThreshold >= blockThreshold) {
            throw new PolicyException("review_threshold must be less than block_threshold");
        }
    }

    private double getDouble(Map<String, Object> map, String key, Double defaultValue) {
        Object v = map.get(key);
        if (v == null) return defaultValue != null ? defaultValue : 0.0;
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(v.toString());
    }

    public static class PolicyException extends RuntimeException {
        public PolicyException(String message) {
            super(message);
        }
    }
}