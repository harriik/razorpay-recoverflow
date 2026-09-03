package com.recoverflow.ai;

/**
 * Minimal LLM client abstraction for RealLlmAiProvider.
 * Implementations must not receive HiddenTruth.
 */
public interface LlmClient {
    /**
     * Call LLM with prompt built from ObservableContext only.
     * @param prompt prompt containing only observable fields
     * @return raw LLM response (expected JSON)
     * @throws LlmTimeoutException on timeout
     * @throws LlmNetworkException on network failure
     */
    String call(String prompt) throws LlmTimeoutException, LlmNetworkException;
}
