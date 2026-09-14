package com.sentinelflow.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sentinelflow.ai.config.AiProperties;
import com.sentinelflow.ai.exception.ToolBudgetExceededException;
import org.junit.jupiter.api.Test;

class ToolCallBudgetTest {

    @Test
    void respectsTotalLimit() {
        AiProperties properties = new AiProperties();
        properties.setMaxToolCalls(3);
        ToolCallBudget budget = new ToolCallBudget(properties);

        budget.begin();
        budget.consume("a");
        budget.consume("b");
        budget.consume("c");
        assertThat(budget.currentTotal()).isEqualTo(3);
        assertThatThrownBy(() -> budget.consume("d"))
                .isInstanceOf(ToolBudgetExceededException.class);
    }

    @Test
    void respectsPerToolLimit() {
        AiProperties properties = new AiProperties();
        properties.setMaxSameToolCalls(2);
        properties.setMaxToolCalls(10);
        ToolCallBudget budget = new ToolCallBudget(properties);

        budget.begin();
        budget.consume("getEvidence");
        budget.consume("getEvidence");
        assertThatThrownBy(() -> budget.consume("getEvidence"))
                .isInstanceOf(ToolBudgetExceededException.class)
                .satisfies(e -> assertThat(((ToolBudgetExceededException) e).getMessage())
                        .contains("getEvidence"));
    }

    @Test
    void failsClosedWhenNoActiveBudget() {
        ToolCallBudget budget = new ToolCallBudget(new AiProperties());
        assertThatThrownBy(() -> budget.consume("getEvidence"))
                .isInstanceOf(ToolBudgetExceededException.class);
    }

    @Test
    void endClearsBudgetForNextRequest() {
        ToolCallBudget budget = new ToolCallBudget(new AiProperties());
        budget.begin();
        budget.consume("getEvidence");
        budget.end();

        budget.begin();
        assertThat(budget.currentTotal()).isZero();
        budget.end();
        assertThat(budget.currentTotal()).isZero();
    }
}