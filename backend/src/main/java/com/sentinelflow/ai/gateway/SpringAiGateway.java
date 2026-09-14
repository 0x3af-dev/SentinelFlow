package com.sentinelflow.ai.gateway;

import com.sentinelflow.ai.config.AiProperties;
import com.sentinelflow.ai.dto.InvestigationExplanation;
import com.sentinelflow.ai.exception.AiProviderResponseException;
import com.sentinelflow.ai.exception.AiProviderUnavailableException;
import com.sentinelflow.ai.exception.ToolBudgetExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Spring AI-backed gateway. Runs on the requesting thread so that the tool call
 * budget (ThreadLocal) stays correct for the whole conversation. Provider
 * failures are classified and rethrown as controlled gateway exceptions; raw
 * provider exceptions never escape.
 *
 * Request wall-clock timeout relies on the provider client network timeouts;
 * the exact per-request timeout option on OpenAiChatOptions landed in later
 * Spring AI releases and is intentionally not used here (see AI-INVESTIGATOR.md,
 * "Limitations").
 */
@Component
@ConditionalOnProperty(prefix = "sentinelflow.ai", name = "enabled", havingValue = "true")
public class SpringAiGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(SpringAiGateway.class);

    private final ChatClient chatClient;
    private final AiProperties properties;

    public SpringAiGateway(ChatClient chatClient, AiProperties properties) {
        this.chatClient = chatClient;
        this.properties = properties;
    }

    @Override
    public String provider() {
        return properties.getProvider();
    }

    @Override
    public String model() {
        return properties.getModel();
    }

    @Override
    public InvestigationExplanation generate(GenerationRequest request) {
        try {
            return chatClient.prompt()
                    .system(request.systemPrompt())
                    .user(request.userQuestion())
                    .tools(request.tools())
                    .options(OpenAiChatOptions.builder()
                            .model(properties.getModel())
                            .temperature(0.0)
                            .maxTokens(properties.getMaxOutputTokens())
                            .build())
                    .call()
                    .entity(InvestigationExplanation.class);
        } catch (ToolBudgetExceededException e) {
            throw new AiProviderResponseException("AI investigation exceeded the tool call budget", e);
        } catch (RuntimeException e) {
            if (isStructuredOutputFailure(e) || isToolLimitFailure(e)) {
                throw new AiProviderResponseException("AI provider returned an invalid structured response", e);
            }
            log.warn("AI provider call failed (provider={}, model={})", provider(), model(), e);
            throw new AiProviderUnavailableException(
                    "AI provider unavailable (provider=" + provider() + ", model=" + model() + ")", e);
        }
    }

    private boolean isStructuredOutputFailure(RuntimeException e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            String name = c.getClass().getName();
            if (name.startsWith("com.fasterxml.jackson.core")
                    || name.contains("JsonProcessingException")
                    || name.contains("InvalidJsonParserException")
                    || name.contains("MessageParsingException")
                    || name.contains("ResponseParsingException")) {
                return true;
            }
            String message = c.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("cannot deserialize")
                        || lower.contains("json parse")
                        || lower.contains("failed to convert")
                        || lower.contains("structured output")
                        || lower.contains("retry:true")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isToolLimitFailure(RuntimeException e) {
        return e.getMessage() != null
                && (e.getMessage().toLowerCase().contains("maximum tool call")
                || e.getMessage().toLowerCase().contains("maximum tool calls")
                || e.getMessage().toLowerCase().contains("tool resolution")
                || e.getMessage().toLowerCase().contains("tool call is not registered"));
    }
}