package com.recoverflow.evaluation;

import com.recoverflow.policy.PolicyContext;
import com.recoverflow.policy.PolicyDecisionType;
import com.recoverflow.policy.PolicyEngine;
import com.recoverflow.recovery.RecoveryActionType;
import com.recoverflow.synthetic.SyntheticCase;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure evaluator-only oracle selector.
 * Uses the SAME deterministic PolicyEngine rules to determine permissible actions,
 * then selects highest trueNetValue among permissible.
 * Never participates in actual decision; only evaluates after decision.
 */
public class TrueOracleEvaluator {

    private final PolicyEngine policyEngine;
    private final TrueDecisionValueCalculator trueCalculator;

    public TrueOracleEvaluator(PolicyEngine policyEngine, TrueDecisionValueCalculator trueCalculator) {
        this.policyEngine = policyEngine;
        this.trueCalculator = trueCalculator;
    }

    public TrueOracleEvaluator(PolicyEngine policyEngine) {
        this(policyEngine, new TrueDecisionValueCalculator());
    }

    public record OracleResult(
            RecoveryActionType oracleAction,
            BigDecimal oracleTrueValue,
            Map<RecoveryActionType, BigDecimal> perActionTrueValues,
            List<RecoveryActionType> permissibleActions
    ) {}

    /**
     * Compute oracle for a synthetic case. Uses hidden P_true via trueCalculator, but respects same policy constraints.
     * PolicyContext is built from observable data (same as real system) to determine permissibility.
     */
    public OracleResult evaluate(SyntheticCase sc) {
        List<RecoveryActionType> candidates = List.of(
                RecoveryActionType.RETRY_NOW,
                RecoveryActionType.SCHEDULE_RETRY,
                RecoveryActionType.SEND_PAYMENT_LINK,
                RecoveryActionType.SEND_REMINDER
        );

        List<RecoveryActionType> permissible = new ArrayList<>();
        Map<RecoveryActionType, BigDecimal> perActionTrue = new java.util.EnumMap<>(RecoveryActionType.class);

        for (RecoveryActionType action : candidates) {
            PolicyContext ctx = toPolicyContext(sc, action);
            var decision = policyEngine.evaluate(ctx);
            if (decision.result() != PolicyDecisionType.ALLOWED) continue;
            Double pTrue = sc.pTrue().get(action);
            if (pTrue == null) continue;
            BigDecimal tv = trueCalculator.calculate(action, sc.observable().amount(), pTrue);
            perActionTrue.put(action, tv);
            permissible.add(action);
        }

        if (permissible.isEmpty()) {
            return new OracleResult(null, null, perActionTrue, permissible);
        }

        RecoveryActionType bestAction = null;
        BigDecimal bestValue = null;
        for (RecoveryActionType action : permissible) {
            BigDecimal tv = perActionTrue.get(action);
            if (bestValue == null || tv.compareTo(bestValue) > 0) {
                bestValue = tv;
                bestAction = action;
            }
        }
        return new OracleResult(bestAction, bestValue, perActionTrue, permissible);
    }

    private PolicyContext toPolicyContext(SyntheticCase sc, RecoveryActionType action) {
        var obs = sc.observable();
        return new PolicyContext(
                obs.amount(), obs.gatewayCode(), obs.attemptCount(), obs.elapsedHours(),
                false, obs.linkAlreadySent(), action,
                new java.math.BigDecimal("10000.0000"), 3, 48);
    }
}
