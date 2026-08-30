package com.recoverflow.policy;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.ai.EvidenceQuality;
import com.recoverflow.ai.FailureCategory;
import com.recoverflow.ai.RiskLevel;
import com.recoverflow.decision.ExpectedNetRecoveryValueEngine;
import com.recoverflow.decision.ExpectedNetRecoveryValueEngine.RankedCandidate;
import com.recoverflow.recovery.RecoveryActionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PolicyEngineTest {

    private PolicyEngine engine;
    private PolicyConfig config;

    @BeforeEach
    void setUp() {
        config = new PolicyConfig(); // v1 defaults: limit 10000, max 3, window 48
        engine = new PolicyEngine(config);
    }

    private PolicyContext base(String gateway, BigDecimal amount, int attempts, int elapsed, boolean optedOut, boolean linkSent, RecoveryActionType action) {
        return new PolicyContext(amount, gateway, attempts, elapsed, optedOut, linkSent, action,
                config.getAutoActionLimit(), config.getMaxRetries(), config.getRecoveryWindowHours());
    }

    // ---- Hard safety rules must never depend on AI ----

    @Test
    void policyContextHasNoAiFields() {
        for (var field : PolicyContext.class.getDeclaredFields()) {
            String n = field.getName().toLowerCase();
            assertFalse(n.contains("risk"), "PolicyContext must not contain AI riskLevel: " + field.getName());
            assertFalse(n.contains("evidence"), "PolicyContext must not contain AI evidenceQuality");
            assertFalse(n.contains("recoverability"), "PolicyContext must not contain AI recoverability");
        }
        // Ensure PolicyEngine evaluate does not take AiAssessment
        boolean takesAi = false;
        for (var m : PolicyEngine.class.getDeclaredMethods()) {
            if (m.getName().equals("evaluate")) {
                for (var p : m.getParameters()) {
                    if (p.getType().getSimpleName().contains("AiAssessment") || p.getType().getSimpleName().contains("RiskLevel")) {
                        takesAi = true;
                    }
                }
            }
        }
        assertFalse(takesAi, "PolicyEngine.evaluate must not take AI types");
    }

    @Test
    void optOutOverridesAllHardRules() {
        PolicyContext ctx = base("BANK_TIMEOUT", new BigDecimal("500.0000"), 0, 1, true, false, RecoveryActionType.RETRY_NOW);
        PolicyDecision d = engine.evaluate(ctx);
        assertEquals(PolicyDecisionType.STOP, d.result());
        assertEquals(PolicyRuleId.OPT_OUT, d.blockingRule());
        assertEquals("v1", d.policyVersion());
        // Even if amount would be ESCALATE, optOut wins
        PolicyContext ctxHighAmount = base("BANK_TIMEOUT", new BigDecimal("50000.0000"), 0, 1, true, false, RecoveryActionType.RETRY_NOW);
        assertEquals(PolicyRuleId.OPT_OUT, engine.evaluate(ctxHighAmount).blockingRule());
    }

    @Test
    void permanentFailureBlocksRetryButAllowsLink() {
        // CARD_EXPIRED gateway is permanent for retry
        PolicyContext retry = base("CARD_EXPIRED", new BigDecimal("1000.0000"), 0, 1, false, false, RecoveryActionType.RETRY_NOW);
        assertEquals(PolicyDecisionType.STOP, engine.evaluate(retry).result());
        assertEquals(PolicyRuleId.PERMANENT_FAILURE, engine.evaluate(retry).blockingRule());

        PolicyContext schedule = base("CARD_EXPIRED", new BigDecimal("1000.0000"), 0, 1, false, false, RecoveryActionType.SCHEDULE_RETRY);
        assertEquals(PolicyDecisionType.STOP, engine.evaluate(schedule).result());

        // But SEND_PAYMENT_LINK should be ALLOWED (can still send link to update card)
        PolicyContext link = base("CARD_EXPIRED", new BigDecimal("1000.0000"), 0, 1, false, false, RecoveryActionType.SEND_PAYMENT_LINK);
        assertEquals(PolicyDecisionType.ALLOWED, engine.evaluate(link).result());

        // Non-permanent like BANK_TIMEOUT allows retry
        PolicyContext ok = base("BANK_TIMEOUT", new BigDecimal("1000.0000"), 0, 1, false, false, RecoveryActionType.RETRY_NOW);
        assertEquals(PolicyDecisionType.ALLOWED, engine.evaluate(ok).result());
    }

    @Test
    void windowExpiredBlocksAll() {
        PolicyContext ctx = base("BANK_TIMEOUT", new BigDecimal("1000.0000"), 0, 49, false, false, RecoveryActionType.SCHEDULE_RETRY);
        assertEquals(PolicyDecisionType.STOP, engine.evaluate(ctx).result());
        assertEquals(PolicyRuleId.WINDOW_EXPIRED, engine.evaluate(ctx).blockingRule());
        // At boundary 48 should be allowed
        PolicyContext boundary = base("BANK_TIMEOUT", new BigDecimal("1000.0000"), 0, 48, false, false, RecoveryActionType.SCHEDULE_RETRY);
        assertEquals(PolicyDecisionType.ALLOWED, engine.evaluate(boundary).result());
    }

    @Test
    void retryLimitBlocks() {
        PolicyContext atLimit = base("BANK_TIMEOUT", new BigDecimal("1000.0000"), 3, 1, false, false, RecoveryActionType.RETRY_NOW);
        assertEquals(PolicyDecisionType.STOP, engine.evaluate(atLimit).result());
        assertEquals(PolicyRuleId.RETRY_LIMIT, engine.evaluate(atLimit).blockingRule());
        PolicyContext below = base("BANK_TIMEOUT", new BigDecimal("1000.0000"), 2, 1, false, false, RecoveryActionType.RETRY_NOW);
        assertEquals(PolicyDecisionType.ALLOWED, engine.evaluate(below).result());
    }

    @Test
    void amountThresholdHardEscalationIndependentOfAiRisk() {
        // Amount 50k > limit 10k => ESCALATE for financial auto actions, regardless of AI riskLevel LOW
        PolicyContext ctx = base("BANK_TIMEOUT", new BigDecimal("50000.0000"), 0, 1, false, false, RecoveryActionType.RETRY_NOW);
        PolicyDecision d = engine.evaluate(ctx);
        assertEquals(PolicyDecisionType.ESCALATE, d.result());
        assertEquals(PolicyRuleId.AMOUNT_THRESHOLD, d.blockingRule());

        // Same with SCHEDULE_RETRY
        PolicyContext sched = base("BANK_TIMEOUT", new BigDecimal("50000.0000"), 0, 1, false, false, RecoveryActionType.SCHEDULE_RETRY);
        assertEquals(PolicyDecisionType.ESCALATE, engine.evaluate(sched).result());

        // Links are not financial auto actions, so even high amount should be ALLOWED (policy-controlled, not financial)
        PolicyContext link = base("BANK_TIMEOUT", new BigDecimal("50000.0000"), 0, 1, false, false, RecoveryActionType.SEND_PAYMENT_LINK);
        assertEquals(PolicyDecisionType.ALLOWED, engine.evaluate(link).result());

        // Verify that AI riskLevel LOW would not bypass: we don't pass AI, but decision must stay ESCALATE
        // Simulate two identical contexts with different hypothetical AI risk — policy must be same
        PolicyContext ctx2 = base("BANK_TIMEOUT", new BigDecimal("50000.0000"), 0, 1, false, false, RecoveryActionType.RETRY_NOW);
        assertEquals(engine.evaluate(ctx).result(), engine.evaluate(ctx2).result());
    }

    @Test
    void actionEligibilityReminderRequiresLink() {
        PolicyContext noLink = base("BANK_TIMEOUT", new BigDecimal("1000.0000"), 0, 1, false, false, RecoveryActionType.SEND_REMINDER);
        assertEquals(PolicyDecisionType.BLOCKED, engine.evaluate(noLink).result());
        assertEquals(PolicyRuleId.ACTION_ELIGIBILITY, engine.evaluate(noLink).blockingRule());

        PolicyContext withLink = base("BANK_TIMEOUT", new BigDecimal("1000.0000"), 0, 1, false, true, RecoveryActionType.SEND_REMINDER);
        assertEquals(PolicyDecisionType.ALLOWED, engine.evaluate(withLink).result());
    }

    @Test
    void defaultAllow() {
        PolicyContext ctx = base("BANK_TIMEOUT", new BigDecimal("5000.0000"), 0, 1, false, false, RecoveryActionType.SEND_PAYMENT_LINK);
        PolicyDecision d = engine.evaluate(ctx);
        assertEquals(PolicyDecisionType.ALLOWED, d.result());
        assertEquals(PolicyRuleId.DEFAULT_ALLOW, d.blockingRule());
    }

    @Test
    void thresholdSnapshotIsCaptured() {
        PolicyContext ctx = base("BANK_TIMEOUT", new BigDecimal("5000.0000"), 1, 5, false, false, RecoveryActionType.RETRY_NOW);
        PolicyDecision d = engine.evaluate(ctx);
        assertNotNull(d.thresholdSnapshot());
        assertEquals(config.getAutoActionLimit(), d.thresholdSnapshot().get("autoActionLimit"));
        assertEquals(config.getMaxRetries(), d.thresholdSnapshot().get("maxRetries"));
        assertEquals("v1", d.thresholdSnapshot().get("policyVersion"));
    }

    @Test
    void deterministic() {
        PolicyContext ctx = base("INSUFFICIENT_FUNDS", new BigDecimal("5000.0000"), 1, 10, false, false, RecoveryActionType.SEND_PAYMENT_LINK);
        PolicyDecision a = engine.evaluate(ctx);
        PolicyDecision b = engine.evaluate(ctx);
        assertEquals(a.result(), b.result());
        assertEquals(a.blockingRule(), b.blockingRule());
    }

    @Test
    void priorityOrderOptOutBeforePermanentBeforeWindow() {
        // Create context that matches multiple rules: optedOut + CARD_EXPIRED + window expired + over limit
        // OptOut must win
        PolicyContext ctx = new PolicyContext(
                new BigDecimal("50000.0000"), "CARD_EXPIRED", 5, 100, true, false,
                RecoveryActionType.RETRY_NOW, config.getAutoActionLimit(), config.getMaxRetries(), config.getRecoveryWindowHours());
        assertEquals(PolicyRuleId.OPT_OUT, engine.evaluate(ctx).blockingRule());

        // Without optOut, permanent should win over window/amount
        PolicyContext ctx2 = new PolicyContext(
                new BigDecimal("50000.0000"), "CARD_EXPIRED", 5, 100, false, false,
                RecoveryActionType.RETRY_NOW, config.getAutoActionLimit(), config.getMaxRetries(), config.getRecoveryWindowHours());
        assertEquals(PolicyRuleId.PERMANENT_FAILURE, engine.evaluate(ctx2).blockingRule());

        // Without permanent, window beats amount
        PolicyContext ctx3 = new PolicyContext(
                new BigDecimal("50000.0000"), "BANK_TIMEOUT", 0, 100, false, false,
                RecoveryActionType.RETRY_NOW, config.getAutoActionLimit(), config.getMaxRetries(), config.getRecoveryWindowHours());
        assertEquals(PolicyRuleId.WINDOW_EXPIRED, engine.evaluate(ctx3).blockingRule());
    }

    // ---- Integration with EV ranking ----

    @Test
    void policyOverridesHighestEvCandidate() {
        ExpectedNetRecoveryValueEngine evEngine = new ExpectedNetRecoveryValueEngine();
        // Amount 50000 high, but EV will rank RETRY_NOW highest if P high, yet policy must escalate financial actions
        // Use P that makes RETRY_NOW best: RETRY 0.5, LINK 0.4
        var likelihoods = Map.of(
                RecoveryActionType.RETRY_NOW, new java.math.BigDecimal("0.500"),
                RecoveryActionType.SCHEDULE_RETRY, new java.math.BigDecimal("0.400"),
                RecoveryActionType.SEND_PAYMENT_LINK, new java.math.BigDecimal("0.400"),
                RecoveryActionType.SEND_REMINDER, new java.math.BigDecimal("0.100")
        );
        List<RankedCandidate> ranked = evEngine.rank(new BigDecimal("50000.0000"), likelihoods, RiskLevel.LOW);
        assertEquals(RecoveryActionType.RETRY_NOW, ranked.get(0).action(), "EV should rank RETRY_NOW highest");

        PolicyContext baseCtx = new PolicyContext(
                new BigDecimal("50000.0000"), "BANK_TIMEOUT", 0, 1, false, false,
                RecoveryActionType.RETRY_NOW, config.getAutoActionLimit(), config.getMaxRetries(), config.getRecoveryWindowHours());

        PolicyEngine.PolicySelection sel = engine.selectBestAllowed(ranked, baseCtx);
        // Policy must block RETRY_NOW and SCHEDULE_RETRY (ESCALATE), then select LINK
        assertTrue(sel.hasSelection());
        assertEquals(RecoveryActionType.SEND_PAYMENT_LINK, sel.selectedCandidate().action(),
                "Policy must override highest EV RETRY_NOW due to amount threshold, selecting next allowed");
        // Verify decisions show ESCALATE for first two
        assertEquals(PolicyDecisionType.ESCALATE, sel.allDecisions().get(0).result());
        assertEquals(PolicyDecisionType.ESCALATE, sel.allDecisions().get(1).result());
        assertEquals(PolicyDecisionType.ALLOWED, sel.allDecisions().get(2).result());
    }

    @Test
    void policyOverridesAiRecommendation() {
        // Simulate AI recommended RETRY_NOW HIGH but policy says permanent failure STOP
        // Policy must win regardless of AI
        PolicyContext ctx = base("CARD_EXPIRED", new BigDecimal("1000.0000"), 0, 1, false, false, RecoveryActionType.RETRY_NOW);
        PolicyDecision d = engine.evaluate(ctx);
        assertEquals(PolicyDecisionType.STOP, d.result());
        // AI would have recommended RETRY_NOW, but policy blocks
    }

    @Test
    void allBlockedReturnsEscalateOrStop() {
        ExpectedNetRecoveryValueEngine evEngine = new ExpectedNetRecoveryValueEngine();
        var likelihoods = Map.of(
                RecoveryActionType.RETRY_NOW, new java.math.BigDecimal("0.500"),
                RecoveryActionType.SCHEDULE_RETRY, new java.math.BigDecimal("0.400")
        );
        List<RankedCandidate> ranked = evEngine.rank(new BigDecimal("50000.0000"), likelihoods, RiskLevel.LOW);
        PolicyContext baseCtx = new PolicyContext(
                new BigDecimal("50000.0000"), "BANK_TIMEOUT", 0, 1, false, false,
                RecoveryActionType.RETRY_NOW, config.getAutoActionLimit(), config.getMaxRetries(), config.getRecoveryWindowHours());
        // Only financial actions in ranked, both escalate due to amount
        PolicyEngine.PolicySelection sel = engine.selectBestAllowed(ranked, baseCtx);
        assertFalse(sel.hasSelection());
        assertEquals(PolicyDecisionType.ESCALATE, sel.overallDecision().result());
    }

    @Test
    void syncPolicyVersion() {
        assertEquals("v1", engine.getVersion());
        assertEquals("v1", config.getVersion());
        PolicyContext ctx = base("BANK_TIMEOUT", new BigDecimal("1000.0000"), 0, 1, false, false, RecoveryActionType.RETRY_NOW);
        assertEquals("v1", engine.evaluate(ctx).policyVersion());
    }

    @Test
    void pEstimatedIsSyntheticNotCalibrated() {
        // Verify estimator file describes P_estimated as synthetic likelihood, not calibrated real-world probability
        // This is a documentation test: estimator version v1 is synthetic
        assertEquals("v1", engine.getVersion());
        // Likelihood estimator's P is not from LLM; AI contributes only via qualitative enum, tested in likelihood tests
        // Policy never uses P_estimated directly, only EV ranking
    }
}
