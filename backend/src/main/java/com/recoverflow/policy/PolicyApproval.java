package com.recoverflow.policy;

import com.recoverflow.recovery.RecoveryActionType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Auditable snapshot of a policy approval at the time it was granted.
 * Retained for execution-time revalidation.
 *
 * Execution layer must re-read latest state and revalidate; a stale approval
 * is invalidated if current policy no longer permits the approved action.
 */
public record PolicyApproval(
        UUID policyDecisionId,
        String policyVersion,
        RecoveryActionType approvedAction,
        Instant approvedAt,
        Map<String, Object> thresholdSnapshot,
        // Snapshot of hard-constraint inputs at approval time
        BigDecimalSnapshot amountSnapshot,
        int attemptCountSnapshot,
        int elapsedHoursSnapshot,
        boolean optedOutSnapshot,
        boolean linkAlreadySentSnapshot,
        String gatewayCodeSnapshot
) {
    public record BigDecimalSnapshot(String value) {
        public java.math.BigDecimal toBigDecimal() { return new java.math.BigDecimal(value); }
    }

    public static PolicyApproval from(PolicyDecision decision, PolicyContext ctx) {
        return new PolicyApproval(
                UUID.randomUUID(),
                decision.policyVersion(),
                decision.action(),
                Instant.now(),
                decision.thresholdSnapshot(),
                new BigDecimalSnapshot(ctx.amount().toPlainString()),
                ctx.attemptCount(),
                ctx.elapsedHours(),
                ctx.optedOut(),
                ctx.linkAlreadySent(),
                ctx.gatewayCode()
        );
    }
}
