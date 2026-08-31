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
    private final com.recoverflow.policy.PolicyConfig policyConfig;

    public TrueOracleEvaluator(PolicyEngine policyEngine, TrueDecisionValueCalculator trueCalculator) {
        this(policyEngine, trueCalculator, new com.recoverflow.policy.PolicyConfig());
    }

    public TrueOracleEvaluator(PolicyEngine policyEngine, TrueDecisionValueCalculator trueCalculator, com.recoverflow.policy.PolicyConfig policyConfig) {
        this.policyEngine = policyEngine;
        this.trueCalculator = trueCalculator;
        this.policyConfig = policyConfig;
    }

    public TrueOracleEvaluator(PolicyEngine policyEngine) {
        this(policyEngine, new TrueDecisionValueCalculator(), new com.recoverflow.policy.PolicyConfig());
    }

    public record OracleResult(
            RecoveryActionType oracleAction,
            BigDecimal oracleTrueValue,
            Map<RecoveryActionType, BigDecimal> perActionTrueValues,
            List<RecoveryActionType> permissibleActions
    ) {}

    /**
     * Compute oracle for a synthetic case using evaluation-wide policy configuration.
     * This is the default for synthetic evaluation where all merchants share 10000/3/48.
     * Explicitly documented as evaluation-wide, used for Baseline A/B, RecoverFlow, and Oracle.
     */
    public OracleResult evaluate(SyntheticCase sc) {
        // Evaluation-wide policy configuration: 10000 / 3 / 48 are evaluation parameters, not production merchant-specific
        PolicyContext base = new PolicyContext(
                sc.observable().amount(), sc.observable().gatewayCode(), sc.observable().attemptCount(), sc.observable().elapsedHours(),
                false, sc.observable().linkAlreadySent(), RecoveryActionType.RETRY_NOW,
                policyConfig.getAutoActionLimit(), policyConfig.getMaxRetries(), policyConfig.getRecoveryWindowHours());
        return evaluate(sc, base);
    }

    /**
     * Compute oracle using the SAME per-case policy snapshot as the practical decision.
     * Preferred when SyntheticCase carries a merchant-specific policy configuration.
     * Ensures oracle and practical decision use identical thresholds.
     */
    public OracleResult evaluate(SyntheticCase sc, PolicyContext basePolicyContext) {
        List<RecoveryActionType> candidates = List.of(
                RecoveryActionType.RETRY_NOW,
                RecoveryActionType.SCHEDULE_RETRY,
                RecoveryActionType.SEND_PAYMENT_LINK,
                RecoveryActionType.SEND_REMINDER
        );

        List<RecoveryActionType> permissible = new ArrayList<>();
        Map<RecoveryActionType, BigDecimal> perActionTrue = new java.util.EnumMap<>(RecoveryActionType.class);

        for (RecoveryActionType action : candidates) {
            PolicyContext ctx = new PolicyContext(
                    sc.observable().amount(), sc.observable().gatewayCode(), sc.observable().attemptCount(), sc.observable().elapsedHours(),
                    basePolicyContext.optedOut(), sc.observable().linkAlreadySent(), action,
                    basePolicyContext.autoActionLimit(), basePolicyContext.maxRetries(), basePolicyContext.recoveryWindowHours());
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
                policyConfig.getAutoActionLimit(), policyConfig.getMaxRetries(), policyConfig.getRecoveryWindowHours());
    }
}
