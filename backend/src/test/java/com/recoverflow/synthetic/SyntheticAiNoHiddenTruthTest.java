package com.recoverflow.synthetic;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.likelihood.ObservableContext;
import com.recoverflow.payment.PaymentMethod;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Ensures Synthetic AI Proxy never receives hidden truth.
 */
class SyntheticAiNoHiddenTruthTest {

    private static final Set<String> FORBIDDEN = Set.of(
            "hiddentruth", "hidden", "ptrue", "groundtruth", "latentrecovery", "truefailure", "hiddenworld"
    );

    @Test
    void proxyMethodSignatureContainsNoHiddenTypes() {
        for (Method m : SyntheticAiProxy.class.getDeclaredMethods()) {
            if (m.getName().equals("assess")) {
                for (var p : m.getParameters()) {
                    String type = p.getType().getSimpleName().toLowerCase();
                    for (String f : FORBIDDEN) {
                        assertFalse(type.contains(f), "SyntheticAiProxy.assess must not take hidden type: " + p.getType().getSimpleName());
                    }
                }
                // Must take ObservableContext
                boolean hasObservable = false;
                for (var p : m.getParameters()) {
                    if (p.getType().getSimpleName().equals("ObservableContext")) hasObservable = true;
                }
                assertTrue(hasObservable, "SyntheticAiProxy must take ObservableContext");
            }
        }
    }

    @Test
    void proxyCannotAccessHiddenDataset() {
        // Verify that SyntheticAiProxy class does not import synthetic HiddenTruth in a way that it could access evaluator dataset
        for (var field : SyntheticAiProxy.class.getDeclaredFields()) {
            String type = field.getType().getSimpleName().toLowerCase();
            for (String f : FORBIDDEN) {
                assertFalse(type.contains(f), "SyntheticAiProxy must not have hidden field: " + field.getName());
            }
        }
        // Ensure it does not have a reference to SyntheticCase or List<SyntheticCase>
        for (var field : SyntheticAiProxy.class.getDeclaredFields()) {
            assertFalse(field.getType().getSimpleName().contains("SyntheticCase"),
                    "SyntheticAiProxy must not hold dataset: " + field.getName());
        }
    }

    @Test
    void executionPathReceivesOnlyObservable() {
        SyntheticAiProxy proxy = new SyntheticAiProxy();
        ObservableContext obs = new ObservableContext(
                new BigDecimal("5000.0000"), "INR", PaymentMethod.CARD, "BANK_TIMEOUT",
                5, 0, 12, 1, false);
        // This call must succeed with only observable + seed, no hidden
        var ai = proxy.assess(obs, 12345L);
        assertNotNull(ai);
        assertNotNull(ai.failureCategory());
        assertNotNull(ai.candidateAssessments());
        assertEquals(4, ai.candidateAssessments().size());
        // Ensure AI output does not contain hidden P_true
        for (var field : ai.getClass().getDeclaredFields()) {
            assertFalse(field.getName().toLowerCase().contains("ptrue"), "AI output must not contain P_true");
        }
    }

    @Test
    void proxyIsVersionedAndAuditable() {
        SyntheticAiProxy proxy = new SyntheticAiProxy();
        assertEquals("synthetic-ai-v1", proxy.getVersion());
        ObservableContext obs = new ObservableContext(
                new BigDecimal("1000.0000"), "INR", PaymentMethod.UPI, "INSUFFICIENT_FUNDS",
                2, 0, 0, 0, false);
        var a1 = proxy.assess(obs, 999L);
        var a2 = proxy.assess(obs, 999L);
        assertEquals(a1.failureCategory(), a2.failureCategory(), "Proxy must be deterministic");
        assertEquals(a1.candidateAssessments().get(0).assessment(), a2.candidateAssessments().get(0).assessment());
    }
}
