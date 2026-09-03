package com.recoverflow.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Environment configuration for AI provider.
 * Never commit credentials. Real LLM requires explicit configuration.
 */
@Component
public class AiProviderConfig {

    @Value("${AI_PROVIDER:SYNTHETIC_AI_PROXY}")
    private String provider;

    @Value("${AI_MODEL:synthetic-ai-v1}")
    private String modelId;

    @Value("${AI_ENDPOINT:}")
    private String endpoint;

    @Value("${AI_API_KEY:}")
    private String apiKey;

    @Value("${AI_TIMEOUT_MS:5000}")
    private int timeoutMs;

    public String getProvider() { return provider; }
    public String getModelId() { return modelId; }
    public String getEndpoint() { return endpoint; }
    public String getApiKey() { return apiKey; }
    public int getTimeoutMs() { return timeoutMs; }

    public boolean isRealLlmConfigured() {
        return "REAL_LLM".equalsIgnoreCase(provider) && modelId != null && !modelId.isBlank() && !modelId.equals("synthetic-ai-v1");
    }
}
