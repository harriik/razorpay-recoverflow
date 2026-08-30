package com.recoverflow.policy;

import com.recoverflow.recovery.RecoveryActionType;
import java.util.Map;

/**
 * Result of evaluating a single candidate against ordered hard rules.
 * Contains threshold snapshot for audit and reproducibility.
 */
public record PolicyDecision(
        RecoveryActionType action,
        PolicyDecisionType result,
        PolicyRuleId blockingRule,
        String reason,
        String policyVersion,
        Map<String, Object> thresholdSnapshot
) {
    public boolean isAllowed() {
        return result == PolicyDecisionType.ALLOWED;
    }
}
