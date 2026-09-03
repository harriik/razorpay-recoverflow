package com.recoverflow.ai;

import com.recoverflow.likelihood.ObservableContext;
import com.recoverflow.synthetic.SyntheticAiProxy;
import org.springframework.stereotype.Component;

/**
 * Synthetic provider – deterministic, observable-only, versioned.
 */
@Component
public class SyntheticAiProvider implements AiDecisionProvider {

    private final SyntheticAiProxy proxy;

    public SyntheticAiProvider(SyntheticAiProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public AiAssessment assess(ObservableContext context) {
        return proxy.assess(context);
    }

    @Override
    public String providerName() {
        return "SYNTHETIC_AI_PROXY";
    }

    @Override
    public String modelId() {
        return proxy.getVersion();
    }
}
