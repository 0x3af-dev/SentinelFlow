package com.sentinelflow.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers AiProperties regardless of the feature flag. The provider beans
 * themselves are conditional (see SpringAiConfig), so the application boots
 * without any model configuration until sentinelflow.ai.enabled=true.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class SentinelFlowAiConfiguration {
}