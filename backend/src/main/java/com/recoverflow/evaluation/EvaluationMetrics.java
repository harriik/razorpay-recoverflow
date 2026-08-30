package com.recoverflow.evaluation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Precise metric definitions for RecoverFlow evaluation.
 * All metrics are SYNTHETIC EVALUATION only, not Razorpay production.
 */
public class EvaluationMetrics {

    /**
     * Business metrics:
     * - revenueAtRisk: sum(amount) where original payment FAILED (all cases are failures, so sum of all amounts)
     *   unit: INR, aggregation: sum
     * - recoveredRevenue: sum(recoveredAmount) where actualOutcome==true for selected action
     *   unit: INR
     * - recoveryRate: recoveredRevenue / revenueAtRisk * 100, unit: %, denominator revenueAtRisk
     * - attempts: count of execution attempts (one per case where selected action != null)
     * - successfulRecoveries: count where recovered
     * - escalations: count where overallPolicy==ESCALATE or no allowed
     * - stopped: count where STOP
     * - failedTerminal: count where FAILED_TERMINAL
     * - averageRecoveryTime: not tracked in synthetic (would be synthetic), set 0 for now
     * - incrementalRecoveredRevenue: recovered(RecoverFlow) - recovered(baseline)
     *
     * Efficiency:
     * - syntheticCustomerFrictionProxy: sum(frictionProxy) for selected actions, unit: INR proxy
     * - frictionPerRecoveredRupee: friction / recoveredRevenue (0 if no recovery)
     * - unnecessaryAttempts: count where baseline attempted but RecoverFlow would have stopped/escalated
     *
     * AI:
     * - actionChangeRate: fraction where AI_ENABLED selected != POLICY_ONLY selected
     * - AIHelpRate: fraction where AI changed and recovered while policy-only would not have
     * - AIHurtRate: fraction where AI changed and policy-only would have recovered but AI did not
     *
     * Safety:
     * - policyBlocks: count where policy blocked highest EV
     * - duplicateActionsPrevented, unsafeActionsPrevented: 0 in synthetic (would be execution layer)
     * - unknownCases: 0 (synthetic has no timeout simulation)
     */
    public record Metrics(
            // Business
            BigDecimal revenueAtRisk,
            BigDecimal recoveredRevenue,
            BigDecimal recoveryRate,
            int attempts,
            int successfulRecoveries,
            int escalations,
            int stopped,
            int failedTerminal,
            BigDecimal incrementalVsBaselineA,
            BigDecimal incrementalVsBaselineB,
            // Efficiency
            BigDecimal syntheticFrictionProxy,
            BigDecimal frictionPerRecoveredRupee,
            int unnecessaryAttempts,
            // AI
            double actionChangeRate,
            double aiHelpRate,
            double aiHurtRate,
            // Safety
            int policyBlocks
    ) {}

    public static Metrics compute(
            BigDecimal revenueAtRisk,
            BigDecimal recovered,
            int attempts, int successes, int escalations, int stopped, int failedTerminal,
            BigDecimal friction,
            BigDecimal baselineARecovered, BigDecimal baselineBRecovered,
            int policyBlocks,
            int changed, int helped, int hurt, int totalCases) {

        BigDecimal rate = revenueAtRisk.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : recovered.multiply(new BigDecimal("100")).divide(revenueAtRisk, 2, RoundingMode.HALF_UP);
        BigDecimal incA = recovered.subtract(baselineARecovered);
        BigDecimal incB = recovered.subtract(baselineBRecovered);
        BigDecimal frictionPer = recovered.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : friction.divide(recovered, 4, RoundingMode.HALF_UP);

        double changeRate = totalCases == 0 ? 0 : (double) changed / totalCases;
        double helpRate = changed == 0 ? 0 : (double) helped / changed;
        double hurtRate = changed == 0 ? 0 : (double) hurt / changed;

        return new Metrics(
                revenueAtRisk, recovered, rate, attempts, successes, escalations, stopped, failedTerminal,
                incA, incB, friction, frictionPer, 0,
                changeRate, helpRate, hurtRate, policyBlocks);
    }
}
