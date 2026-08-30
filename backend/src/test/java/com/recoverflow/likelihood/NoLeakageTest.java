package com.recoverflow.likelihood;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.ai.AiAssessment;
import java.lang.reflect.Field;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Ensures no HiddenTruth or GroundTruth leaks into decision-time types.
 * Decision-time types must not contain fields like trueFailureCategory, latentRecoveryPropensity, groundTruthOutcome.
 */
class NoLeakageTest {

    private static final Set<String> FORBIDDEN_SUBSTRINGS = Set.of(
            "trueFailureCategory", "truefailurecategory",
            "latent", "latentRecoveryPropensity",
            "groundTruth", "groundtruth",
            "future", "recoveredAt", "hidden", "p_true", "ptrue"
    );

    @Test
    void observableContextHasNoHiddenFields() {
        for (Field f : ObservableContext.class.getDeclaredFields()) {
            String name = f.getName().toLowerCase();
            for (String forbidden : FORBIDDEN_SUBSTRINGS) {
                assertFalse(name.contains(forbidden.toLowerCase()),
                        "ObservableContext must not contain hidden field: " + f.getName());
            }
        }
        // Allowlist check: only 9 fields expected
        assertEquals(9, ObservableContext.class.getRecordComponents().length,
                "ObservableContext should have exactly 9 allowlisted fields");
    }

    @Test
    void aiAssessmentHasNoHiddenOrProbabilityFields() {
        for (Field f : AiAssessment.class.getDeclaredFields()) {
            String name = f.getName().toLowerCase();
            for (String forbidden : FORBIDDEN_SUBSTRINGS) {
                assertFalse(name.contains(forbidden.toLowerCase()),
                        "AiAssessment must not contain hidden field: " + f.getName());
            }
            // Must not contain numeric probability
            assertFalse(name.contains("probability") && f.getType().getSimpleName().contains("BigDecimal"),
                    "AiAssessment must not contain numeric probability: " + f.getName());
            assertFalse(name.equals("p_estimated") || name.equals("p_true"),
                    "AiAssessment must not contain P field: " + f.getName());
        }
        // Ensure no BigDecimal fields in AiAssessment
        for (Field f : AiAssessment.class.getDeclaredFields()) {
            assertNotEquals("BigDecimal", f.getType().getSimpleName(),
                    "AiAssessment must not have BigDecimal probability field: " + f.getName());
        }
    }

    @Test
    void estimatorInputTypesDoNotReferenceHiddenTruth() {
        // Verify InterventionLikelihoodEstimator method signatures don't include HiddenTruth types
        boolean hasObservable = false;
        boolean hasAi = false;
        boolean hasHidden = false;
        for (var m : com.recoverflow.likelihood.InterventionLikelihoodEstimator.class.getDeclaredMethods()) {
            for (var p : m.getParameters()) {
                String typeName = p.getType().getSimpleName().toLowerCase();
                if (typeName.contains("observablecontext")) hasObservable = true;
                if (typeName.contains("aiassessment")) hasAi = true;
                for (String forbidden : FORBIDDEN_SUBSTRINGS) {
                    assertFalse(typeName.contains(forbidden.toLowerCase()),
                            "Estimator must not take hidden type: " + p.getType().getSimpleName() + " in " + m.getName());
                }
                if (typeName.contains("hiddentruth") || typeName.contains("groundtruth")) {
                    hasHidden = true;
                }
            }
        }
        assertTrue(hasObservable, "Estimator must take ObservableContext");
        assertTrue(hasAi, "Estimator must take AiAssessment");
        assertFalse(hasHidden, "Estimator must not reference hidden truth");
    }

    @Test
    void decisionEngineIsPureNoHiddenReference() {
        for (var m : com.recoverflow.decision.ExpectedNetRecoveryValueEngine.class.getDeclaredMethods()) {
            for (var p : m.getParameters()) {
                String typeName = p.getType().getSimpleName().toLowerCase();
                for (String forbidden : FORBIDDEN_SUBSTRINGS) {
                    assertFalse(typeName.contains(forbidden.toLowerCase()),
                            "EV engine must not take hidden type: " + p.getType().getSimpleName());
                }
            }
        }
    }
}
