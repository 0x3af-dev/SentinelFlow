package com.sentinelflow.analytics.disagreement;

import static org.assertj.core.api.Assertions.assertThat;

import com.sentinelflow.analytics.dto.DecisionReplayResponse.RuleInfo;
import com.sentinelflow.analytics.dto.DisagreementInfo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DisagreementAnalysisTest {

    private static final RuleInfo HIGH_RULE =
            new RuleInfo("r1", "1.0", "HIGH", "high severity", Map.of("x", 1));
    private static final RuleInfo MEDIUM_RULE =
            new RuleInfo("r2", "1.0", "MEDIUM", "medium severity", Map.of("x", 2));

    @Test
    void highModelHighRuleClassified() {
        DisagreementInfo info = DisagreementInfo.of("HIGH", 0.91, List.of(MEDIUM_RULE, HIGH_RULE));
        assertThat(info.category()).isEqualTo("ML_HIGH_RULE_HIGH");
        assertThat(info.modelLevel()).isEqualTo("HIGH");
        assertThat(info.ruleLevel()).isEqualTo("HIGH");
    }

    @Test
    void highModelLowRuleClassified() {
        DisagreementInfo info = DisagreementInfo.of("HIGH", 0.91, List.of(MEDIUM_RULE));
        assertThat(info.category()).isEqualTo("ML_HIGH_RULE_LOW");
        assertThat(info.ruleLevel()).isEqualTo("LOW");
    }

    @Test
    void lowModelHighRuleClassified() {
        DisagreementInfo info = DisagreementInfo.of("LOW", 0.2, List.of(HIGH_RULE));
        assertThat(info.category()).isEqualTo("ML_LOW_RULE_HIGH");
        assertThat(info.modelLevel()).isEqualTo("LOW");
    }

    @Test
    void lowModelLowRuleClassified() {
        DisagreementInfo info = DisagreementInfo.of("LOW", 0.2, List.of());
        assertThat(info.category()).isEqualTo("ML_LOW_RULE_LOW");
        assertThat(info.modelLevel()).isEqualTo("LOW");
        assertThat(info.ruleLevel()).isEqualTo("LOW");
    }

    @Test
    void scoreBoundaryMapsToHighEvenWhenLabelLow() {
        // Score >= 0.6 dominates an ambiguous/low prediction label.
        DisagreementInfo info = DisagreementInfo.of("LOW", 0.61, List.of());
        assertThat(info.modelLevel()).isEqualTo("HIGH");
        assertThat(info.category()).isEqualTo("ML_HIGH_RULE_LOW");
    }
}