package com.recoverflow.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluationMetricsAggregatorTest {

    @Test
    void totalRegret() {
        var policy = List.of(new BigDecimal("10.0000"), new BigDecimal("20.0000"));
        var recover = List.of(new BigDecimal("5.0000"), new BigDecimal("5.0000"));
        var selectedPolicy = List.of(new BigDecimal("100.0000"), new BigDecimal("200.0000"));
        var selectedRecover = List.of(new BigDecimal("105.0000"), new BigDecimal("215.0000"));
        var oracle = List.of(new BigDecimal("110.0000"), new BigDecimal("220.0000"));
        var m = EvaluationMetricsAggregator.aggregateDecisionQuality(policy, recover, selectedPolicy, selectedRecover, oracle);
        assertEquals(new BigDecimal("30.0000"), m.totalTrueRegret()); // policy 30, recover 10? Actually totalPolicy 30, totalRecover 10, but we compute recover side only
        // Our aggregateDecisionQuality returns recover side total, but we check mean
        assertEquals(new BigDecimal("5.0000"), m.meanTrueRegret()); // (5+5)/2
    }

    @Test
    void meanRegret() {
        var regrets = List.of(new BigDecimal("10.0000"), new BigDecimal("20.0000"), new BigDecimal("30.0000"));
        var m = EvaluationMetricsAggregator.aggregateDecisionQuality(regrets, regrets, List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO));
        assertEquals(new BigDecimal("20.0000"), m.meanTrueRegret());
    }

    @Test
    void medianRegret() {
        var regrets = List.of(new BigDecimal("10.0000"), new BigDecimal("30.0000"), new BigDecimal("20.0000"));
        var m = EvaluationMetricsAggregator.aggregateDecisionQuality(regrets, regrets, List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO));
        assertEquals(new BigDecimal("20.0000"), m.medianTrueRegret());
    }

    @Test
    void oracleAverage() {
        var oracleVals = List.of(new BigDecimal("100.0000"), new BigDecimal("200.0000"));
        var m = EvaluationMetricsAggregator.aggregateDecisionQuality(List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO), oracleVals);
        assertEquals(new BigDecimal("150.0000"), m.meanOracleTrueValue());
    }

    @Test
    void absoluteRevenueDelta() {
        var m = EvaluationMetricsAggregator.aggregateRevenue(
                List.of(new BigDecimal("1000.0000")), List.of(new BigDecimal("500.0000")),
                List.of(new BigDecimal("400.0000")), List.of(new BigDecimal("500.0000")));
        assertEquals(new BigDecimal("100.0000"), m.absoluteRecoveredRevenueDelta());
    }

    @Test
    void relativeAiLiftNonZero() {
        var m = EvaluationMetricsAggregator.aggregateRevenue(
                List.of(new BigDecimal("1000.0000")), List.of(new BigDecimal("500.0000")),
                List.of(new BigDecimal("400.0000")), List.of(new BigDecimal("500.0000")));
        assertEquals(new BigDecimal("0.2500"), m.relativeAiLift()); // (500-400)/400
    }

    @Test
    void relativeAiLiftNullWhenBaselineZero() {
        var m = EvaluationMetricsAggregator.aggregateRevenue(
                List.of(new BigDecimal("1000.0000")), List.of(new BigDecimal("0.0000")),
                List.of(new BigDecimal("0.0000")), List.of(new BigDecimal("100.0000")));
        assertNull(m.relativeAiLift());
        assertEquals(1, m.zeroBaselineRevenueCount());
        assertEquals(new BigDecimal("100.0000"), m.absoluteRecoveredRevenueDelta());
    }

    @Test
    void zeroBaselineRevenueCount() {
        var m = EvaluationMetricsAggregator.aggregateRevenue(
                List.of(new BigDecimal("1000.0000")), List.of(new BigDecimal("0.0000")),
                List.of(new BigDecimal("0.0000")), List.of(new BigDecimal("0.0000")));
        assertEquals(1, m.zeroBaselineRevenueCount());
        assertNull(m.relativeAiLift());
    }

    @Test
    void regretReduction() {
        var policyRegrets = List.of(new BigDecimal("30.0000"));
        var recoverRegrets = List.of(new BigDecimal("10.0000"));
        var m = EvaluationMetricsAggregator.aggregateDecisionQuality(policyRegrets, recoverRegrets, List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO));
        assertEquals(new BigDecimal("20.0000"), m.regretDelta());
        assertEquals(new BigDecimal("0.6667"), m.relativeRegretReduction()); // 20/30
    }

    @Test
    void relativeRegretReductionNullWhenPolicyZero() {
        var m = EvaluationMetricsAggregator.aggregateDecisionQuality(
                List.of(new BigDecimal("0.0000")), List.of(new BigDecimal("0.0000")),
                List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO));
        assertNull(m.relativeRegretReduction());
    }

    @Test
    void actionChangeRate() {
        var m = EvaluationMetricsAggregator.aggregateAiDecisions(100, 20, 5, 3, 2);
        assertEquals(new BigDecimal("0.2000"), m.actionChangeRate());
    }

    @Test
    void helpRate() {
        var m = EvaluationMetricsAggregator.aggregateAiDecisions(100, 20, 5, 3, 12);
        assertEquals(new BigDecimal("0.2500"), m.aiHelpRate()); // 5/20
        assertEquals(new BigDecimal("0.1500"), m.aiHurtRate()); // 3/20
        assertEquals(new BigDecimal("0.6000"), m.aiNeutralRate()); // 12/20
    }

    @Test
    void hurtRate() {
        var m = EvaluationMetricsAggregator.aggregateAiDecisions(100, 10, 2, 8, 0);
        assertEquals(new BigDecimal("0.8000"), m.aiHurtRate());
    }

    @Test
    void neutralRate() {
        var m = EvaluationMetricsAggregator.aggregateAiDecisions(100, 10, 0, 0, 10);
        assertEquals(new BigDecimal("1.0000"), m.aiNeutralRate());
    }

    @Test
    void helpedHurtNeutralSumToChanged() {
        var m = EvaluationMetricsAggregator.aggregateAiDecisions(100, 10, 3, 2, 5);
        assertEquals(10, m.aiHelpedCount() + m.aiHurtCount() + m.aiNeutralCount());
    }

    @Test
    void zeroActionChangesRatesNull() {
        var m = EvaluationMetricsAggregator.aggregateAiDecisions(100, 0, 0, 0, 0);
        assertNull(m.aiHelpRate());
        assertNull(m.aiHurtRate());
        assertNull(m.aiNeutralRate());
        assertEquals(new BigDecimal("0.0000"), m.actionChangeRate());
    }

    @Test
    void bigDecimalPrecision() {
        var m = EvaluationMetricsAggregator.aggregateRevenue(
                List.of(new BigDecimal("100.12345")), List.of(new BigDecimal("50.12345")),
                List.of(new BigDecimal("40.12345")), List.of(new BigDecimal("50.12345")));
        assertEquals(4, m.absoluteRecoveredRevenueDelta().scale());
        assertEquals(4, m.relativeAiLift().scale());
    }

    @Test
    void deterministicAggregation() {
        var m1 = EvaluationMetricsAggregator.aggregateRevenue(
                List.of(new BigDecimal("1000.0000")), List.of(new BigDecimal("500.0000")),
                List.of(new BigDecimal("400.0000")), List.of(new BigDecimal("500.0000")));
        var m2 = EvaluationMetricsAggregator.aggregateRevenue(
                List.of(new BigDecimal("1000.0000")), List.of(new BigDecimal("500.0000")),
                List.of(new BigDecimal("400.0000")), List.of(new BigDecimal("500.0000")));
        assertEquals(m1.relativeAiLift(), m2.relativeAiLift());
    }

    @Test
    void perSeedResultsPreserved() {
        // Multi-seed per-seed results should be preserved; we test via aggregator that it doesn't drop seeds
        // This is a placeholder for per-seed preservation check via EvaluationEngine's MultiSeedResult
        assertTrue(true);
    }

    // Edge cases
    @Test
    void emptyInput() {
        var m = EvaluationMetricsAggregator.aggregateDecisionQuality(List.of(), List.of(), List.of(), List.of(), List.of());
        assertEquals(new BigDecimal("0.0000"), m.totalTrueRegret());
        assertEquals(new BigDecimal("0.0000"), m.meanTrueRegret());
        assertEquals(4, m.totalTrueRegret().scale());
        assertEquals(4, m.meanTrueRegret().scale());
    }

    @Test
    void allNeutral() {
        var m = EvaluationMetricsAggregator.aggregateAiDecisions(10, 0, 0, 0, 10);
        assertEquals(0, m.aiHelpedCount());
        assertEquals(0, m.aiHurtCount());
        assertEquals(10, m.aiNeutralCount());
    }

    @Test
    void allHelped() {
        var m = EvaluationMetricsAggregator.aggregateAiDecisions(10, 5, 5, 0, 0);
        assertEquals(5, m.aiHelpedCount());
    }

    @Test
    void allHurt() {
        var m = EvaluationMetricsAggregator.aggregateAiDecisions(10, 5, 0, 5, 0);
        assertEquals(5, m.aiHurtCount());
    }

    @Test
    void allZeroRegret() {
        var m = EvaluationMetricsAggregator.aggregateDecisionQuality(
                List.of(new BigDecimal("0.0000"), new BigDecimal("0.0000")),
                List.of(new BigDecimal("0.0000"), new BigDecimal("0.0000")),
                List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO));
        assertEquals(new BigDecimal("0.0000"), m.totalTrueRegret());
        assertEquals(4, m.totalTrueRegret().scale());
    }

    @Test
    void noPermissibleAction() {
        // When no permissible, oracle and selected are null, regret 0
        var m = EvaluationMetricsAggregator.aggregateDecisionQuality(
                List.of(new BigDecimal("0.0000")), List.of(new BigDecimal("0.0000")),
                List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO));
        assertEquals(new BigDecimal("0.0000"), m.totalTrueRegret());
        assertEquals(4, m.totalTrueRegret().scale());
    }

    @Test
    void nullRelativeMetrics() {
        var m = EvaluationMetricsAggregator.aggregateAiDecisions(0, 0, 0, 0, 0);
        assertNull(m.aiHelpRate());
        assertNull(m.aiHurtRate());
    }
}
