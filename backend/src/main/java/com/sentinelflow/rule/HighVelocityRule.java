package com.sentinelflow.rule;

import com.sentinelflow.shared.dto.EnrichmentContext;
import com.sentinelflow.shared.dto.FeatureSet;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class HighVelocityRule implements Rule {

    private static final String RULE_ID = "RULE-003";
    private static final String VERSION = "v1";
    private static final int THRESHOLD_1H = 10;

    @Override
    public String getRuleId() {
        return RULE_ID;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public RuleResult evaluate(EnrichmentContext context, FeatureSet features) {
        Integer velocity1h = features.getInteger("transactions_last_1_hour");
        boolean triggered = velocity1h != null && velocity1h >= THRESHOLD_1H;

        if (triggered) {
            return new RuleResult(
                    RULE_ID, VERSION, true, "HIGH",
                    "High transaction velocity in last hour",
                    Map.of(
                            "transactions_last_1_hour", velocity1h,
                            "threshold", THRESHOLD_1H
                    )
            );
        }
        return RuleResult.notTriggered(this);
    }
}