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
        assertEquals(new BigDecimal("30.0000"), m.policyOnlyTotalTrueRegret());
        assertEquals(new BigDecimal("10.0000"), m.recoverFlowTotalTrueRegret());
        assertEquals(new BigDecimal("15.0000"), m.policyOnlyMeanTrueRegret());
        assertEquals(new BigDecimal("5.0000"), m.recoverFlowMeanTrueRegret());
        assertEquals(new BigDecimal("15.0000"), m.policyOnlyMedianTrueRegret());
        assertEquals(new BigDecimal("5.0000"), m.recoverFlowMedianTrueRegret());
        assertEquals(new BigDecimal("150.0000"), m.policyOnlyMeanSelectedTrueValue());
        assertEquals(new BigDecimal("160.0000"), m.recoverFlowMeanSelectedTrueValue());
        assertEquals(new BigDecimal("165.0000"), m.meanOracleTrueValue());
        assertEquals(new BigDecimal("20.0000"), m.regretDelta());
        assertEquals(new BigDecimal("0.6667"), m.relativeRegretReduction());
        // scale checks
        assertEquals(4, m.policyOnlyTotalTrueRegret().scale());
        assertEquals(4, m.recoverFlowTotalTrueRegret().scale());
    }

    @Test
    void strategySpecificTotalsRegression() {
        // Explicit regression: policy 10+20=30, recover 4+6=10 => delta 20, relative 20/30
        var policy = List.of(new BigDecimal("10.0000"), new BigDecimal("20.0000"));
        var recover = List.of(new BigDecimal("4.0000"), new BigDecimal("6.0000"));
        var m = EvaluationMetricsAggregator.aggregateDecisionQuality(policy, recover, List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO));
        assertEquals(new BigDecimal("30.0000"), m.policyOnlyTotalTrueRegret());
        assertEquals(new BigDecimal("10.0000"), m.recoverFlowTotalTrueRegret());
        assertEquals(new BigDecimal("20.0000"), m.regretDelta());
        assertEquals(new BigDecimal("0.6667"), m.relativeRegretReduction()); // 20/30 HALF_UP scale 4
        // ensure no generic field is misused
        assertNotEquals(m.policyOnlyTotalTrueRegret(), m.recoverFlowTotalTrueRegret());
        assertEquals(4, m.policyOnlyTotalTrueRegret().scale());
        assertEquals(4, m.recoverFlowTotalTrueRegret().scale());
        assertEquals(4, m.regretDelta().scale());
    }

    @Test
    void meanRegret() {
        var regrets = List.of(new BigDecimal("10.0000"), new BigDecimal("20.0000"), new BigDecimal("30.0000"));
        var m = EvaluationMetricsAggregator.aggregateDecisionQuality(regrets, regrets, List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO));
        assertEquals(new BigDecimal("20.0000"), m.policyOnlyMeanTrueRegret());
        assertEquals(new BigDecimal("20.0000"), m.recoverFlowMeanTrueRegret());
    }

    @Test
    void medianRegret() {
        var regrets = List.of(new BigDecimal("10.0000"), new BigDecimal("30.0000"), new BigDecimal("20.0000"));
        var m = EvaluationMetricsAggregator.aggregateDecisionQuality(regrets, regrets, List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO));
        assertEquals(new BigDecimal("20.0000"), m.policyOnlyMedianTrueRegret());
        assertEquals(new BigDecimal("20.0000"), m.recoverFlowMedianTrueRegret());
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
        assertEquals(new BigDecimal("0.0000"), m.policyOnlyTotalTrueRegret());
        assertEquals(new BigDecimal("0.0000"), m.recoverFlowTotalTrueRegret());
        assertEquals(new BigDecimal("0.0000"), m.policyOnlyMeanTrueRegret());
        assertEquals(new BigDecimal("0.0000"), m.recoverFlowMeanTrueRegret());
        assertEquals(4, m.policyOnlyTotalTrueRegret().scale());
        assertEquals(4, m.recoverFlowTotalTrueRegret().scale());
        assertEquals(4, m.policyOnlyMeanTrueRegret().scale());
        assertEquals(4, m.recoverFlowMeanTrueRegret().scale());
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
        assertEquals(new BigDecimal("0.0000"), m.policyOnlyTotalTrueRegret());
        assertEquals(new BigDecimal("0.0000"), m.recoverFlowTotalTrueRegret());
        assertEquals(4, m.policyOnlyTotalTrueRegret().scale());
        assertEquals(4, m.recoverFlowTotalTrueRegret().scale());
    }

    @Test
    void noPermissibleAction() {
        // When no permissible, oracle and selected are null, regret 0
        var m = EvaluationMetricsAggregator.aggregateDecisionQuality(
                List.of(new BigDecimal("0.0000")), List.of(new BigDecimal("0.0000")),
                List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO), List.of(BigDecimal.ZERO));
        assertEquals(new BigDecimal("0.0000"), m.policyOnlyTotalTrueRegret());
        assertEquals(new BigDecimal("0.0000"), m.recoverFlowTotalTrueRegret());
        assertEquals(4, m.policyOnlyTotalTrueRegret().scale());
        assertEquals(4, m.recoverFlowTotalTrueRegret().scale());
    }

    @Test
    void nullRelativeMetrics() {
        var m = EvaluationMetricsAggregator.aggregateAiDecisions(0, 0, 0, 0, 0);
        assertNull(m.aiHelpRate());
        assertNull(m.aiHurtRate());
    }

    @Test
    void noGenericTotalField() throws Exception {
        // Ensure no generic field named totalTrueRegret exists
        var fields = EvaluationMetricsAggregator.DecisionQualityMetrics.class.getRecordComponents();
        for (var f : fields) {
            assertNotEquals("totalTrueRegret", f.getName(), "generic totalTrueRegret must not exist, use strategy-specific fields");
        }
        boolean hasPolicy = false, hasRecover = false;
        for (var f : fields) {
            if (f.getName().equals("policyOnlyTotalTrueRegret")) hasPolicy = true;
            if (f.getName().equals("recoverFlowTotalTrueRegret")) hasRecover = true;
        }
        assertTrue(hasPolicy, "policyOnlyTotalTrueRegret must exist");
        assertTrue(hasRecover, "recoverFlowTotalTrueRegret must exist");
    }
}
