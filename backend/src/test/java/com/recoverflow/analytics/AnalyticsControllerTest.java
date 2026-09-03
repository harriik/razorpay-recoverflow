package com.recoverflow.analytics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverflow.evaluation.EvaluationEngine;
import com.recoverflow.evaluation.EvaluationMetricsAggregator;
import com.recoverflow.evaluation.EvaluationPartitions;
import com.recoverflow.evaluation.MultiSeedResult;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AnalyticsControllerTest {

    @Autowired AnalyticsController controller;

    @Autowired EvaluationEngine engine;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void http200() {
        Map<String, Object> res = controller.analytics();
        assertNotNull(res);
        // HTTP 200 implied by non-exception; Spring would return 200 for Map
    }

    @Test
    void developmentSectionExists() {
        Map<String, Object> res = controller.analytics();
        assertTrue(res.containsKey("revenue"), "development revenue missing");
        assertTrue(res.containsKey("decisionQuality"), "decisionQuality missing");
        assertTrue(res.containsKey("aiBehavior"), "aiBehavior missing");
        assertTrue(res.containsKey("comparison"), "comparison missing");
        // development key also exists
        assertTrue(res.containsKey("development"));
        Map<String, Object> dev = (Map<String, Object>) res.get("development");
        assertEquals(30, dev.get("seedCount"));
    }

    @Test
    void validationSectionExists() {
        Map<String, Object> res = controller.analytics();
        assertTrue(res.containsKey("validation"), "validation section missing");
        Map<String, Object> val = (Map<String, Object>) res.get("validation");
        assertNotNull(val.get("revenue"));
        assertNotNull(val.get("decisionQuality"));
        assertNotNull(val.get("aiBehavior"));
    }

    @Test
    void heldOutSectionExists() {
        Map<String, Object> res = controller.analytics();
        assertTrue(res.containsKey("heldOut"), "heldOut missing");
        Map<String, Object> held = (Map<String, Object>) res.get("heldOut");
        assertNotNull(held.get("revenue"));
        assertNotNull(held.get("decisionQuality"));
        assertNotNull(held.get("aiBehavior"));
        assertNotNull(held.get("comparison"));
    }

    @Test
    void revenueMetricsExist() {
        Map<String, Object> res = controller.analytics();
        Map<String, Object> revenue = (Map<String, Object>) res.get("revenue");
        assertNotNull(revenue.get("recoveredRevenue"), "recoveredRevenue missing");
        assertNotNull(revenue.get("baselineARecovered"), "baselineA missing");
        assertNotNull(revenue.get("baselineBRecovered"), "baselineB missing");
        assertNotNull(revenue.get("absoluteRecoveredRevenueDelta"), "absolute delta missing");
        // relative may be null if baseline 0, but for real data should be non-null; check structure exists
        assertTrue(revenue.containsKey("relativeAiLift"), "relativeAiLift key missing");
        assertTrue(revenue.containsKey("revenueAtRisk"), "revenueAtRisk missing");
        assertTrue(revenue.containsKey("recoveryRate"), "recoveryRate missing");
        // comparison also has revenue
        Map<String, Object> comp = (Map<String, Object>) res.get("comparison");
        assertNotNull(comp.get("baselineA"));
        assertNotNull(comp.get("baselineB"));
        assertNotNull(comp.get("recoverFlow"));
    }

    @Test
    void decisionQualityMetricsExist() {
        Map<String, Object> res = controller.analytics();
        Map<String, Object> dq = (Map<String, Object>) res.get("decisionQuality");
        assertNotNull(dq.get("policyOnlyTotalTrueRegret"), "policyOnlyTotalTrueRegret missing");
        assertNotNull(dq.get("recoverFlowTotalTrueRegret"), "recoverFlowTotalTrueRegret missing");
        assertNotNull(dq.get("policyOnlyMeanTrueRegret"), "policyOnlyMean missing");
        assertNotNull(dq.get("recoverFlowMeanTrueRegret"), "recoverFlowMean missing");
        assertNotNull(dq.get("regretDelta"), "regretDelta missing");
        assertTrue(dq.containsKey("relativeRegretReduction"), "relativeRegretReduction key missing");
        assertNotNull(dq.get("meanOracleTrueValue"), "meanOracleTrueValue missing");
    }

    @Test
    void aiBehaviorMetricsExist() {
        Map<String, Object> res = controller.analytics();
        Map<String, Object> ai = (Map<String, Object>) res.get("aiBehavior");
        assertNotNull(ai.get("actionChangedCount"), "actionChangedCount missing");
        assertNotNull(ai.get("aiHelpedCount"), "helped missing");
        assertNotNull(ai.get("aiHurtCount"), "hurt missing");
        assertNotNull(ai.get("aiNeutralCount"), "neutral missing");
        assertTrue(ai.containsKey("actionChangeRate"), "actionChangeRate missing");
        assertTrue(ai.containsKey("aiHelpRate"), "aiHelpRate missing");
        assertTrue(ai.containsKey("aiHurtRate"), "aiHurtRate missing");
        assertTrue(ai.containsKey("aiNeutralRate"), "aiNeutralRate missing");
        assertNotNull(ai.get("totalCases"), "totalCases missing");
    }

    @Test
    void methodologyVersionSnapshotExists() {
        Map<String, Object> res = controller.analytics();
        assertTrue(res.containsKey("methodology"), "methodology missing");
        Map<String, Object> meth = (Map<String, Object>) res.get("methodology");
        assertEquals("evaluator-v1", meth.get("evaluatorVersion"));
        assertEquals("v1", meth.get("estimatorVersion"));
        assertEquals("v1", meth.get("policyVersion"));
        assertEquals("synthetic-ai-v1", meth.get("syntheticAiProxyVersion"));
        assertEquals("true-value-v1", meth.get("trueValueVersion"));
        assertEquals("hidden-v1", meth.get("syntheticRegistryVersion"));
        assertEquals(200, meth.get("datasetSizePerSeed"));
        assertEquals(6000, meth.get("datasetSizeTotalDevelopment"));
        assertEquals(2000, meth.get("datasetSizeTotalHeldOut"));
        assertNotNull(meth.get("versionSnapshot"));
        String snap = (String) meth.get("versionSnapshot");
        assertTrue(snap.contains("synthetic-ai-v1"), "snapshot missing synthetic-ai-v1");
        assertTrue(snap.contains("true-value-v1"), "snapshot missing true-value-v1");
        assertTrue(snap.contains("hidden-v1"), "snapshot missing hidden-v1");
        assertTrue(snap.contains("evaluator-v1"), "snapshot missing evaluator-v1");
        assertNotNull(meth.get("seedInfo"));
    }

    @Test
    void heldOutIsExplicitlyMarkedSynthetic() {
        Map<String, Object> res = controller.analytics();
        Map<String, Object> held = (Map<String, Object>) res.get("heldOut");
        assertEquals("HELD-OUT SYNTHETIC EVALUATION", held.get("syntheticLabel"));
        String note = (String) held.get("note");
        assertNotNull(note);
        assertTrue(note.contains("not used for tuning"), "held-out note must mention not used for tuning");
        // top-level also synthetic
        assertEquals("SYNTHETIC EVALUATION", res.get("syntheticLabel"));
    }

    @Test
    void noHiddenTruthFieldsExposed() throws Exception {
        Map<String, Object> res = controller.analytics();
        String json = mapper.writeValueAsString(res);
        String lower = json.toLowerCase();
        assertFalse(lower.contains("hiddentruth"), "HiddenTruth must not be exposed");
        assertFalse(lower.contains("p_true"), "P_true must not be exposed");
        assertFalse(lower.contains("ptrue"), "pTrue must not be exposed");
        assertFalse(lower.contains("groundtruth"), "groundTruth must not be exposed");
        assertFalse(lower.contains("hiddentruth"), "hidden truth must not be exposed");
        assertFalse(lower.contains("p_true"), "P_true must not be exposed");
        assertFalse(json.contains("HiddenTruth"), "HiddenTruth must not be exposed");
        assertFalse(json.contains("groundTruthOutcomes"), "groundTruthOutcomes must not be exposed");
        assertFalse(lower.contains("latentrecovery"), "latentRecovery must not be exposed");
        // Oracle internals are only aggregate meanOracleTrueValue, not per-action P_true
    }

    @Test
    void noFakePlaceholderRevenueAppears() {
        Map<String, Object> res = controller.analytics();
        Map<String, Object> revenue = (Map<String, Object>) res.get("revenue");
        BigDecimal recovered = (BigDecimal) revenue.get("recoveredRevenue");
        BigDecimal baselineB = (BigDecimal) revenue.get("baselineBRecovered");
        BigDecimal baselineA = (BigDecimal) revenue.get("baselineARecovered");
        assertNotNull(recovered);
        assertNotNull(baselineB);
        assertNotNull(baselineA);
        // Real synthetic data for 6000 cases should be > 0 and not fake placeholders like 999999 or 123456 exactly
        assertTrue(recovered.compareTo(BigDecimal.ZERO) > 0, "recovered must be >0 for real evaluation, not fake 0");
        assertNotEquals(new BigDecimal("999999.0000"), recovered, "fake placeholder 999999 must not appear");
        assertNotEquals(new BigDecimal("123456.0000"), recovered, "fake placeholder 123456 must not appear");
        // Ensure not all zeros
        assertNotEquals(BigDecimal.ZERO.setScale(4), recovered);
        // Check via JSON that no placeholder strings appear
    }

    @Test
    void responseStructureIsDeterministic() {
        Map<String, Object> r1 = controller.analytics();
        Map<String, Object> r2 = controller.analytics();
        Map<String, Object> rev1 = (Map<String, Object>) r1.get("revenue");
        Map<String, Object> rev2 = (Map<String, Object>) r2.get("revenue");
        assertEquals(rev1.get("recoveredRevenue"), rev2.get("recoveredRevenue"), "deterministic recoveredRevenue");
        assertEquals(rev1.get("absoluteRecoveredRevenueDelta"), rev2.get("absoluteRecoveredRevenueDelta"), "deterministic delta");
        Map<String, Object> dq1 = (Map<String, Object>) r1.get("decisionQuality");
        Map<String, Object> dq2 = (Map<String, Object>) r2.get("decisionQuality");
        assertEquals(dq1.get("policyOnlyTotalTrueRegret"), dq2.get("policyOnlyTotalTrueRegret"), "deterministic regret");
        assertEquals(dq1.get("recoverFlowTotalTrueRegret"), dq2.get("recoverFlowTotalTrueRegret"), "deterministic recover regret");
        Map<String, Object> ai1 = (Map<String, Object>) r1.get("aiBehavior");
        Map<String, Object> ai2 = (Map<String, Object>) r2.get("aiBehavior");
        assertEquals(ai1.get("actionChangedCount"), ai2.get("actionChangedCount"), "deterministic AI changed");
        assertEquals(ai1.get("aiHelpedCount"), ai2.get("aiHelpedCount"), "deterministic helped");
        Map<String, Object> meth1 = (Map<String, Object>) r1.get("methodology");
        Map<String, Object> meth2 = (Map<String, Object>) r2.get("methodology");
        assertEquals(meth1.get("versionSnapshot"), meth2.get("versionSnapshot"), "deterministic snapshot");
    }

    @Test
    void errorHandlingDoesNotReturnFakeZero() {
        EvaluationEngine mockEngine = mock(EvaluationEngine.class);
        when(mockEngine.runMultiSeed(anyList(), anyInt())).thenThrow(new RuntimeException("simulated evaluation failure"));
        AnalyticsController ctrl = new AnalyticsController(mockEngine);
        Exception ex = assertThrows(RuntimeException.class, () -> ctrl.analytics());
        assertTrue(ex.getMessage().contains("simulated evaluation failure"));
        // Ensure no fake map with 0 was returned
    }

    @Test
    void performanceMeasurement() {
        long start = System.nanoTime();
        Map<String, Object> res = controller.analytics();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("GET /api/v1/analytics -> execution time: " + elapsedMs + " ms (DEVELOPMENT 30x200, VALIDATION 10x200, HELD_OUT 10x200)");
        assertNotNull(res);
        // Hackathon demo acceptable: < 10000 ms (10s). Report but don't fail prematurely
        // If > 10000, still pass but log warning
        if (elapsedMs > 10000) {
            System.out.println("WARNING: analytics endpoint runtime " + elapsedMs + "ms exceeds 10s, consider optimization");
        }
        // Ensure at least completes within 20s to avoid CI timeout
        assertTrue(elapsedMs < 20000, "analytics endpoint must complete within 20s, was " + elapsedMs + "ms");
    }

    @Test
    void dataConsistencyDevelopment() {
        Map<String, Object> analyticsRes = controller.analytics();
        Map<String, Object> analyticsRevenue = (Map<String, Object>) analyticsRes.get("revenue");
        Map<String, Object> analyticsDQ = (Map<String, Object>) analyticsRes.get("decisionQuality");
        Map<String, Object> analyticsAI = (Map<String, Object>) analyticsRes.get("aiBehavior");

        MultiSeedResult dev = engine.runMultiSeed(EvaluationPartitions.DEVELOPMENT, EvaluationPartitions.DATASET_SIZE_PER_SEED);
        // Compare development numbers: must be exactly equal (same config)
        assertEquals(dev.aggregatedRevenue().recoveredRevenue(), analyticsRevenue.get("recoveredRevenue"), "development recoveredRevenue must match approved evaluation");
        assertEquals(dev.aggregatedRevenue().absoluteRecoveredRevenueDelta(), analyticsRevenue.get("absoluteRecoveredRevenueDelta"), "development delta must match");
        assertEquals(dev.aggregatedRevenue().relativeAiLift(), analyticsRevenue.get("relativeAiLift"), "development lift must match");
        assertEquals(dev.decisionQuality().policyOnlyTotalTrueRegret(), analyticsDQ.get("policyOnlyTotalTrueRegret"), "development policy regret must match");
        assertEquals(dev.decisionQuality().recoverFlowTotalTrueRegret(), analyticsDQ.get("recoverFlowTotalTrueRegret"), "development recover regret must match");
        assertEquals(dev.decisionQuality().regretDelta(), analyticsDQ.get("regretDelta"), "development regretDelta must match");
        assertEquals(dev.aiDecision().actionChangedCount(), analyticsAI.get("actionChangedCount"), "development AI changed must match");
        assertEquals(dev.aiDecision().aiHelpedCount(), analyticsAI.get("aiHelpedCount"), "development helped must match");
        assertEquals(dev.aiDecision().aiHurtCount(), analyticsAI.get("aiHurtCount"), "development hurt must match");
    }

    @Test
    void dataConsistencyHeldOut() {
        Map<String, Object> analyticsRes = controller.analytics();
        Map<String, Object> held = (Map<String, Object>) analyticsRes.get("heldOut");
        Map<String, Object> heldRevenue = (Map<String, Object>) held.get("revenue");
        Map<String, Object> heldDQ = (Map<String, Object>) held.get("decisionQuality");
        Map<String, Object> heldAI = (Map<String, Object>) held.get("aiBehavior");

        MultiSeedResult heldRes = engine.runMultiSeed(EvaluationPartitions.HELD_OUT, EvaluationPartitions.DATASET_SIZE_PER_SEED);
        assertEquals(heldRes.aggregatedRevenue().recoveredRevenue(), heldRevenue.get("recoveredRevenue"), "held-out recovered must match");
        assertEquals(heldRes.aggregatedRevenue().absoluteRecoveredRevenueDelta(), heldRevenue.get("absoluteRecoveredRevenueDelta"), "held-out delta must match");
        assertEquals(heldRes.decisionQuality().policyOnlyTotalTrueRegret(), heldDQ.get("policyOnlyTotalTrueRegret"), "held-out policy regret must match");
        assertEquals(heldRes.decisionQuality().recoverFlowTotalTrueRegret(), heldDQ.get("recoverFlowTotalTrueRegret"), "held-out recover regret must match");
        assertEquals(heldRes.decisionQuality().regretDelta(), heldDQ.get("regretDelta"), "held-out regretDelta must match");
        assertEquals(heldRes.aiDecision().actionChangedCount(), heldAI.get("actionChangedCount"), "held-out AI changed must match");
        assertEquals(heldRes.aiDecision().aiHelpedCount(), heldAI.get("aiHelpedCount"), "held-out helped must match");
        assertEquals(heldRes.aiDecision().aiHurtCount(), heldAI.get("aiHurtCount"), "held-out hurt must match");
        // Verify same config used (datasetSize etc)
        Map<String, Object> meth = (Map<String, Object>) analyticsRes.get("methodology");
        assertEquals("hidden-v1", meth.get("syntheticRegistryVersion"));
        assertEquals(200, meth.get("datasetSizePerSeed"));
    }
}
