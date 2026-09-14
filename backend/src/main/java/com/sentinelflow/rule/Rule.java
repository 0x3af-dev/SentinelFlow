package com.sentinelflow.rule;

import com.sentinelflow.shared.dto.EnrichmentContext;
import com.sentinelflow.shared.dto.FeatureSet;

public interface Rule {
    String getRuleId();
    String getVersion();
    RuleResult evaluate(EnrichmentContext context, FeatureSet features);
}