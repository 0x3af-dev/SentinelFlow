package com.sentinelflow.rule;

import com.sentinelflow.shared.dto.EnrichmentContext;
import com.sentinelflow.shared.dto.FeatureSet;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NewLocationRule implements Rule {

    private static final String RULE_ID = "RULE-004";
    private static final String VERSION = "v1";

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
        Integer isNewLocation = features.getInteger("is_new_location");
        if (isNewLocation == null) {
            Object v = features.features().get("is_new_location");
            if (v instanceof Boolean b) isNewLocation = b ? 1 : 0;
        }
        boolean triggered = isNewLocation != null && isNewLocation == 1;

        if (triggered) {
            return new RuleResult(
                    RULE_ID, VERSION, true, "HIGH",
                    "Transaction from location not previously associated with user",
                    Map.of("is_new_location", true)
            );
        }
        return RuleResult.notTriggered(this);
    }
}