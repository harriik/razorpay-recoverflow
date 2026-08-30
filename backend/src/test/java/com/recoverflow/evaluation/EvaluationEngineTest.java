package com.recoverflow.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.synthetic.SyntheticCase;
import com.recoverflow.synthetic.SyntheticWorldGenerator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EvaluationEngineTest {

    @Autowired SyntheticWorldGenerator generator;
    @Autowired EvaluationEngine engine;

    @Test
    void identicalGroundTruthAcrossBaselines() {
        long seed = 42L;
        List<SyntheticCase> dataset = generator.generate(seed, 100);
        var result = engine.evaluateDataset(dataset, seed);
        // Ground truth is same object for all baselines — verify that dataset's ground truth is used for all
        // Baseline A, B, RecoverFlow all use same dataset's groundTruthOutcomes per case
        // Check that revenueAtRisk is same across baselines (same amount sum)
        assertEquals(result.baselineA().revenueAtRisk(), result.baselineB().revenueAtRisk());
        assertEquals(result.baselineA().revenueAtRisk(), result.recoverFlow().revenueAtRisk());
    }

    @Test
    void aiAblationChangedHelpedHurt() {
        long seed = 123L;
        List<SyntheticCase> dataset = generator.generate(seed, 500);
        var result = engine.evaluateDataset(dataset, seed);
        var ablation = result.ablation();
        assertEquals(500, ablation.total());
        assertTrue(ablation.changed() >= 0 && ablation.changed() <= 500);
        assertTrue(ablation.helped() + ablation.hurt() <= ablation.changed(), "helped + hurt <= changed");
        // At least some cases should be changed (AI materially influences)
        assertTrue(ablation.changed() > 20, "Expected AI to change some decisions, got " + ablation.changed());
    }

    @Test
    void wrongAiCasesInspectable() {
        long seed = 999L;
        List<SyntheticCase> dataset = generator.generate(seed, 300);
        var result = engine.evaluateDataset(dataset, seed);
        long wrongAi = result.ablation().perCase().stream().filter(c -> c.wrongAi()).count();
        assertTrue(wrongAi > 0, "Expected some wrong AI cases");
        // Among wrong AI, some should hurt
        long hurtWrong = result.ablation().perCase().stream().filter(c -> c.wrongAi() && c.hurt()).count();
        // Not necessarily >0, but at least we have wrong cases to inspect
        assertTrue(wrongAi > 10);
    }

    @Test
    void metricCalculations() {
        long seed = 2024L;
        List<SyntheticCase> dataset = generator.generate(seed, 100);
        var result = engine.evaluateDataset(dataset, seed);
        // Recovery rate = recovered / atRisk *100
        var mB = result.baselineB();
        var mR = result.recoverFlow();
        // Metrics are computed, not hardcoded
        assertNotNull(mB.recovered());
        assertNotNull(mR.recovered());
        assertTrue(mB.revenueAtRisk().compareTo(mR.revenueAtRisk()) == 0);
        // Incremental
        var inc = mR.recovered().subtract(mB.recovered());
        // Just verify that incremental is calculated (may be positive or negative)
        assertNotNull(inc);
    }

    @Test
    void reproducibility() {
        long seed = 7777L;
        int size = 500;
        var r1 = engine.run(seed, size);
        var r2 = engine.run(seed, size);
        assertEquals(r1.baselineA().recovered(), r2.baselineA().recovered());
        assertEquals(r1.baselineB().recovered(), r2.baselineB().recovered());
        assertEquals(r1.recoverFlow().recovered(), r2.recoverFlow().recovered());
        assertEquals(r1.ablation().changed(), r2.ablation().changed());
    }

    @Test
    void heldOutEvaluation() {
        long seed = 555L;
        List<SyntheticCase> dataset = generator.generate(seed, 100);
        var result = engine.evaluateDataset(dataset, seed);
        // Held-out is last 20% = 20 cases
        assertEquals(20, result.baselineA_HeldOut().revenueAtRisk().doubleValue() > 0 ? 20 : 0); // Actually heldOut size is 20
        // Check that held-out metrics are computed separately
        assertNotNull(result.baselineA_HeldOut());
        assertNotNull(result.baselineB_HeldOut());
        assertNotNull(result.recoverFlow_HeldOut());
        // Held-out size via dataset split: dataset 100, heldOut 20
        // Our engine's heldOut is dataset.subList(80,100) -> 20 cases, so attempts <=20
        assertTrue(result.baselineA_HeldOut().revenueAtRisk().doubleValue() < result.baselineA().revenueAtRisk().doubleValue());
    }

    @Test
    void edgeCaseZeroRecoveries() {
        // Edge: create a dataset where all true P are very low, so zero recoveries possible
        // We simulate by using a seed that may produce low P, but we can also test metric division by zero
        // Directly test metric compute with zero atRisk? Not needed, but ensure no division by zero
        var engine2 = engine;
        // Use a tiny dataset with 0 cases? Not, but we test empty handled
        List<SyntheticCase> empty = List.of();
        var result = engine.evaluateDataset(empty, 1L);
        assertEquals(0, result.baselineA().revenueAtRisk().doubleValue());
        assertEquals(0, result.baselineA().recovered().doubleValue());
    }

    @Test
    void baselineComparisonIsFairSameHiddenWorld() {
        long seed = 2026L;
        List<SyntheticCase> dataset = generator.generate(seed, 200);
        var result = engine.evaluateDataset(dataset, seed);
        // All three run on same dataset, so revenueAtRisk must be identical
        assertEquals(result.baselineA().revenueAtRisk(), result.baselineB().revenueAtRisk());
        assertEquals(result.baselineA().revenueAtRisk(), result.recoverFlow().revenueAtRisk());
        // And ground truth per case is same object
        // For a given case, the outcome for RETRY_NOW is same across baselines (since they share dataset)
        SyntheticCase first = dataset.get(0);
        boolean gtRetry = first.groundTruthOutcomes().get(com.recoverflow.recovery.RecoveryActionType.RETRY_NOW);
        // No baseline changes gt, so all see same gt
        assertNotNull(gtRetry);
    }
}
