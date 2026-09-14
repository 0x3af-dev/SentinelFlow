package com.sentinelflow.ai.exception;

/**
 * Raised by the tool budget guard when the model requests more tool executions
 * than the per-request policy allows. Spring AI propagates tool exceptions to
 * the caller when {@code spring.ai.tools.throw-exception-on-error=true}, which
 * terminates the conversation instead of letting the model explore endlessly.
 */
public class ToolBudgetExceededException extends RuntimeException {

    public ToolBudgetExceededException(String message) {
        super(message);
    }
}