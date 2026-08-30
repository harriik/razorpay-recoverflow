package com.recoverflow.policy;

import com.recoverflow.recovery.RecoveryActionType;
import java.time.Instant;
import java.util.UUID;

/**
 * Result of execution-time revalidation of a previously stored approval.
 */
public record PolicyRevalidationResult(
        boolean valid,
        UUID policyDecisionId,
        String policyVersion,
        RecoveryActionType approvedAction,
        Instant approvedAt,
        Instant finalPolicyValidationAt,
        PolicyDecision currentDecision,
        PolicyApproval originalApproval,
        String invalidationReason
) {
    public static PolicyRevalidationResult valid(PolicyApproval approval, PolicyDecision current, Instant now) {
        return new PolicyRevalidationResult(true, approval.policyDecisionId(), approval.policyVersion(),
                approval.approvedAction(), approval.approvedAt(), now, current, approval, null);
    }

    public static PolicyRevalidationResult invalid(PolicyApproval approval, PolicyDecision current, Instant now, String reason) {
        return new PolicyRevalidationResult(false, approval.policyDecisionId(), approval.policyVersion(),
                approval.approvedAction(), approval.approvedAt(), now, current, approval, reason);
    }
}
