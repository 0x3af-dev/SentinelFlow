package com.sentinelflow.rule;

import com.sentinelflow.shared.dto.EnrichmentContext;
import com.sentinelflow.shared.dto.FeatureSet;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NewDeviceRule implements Rule {

    private static final String RULE_ID = "RULE-002";
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
        Integer isNewDevice = features.getInteger("is_new_device");
        if (isNewDevice == null) {
            Object v = features.features().get("is_new_device");
            if (v instanceof Boolean b) isNewDevice = b ? 1 : 0;
        }
        boolean triggered = isNewDevice != null && isNewDevice == 1;

        if (triggered) {
            return new RuleResult(
                    RULE_ID, VERSION, true, "HIGH",
                    "Transaction from device not previously associated with user",
                    Map.of("is_new_device", true)
            );
        }
        return RuleResult.notTriggered(this);
    }
}