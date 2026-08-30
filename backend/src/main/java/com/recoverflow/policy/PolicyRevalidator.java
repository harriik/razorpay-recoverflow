package com.recoverflow.policy;

import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Execution-time policy revalidation.
 *
 * Must be invoked by Phase 5 ExecutionService BEFORE any gateway call, after re-reading latest
 * case/merchant/customer state. No trust in stored ACTION_APPROVED without fresh validation.
 *
 * Handles stale approval caused by:
 * - customer opt-out
 * - retry count changes
 * - recovery-window expiry
 * - merchant threshold/config changes
 * - concurrent execution (via optimistic locking at service layer)
 *
 * Policy authority remains in PolicyEngine; this class is a thin revalidation facade that
 * preserves audit fields (policyVersion, policyDecisionId, thresholdSnapshot, approvedAction,
 * approvedAt, finalPolicyValidationAt).
 */
@Component
public class PolicyRevalidator {

    private final PolicyEngine policyEngine;

    public PolicyRevalidator(PolicyEngine policyEngine) {
        this.policyEngine = policyEngine;
    }

    /**
     * Revalidate a previously stored approval against current context.
     * Returns valid only if:
     * - current policy still ALLOWS the originally approved action
     * - policy version has not introduced a breaking change that would now block (checked via re-evaluation)
     * - threshold snapshot mismatch is reflected in current decision (e.g., merchant limit lowered)
     */
    public PolicyRevalidationResult revalidate(PolicyApproval approval, PolicyContext currentCtx) {
        Instant now = Instant.now();
        PolicyDecision current = policyEngine.evaluate(currentCtx);

        // Approved action must still be the same and still ALLOWED
        boolean actionStillAllowed = current.result() == PolicyDecisionType.ALLOWED
                && current.action() == approval.approvedAction();

        if (actionStillAllowed) {
            // Also verify no threshold drift that would have blocked via re-evaluation already covers it
            return PolicyRevalidationResult.valid(approval, current, now);
        }

        String reason = buildInvalidationReason(approval, currentCtx, current);
        return PolicyRevalidationResult.invalid(approval, current, now, reason);
    }

    /**
     * Convenience: revalidate using fresh context built from latest case/merchant/customer reads.
     * ExecutionService is responsible for building currentCtx from DB reads.
     */
    public PolicyRevalidationResult revalidateForExecution(PolicyApproval approval, PolicyContext freshCtx) {
        return revalidate(approval, freshCtx);
    }

    private String buildInvalidationReason(PolicyApproval approval, PolicyContext current, PolicyDecision currentDecision) {
        String base = currentDecision.reason();
        if (current.optedOut() && !approval.optedOutSnapshot()) {
            return "customer_opted_out_since_approval: " + base;
        }
        if (current.attemptCount() != approval.attemptCountSnapshot()) {
            return "attempt_count_changed_" + approval.attemptCountSnapshot() + "->" + current.attemptCount() + ": " + base;
        }
        if (current.elapsedHours() != approval.elapsedHoursSnapshot()) {
            return "window_changed_" + approval.elapsedHoursSnapshot() + "->" + current.elapsedHours() + ": " + base;
        }
        Object oldLimit = approval.thresholdSnapshot().get("autoActionLimit");
        if (oldLimit != null && !oldLimit.toString().equals(current.autoActionLimit().toPlainString())) {
            return "threshold_changed_" + oldLimit + "->" + current.autoActionLimit().toPlainString() + ": " + base;
        }
        if (!current.gatewayCode().equals(approval.gatewayCodeSnapshot())) {
            return "gateway_changed_" + approval.gatewayCodeSnapshot() + "->" + current.gatewayCode() + ": " + base;
        }
        if (currentDecision.blockingRule() != null) {
            return "revalidation_failed_" + currentDecision.blockingRule().name() + ": " + base;
        }
        return "revalidation_failed: " + base;
    }
}
