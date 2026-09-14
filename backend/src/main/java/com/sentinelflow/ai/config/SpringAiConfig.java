package com.sentinelflow.ai.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * Provider beans created only when sentinelflow.ai.enabled=true. With the
 * feature disabled, no OpenAI client is instantiated, so the application runs
 * entirely without an API key or network access to any model provider.
 */
@Configuration
@ConditionalOnProperty(prefix = "sentinelflow.ai", name = "enabled", havingValue = "true")
public class SpringAiConfig {

    @Bean
    public OpenAiApi sentinelFlowOpenAiApi(AiProperties properties) {
        return OpenAiApi.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .build();
    }

    @Bean
    public OpenAiChatModel sentinelFlowOpenAiChatModel(OpenAiApi sentinelFlowOpenAiApi, AiProperties properties) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.getModel())
                .temperature(0.0)
                .maxTokens(properties.getMaxOutputTokens())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(sentinelFlowOpenAiApi)
                .defaultOptions(options)
                .build();
    }

    @Bean
    public ChatClient sentinelFlowChatClient(OpenAiChatModel sentinelFlowOpenAiChatModel) {
        return ChatClient.create(sentinelFlowOpenAiChatModel);
    }
}