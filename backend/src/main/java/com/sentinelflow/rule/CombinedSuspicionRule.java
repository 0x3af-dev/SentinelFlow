package com.sentinelflow.rule;

import com.sentinelflow.shared.dto.EnrichmentContext;
import com.sentinelflow.shared.dto.FeatureSet;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CombinedSuspicionRule implements Rule {

    private static final String RULE_ID = "RULE-005";
    private static final String VERSION = "v1";
    private static final int MIN_INDEPENDENT_SIGNALS = 3;

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
        int signalCount = 0;
        Map<String, Object> signals = new java.util.HashMap<>();

        // Check independent risk signals
        Integer isNewDevice = features.getInteger("is_new_device");
        if (isNewDevice == null) {
            Object v = features.features().get("is_new_device");
            if (v instanceof Boolean b) isNewDevice = b ? 1 : 0;
        }
        if (isNewDevice != null && isNewDevice == 1) {
            signalCount++;
            signals.put("new_device", true);
        }
        Integer isNewLocation = features.getInteger("is_new_location");
        if (isNewLocation == null) {
            Object v2 = features.features().get("is_new_location");
            if (v2 instanceof Boolean b) isNewLocation = b ? 1 : 0;
        }
        if (isNewLocation != null && isNewLocation == 1) {
            signalCount++;
            signals.put("new_location", true);
        }
        Integer velocity1h = features.getInteger("transactions_last_1_hour");
        if (velocity1h != null && velocity1h >= 5) {
            signalCount++;
            signals.put("high_velocity_1h", velocity1h);
        }
        Double amount = features.getDouble("transaction_amount");
        if (amount != null && amount >= 10000) {
            signalCount++;
            signals.put("elevated_amount", amount);
        }
        Double accountAge = features.getDouble("user_account_age_days");
        if (accountAge != null && accountAge < 30) {
            signalCount++;
            signals.put("new_account", accountAge.intValue());
        }

        boolean triggered = signalCount >= MIN_INDEPENDENT_SIGNALS;

        if (triggered) {
            return new RuleResult(
                    RULE_ID, VERSION, true, "HIGH",
                    "Multiple independent risk signals detected",
                    Map.of("signal_count", signalCount, "signals", signals)
            );
        }
        return RuleResult.notTriggered(this);
    }
}