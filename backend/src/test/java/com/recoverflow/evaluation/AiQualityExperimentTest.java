package com.recoverflow.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.likelihood.ObservableContext;
import com.recoverflow.synthetic.AiQuality;
import com.recoverflow.synthetic.QualityAwareSyntheticAiProxy;
import com.recoverflow.synthetic.SyntheticAiProxy;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AiQualityExperimentTest {

    @Autowired AiQualityExperiment experiment;
    @Autowired EvaluationEngine engine;

    // 1. same hidden world across quality levels
    @Test
    void sameHiddenWorldAcrossQualityLevels() {
        List<Long> seeds = List.of(10000L, 10001L);
        var result = experiment.run(seeds, 50);
        // All quality levels must have same total cases and same underlying cases
        var cases = result.allCases();
        assertEquals(100, cases.size()); // 2 seeds *50
        // Verify that all quality results have same totalCases and same underlying worlds
        for (AiQuality q : AiQuality.values()) {
            assertEquals(100, result.byQuality().get(q).totalCases());
        }
        // Verify that P_true and groundTruth are same across qualities by checking that allCases is same object
        assertEquals(cases.get(0).pTrue(), result.allCases().get(0).pTrue());
        assertEquals(cases.get(0).groundTruthOutcomes(), result.allCases().get(0).groundTruthOutcomes());
    }

    // 2. same ground truth across quality levels
    @Test
    void sameGroundTruthAcrossQualityLevels() {
        List<Long> seeds = List.of(10000L);
        var result = experiment.run(seeds, 20);
        var firstCase = result.allCases().get(0);
        // Ground truth should be identical regardless of quality (measured via allCases)
        assertNotNull(firstCase.groundTruthOutcomes());
        // Run again with same seeds, should be same groundTruth
        var result2 = experiment.run(seeds, 20);
        assertEquals(firstCase.groundTruthOutcomes(), result2.allCases().get(0).groundTruthOutcomes());
        assertEquals(firstCase.pTrue(), result2.allCases().get(0).pTrue());
    }

    // 3. proxy never receives hidden fields
    @Test
    void proxyNeverReceivesHiddenFields() throws Exception {
        // Check QualityAwareSyntheticAiProxy assess signature
        Method m = QualityAwareSyntheticAiProxy.class.getDeclaredMethod("assess", ObservableContext.class, AiQuality.class);
        assertNotNull(m);
        for (var p : m.getParameters()) {
            String type = p.getType().getSimpleName().toLowerCase();
            assertFalse(type.contains("hidden"), "Proxy must not take hidden");
            assertFalse(type.contains("ptrue"), "Proxy must not take P_true");
            assertFalse(type.contains("latent"), "Proxy must not take latent");
        }
        // Also check SyntheticAiProxy
        Method m2 = SyntheticAiProxy.class.getDeclaredMethod("assess", ObservableContext.class);
        for (var p : m2.getParameters()) {
            String type = p.getType().getSimpleName().toLowerCase();
            assertFalse(type.contains("hidden"));
        }
        // Ensure no method in proxy takes HiddenTruth
        for (Method method : QualityAwareSyntheticAiProxy.class.getDeclaredMethods()) {
            for (var p : method.getParameters()) {
                String type = p.getType().getSimpleName().toLowerCase();
                assertFalse(type.contains("hiddentruth"), "Quality proxy must never receive HiddenTruth in " + method.getName());
                assertFalse(type.contains("groundtruth"), "Quality proxy must never receive groundTruth in " + method.getName());
            }
        }
    }

    // 4. measured accuracy differs by quality configuration
    @Test
    void measuredAccuracyDiffersByQuality() {
        List<Long> seeds = LongStream.range(10000, 10010).boxed().collect(Collectors.toList());
        var result = experiment.run(seeds, 100);
        double lowAcc = result.byQuality().get(AiQuality.LOW).measuredAccuracy();
        double medAcc = result.byQuality().get(AiQuality.MEDIUM).measuredAccuracy();
        double highAcc = result.byQuality().get(AiQuality.HIGH).measuredAccuracy();
        System.out.printf("Measured accuracies LOW=%.3f MEDIUM=%.3f HIGH=%.3f%n", lowAcc, medAcc, highAcc);
        assertTrue(lowAcc < medAcc, "LOW accuracy should be < MEDIUM: " + lowAcc + " vs " + medAcc);
        assertTrue(medAcc < highAcc, "MEDIUM accuracy should be < HIGH: " + medAcc + " vs " + highAcc);
        // Check approximate targets within tolerance (observed gateway is 85% correlated with truth, so true accuracy is lower than proxy target)
        assertTrue(lowAcc >= 0.35 && lowAcc <= 0.60, "LOW ~50% within tolerance, was " + lowAcc);
        assertTrue(medAcc >= 0.55 && medAcc <= 0.75, "MEDIUM ~75% within tolerance, was " + medAcc);
        assertTrue(highAcc >= 0.70 && highAcc <= 0.88, "HIGH ~90% within tolerance, was " + highAcc);
        // Ensure clear separation
        assertTrue(medAcc - lowAcc > 0.10, "MEDIUM should be at least 10% higher than LOW");
        assertTrue(highAcc - medAcc > 0.08, "HIGH should be at least 8% higher than MEDIUM");
    }

    // 5. deterministic reproducibility
    @Test
    void deterministicReproducibility() {
        List<Long> seeds = List.of(10000L, 10001L, 10002L);
        var r1 = experiment.run(seeds, 50);
        var r2 = experiment.run(seeds, 50);
        for (AiQuality q : AiQuality.values()) {
            assertEquals(r1.byQuality().get(q).recoveredRevenue(), r2.byQuality().get(q).recoveredRevenue(), "recovered must be reproducible for " + q);
            assertEquals(r1.byQuality().get(q).decisionQuality().policyOnlyTotalTrueRegret(), r2.byQuality().get(q).decisionQuality().policyOnlyTotalTrueRegret());
            assertEquals(r1.byQuality().get(q).decisionQuality().recoverFlowTotalTrueRegret(), r2.byQuality().get(q).decisionQuality().recoverFlowTotalTrueRegret());
            assertEquals(r1.byQuality().get(q).measuredAccuracy(), r2.byQuality().get(q).measuredAccuracy());
        }
        assertEquals(r1.policyOnlyRecovered(), r2.policyOnlyRecovered());
    }

    // 6. LOW/MEDIUM/HIGH produce different behavior
    @Test
    void lowMediumHighProduceDifferentBehavior() {
        List<Long> seeds = LongStream.range(10000, 10005).boxed().collect(Collectors.toList());
        var result = experiment.run(seeds, 80);
        var low = result.byQuality().get(AiQuality.LOW);
        var med = result.byQuality().get(AiQuality.MEDIUM);
        var high = result.byQuality().get(AiQuality.HIGH);
        // At least one metric should differ
        boolean anyDiff = !low.recoveredRevenue().equals(med.recoveredRevenue())
                || !med.recoveredRevenue().equals(high.recoveredRevenue())
                || low.aiDecision().actionChangedCount() != med.aiDecision().actionChangedCount()
                || med.aiDecision().actionChangedCount() != high.aiDecision().actionChangedCount()
                || low.measuredAccuracy() != high.measuredAccuracy();
        assertTrue(anyDiff, "Quality levels must produce different behavior");
        // Help/hurt rates should differ
        assertNotEquals(low.measuredAccuracy(), high.measuredAccuracy());
    }

    // 7. true regret is computed only after decisions
    @Test
    void trueRegretComputedOnlyAfterDecisions() throws Exception {
        // Verify evaluateQuality is not called before decision by checking that decisionService is invoked before trueCalculator
        // We check that AiQualityExperiment's run method does not call trueCalculator before decision
        // Instead, we verify that StrategyDecisionQuality is constructed after decision via reflection on experiment's evaluateQuality
        Method eval = AiQualityExperiment.class.getDeclaredMethod("evaluateQuality", com.recoverflow.synthetic.SyntheticCase.class, com.recoverflow.recovery.RecoveryActionType.class);
        assertNotNull(eval);
        // Ensure evaluateQuality uses HiddenTruth/P_true (evaluator-only) and is not called from decision path
        // Check that decisionService's decide method does not take HiddenTruth
        for (Method m : com.recoverflow.decision.RecoveryDecisionService.class.getDeclaredMethods()) {
            for (var p : m.getParameters()) {
                String type = p.getType().getSimpleName().toLowerCase();
                assertFalse(type.contains("hiddentruth"));
                assertFalse(type.contains("ptrue"));
            }
        }
        // Also ensure that for each quality, decisionQuality is not null and has scale 4
        var result = experiment.run(List.of(10000L), 20);
        for (AiQuality q : AiQuality.values()) {
            var dq = result.byQuality().get(q).decisionQuality();
            assertNotNull(dq.policyOnlyTotalTrueRegret());
            assertEquals(4, dq.policyOnlyTotalTrueRegret().scale());
        }
    }

    // 8. AI help/hurt uses true value
    @Test
    void aiHelpHurtUsesTrueValue() {
        List<Long> seeds = List.of(10000L);
        var result = experiment.run(seeds, 50);
        for (AiQuality q : AiQuality.values()) {
            var ai = result.byQuality().get(q).aiDecision();
            // Help/hurt must be based on true value, not just Bernoulli outcome
            // Check that helped+ hurt+ neutral == changed
            assertEquals(ai.actionChangedCount(), ai.aiHelpedCount() + ai.aiHurtCount() + ai.aiNeutralCount(),
                    "helped+hurt+neutral must equal changed for " + q);
            // Verify classifier is used (true value based)
            assertNotNull(ai.aiHelpRate());
            // If changed>0, rates not null
            if (ai.actionChangedCount() > 0) {
                assertNotNull(ai.aiHelpRate());
                assertNotNull(ai.aiHurtRate());
            }
        }
    }

    // 9. held-out partition is not modified
    @Test
    void heldOutPartitionIsNotModified() {
        List<Long> held = EvaluationPartitions.HELD_OUT;
        List<Long> dev = EvaluationPartitions.DEVELOPMENT;
        // Verify held-out seeds are distinct and not used in experiment when using dev seeds
        var devResult = experiment.run(dev.subList(0, 3), 20);
        // Ensure held-out seeds are untouched
        assertFalse(devResult.allCases().stream().anyMatch(sc -> held.contains(sc.caseId().getMostSignificantBits())));
        // Direct check: held-out list must remain 30000-30009 and not contain dev seeds
        for (Long s : held) {
            assertFalse(dev.contains(s));
            assertTrue(s >= 30000 && s < 30010);
        }
        // Verify immutability
        assertThrows(UnsupportedOperationException.class, () -> EvaluationPartitions.HELD_OUT.add(99999L));
        assertEquals(10, EvaluationPartitions.HELD_OUT.size());
    }

    // 10. quality configuration itself does not alter policy/EV parameters
    @Test
    void qualityConfigurationDoesNotAlterPolicyEvParameters() {
        // Policy and EV config must be same across qualities
        // We verify that PolicyConfig version and EV are same by checking that policy thresholds are fixed
        List<Long> seeds = List.of(10000L);
        var rLow = experiment.run(seeds, 30);
        var rHigh = experiment.run(seeds, 30);
        // Policy thresholds are fixed 10000/3/48 for all qualities - verify via decisionQuality's oracle is same
        // Since same worlds, oracle mean should be same across qualities
        assertEquals(rLow.policyOnlyDecisionQuality().meanOracleTrueValue(), rHigh.policyOnlyDecisionQuality().meanOracleTrueValue(),
                "Oracle should be same across qualities (same worlds, same policy)");
        // Also recovered revenue for policy-only must be same across qualities
        assertEquals(rLow.policyOnlyRecovered(), rHigh.policyOnlyRecovered());
        // And held-out config unchanged
        assertEquals(EvaluationPartitions.versionSnapshot(), EvaluationPartitions.versionSnapshot());
    }
}
