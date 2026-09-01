package com.recoverflow.evaluation;

import com.recoverflow.recovery.RecoveryActionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable evaluator-only result for one strategy's decision quality.
 * Populated ONLY by evaluator after the strategy has selected an action.
 * Never passed into decision-time components (AI, estimator, EV, policy, execution).
 * All monetary fields use BigDecimal (scale 4, HALF_UP).
 *
 * Semantics:
 * - selectedAction: action actually selected by the strategy, or null if none permissible
 * - oracleAction: best permissible action under trueNetValue, or null if none permissible
 * - selectedTrueValue: trueNetValue(selectedAction) or null/0 if no action
 * - oracleTrueValue: trueNetValue(oracleAction) or null/0 if no oracle
 * - decisionRegret = max(0, oracleTrueValue - selectedTrueValue); 0 if selected == oracle or no oracle;
 *   null values are treated as 0 for regret; regret is never negative.
 */
public record StrategyDecisionQuality(
        RecoveryActionType selectedAction,
        RecoveryActionType oracleAction,
        BigDecimal selectedTrueValue,
        BigDecimal oracleTrueValue,
        BigDecimal decisionRegret
) {
    public StrategyDecisionQuality {
        // Normalize scale and null handling, but keep immutability via compact constructor logic
        // We do not calculate regret here if a dedicated calculator is cleaner; we validate invariants only
        if (selectedTrueValue != null) {
            selectedTrueValue = selectedTrueValue.setScale(4, RoundingMode.HALF_UP);
        }
        if (oracleTrueValue != null) {
            oracleTrueValue = oracleTrueValue.setScale(4, RoundingMode.HALF_UP);
        }
        if (decisionRegret != null) {
            decisionRegret = decisionRegret.setScale(4, RoundingMode.HALF_UP);
            if (decisionRegret.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("decisionRegret must be >= 0");
            }
        }
        // If both actions are non-null and equal, regret must be 0 (or null treated as 0)
        if (selectedAction != null && oracleAction != null && selectedAction == oracleAction) {
            BigDecimal effectiveRegret = decisionRegret != null ? decisionRegret : BigDecimal.ZERO;
            if (effectiveRegret.compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException("regret must be 0 when selected == oracle");
            }
        }
        // Normalize null true values to null (not 0) to preserve explicit no-action semantics
        // Caller may use 0 for aggregation; record keeps null to distinguish no-action
    }

    /**
     * Factory for no-action case: no permissible action, no true values, regret 0.
     */
    public static StrategyDecisionQuality noAction() {
        return new StrategyDecisionQuality(null, null, null, null, BigDecimal.ZERO.setScale(4));
    }

    /**
     * Convenience to compute regret from already-known true values without re-calculating trueNetValue.
     * Used only by evaluator; decision path never calls this.
     */
    public static BigDecimal computeRegret(BigDecimal oracleTrueValue, BigDecimal selectedTrueValue) {
        BigDecimal o = oracleTrueValue != null ? oracleTrueValue : BigDecimal.ZERO;
        BigDecimal s = selectedTrueValue != null ? selectedTrueValue : BigDecimal.ZERO;
        BigDecimal diff = o.subtract(s);
        if (diff.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO.setScale(4);
        return diff.setScale(4, RoundingMode.HALF_UP);
    }
}
