package com.recoverflow.evaluation;

import com.recoverflow.decision.ExpectedNetRecoveryValueEngine;
import com.recoverflow.recovery.RecoveryActionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Pure evaluator-only true value calculator.
 * trueNetValue(action) = P_true(action) * amount - operationalCost - syntheticFrictionProxy - deterministicTrueRiskCost
 * P_true comes ONLY from HiddenTruth (evaluator-side). Costs are same as EV engine but true risk is
 * deterministic per action (versioned), independent of AI riskLevel, evidenceQuality, recoverability, P_estimated.
 * Same action has same deterministic costs regardless of strategy.
 */
public class TrueDecisionValueCalculator {

    public static final String VERSION = "true-value-v1";

    private final ExpectedNetRecoveryValueEngine evEngine;

    // Deterministic true risk costs per action, versioned, independent of AI or hidden latent
    private static final Map<RecoveryActionType, BigDecimal> TRUE_RISK = Map.of(
            RecoveryActionType.RETRY_NOW, new BigDecimal("5.00"),
            RecoveryActionType.SCHEDULE_RETRY, new BigDecimal("5.00"),
            RecoveryActionType.SEND_PAYMENT_LINK, new BigDecimal("5.00"),
            RecoveryActionType.SEND_REMINDER, new BigDecimal("5.00")
    );

    public TrueDecisionValueCalculator(ExpectedNetRecoveryValueEngine evEngine) {
        this.evEngine = evEngine;
    }

    // For tests without Spring
    public TrueDecisionValueCalculator() {
        this.evEngine = new ExpectedNetRecoveryValueEngine();
    }

    public String getVersion() { return VERSION; }

    public BigDecimal trueRiskFor(RecoveryActionType action) {
        return TRUE_RISK.getOrDefault(action, new BigDecimal("5.00"));
    }

    public BigDecimal calculate(RecoveryActionType action, BigDecimal amount, double pTrue) {
        if (action == null || amount == null) throw new IllegalArgumentException("action and amount required");
        BigDecimal p = BigDecimal.valueOf(pTrue);
        BigDecimal expected = p.multiply(amount);
        BigDecimal cost = evEngine.costFor(action);
        BigDecimal friction = evEngine.frictionFor(action);
        BigDecimal risk = trueRiskFor(action);
        return expected.subtract(cost).subtract(friction).subtract(risk).setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal calculate(RecoveryActionType action, BigDecimal amount, Double pTrue) {
        if (pTrue == null) return null;
        return calculate(action, amount, pTrue.doubleValue());
    }
}
