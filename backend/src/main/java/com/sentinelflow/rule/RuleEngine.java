package com.sentinelflow.rule;

import com.sentinelflow.shared.dto.EnrichmentContext;
import com.sentinelflow.shared.dto.FeatureSet;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RuleEngine {

    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        this.rules = rules;
    }

    public RuleEvaluationResult evaluate(EnrichmentContext context, FeatureSet features) {
        List<RuleResult> results = rules.stream()
                .map(rule -> rule.evaluate(context, features))
                .collect(Collectors.toList());

        List<RuleResult> triggered = results.stream()
                .filter(RuleResult::triggered)
                .collect(Collectors.toList());

        return new RuleEvaluationResult(results, triggered);
    }

    public record RuleEvaluationResult(
            List<RuleResult> allResults,
            List<RuleResult> triggeredRules
    ) {
        public boolean hasTriggeredRules() {
            return !triggeredRules.isEmpty();
        }

        public int triggeredCount() {
            return triggeredRules.size();
        }
    }
}