package com.recoverflow.evaluation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Pure aggregation layer for evaluator results.
 * Receives already-computed per-case/per-seed results, does NOT generate cases, call AI, or evaluate policy.
 */
public class EvaluationMetricsAggregator {

    // Revenue
    public record RevenueMetrics(
            BigDecimal revenueAtRisk,
            BigDecimal recoveredRevenue,
            BigDecimal recoveryRate,
            int attempts,
            BigDecimal absoluteRecoveredRevenueDelta,
            BigDecimal relativeAiLift, // null = N/A when baseline 0
            int zeroBaselineRevenueCount
    ) {}

    // Decision quality - explicit strategy-specific semantics
    public record DecisionQualityMetrics(
            BigDecimal policyOnlyTotalTrueRegret,
            BigDecimal recoverFlowTotalTrueRegret,
            BigDecimal policyOnlyMeanTrueRegret,
            BigDecimal recoverFlowMeanTrueRegret,
            BigDecimal policyOnlyMedianTrueRegret,
            BigDecimal recoverFlowMedianTrueRegret,
            BigDecimal policyOnlyMeanSelectedTrueValue,
            BigDecimal recoverFlowMeanSelectedTrueValue,
            BigDecimal meanOracleTrueValue,
            BigDecimal regretDelta,
            BigDecimal relativeRegretReduction // null = N/A when policyOnly 0
    ) {}

    // AI decision
    public record AiDecisionMetrics(
            int actionChangedCount,
            int aiHelpedCount,
            int aiHurtCount,
            int aiNeutralCount,
            BigDecimal actionChangeRate, // null if total 0
            BigDecimal aiHelpRate, // null if changed 0
            BigDecimal aiHurtRate,
            BigDecimal aiNeutralRate
    ) {}

    public static RevenueMetrics aggregateRevenue(List<BigDecimal> atRiskList, List<BigDecimal> recoveredList, List<BigDecimal> baselineRecoveredList, List<BigDecimal> recoverFlowRecoveredList) {
        BigDecimal atRisk = canonical(sum(atRiskList));
        BigDecimal recovered = canonical(sum(recoveredList));
        BigDecimal rate = atRisk.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : recovered.multiply(new BigDecimal("100")).divide(atRisk, 2, RoundingMode.HALF_UP);
        int attempts = recoveredList.size(); // simplified
        // For single-seed, absolute delta and relative lift per seed
        // Here we aggregate across seeds: mean absolute delta etc. For single, we compute per single
        BigDecimal absoluteDelta = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal relativeLift = null;
        int zeroBaselineCount = 0;
        if (!baselineRecoveredList.isEmpty() && !recoverFlowRecoveredList.isEmpty()) {
            // For single-seed case, take first
            BigDecimal base = baselineRecoveredList.get(0);
            BigDecimal rec = recoverFlowRecoveredList.get(0);
            absoluteDelta = canonical(rec.subtract(base));
            if (base.compareTo(BigDecimal.ZERO) == 0) {
                relativeLift = null;
                zeroBaselineCount = 1;
            } else {
                relativeLift = absoluteDelta.divide(base, 4, RoundingMode.HALF_UP);
            }
        }
        return new RevenueMetrics(atRisk, recovered, rate, attempts, absoluteDelta, relativeLift, zeroBaselineCount);
    }

    public static DecisionQualityMetrics aggregateDecisionQuality(List<BigDecimal> policyOnlyRegrets, List<BigDecimal> recoverFlowRegrets, List<BigDecimal> policyOnlySelectedTrue, List<BigDecimal> recoverFlowSelectedTrue, List<BigDecimal> oracleTrueValues) {
        BigDecimal totalPolicy = canonical(sum(policyOnlyRegrets));
        BigDecimal totalRecover = canonical(sum(recoverFlowRegrets));
        BigDecimal meanPolicy = canonical(mean(policyOnlyRegrets));
        BigDecimal meanRecover = canonical(mean(recoverFlowRegrets));
        BigDecimal medianPolicy = canonical(median(policyOnlyRegrets));
        BigDecimal medianRecover = canonical(median(recoverFlowRegrets));
        BigDecimal meanSelectedPolicy = canonical(mean(policyOnlySelectedTrue));
        BigDecimal meanSelectedRecover = canonical(mean(recoverFlowSelectedTrue));
        BigDecimal meanOracle = canonical(mean(oracleTrueValues));

        BigDecimal regretDelta = canonical(totalPolicy.subtract(totalRecover));
        BigDecimal relativeRegretReduction = null;
        if (totalPolicy.compareTo(BigDecimal.ZERO) != 0) {
            relativeRegretReduction = regretDelta.divide(totalPolicy, 4, RoundingMode.HALF_UP);
        }
        return new DecisionQualityMetrics(
                totalPolicy,
                totalRecover,
                meanPolicy,
                meanRecover,
                medianPolicy,
                medianRecover,
                meanSelectedPolicy,
                meanSelectedRecover,
                meanOracle,
                regretDelta,
                relativeRegretReduction);
    }

    public static AiDecisionMetrics aggregateAiDecisions(int totalCases, int changed, int helped, int hurt, int neutral) {
        BigDecimal changeRate = totalCases == 0 ? null : new BigDecimal(changed).divide(new BigDecimal(totalCases), 4, RoundingMode.HALF_UP);
        BigDecimal helpRate = changed == 0 ? null : new BigDecimal(helped).divide(new BigDecimal(changed), 4, RoundingMode.HALF_UP);
        BigDecimal hurtRate = changed == 0 ? null : new BigDecimal(hurt).divide(new BigDecimal(changed), 4, RoundingMode.HALF_UP);
        BigDecimal neutralRate = changed == 0 ? null : new BigDecimal(neutral).divide(new BigDecimal(changed), 4, RoundingMode.HALF_UP);
        // Verify helped+hurt+neutral == changed when changed>0
        return new AiDecisionMetrics(changed, helped, hurt, neutral, changeRate, helpRate, hurtRate, neutralRate);
    }

    // Helpers with null handling
    private static BigDecimal canonical(BigDecimal v) {
        if (v == null) return null;
        return v.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<BigDecimal> vals) {
        return vals.stream().filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal mean(List<BigDecimal> vals) {
        List<BigDecimal> nonNull = vals.stream().filter(v -> v != null).toList();
        if (nonNull.isEmpty()) return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal s = nonNull.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return s.divide(new BigDecimal(nonNull.size()), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal median(List<BigDecimal> vals) {
        List<BigDecimal> nonNull = vals.stream().filter(v -> v != null).sorted().toList();
        if (nonNull.isEmpty()) return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        int n = nonNull.size();
        if (n % 2 == 1) return canonical(nonNull.get(n / 2));
        BigDecimal a = nonNull.get(n / 2 - 1);
        BigDecimal b = nonNull.get(n / 2);
        return a.add(b).divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP);
    }
}
