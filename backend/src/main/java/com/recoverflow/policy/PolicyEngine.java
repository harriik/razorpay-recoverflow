package com.recoverflow.policy;

import com.recoverflow.decision.ExpectedNetRecoveryValueEngine.RankedCandidate;
import com.recoverflow.recovery.RecoveryActionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Deterministic policy engine that enforces hard safety constraints.
 * Must never read AI fields (riskLevel, evidenceQuality, recoverability).
 * Evaluates ordered rules; first match decides outcome.
 * Consumes EV ranking but remains independent of EV formula.
 *
 * Execution-time revalidation: Phase 5 ExecutionService must NOT trust a previously stored
 * ACTION_APPROVED without re-reading latest case/merchant/customer state and revalidating
 * via PolicyRevalidator before any gateway call (handles opt-out, retry-count, window expiry,
 * threshold changes, concurrency). See PolicyRevalidator.
 *
 * High-value clarification: financial actions (RETRY_*) escalate above autoActionLimit;
 * SEND_PAYMENT_LINK remains allowed (non-financial, does not move money).
 * Business-policy authority stays here; ExecutionService independently enforces
 * ACTION_APPROVED + current policy validity + idempotency + state invariants via optimistic locking.
 */
@Component
public class PolicyEngine {

    private final PolicyConfig config;

    public PolicyEngine(PolicyConfig config) {
        this.config = config;
    }

    public String getVersion() {
        return config.getVersion();
    }

    /**
     * Evaluate a single candidate against hard rules in priority order.
     */
    public PolicyDecision evaluate(PolicyContext ctx) {
        Map<String, Object> snapshot = Map.of(
                "autoActionLimit", ctx.autoActionLimit(),
                "maxRetries", ctx.maxRetries(),
                "recoveryWindowHours", ctx.recoveryWindowHours(),
                "attemptCount", ctx.attemptCount(),
                "elapsedHours", ctx.elapsedHours(),
                "policyVersion", config.getVersion()
        );

        // 1. OPT_OUT — highest priority, no AI dependency
        if (ctx.optedOut()) {
            return new PolicyDecision(ctx.candidateAction(), PolicyDecisionType.STOP,
                    PolicyRuleId.OPT_OUT, "customer_opted_out", config.getVersion(), snapshot);
        }

        // 2. PERMANENT_FAILURE — based on observable gatewayCode, not AI failureCategory
        // CARD_EXPIRED and AUTH_FAILED are permanent for retry actions
        if (isPermanentFailure(ctx.gatewayCode()) && isRetryAction(ctx.candidateAction())) {
            return new PolicyDecision(ctx.candidateAction(), PolicyDecisionType.STOP,
                    PolicyRuleId.PERMANENT_FAILURE, "permanently_failed_retry_not_permissible", config.getVersion(), snapshot);
        }

        // 3. WINDOW_EXPIRED
        if (ctx.elapsedHours() > ctx.recoveryWindowHours()) {
            return new PolicyDecision(ctx.candidateAction(), PolicyDecisionType.STOP,
                    PolicyRuleId.WINDOW_EXPIRED, "recovery_window_expired", config.getVersion(), snapshot);
        }

        // 4. RETRY_LIMIT
        if (ctx.attemptCount() >= ctx.maxRetries()) {
            return new PolicyDecision(ctx.candidateAction(), PolicyDecisionType.STOP,
                    PolicyRuleId.RETRY_LIMIT, "retry_limit_exceeded", config.getVersion(), snapshot);
        }

        // 5. AMOUNT_THRESHOLD — hard, independent of AI riskLevel
        // Clarified high-value policy: financial auto actions (RETRY_NOW, SCHEDULE_RETRY) that move money are escalated
        // when amount > autoActionLimit, but SEND_PAYMENT_LINK remains ALLOWED because it does not itself move money
        // (it creates a link for later customer action). If policy required escalating ALL auto actions for high value,
        // this rule would check all RecoveryActionType; current design intentionally allows LINK.
        if (ctx.amount().compareTo(ctx.autoActionLimit()) > 0 && isAutomaticFinancialAction(ctx.candidateAction())) {
            return new PolicyDecision(ctx.candidateAction(), PolicyDecisionType.ESCALATE,
                    PolicyRuleId.AMOUNT_THRESHOLD, "amount_exceeds_auto_limit", config.getVersion(), snapshot);
        }

        // 6. ACTION_ELIGIBILITY — deterministic, no AI
        if (ctx.candidateAction() == RecoveryActionType.SEND_REMINDER && !ctx.linkAlreadySent()) {
            return new PolicyDecision(ctx.candidateAction(), PolicyDecisionType.BLOCKED,
                    PolicyRuleId.ACTION_ELIGIBILITY, "reminder_requires_prior_link", config.getVersion(), snapshot);
        }

        // 7. DEFAULT_ALLOW
        return new PolicyDecision(ctx.candidateAction(), PolicyDecisionType.ALLOWED,
                PolicyRuleId.DEFAULT_ALLOW, "permissible", config.getVersion(), snapshot);
    }

    /**
     * Filter ranked candidates by policy, returning highest-allowed.
     * Policy is capable of overriding AI recommendation and highest EV.
     */
    public PolicySelection selectBestAllowed(
            List<RankedCandidate> ranked,
            PolicyContext baseCtx) {

        List<PolicyDecision> decisions = new ArrayList<>();
        for (RankedCandidate rc : ranked) {
            PolicyContext ctx = new PolicyContext(
                    baseCtx.amount(), baseCtx.gatewayCode(), baseCtx.attemptCount(),
                    baseCtx.elapsedHours(), baseCtx.optedOut(), baseCtx.linkAlreadySent(),
                    rc.action(), baseCtx.autoActionLimit(), baseCtx.maxRetries(), baseCtx.recoveryWindowHours());
            PolicyDecision d = evaluate(ctx);
            decisions.add(d);
        }

        Optional<PolicyDecision> allowed = decisions.stream()
                .filter(PolicyDecision::isAllowed)
                .findFirst(); // ranked order preserved, first allowed is highest EV allowed

        if (allowed.isPresent()) {
            // Find corresponding RankedCandidate
            RankedCandidate candidate = ranked.stream()
                    .filter(rc -> rc.action() == allowed.get().action())
                    .findFirst().orElse(null);
            return new PolicySelection(candidate, decisions, allowed.get());
        }

        // No allowed candidate: check if any escalate, else stopped
        boolean hasEscalate = decisions.stream().anyMatch(d -> d.result() == PolicyDecisionType.ESCALATE);
        PolicyDecisionType overall = hasEscalate ? PolicyDecisionType.ESCALATE : PolicyDecisionType.STOP;
        return new PolicySelection(null, decisions, new PolicyDecision(
                null, overall, null, hasEscalate ? "all_permissible_blocked_escalate" : "all_blocked_stop",
                config.getVersion(), Map.of("policyVersion", config.getVersion())));
    }

    private boolean isPermanentFailure(String gatewayCode) {
        return "CARD_EXPIRED".equals(gatewayCode) || "AUTH_FAILED".equals(gatewayCode);
    }

    private boolean isRetryAction(RecoveryActionType action) {
        return action == RecoveryActionType.RETRY_NOW || action == RecoveryActionType.SCHEDULE_RETRY;
    }

    private boolean isAutomaticFinancialAction(RecoveryActionType action) {
        // Financial auto actions that move money; links/reminders are not financial execution
        return action == RecoveryActionType.RETRY_NOW || action == RecoveryActionType.SCHEDULE_RETRY;
    }

    public record PolicySelection(
            RankedCandidate selectedCandidate,
            List<PolicyDecision> allDecisions,
            PolicyDecision overallDecision
    ) {
        public boolean hasSelection() { return selectedCandidate != null; }
    }
}
