package com.recoverflow.evaluation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Multi-seed aggregation for at least 30 independent seeds.
 * Structure allows increasing to 50+ via configuration only.
 */
public record MultiSeedResult(
        int seedCount,
        List<PerSeedResult> perSeedResults,
        BigDecimal meanRecoverFlowRevenue,
        BigDecimal medianRecoverFlowRevenue,
        BigDecimal meanBaselineBRevenue,
        BigDecimal meanBaselineARevenue,
        BigDecimal meanAiLift,
        BigDecimal medianAiLift,
        BigDecimal stdDevAiLift,
        int winCount,
        int lossCount,
        int tieCount,
        BigDecimal minAiLift,
        BigDecimal maxAiLift,
        EvaluationMetricsAggregator.RevenueMetrics aggregatedRevenue,
        EvaluationMetricsAggregator.DecisionQualityMetrics decisionQuality,
        EvaluationMetricsAggregator.AiDecisionMetrics aiDecision
) {
    public record PerSeedResult(
            long seed,
            BigDecimal baselineBRecovered,
            BigDecimal recoverFlowRecovered,
            BigDecimal aiLift, // (recoverFlow - baselineB)/baselineB, 0 if baselineB==0
            BigDecimal baselineARecovered,
            EvaluationMetricsAggregator.RevenueMetrics revenue,
            EvaluationMetricsAggregator.DecisionQualityMetrics decisionQuality,
            EvaluationMetricsAggregator.AiDecisionMetrics aiDecision
    ) {
        // Backward-compatible constructor for tests that use 4-arg form
        public PerSeedResult(long seed, BigDecimal baselineBRecovered, BigDecimal recoverFlowRecovered, BigDecimal aiLift) {
            this(seed, baselineBRecovered, recoverFlowRecovered, aiLift, null, null, null, null);
        }
    }

    // Backward-compatible constructor for 13-field form (pre-aggregation)
    public MultiSeedResult(int seedCount, List<PerSeedResult> perSeedResults,
                           BigDecimal meanRecoverFlowRevenue, BigDecimal medianRecoverFlowRevenue,
                           BigDecimal meanBaselineBRevenue, BigDecimal meanAiLift, BigDecimal medianAiLift,
                           BigDecimal stdDevAiLift, int winCount, int lossCount, int tieCount,
                           BigDecimal minAiLift, BigDecimal maxAiLift) {
        this(seedCount, perSeedResults, meanRecoverFlowRevenue, medianRecoverFlowRevenue, meanBaselineBRevenue, null,
                meanAiLift, medianAiLift, stdDevAiLift, winCount, lossCount, tieCount, minAiLift, maxAiLift,
                null, null, null);
    }
}
