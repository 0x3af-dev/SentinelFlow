package com.sentinelflow.ai.gateway;

import com.sentinelflow.ai.dto.InvestigationExplanation;

/**
 * Port for the LLM provider used by the AI investigator. Implementations must
 * translate provider failures into DomainExceptions that the service layer maps
 * to controlled AnalyticsExceptions — never raw provider errors.
 */
public interface AiGateway {

    /** Provider identifier recorded on the audit trail, e.g. "openai". */
    String provider();

    /** Model identifier recorded on the audit trail, e.g. "gpt-4o-mini". */
    String model();

    /**
     * Invokes the model and maps the raw provider response onto the required
     * structured schema.
     *
     * @throws AiProviderUnavailableException provider unreachable/timed out/rate limited
     * @throws AiProviderResponseException    response could not be mapped to the schema
     */
    InvestigationExplanation generate(GenerationRequest request);

    record GenerationRequest(
            String systemPrompt,
            String userQuestion,
            Object tools
    ) {
    }
}