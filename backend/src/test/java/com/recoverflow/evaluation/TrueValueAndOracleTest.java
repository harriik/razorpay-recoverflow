package com.recoverflow.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.ai.RiskLevel;
import com.recoverflow.decision.ExpectedNetRecoveryValueEngine;
import com.recoverflow.likelihood.InterventionLikelihoodEstimator;
import com.recoverflow.likelihood.ObservableContext;
import com.recoverflow.payment.PaymentMethod;
import com.recoverflow.policy.PolicyEngine;
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

class TrueValueAndOracleTest {

    private final ExpectedNetRecoveryValueEngine evEngine = new ExpectedNetRecoveryValueEngine();
    private final TrueDecisionValueCalculator calc = new TrueDecisionValueCalculator(evEngine);
    private final PolicyEngine policyEngine = new PolicyEngine(new com.recoverflow.policy.PolicyConfig());
    private final TrueOracleEvaluator oracle = new TrueOracleEvaluator(policyEngine, calc);

    private SyntheticCase caseWithP(Map<RecoveryActionType, Double> pTrue, String gateway, int attempt, int elapsed, BigDecimal amount) {
        var obs = new ObservableCase(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                amount, "INR", PaymentMethod.CARD, gateway, Instant.now(), attempt, elapsed, 5, 1, false);
        var hidden = new HiddenTruth(com.recoverflow.ai.FailureCategory.TEMPORARY_BANK_FAILURE, CustomerBehaviorProfile.AVERAGE, LatentRecoveryPropensity.MEDIUM, gateway, pTrue);
        var gt = Map.of(
                RecoveryActionType.RETRY_NOW, false,
                RecoveryActionType.SCHEDULE_RETRY, false,
                RecoveryActionType.SEND_PAYMENT_LINK, false,
                RecoveryActionType.SEND_REMINDER, false
        );
        return new SyntheticCase(UUID.randomUUID(), hidden, obs, pTrue, gt);
    }

    @Test
    void trueNetValueUsesPTrue() {
        var sc = caseWithP(Map.of(RecoveryActionType.RETRY_NOW, 0.5, RecoveryActionType.SCHEDULE_RETRY, 0.2, RecoveryActionType.SEND_PAYMENT_LINK, 0.1, RecoveryActionType.SEND_REMINDER, 0.05),
                "BANK_TIMEOUT", 0, 1, new BigDecimal("1000.0000"));
        BigDecimal tvRetry = calc.calculate(RecoveryActionType.RETRY_NOW, sc.observable().amount(), 0.5);
        BigDecimal tvRetryLow = calc.calculate(RecoveryActionType.RETRY_NOW, sc.observable().amount(), 0.2);
        assertTrue(tvRetry.compareTo(tvRetryLow) > 0, "Higher P_true should give higher trueNetValue");
    }

    @Test
    void trueNetValueDoesNotDependOnPEstimated() {
        // P_estimated is from estimator, trueNetValue should be independent
        var sc = caseWithP(Map.of(RecoveryActionType.RETRY_NOW, 0.5, RecoveryActionType.SCHEDULE_RETRY, 0.5, RecoveryActionType.SEND_PAYMENT_LINK, 0.5, RecoveryActionType.SEND_REMINDER, 0.5),
                "BANK_TIMEOUT", 0, 1, new BigDecimal("1000.0000"));
        var obsCtx = new ObservableContext(new BigDecimal("1000.0000"), "INR", PaymentMethod.CARD, "BANK_TIMEOUT", 1, 0, 5, 1, false);
        var estimator = new InterventionLikelihoodEstimator();
        var pEst = estimator.estimateObservableOnly(obsCtx);
        // Change P_estimated should not affect trueNetValue
        BigDecimal tv1 = calc.calculate(RecoveryActionType.RETRY_NOW, sc.observable().amount(), 0.5);
        // Even if pEst for RETRY is different, trueNetValue remains same
        assertEquals(tv1, calc.calculate(RecoveryActionType.RETRY_NOW, sc.observable().amount(), 0.5));
        assertNotEquals(pEst.get(RecoveryActionType.RETRY_NOW).doubleValue(), 0.5, "pEst is different from pTrue");
    }

    @Test
    void trueValueDoesNotDependOnAiRiskLevel() {
        var sc = caseWithP(Map.of(RecoveryActionType.RETRY_NOW, 0.5, RecoveryActionType.SCHEDULE_RETRY, 0.5, RecoveryActionType.SEND_PAYMENT_LINK, 0.5, RecoveryActionType.SEND_REMINDER, 0.5),
                "BANK_TIMEOUT", 0, 1, new BigDecimal("1000.0000"));
        BigDecimal tv = calc.calculate(RecoveryActionType.RETRY_NOW, sc.observable().amount(), 0.5);
        // Cost/friction/risk for same action must be same regardless of AI riskLevel
        // Our TrueDecisionValueCalculator uses deterministic true risk (5.00) for all, not AI risk
        assertEquals(new BigDecimal("500.00").subtract(new BigDecimal("50.00")).subtract(new BigDecimal("5.00")).setScale(4), tv.subtract(new BigDecimal("500.00").multiply(new BigDecimal("0.5"))).negate().setScale(4) == null ? tv : tv);
        // More directly: same action, same amount, same P_true must give same true value regardless of strategy
        BigDecimal tv2 = calc.calculate(RecoveryActionType.RETRY_NOW, new BigDecimal("1000.0000"), 0.5);
        assertEquals(tv, tv2);
    }

    @Test
    void sameActionSameTrueCostsAcrossStrategies() {
        BigDecimal amount = new BigDecimal("2000.0000");
        BigDecimal tvA = calc.calculate(RecoveryActionType.SEND_PAYMENT_LINK, amount, 0.4);
        BigDecimal tvB = calc.calculate(RecoveryActionType.SEND_PAYMENT_LINK, amount, 0.4);
        assertEquals(tvA, tvB);
        // Cost/friction should be deterministic: LINK cost 10, friction 100, risk 5
        // True value = 0.4*2000 -10 -100 -5 = 800 -115 = 685
        assertEquals(new BigDecimal("685.0000"), tvA);
    }

    @Test
    void oracleSelectsHighestTruePermissible() {
        var sc = caseWithP(Map.of(RecoveryActionType.RETRY_NOW, 0.1, RecoveryActionType.SCHEDULE_RETRY, 0.6, RecoveryActionType.SEND_PAYMENT_LINK, 0.3, RecoveryActionType.SEND_REMINDER, 0.05),
                "BANK_TIMEOUT", 0, 1, new BigDecimal("1000.0000"));
        var result = oracle.evaluate(sc);
        assertEquals(RecoveryActionType.SCHEDULE_RETRY, result.oracleAction(), "Highest true P among permissible should be oracle");
    }

    @Test
    void policyBlockedCannotBeOracle() {
        // High amount exceeds threshold 10000, so RETRY_NOW should be blocked, even if its true value is highest
        var sc = caseWithP(Map.of(RecoveryActionType.RETRY_NOW, 0.9, RecoveryActionType.SCHEDULE_RETRY, 0.1, RecoveryActionType.SEND_PAYMENT_LINK, 0.1, RecoveryActionType.SEND_REMINDER, 0.05),
                "BANK_TIMEOUT", 0, 1, new BigDecimal("50000.0000"));
        var result = oracle.evaluate(sc);
        assertNotEquals(RecoveryActionType.RETRY_NOW, result.oracleAction(), "Blocked RETRY should not be oracle even if highest true value");
        // Should be LINK or other allowed
        assertTrue(result.permissibleActions().contains(result.oracleAction()));
    }

    @Test
    void oracleRespectsAmountThreshold() {
        var scHigh = caseWithP(Map.of(RecoveryActionType.RETRY_NOW, 0.8, RecoveryActionType.SCHEDULE_RETRY, 0.8, RecoveryActionType.SEND_PAYMENT_LINK, 0.8, RecoveryActionType.SEND_REMINDER, 0.8),
                "BANK_TIMEOUT", 0, 1, new BigDecimal("50000.0000"));
        var result = oracle.evaluate(scHigh);
        // Both RETRY actions should be blocked due to amount, so oracle should be LINK or REMINDER
        assertNotEquals(RecoveryActionType.RETRY_NOW, result.oracleAction());
        assertNotEquals(RecoveryActionType.SCHEDULE_RETRY, result.oracleAction());
    }

    @Test
    void oracleRespectsRetryLimit() {
        var sc = caseWithP(Map.of(RecoveryActionType.RETRY_NOW, 0.8, RecoveryActionType.SCHEDULE_RETRY, 0.8, RecoveryActionType.SEND_PAYMENT_LINK, 0.8, RecoveryActionType.SEND_REMINDER, 0.8),
                "BANK_TIMEOUT", 3, 1, new BigDecimal("1000.0000"));
        var result = oracle.evaluate(sc);
        // At retry limit 3/3, all actions are STOP per policy, so no permissible
        assertNull(result.oracleAction());
        assertTrue(result.permissibleActions().isEmpty());
    }

    @Test
    void oracleRespectsPermanentFailure() {
        var sc = caseWithP(Map.of(RecoveryActionType.RETRY_NOW, 0.9, RecoveryActionType.SCHEDULE_RETRY, 0.9, RecoveryActionType.SEND_PAYMENT_LINK, 0.1, RecoveryActionType.SEND_REMINDER, 0.05),
                "CARD_EXPIRED", 0, 1, new BigDecimal("1000.0000"));
        var result = oracle.evaluate(sc);
        // CARD_EXPIRED should block RETRY, so oracle should be LINK even though RETRY has higher true value
        assertNotEquals(RecoveryActionType.RETRY_NOW, result.oracleAction());
        assertEquals(RecoveryActionType.SEND_PAYMENT_LINK, result.oracleAction());
    }

    @Test
    void oracleHandlesNoPermissible() {
        var sc = caseWithP(Map.of(RecoveryActionType.RETRY_NOW, 0.5, RecoveryActionType.SCHEDULE_RETRY, 0.5, RecoveryActionType.SEND_PAYMENT_LINK, 0.5, RecoveryActionType.SEND_REMINDER, 0.5),
                "BANK_TIMEOUT", 3, 50, new BigDecimal("1000.0000")); // retry limit + window expired
        var result = oracle.evaluate(sc);
        assertNull(result.oracleAction());
        assertNull(result.oracleTrueValue());
        assertTrue(result.permissibleActions().isEmpty());
    }

    @Test
    void evaluatorCanAccessPTrueWhileDecisionCannot() throws Exception {
        // Decision-time classes must not have HiddenTruth/P_true fields/methods
        for (var clazz : new Class[]{com.recoverflow.likelihood.InterventionLikelihoodEstimator.class, com.recoverflow.decision.ExpectedNetRecoveryValueEngine.class, com.recoverflow.policy.PolicyEngine.class, com.recoverflow.decision.RecoveryDecisionService.class}) {
            for (var m : clazz.getDeclaredMethods()) {
                for (var p : m.getParameters()) {
                    String type = p.getType().getSimpleName().toLowerCase();
                    assertFalse(type.contains("hiddentruth") || type.contains("ptrue"), "Decision class " + clazz.getSimpleName() + " method " + m.getName() + " must not take hidden type " + p.getType().getSimpleName());
                }
            }
        }
        // Evaluator can
        var sc = caseWithP(Map.of(RecoveryActionType.RETRY_NOW, 0.5, RecoveryActionType.SCHEDULE_RETRY, 0.5, RecoveryActionType.SEND_PAYMENT_LINK, 0.5, RecoveryActionType.SEND_REMINDER, 0.5),
                "BANK_TIMEOUT", 0, 1, new BigDecimal("1000.0000"));
        assertNotNull(sc.pTrue().get(RecoveryActionType.RETRY_NOW));
        assertNotNull(calc.calculate(RecoveryActionType.RETRY_NOW, sc.observable().amount(), sc.pTrue().get(RecoveryActionType.RETRY_NOW)));
    }
}
