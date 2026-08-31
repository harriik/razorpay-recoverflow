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
        BigDecimal meanAiLift,
        BigDecimal medianAiLift,
        BigDecimal stdDevAiLift,
        int winCount,
        int lossCount,
        int tieCount,
        BigDecimal minAiLift,
        BigDecimal maxAiLift
) {
    public record PerSeedResult(
            long seed,
            BigDecimal baselineBRecovered,
            BigDecimal recoverFlowRecovered,
            BigDecimal aiLift // (recoverFlow - baselineB)/baselineB, 0 if baselineB==0
    ) {}
}
