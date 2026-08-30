package com.recoverflow.policy;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.recovery.RecoveryActionType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PolicyRevalidationTest {

    private PolicyConfig config;
    private PolicyEngine engine;
    private PolicyRevalidator revalidator;

    @BeforeEach
    void setUp() {
        config = new PolicyConfig();
        engine = new PolicyEngine(config);
        revalidator = new PolicyRevalidator(engine);
    }

    private PolicyContext ctx(BigDecimal amount, String gateway, int attempts, int elapsed, boolean optedOut, boolean linkSent, RecoveryActionType action, BigDecimal limit) {
        return new PolicyContext(amount, gateway, attempts, elapsed, optedOut, linkSent, action,
                limit, config.getMaxRetries(), config.getRecoveryWindowHours());
    }

    @Test
    void approvedThenMerchantThresholdChangesBlocked() {
        // Approval at limit 10000, amount 5000 -> ALLOWED
        PolicyContext approvedCtx = ctx(new BigDecimal("5000.0000"), "BANK_TIMEOUT", 0, 1, false, false, RecoveryActionType.RETRY_NOW, new BigDecimal("10000.0000"));
        PolicyDecision approvedDecision = engine.evaluate(approvedCtx);
        assertEquals(PolicyDecisionType.ALLOWED, approvedDecision.result());
        PolicyApproval approval = PolicyApproval.from(approvedDecision, approvedCtx);

        // Merchant lowers limit to 4000, same amount now exceeds
        PolicyContext currentCtx = ctx(new BigDecimal("5000.0000"), "BANK_TIMEOUT", 0, 1, false, false, RecoveryActionType.RETRY_NOW, new BigDecimal("4000.0000"));

        PolicyRevalidationResult result = revalidator.revalidate(approval, currentCtx);
        assertFalse(result.valid(), "Stale approval must be invalidated when threshold changes");
        assertTrue(result.invalidationReason().contains("threshold_changed"));
        assertEquals(PolicyDecisionType.ESCALATE, result.currentDecision().result());
        // Audit fields retained
        assertEquals(approval.policyDecisionId(), result.policyDecisionId());
        assertEquals(approval.policyVersion(), result.policyVersion());
        assertNotNull(result.finalPolicyValidationAt());
        assertEquals(approval.approvedAction(), result.approvedAction());
    }

    @Test
    void approvedThenCustomerOptsOutBlocked() {
        PolicyContext approvedCtx = ctx(new BigDecimal("5000.0000"), "BANK_TIMEOUT", 0, 1, false, false, RecoveryActionType.SCHEDULE_RETRY, new BigDecimal("10000.0000"));
        PolicyDecision approvedDecision = engine.evaluate(approvedCtx);
        assertEquals(PolicyDecisionType.ALLOWED, approvedDecision.result());
        PolicyApproval approval = PolicyApproval.from(approvedDecision, approvedCtx);

        // Customer opts out after approval
        PolicyContext currentCtx = ctx(new BigDecimal("5000.0000"), "BANK_TIMEOUT", 0, 1, true, false, RecoveryActionType.SCHEDULE_RETRY, new BigDecimal("10000.0000"));
        PolicyRevalidationResult result = revalidator.revalidate(approval, currentCtx);
        assertFalse(result.valid());
        assertTrue(result.invalidationReason().contains("customer_opted_out"));
        assertEquals(PolicyDecisionType.STOP, result.currentDecision().result());
    }

    @Test
    void approvedThenRetryLimitChangesBlocked() {
        // Initially attempt 2/3 -> ALLOWED
        PolicyContext approvedCtx = ctx(new BigDecimal("5000.0000"), "BANK_TIMEOUT", 2, 1, false, false, RecoveryActionType.RETRY_NOW, new BigDecimal("10000.0000"));
        PolicyDecision approvedDecision = engine.evaluate(approvedCtx);
        assertEquals(PolicyDecisionType.ALLOWED, approvedDecision.result());
        PolicyApproval approval = PolicyApproval.from(approvedDecision, approvedCtx);

        // Retry count increments to 3 (limit reached) before execution
        PolicyContext currentCtx = ctx(new BigDecimal("5000.0000"), "BANK_TIMEOUT", 3, 1, false, false, RecoveryActionType.RETRY_NOW, new BigDecimal("10000.0000"));
        PolicyRevalidationResult result = revalidator.revalidate(approval, currentCtx);
        assertFalse(result.valid());
        assertTrue(result.invalidationReason().contains("attempt_count_changed"));
        assertEquals(PolicyDecisionType.STOP, result.currentDecision().result());
    }

    @Test
    void staleApprovalCannotBypassWindowExpiry() {
        PolicyContext approvedCtx = ctx(new BigDecimal("5000.0000"), "BANK_TIMEOUT", 0, 10, false, false, RecoveryActionType.RETRY_NOW, new BigDecimal("10000.0000"));
        PolicyDecision approvedDecision = engine.evaluate(approvedCtx);
        assertEquals(PolicyDecisionType.ALLOWED, approvedDecision.result());
        PolicyApproval approval = PolicyApproval.from(approvedDecision, approvedCtx);

        // Window expires: elapsed 50 > 48
        PolicyContext currentCtx = ctx(new BigDecimal("5000.0000"), "BANK_TIMEOUT", 0, 50, false, false, RecoveryActionType.RETRY_NOW, new BigDecimal("10000.0000"));
        PolicyRevalidationResult result = revalidator.revalidate(approval, currentCtx);
        assertFalse(result.valid());
        assertEquals(PolicyDecisionType.STOP, result.currentDecision().result());
    }

    @Test
    void staleApprovalCannotBypassPolicyWhenReEvaluating() {
        // Simulate that EV and AI previously led to RETRY_NOW approval, but revalidation must re-evaluate independently
        PolicyContext approvedCtx = ctx(new BigDecimal("5000.0000"), "BANK_TIMEOUT", 0, 1, false, false, RecoveryActionType.RETRY_NOW, new BigDecimal("10000.0000"));
        PolicyDecision approvedDecision = engine.evaluate(approvedCtx);
        PolicyApproval approval = PolicyApproval.from(approvedDecision, approvedCtx);
        // Direct policy bypass attempt: even if someone tries to call gateway with old approval, revalidation must fail if any hard rule now blocks
        PolicyContext currentCtx = ctx(new BigDecimal("50000.0000"), "BANK_TIMEOUT", 0, 1, false, false, RecoveryActionType.RETRY_NOW, new BigDecimal("10000.0000"));
        // Note amount changed? Actually amount is immutable per case, but we simulate that at execution time the amount is re-read and found to exceed limit (if approval was for different amount bucket)
        // More realistic: same amount but limit changed, already covered. This test ensures stale approval with different amount cannot bypass.
        // Here we test that if current amount is high-value, revalidation fails regardless of AI
        PolicyRevalidationResult result = revalidator.revalidate(approval, currentCtx);
        // This will fail because amount differs? Actually approval amount snapshot is 5000, current 50000 -> threshold check will escalate
        assertFalse(result.valid());
    }

    @Test
    void highValuePolicyLinkRemainsAllowedWhileRetryEscalated() {
        // High-value 50k: RETRY escalates, LINK allowed — clarified rule
        PolicyContext retry = ctx(new BigDecimal("50000.0000"), "BANK_TIMEOUT", 0, 1, false, false, RecoveryActionType.RETRY_NOW, new BigDecimal("10000.0000"));
        assertEquals(PolicyDecisionType.ESCALATE, engine.evaluate(retry).result());

        PolicyContext link = ctx(new BigDecimal("50000.0000"), "BANK_TIMEOUT", 0, 1, false, false, RecoveryActionType.SEND_PAYMENT_LINK, new BigDecimal("10000.0000"));
        assertEquals(PolicyDecisionType.ALLOWED, engine.evaluate(link).result(),
                "High-value LINK must remain allowed because it does not move money; RETRY escalates");

        // Also SEND_REMINDER with linkAlreadySent true should be allowed even at high value
        PolicyContext reminder = ctx(new BigDecimal("50000.0000"), "BANK_TIMEOUT", 0, 1, false, true, RecoveryActionType.SEND_REMINDER, new BigDecimal("10000.0000"));
        assertEquals(PolicyDecisionType.ALLOWED, engine.evaluate(reminder).result());
    }

    @Test
    void revalidationPreservesAuditFields() {
        PolicyContext ctx = ctx(new BigDecimal("1000.0000"), "BANK_TIMEOUT", 0, 1, false, false, RecoveryActionType.RETRY_NOW, new BigDecimal("10000.0000"));
        PolicyDecision d = engine.evaluate(ctx);
        PolicyApproval approval = PolicyApproval.from(d, ctx);
        assertNotNull(approval.policyDecisionId());
        assertEquals("v1", approval.policyVersion());
        assertNotNull(approval.thresholdSnapshot());
        assertEquals(RecoveryActionType.RETRY_NOW, approval.approvedAction());
        assertNotNull(approval.approvedAt());

        PolicyRevalidationResult result = revalidator.revalidate(approval, ctx);
        assertTrue(result.valid());
        assertEquals(approval.policyDecisionId(), result.policyDecisionId());
        assertEquals(approval.policyVersion(), result.policyVersion());
        assertEquals(approval.approvedAction(), result.approvedAction());
        assertEquals(approval.approvedAt(), result.approvedAt());
        assertNotNull(result.finalPolicyValidationAt());
        assertTrue(result.finalPolicyValidationAt().isAfter(approval.approvedAt()) || result.finalPolicyValidationAt().equals(approval.approvedAt()));
    }

    @Test
    void noDirectPathFromAiToGatewayRevalidationIsRequired() {
        // Policy must be revalidated even if AI recommended and EV ranked highest
        // This test documents the execution boundary: no direct AI->gateway without policy
        // We simulate an AI that says RETRY_NOW HIGH, but policy currently STOP due to opt-out
        PolicyContext ctx = ctx(new BigDecimal("1000.0000"), "BANK_TIMEOUT", 0, 1, true, false, RecoveryActionType.RETRY_NOW, new BigDecimal("10000.0000"));
        PolicyDecision d = engine.evaluate(ctx);
        assertEquals(PolicyDecisionType.STOP, d.result(), "Policy STOP must override any AI/EV");
        // Revalidation of an old approval that was ALLOWED before opt-out must now fail
        PolicyContext oldCtx = ctx(new BigDecimal("1000.0000"), "BANK_TIMEOUT", 0, 1, false, false, RecoveryActionType.RETRY_NOW, new BigDecimal("10000.0000"));
        PolicyApproval oldApproval = PolicyApproval.from(engine.evaluate(oldCtx), oldCtx);
        PolicyRevalidationResult reval = revalidator.revalidate(oldApproval, ctx);
        assertFalse(reval.valid(), "Old approval cannot bypass current policy STOP");
    }
}
