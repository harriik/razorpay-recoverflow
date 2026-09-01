package com.recoverflow.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.recovery.RecoveryActionType;
import com.recoverflow.synthetic.CustomerBehaviorProfile;
import com.recoverflow.synthetic.HiddenTruth;
import com.recoverflow.synthetic.LatentRecoveryPropensity;
import com.recoverflow.synthetic.ObservableCase;
import com.recoverflow.synthetic.SyntheticCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SingleCaseRegretIntegrationTest {

    @Autowired EvaluationEngine engine;

    private SyntheticCase caseWith(String gateway, int attempt, int elapsed, Map<RecoveryActionType, Double> pTrue, BigDecimal amount, boolean linkSent) {
        var obs = new ObservableCase(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                amount, "INR", com.recoverflow.payment.PaymentMethod.CARD, gateway, Instant.now(), attempt, elapsed, 5, 1, linkSent);
        var hidden = new HiddenTruth(com.recoverflow.ai.FailureCategory.TEMPORARY_BANK_FAILURE, CustomerBehaviorProfile.AVERAGE, LatentRecoveryPropensity.MEDIUM, gateway, pTrue);
        return new SyntheticCase(UUID.randomUUID(), hidden, obs, pTrue, Map.of(RecoveryActionType.RETRY_NOW, false, RecoveryActionType.SCHEDULE_RETRY, false, RecoveryActionType.SEND_PAYMENT_LINK, false, RecoveryActionType.SEND_REMINDER, false));
    }

    @Test
    void selectedEqualsOracleRegretZero() {
        var sc = caseWith("BANK_TIMEOUT", 0, 1,
                Map.of(RecoveryActionType.RETRY_NOW, 0.1, RecoveryActionType.SCHEDULE_RETRY, 0.8, RecoveryActionType.SEND_PAYMENT_LINK, 0.3, RecoveryActionType.SEND_REMINDER, 0.05),
                new BigDecimal("1000.0000"), false);
        var q = engine.evaluateQuality(sc, RecoveryActionType.SCHEDULE_RETRY);
        assertEquals(RecoveryActionType.SCHEDULE_RETRY, q.oracleAction());
        assertEquals(0, q.decisionRegret().compareTo(BigDecimal.ZERO));
    }

    @Test
    void suboptimalPositiveRegret() {
        var sc = caseWith("BANK_TIMEOUT", 0, 1,
                Map.of(RecoveryActionType.RETRY_NOW, 0.1, RecoveryActionType.SCHEDULE_RETRY, 0.8, RecoveryActionType.SEND_PAYMENT_LINK, 0.3, RecoveryActionType.SEND_REMINDER, 0.05),
                new BigDecimal("1000.0000"), false);
        var q = engine.evaluateQuality(sc, RecoveryActionType.RETRY_NOW);
        assertTrue(q.decisionRegret().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(RecoveryActionType.SCHEDULE_RETRY, q.oracleAction());
    }

    @Test
    void noPermissibleBothNullRegretZero() {
        var sc = caseWith("CARD_EXPIRED", 3, 50,
                Map.of(RecoveryActionType.RETRY_NOW, 0.9, RecoveryActionType.SCHEDULE_RETRY, 0.9, RecoveryActionType.SEND_PAYMENT_LINK, 0.1, RecoveryActionType.SEND_REMINDER, 0.05),
                new BigDecimal("1000.0000"), false);
        var q = engine.evaluateQuality(sc, null);
        assertNull(q.selectedAction());
        assertNull(q.oracleAction());
        assertEquals(0, q.decisionRegret().compareTo(BigDecimal.ZERO));
    }

    @Test
    void strategyNoActionButOracleExistsRegretIsOracleTrueValue() {
        var sc = caseWith("BANK_TIMEOUT", 0, 1,
                Map.of(RecoveryActionType.RETRY_NOW, 0.1, RecoveryActionType.SCHEDULE_RETRY, 0.8, RecoveryActionType.SEND_PAYMENT_LINK, 0.3, RecoveryActionType.SEND_REMINDER, 0.05),
                new BigDecimal("1000.0000"), false);
        var q = engine.evaluateQuality(sc, null);
        assertNull(q.selectedAction());
        assertNotNull(q.oracleAction());
        assertEquals(q.oracleTrueValue(), q.decisionRegret());
    }

    @Test
    void baselineAGetQuality() {
        var sc = caseWith("BANK_TIMEOUT", 0, 1, Map.of(RecoveryActionType.RETRY_NOW, 0.5, RecoveryActionType.SCHEDULE_RETRY, 0.5, RecoveryActionType.SEND_PAYMENT_LINK, 0.5, RecoveryActionType.SEND_REMINDER, 0.5), new BigDecimal("1000.0000"), false);
        var q = engine.evaluateQuality(sc, RecoveryActionType.RETRY_NOW);
        assertNotNull(q);
        assertEquals(RecoveryActionType.RETRY_NOW, q.selectedAction());
    }

    @Test
    void baselineBGetQuality() {
        var sc = caseWith("BANK_TIMEOUT", 0, 1, Map.of(RecoveryActionType.RETRY_NOW, 0.5, RecoveryActionType.SCHEDULE_RETRY, 0.5, RecoveryActionType.SEND_PAYMENT_LINK, 0.5, RecoveryActionType.SEND_REMINDER, 0.5), new BigDecimal("1000.0000"), false);
        var q = engine.evaluateQuality(sc, RecoveryActionType.SCHEDULE_RETRY);
        assertNotNull(q);
    }

    @Test
    void recoverFlowGetQuality() {
        var sc = caseWith("BANK_TIMEOUT", 0, 1, Map.of(RecoveryActionType.RETRY_NOW, 0.5, RecoveryActionType.SCHEDULE_RETRY, 0.5, RecoveryActionType.SEND_PAYMENT_LINK, 0.5, RecoveryActionType.SEND_REMINDER, 0.5), new BigDecimal("1000.0000"), false);
        var q = engine.evaluateQuality(sc, RecoveryActionType.SEND_PAYMENT_LINK);
        assertNotNull(q);
    }

    @Test
    void allThreeShareSameOracleForOneCase() {
        var sc = caseWith("BANK_TIMEOUT", 0, 1, Map.of(RecoveryActionType.RETRY_NOW, 0.2, RecoveryActionType.SCHEDULE_RETRY, 0.7, RecoveryActionType.SEND_PAYMENT_LINK, 0.3, RecoveryActionType.SEND_REMINDER, 0.05), new BigDecimal("1000.0000"), false);
        var qA = engine.evaluateQuality(sc, RecoveryActionType.RETRY_NOW);
        var qB = engine.evaluateQuality(sc, RecoveryActionType.SCHEDULE_RETRY);
        var qR = engine.evaluateQuality(sc, RecoveryActionType.SCHEDULE_RETRY);
        assertEquals(qA.oracleAction(), qB.oracleAction());
        assertEquals(qA.oracleTrueValue(), qB.oracleTrueValue());
        assertEquals(qB.oracleAction(), qR.oracleAction());
    }

    @Test
    void actualOutcomeDoesNotAffectRegret() {
        var sc1 = caseWith("BANK_TIMEOUT", 0, 1, Map.of(RecoveryActionType.RETRY_NOW, 0.5, RecoveryActionType.SCHEDULE_RETRY, 0.8, RecoveryActionType.SEND_PAYMENT_LINK, 0.3, RecoveryActionType.SEND_REMINDER, 0.05), new BigDecimal("1000.0000"), false);
        var sc2 = new SyntheticCase(sc1.caseId(), sc1.hiddenTruth(), sc1.observable(), sc1.pTrue(), Map.of(RecoveryActionType.RETRY_NOW, true, RecoveryActionType.SCHEDULE_RETRY, true, RecoveryActionType.SEND_PAYMENT_LINK, true, RecoveryActionType.SEND_REMINDER, true));
        var q1 = engine.evaluateQuality(sc1, RecoveryActionType.RETRY_NOW);
        var q2 = engine.evaluateQuality(sc2, RecoveryActionType.RETRY_NOW);
        assertEquals(q1.decisionRegret(), q2.decisionRegret());
        assertEquals(q1.selectedTrueValue(), q2.selectedTrueValue());
    }

    @Test
    void hiddenTruthOnlyAfterDecision() {
        // Verify decision-time components never receive HiddenTruth
        for (var clazz : new Class[]{com.recoverflow.decision.RecoveryDecisionService.class, com.recoverflow.likelihood.InterventionLikelihoodEstimator.class, com.recoverflow.decision.ExpectedNetRecoveryValueEngine.class, com.recoverflow.policy.PolicyEngine.class, com.recoverflow.synthetic.SyntheticAiProxy.class}) {
            for (var m : clazz.getDeclaredMethods()) {
                for (var p : m.getParameters()) {
                    String type = p.getType().getSimpleName().toLowerCase();
                    assertFalse(type.contains("hiddentruth") || type.contains("ptrue"), "Decision-time " + clazz.getSimpleName() + "." + m.getName() + " must not take hidden");
                }
            }
        }
        // But evaluator can
        var sc = caseWith("BANK_TIMEOUT", 0, 1, Map.of(RecoveryActionType.RETRY_NOW, 0.5, RecoveryActionType.SCHEDULE_RETRY, 0.5, RecoveryActionType.SEND_PAYMENT_LINK, 0.5, RecoveryActionType.SEND_REMINDER, 0.5), new BigDecimal("1000.0000"), false);
        var q = engine.evaluateQuality(sc, RecoveryActionType.RETRY_NOW);
        assertNotNull(q.oracleTrueValue());
    }

    @Test
    void reproducibilitySameSeedSameQuality() {
        var sc = caseWith("BANK_TIMEOUT", 0, 1, Map.of(RecoveryActionType.RETRY_NOW, 0.5, RecoveryActionType.SCHEDULE_RETRY, 0.8, RecoveryActionType.SEND_PAYMENT_LINK, 0.3, RecoveryActionType.SEND_REMINDER, 0.05), new BigDecimal("1000.0000"), false);
        var q1 = engine.evaluateQuality(sc, RecoveryActionType.RETRY_NOW);
        var q2 = engine.evaluateQuality(sc, RecoveryActionType.RETRY_NOW);
        assertEquals(q1.decisionRegret(), q2.decisionRegret());
        assertEquals(q1.oracleAction(), q2.oracleAction());
        assertEquals(q1.selectedTrueValue(), q2.selectedTrueValue());
    }

    @Test
    void deterministicFixturesGoodDecision() {
        // Policy-only A, RecoverFlow B, Oracle B => regretPolicyOnly >0, regretAi=0
        var sc = caseWith("BANK_TIMEOUT", 0, 1,
                Map.of(RecoveryActionType.RETRY_NOW, 0.1, RecoveryActionType.SCHEDULE_RETRY, 0.8, RecoveryActionType.SEND_PAYMENT_LINK, 0.3, RecoveryActionType.SEND_REMINDER, 0.05),
                new BigDecimal("1000.0000"), false);
        var qPolicy = engine.evaluateQuality(sc, RecoveryActionType.RETRY_NOW);
        var qAi = engine.evaluateQuality(sc, RecoveryActionType.SCHEDULE_RETRY);
        assertTrue(qPolicy.decisionRegret().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(0, qAi.decisionRegret().compareTo(BigDecimal.ZERO));
    }

    @Test
    void deterministicFixtureAiHurts() {
        // Policy-only B (optimal), RecoverFlow A (suboptimal), Oracle B => regretAi > regretPolicyOnly
        var sc = caseWith("BANK_TIMEOUT", 0, 1,
                Map.of(RecoveryActionType.RETRY_NOW, 0.1, RecoveryActionType.SCHEDULE_RETRY, 0.8, RecoveryActionType.SEND_PAYMENT_LINK, 0.3, RecoveryActionType.SEND_REMINDER, 0.05),
                new BigDecimal("1000.0000"), false);
        var qPolicy = engine.evaluateQuality(sc, RecoveryActionType.SCHEDULE_RETRY);
        var qAi = engine.evaluateQuality(sc, RecoveryActionType.RETRY_NOW);
        assertTrue(qAi.decisionRegret().compareTo(qPolicy.decisionRegret()) > 0);
    }

    @Test
    void deterministicFixtureSameAction() {
        var sc = caseWith("BANK_TIMEOUT", 0, 1,
                Map.of(RecoveryActionType.RETRY_NOW, 0.8, RecoveryActionType.SCHEDULE_RETRY, 0.1, RecoveryActionType.SEND_PAYMENT_LINK, 0.1, RecoveryActionType.SEND_REMINDER, 0.05),
                new BigDecimal("1000.0000"), false);
        var q1 = engine.evaluateQuality(sc, RecoveryActionType.RETRY_NOW);
        var q2 = engine.evaluateQuality(sc, RecoveryActionType.RETRY_NOW);
        assertEquals(q1.decisionRegret(), q2.decisionRegret());
        assertEquals(q1.selectedTrueValue(), q2.selectedTrueValue());
    }

    @Test
    void deterministicFixtureOraclePolicyLimited() {
        // Highest P_true is RETRY but policy blocks RETRY due to amount threshold, oracle must pick next permissible
        var sc = caseWith("BANK_TIMEOUT", 0, 1,
                Map.of(RecoveryActionType.RETRY_NOW, 0.9, RecoveryActionType.SCHEDULE_RETRY, 0.9, RecoveryActionType.SEND_PAYMENT_LINK, 0.8, RecoveryActionType.SEND_REMINDER, 0.05),
                new BigDecimal("50000.0000"), false); // 50000 > 10000, so RETRY blocked
        var q = engine.evaluateQuality(sc, RecoveryActionType.SEND_PAYMENT_LINK);
        // Oracle should not be RETRY_NOW even though it has highest P_true
        assertNotEquals(RecoveryActionType.RETRY_NOW, q.oracleAction());
        assertTrue(q.oracleAction() == RecoveryActionType.SEND_PAYMENT_LINK || q.oracleAction() == RecoveryActionType.SCHEDULE_RETRY);
    }
}
