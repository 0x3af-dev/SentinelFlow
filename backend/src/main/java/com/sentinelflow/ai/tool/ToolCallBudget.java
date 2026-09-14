package com.sentinelflow.ai.tool;

import com.sentinelflow.ai.config.AiProperties;
import com.sentinelflow.ai.exception.ToolBudgetExceededException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Per-request budget for controlled tool execution. The model may explore a
 * bounded number of times: every evidence retrieval is scoped to this budget,
 * so a single investigation can never exhaustively page the database. The
 * counter is thread-local because a ChatClient round trip executes tools on the
 * calling thread; the orchestration service opens and closes a budget around
 * each model call.
 */
@Component
public class ToolCallBudget {

    private final AiProperties properties;

    private final ThreadLocal<Counter> current = new ThreadLocal<>();

    public ToolCallBudget(AiProperties properties) {
        this.properties = properties;
    }

    /** Opens a fresh per-request budget. */
    public void begin() {
        current.set(new Counter());
    }

    /**
     * Registers one tool execution. Throws once the total or per-tool limit is
     * exceeded so the conversation is terminated instead of exploring further.
     */
    public void consume(String toolName) {
        Counter counter = current.get();
        if (counter == null) {
            // Tool invoked outside an active budget (should never happen in
            // production flows); fail closed.
            throw new ToolBudgetExceededException("No active tool budget for request");
        }
        counter.increment(toolName);
        if (counter.total > properties.getMaxToolCalls()) {
            throw new ToolBudgetExceededException(
                    "Tool budget exceeded: total tool calls > " + properties.getMaxToolCalls());
        }
        if (counter.perTool.getOrDefault(toolName, 0) > properties.getMaxSameToolCalls()) {
            throw new ToolBudgetExceededException(
                    "Tool budget exceeded: repeated calls to " + toolName + " > "
                            + properties.getMaxSameToolCalls());
        }
    }

    /** Returns the number of tool executions since {@link #begin()}. */
    public int currentTotal() {
        Counter counter = current.get();
        return counter == null ? 0 : counter.total;
    }

    /** Closes the budget; must be called in a finally block. */
    public void end() {
        current.remove();
    }

    private static final class Counter {
        private int total;
        private final Map<String, Integer> perTool = new HashMap<>();

        private void increment(String toolName) {
            total++;
            perTool.merge(toolName, 1, Integer::sum);
        }
    }
}