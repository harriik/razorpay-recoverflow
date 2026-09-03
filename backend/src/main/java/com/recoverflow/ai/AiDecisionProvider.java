package com.recoverflow.ai;

import com.recoverflow.likelihood.ObservableContext;

/**
 * Small provider abstraction for AI decisions.
 * Both SYNTHETIC_AI_PROXY and REAL_LLM must produce the same AiAssessment schema.
 * Input is ONLY ObservableContext – never HiddenTruth/P_true.
 */
public interface AiDecisionProvider {

    AiAssessment assess(ObservableContext context);

    String providerName();

    String modelId();
}
