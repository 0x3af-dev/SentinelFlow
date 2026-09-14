package com.sentinelflow.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SentinelFlow AI investigator configuration
 * ({@code sentinelflow.ai.*}). Defaults keep the capability disabled so the
 * application boots with no API key and no LLM dependency on the transaction
 * path. All limits are hard caps enforced by the application (tool budget,
 * context builder, response validator) — documented in
 * {@code docs/ai/AI-INVESTIGATOR.md}.
 */
@ConfigurationProperties(prefix = "sentinelflow.ai")
public class AiProperties {

    private boolean enabled = false;
    private String provider = "openai-compatible";
    private String model = "gpt-4o-mini";
    private String apiKey = "";
    private String baseUrl = "https://api.openai.com/v1";
    private int timeoutSeconds = 30;
    private int maxToolCalls = 12;
    private int maxSameToolCalls = 3;
    private int maxResultRows = 50;
    private int maxEvidenceNodes = 100;
    private int maxHistoryEvents = 100;
    private int maxOutputTokens = 1500;
    private int maxObservations = 20;
    private int maxFindings = 20;
    private int maxEvidenceReferences = 100;
    private int maxRecommendedEvidence = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public int getMaxSameToolCalls() {
        return maxSameToolCalls;
    }

    public void setMaxSameToolCalls(int maxSameToolCalls) {
        this.maxSameToolCalls = maxSameToolCalls;
    }

    public int getMaxResultRows() {
        return maxResultRows;
    }

    public void setMaxResultRows(int maxResultRows) {
        this.maxResultRows = maxResultRows;
    }

    public int getMaxEvidenceNodes() {
        return maxEvidenceNodes;
    }

    public void setMaxEvidenceNodes(int maxEvidenceNodes) {
        this.maxEvidenceNodes = maxEvidenceNodes;
    }

    public int getMaxHistoryEvents() {
        return maxHistoryEvents;
    }

    public void setMaxHistoryEvents(int maxHistoryEvents) {
        this.maxHistoryEvents = maxHistoryEvents;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getMaxObservations() {
        return maxObservations;
    }

    public void setMaxObservations(int maxObservations) {
        this.maxObservations = maxObservations;
    }

    public int getMaxFindings() {
        return maxFindings;
    }

    public void setMaxFindings(int maxFindings) {
        this.maxFindings = maxFindings;
    }

    public int getMaxEvidenceReferences() {
        return maxEvidenceReferences;
    }

    public void setMaxEvidenceReferences(int maxEvidenceReferences) {
        this.maxEvidenceReferences = maxEvidenceReferences;
    }

    public int getMaxRecommendedEvidence() {
        return maxRecommendedEvidence;
    }

    public void setMaxRecommendedEvidence(int maxRecommendedEvidence) {
        this.maxRecommendedEvidence = maxRecommendedEvidence;
    }
}