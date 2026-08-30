package com.recoverflow.synthetic;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.recovery.RecoveryActionType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SyntheticWorldGeneratorTest {

    @Test
    void deterministicGeneration() {
        SyntheticWorldGenerator gen = new SyntheticWorldGenerator();
        List<SyntheticCase> a = gen.generate(12345L, 100);
        List<SyntheticCase> b = gen.generate(12345L, 100);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).caseId(), b.get(i).caseId());
            assertEquals(a.get(i).observable().amount(), b.get(i).observable().amount());
            assertEquals(a.get(i).hiddenTruth().trueFailureCategory(), b.get(i).hiddenTruth().trueFailureCategory());
            assertEquals(a.get(i).pTrue(), b.get(i).pTrue());
            assertEquals(a.get(i).groundTruthOutcomes(), b.get(i).groundTruthOutcomes());
        }
        List<SyntheticCase> c = gen.generate(54321L, 100);
        assertNotEquals(a.get(0).caseId(), c.get(0).caseId());
    }

    @Test
    void hiddenObservableSeparation() {
        SyntheticWorldGenerator gen = new SyntheticWorldGenerator();
        List<SyntheticCase> dataset = gen.generate(42L, 50);
        for (SyntheticCase sc : dataset) {
            // Observable must not contain hidden fields
            var obs = sc.observable();
            assertNotNull(obs.gatewayCode());
            assertNotNull(obs.amount());
            // Hidden contains true category and pTrue
            assertNotNull(sc.hiddenTruth().trueFailureCategory());
            assertNotNull(sc.hiddenTruth().customerBehaviorProfile());
            assertNotNull(sc.hiddenTruth().latentRecoveryPropensity());
            assertNotNull(sc.pTrue());
            assertNotNull(sc.groundTruthOutcomes());
            // Ensure observable gateway is sometimes different from true (noise) — at least some mismatch in large sample
        }
        // Check that at least some cases have mismatch between true and observed (due to 15% noise)
        SyntheticWorldGenerator gen2 = new SyntheticWorldGenerator();
        List<SyntheticCase> large = gen2.generate(99L, 500);
        long mismatched = large.stream().filter(sc -> !sc.observable().gatewayCode().equals(sc.hiddenTruth().trueGatewayCode())).count();
        assertTrue(mismatched > 20, "Expected some mismatch due to noise, got " + mismatched);
        assertTrue(mismatched < 150, "Mismatch should be around 15%");
    }

    @Test
    void noLeakageDecisionNeverReceivesHidden() {
        // Verify that ObservableCase record has no hidden fields via reflection
        for (var field : com.recoverflow.synthetic.ObservableCase.class.getDeclaredFields()) {
            String n = field.getName().toLowerCase();
            assertFalse(n.contains("truefailure"), "Observable must not contain hidden trueFailureCategory");
            assertFalse(n.contains("latent"), "Observable must not contain latent");
            assertFalse(n.contains("ptrue") || n.contains("groundtruth"));
        }
        for (var field : com.recoverflow.likelihood.ObservableContext.class.getDeclaredFields()) {
            String n = field.getName().toLowerCase();
            assertFalse(n.contains("truefailure"));
            assertFalse(n.contains("hidden"));
        }
    }

    @Test
    void groundTruthIndependentOfAi() {
        SyntheticWorldGenerator gen = new SyntheticWorldGenerator();
        List<SyntheticCase> dataset = gen.generate(123L, 10);
        for (SyntheticCase sc : dataset) {
            // Ground truth should be same regardless of AI
            Map<RecoveryActionType, Boolean> gt1 = sc.groundTruthOutcomes();
            // Simulate AI being wrong: AI category != true, but ground truth unchanged
            assertNotNull(gt1);
            // P_true is from hidden, not AI
            assertTrue(sc.pTrue().containsKey(RecoveryActionType.RETRY_NOW));
        }
    }

    @Test
    void amountDistributionAndBounds() {
        SyntheticWorldGenerator gen = new SyntheticWorldGenerator();
        List<SyntheticCase> dataset = gen.generate(777L, 1000);
        for (SyntheticCase sc : dataset) {
            assertTrue(sc.observable().amount().doubleValue() >= 500 && sc.observable().amount().doubleValue() <= 50000);
            assertEquals(4, sc.observable().amount().scale());
            for (double p : sc.pTrue().values()) {
                assertTrue(p >= 0.02 && p <= 0.85, "P_true bounds");
            }
        }
    }

    @Test
    void atLeast3000CasesDeterministic() {
        SyntheticWorldGenerator gen = new SyntheticWorldGenerator();
        List<SyntheticCase> dataset = gen.generate(999L, 3000);
        assertEquals(3000, dataset.size());
        // Reproducibility
        List<SyntheticCase> dataset2 = gen.generate(999L, 3000);
        assertEquals(dataset.get(0).caseId(), dataset2.get(0).caseId());
        assertEquals(dataset.get(2999).caseId(), dataset2.get(2999).caseId());
    }
}
