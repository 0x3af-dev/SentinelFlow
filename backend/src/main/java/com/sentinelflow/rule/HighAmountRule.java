package com.sentinelflow.rule;

import com.sentinelflow.shared.dto.EnrichmentContext;
import com.sentinelflow.shared.dto.FeatureSet;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class HighAmountRule implements Rule {

    private static final String RULE_ID = "RULE-001";
    private static final String VERSION = "v1";
    private static final BigDecimal THRESHOLD = new BigDecimal("50000");

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
        BigDecimal amount = context.transaction().getAmount();
        boolean triggered = amount.compareTo(THRESHOLD) >= 0;

        if (triggered) {
            return new RuleResult(
                    RULE_ID, VERSION, true, "HIGH",
                    "Transaction amount exceeds high-value threshold",
                    Map.of(
                            "transaction_amount", amount,
                            "threshold", THRESHOLD
                    )
            );
        }
        return RuleResult.notTriggered(this);
    }
}