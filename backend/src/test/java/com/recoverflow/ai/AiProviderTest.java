package com.recoverflow.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recoverflow.evaluation.EvaluationPartitions;
import com.recoverflow.evaluation.RealLlmAuditFixture;
import com.recoverflow.evaluation.RealLlmAuditService;
import com.recoverflow.likelihood.ObservableContext;
import com.recoverflow.payment.PaymentMethod;
import com.recoverflow.synthetic.SyntheticAiProxy;
import com.recoverflow.synthetic.SyntheticCase;
import com.recoverflow.synthetic.SyntheticWorldGenerator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AiProviderTest {

    @Autowired SyntheticAiProxy syntheticProxy;
    @Autowired SyntheticWorldGenerator generator;
    @Autowired RealLlmAuditService auditService;
    @Autowired AiProviderConfig config;

    private ObservableContext sampleObs() {
        return new ObservableContext(new BigDecimal("5000.0000"), "INR", PaymentMethod.CARD, "BANK_TIMEOUT", 5, 1, 2, 1, false);
    }

    // 1. synthetic provider implements common interface
    @Test
    void syntheticProviderImplementsInterface() {
        AiDecisionProvider p = new SyntheticAiProvider(syntheticProxy);
        assertTrue(p instanceof AiDecisionProvider);
        assertEquals("SYNTHETIC_AI_PROXY", p.providerName());
        assertNotNull(p.modelId());
        AiAssessment a = p.assess(sampleObs());
        assertNotNull(a);
        assertEquals(4, a.candidateAssessments().size());
    }

    // 2. real LLM provider implements common interface
    @Test
    void realLlmProviderImplementsInterface() {
        var cfg = new AiProviderConfig();
        // Use reflection to set private fields for test
        try {
            var f = AiProviderConfig.class.getDeclaredField("provider");
            f.setAccessible(true);
            f.set(cfg, "REAL_LLM");
            var f2 = AiProviderConfig.class.getDeclaredField("modelId");
            f2.setAccessible(true);
            f2.set(cfg, "test-model");
        } catch (Exception e) { fail(e); }
        AiDecisionProvider p = new RealLlmAiProvider(syntheticProxy, cfg, prompt -> validJson(sampleObs()));
        assertTrue(p instanceof AiDecisionProvider);
        assertEquals("REAL_LLM", p.providerName());
        assertEquals("test-model", p.modelId());
    }

    private String validJson(ObservableContext obs) {
        return """
                {
                  "failureCategory": "TEMPORARY_BANK_FAILURE",
                  "recoverability": "MEDIUM",
                  "candidateAssessments": [
                    {"action": "RETRY_NOW", "assessment": "HIGH", "applicable": true},
                    {"action": "SCHEDULE_RETRY", "assessment": "MEDIUM", "applicable": true},
                    {"action": "SEND_PAYMENT_LINK", "assessment": "LOW", "applicable": true},
                    {"action": "SEND_REMINDER", "assessment": "LOW", "applicable": false, "reason": "reminder_requires_prior_link"}
                  ],
                  "recommendedAction": "RETRY_NOW",
                  "evidenceQuality": "MEDIUM",
                  "riskLevel": "LOW",
                  "reasoningSummary": "test valid"
                }
                """;
    }

    // 3. valid LLM output maps correctly to AiAssessment
    @Test
    void validLlmOutputMapsCorrectly() {
        var cfg = new AiProviderConfig();
        try {
            var f = AiProviderConfig.class.getDeclaredField("provider");
            f.setAccessible(true);
            f.set(cfg, "REAL_LLM");
            var f2 = AiProviderConfig.class.getDeclaredField("modelId");
            f2.setAccessible(true);
            f2.set(cfg, "test-model");
        } catch (Exception e) { fail(e); }
        RealLlmAiProvider p = new RealLlmAiProvider(syntheticProxy, cfg, prompt -> validJson(sampleObs()));
        AiAssessment a = p.assess(sampleObs());
        assertEquals(FailureCategory.TEMPORARY_BANK_FAILURE, a.failureCategory());
        assertEquals(Recoverability.MEDIUM, a.recoverability());
        assertEquals(4, a.candidateAssessments().size());
        assertEquals(EvidenceQuality.MEDIUM, a.evidenceQuality());
        assertFalse(a.reasoningSummary().contains("fallback"));
    }

    // 4. malformed output triggers fallback
    @Test
    void malformedOutputTriggersFallback() {
        var cfg = new AiProviderConfig();
        try {
            var f = AiProviderConfig.class.getDeclaredField("provider");
            f.setAccessible(true);
            f.set(cfg, "REAL_LLM");
            var f2 = AiProviderConfig.class.getDeclaredField("modelId");
            f2.setAccessible(true);
            f2.set(cfg, "test-model");
        } catch (Exception e) { fail(e); }
        RealLlmAiProvider p = new RealLlmAiProvider(syntheticProxy, cfg, prompt -> "{ malformed json ");
        AiAssessment a = p.assess(sampleObs());
        assertNotNull(a);
        assertTrue(a.reasoningSummary().contains("fallback"), "Must fallback on malformed JSON");
    }

    // 5. timeout triggers fallback
    @Test
    void timeoutTriggersFallback() {
        var cfg = new AiProviderConfig();
        try {
            var f = AiProviderConfig.class.getDeclaredField("provider");
            f.setAccessible(true);
            f.set(cfg, "REAL_LLM");
            var f2 = AiProviderConfig.class.getDeclaredField("modelId");
            f2.setAccessible(true);
            f2.set(cfg, "test-model");
        } catch (Exception e) { fail(e); }
        RealLlmAiProvider p = new RealLlmAiProvider(syntheticProxy, cfg, prompt -> { throw new LlmTimeoutException("timeout"); });
        AiAssessment a = p.assess(sampleObs());
        assertNotNull(a);
        assertTrue(a.reasoningSummary().contains("fallback"));
        assertTrue(a.reasoningSummary().contains("TIMEOUT") || a.reasoningSummary().contains("timeout"));
    }

    // 6. missing enum triggers fallback
    @Test
    void missingEnumTriggersFallback() {
        var cfg = new AiProviderConfig();
        try {
            var f = AiProviderConfig.class.getDeclaredField("provider");
            f.setAccessible(true);
            f.set(cfg, "REAL_LLM");
            var f2 = AiProviderConfig.class.getDeclaredField("modelId");
            f2.setAccessible(true);
            f2.set(cfg, "test-model");
        } catch (Exception e) { fail(e); }
        String missingFieldJson = """
                {
                  "recoverability": "MEDIUM",
                  "candidateAssessments": [
                    {"action": "RETRY_NOW", "assessment": "HIGH", "applicable": true},
                    {"action": "SCHEDULE_RETRY", "assessment": "MEDIUM", "applicable": true},
                    {"action": "SEND_PAYMENT_LINK", "assessment": "LOW", "applicable": true},
                    {"action": "SEND_REMINDER", "assessment": "LOW", "applicable": false}
                  ],
                  "recommendedAction": "RETRY_NOW",
                  "evidenceQuality": "MEDIUM",
                  "riskLevel": "LOW",
                  "reasoningSummary": "missing failureCategory"
                }
                """;
        RealLlmAiProvider p = new RealLlmAiProvider(syntheticProxy, cfg, prompt -> missingFieldJson);
        AiAssessment a = p.assess(sampleObs());
        assertTrue(a.reasoningSummary().contains("fallback"));
    }

    // 7. numeric probability from LLM is rejected
    @Test
    void numericProbabilityIsRejected() {
        var cfg = new AiProviderConfig();
        try {
            var f = AiProviderConfig.class.getDeclaredField("provider");
            f.setAccessible(true);
            f.set(cfg, "REAL_LLM");
            var f2 = AiProviderConfig.class.getDeclaredField("modelId");
            f2.setAccessible(true);
            f2.set(cfg, "test-model");
        } catch (Exception e) { fail(e); }
        String withProb = """
                {
                  "failureCategory": "TEMPORARY_BANK_FAILURE",
                  "recoverability": "MEDIUM",
                  "candidateAssessments": [
                    {"action": "RETRY_NOW", "assessment": "HIGH", "applicable": true},
                    {"action": "SCHEDULE_RETRY", "assessment": "MEDIUM", "applicable": true},
                    {"action": "SEND_PAYMENT_LINK", "assessment": "LOW", "applicable": true},
                    {"action": "SEND_REMINDER", "assessment": "LOW", "applicable": false}
                  ],
                  "recommendedAction": "RETRY_NOW",
                  "evidenceQuality": "MEDIUM",
                  "riskLevel": "LOW",
                  "reasoningSummary": "has prob",
                  "probability": 0.85,
                  "p_true": 0.9
                }
                """;
        RealLlmAiProvider p = new RealLlmAiProvider(syntheticProxy, cfg, prompt -> withProb);
        AiAssessment a = p.assess(sampleObs());
        assertTrue(a.reasoningSummary().contains("fallback"), "Numeric probability must be rejected and fallback");
        // Ensure no probability in resulting assessment (AiAssessment has no numeric prob field)
        assertNotNull(a.failureCategory());
    }

    // 8. HiddenTruth is not in the LLM input
    @Test
    void hiddenTruthNotInLlmInput() {
        // RealLlmAiProvider.buildPrompt is private, but we can test via LlmClient capture
        final String[] capturedPrompt = new String[1];
        var cfg = new AiProviderConfig();
        try {
            var f = AiProviderConfig.class.getDeclaredField("provider");
            f.setAccessible(true);
            f.set(cfg, "REAL_LLM");
            var f2 = AiProviderConfig.class.getDeclaredField("modelId");
            f2.setAccessible(true);
            f2.set(cfg, "test-model");
        } catch (Exception e) { fail(e); }
        LlmClient capturing = prompt -> {
            capturedPrompt[0] = prompt;
            return validJson(sampleObs());
        };
        RealLlmAiProvider p = new RealLlmAiProvider(syntheticProxy, cfg, capturing);
        p.assess(sampleObs());
        String prompt = capturedPrompt[0];
        assertNotNull(prompt);
        String lower = prompt.toLowerCase();
        assertFalse(lower.contains("hiddentruth"));
        assertFalse(lower.contains("p_true"));
        assertFalse(lower.contains("latentrecovery"));
        assertFalse(lower.contains("groundtruth"));
        assertFalse(lower.contains("oracle"));
        assertTrue(prompt.contains("BANK_TIMEOUT") || prompt.contains("gatewayCode"));
    }

    // 9. both providers feed the same downstream pipeline
    @Test
    void bothProvidersFeedSameDownstreamPipeline() {
        // Use real generator for audit fixtures
        var fixtures = RealLlmAuditFixture.generate(generator, 20);
        AiDecisionProvider synth = new SyntheticAiProvider(syntheticProxy);
        var cfg = new AiProviderConfig();
        try {
            var f = AiProviderConfig.class.getDeclaredField("provider");
            f.setAccessible(true);
            f.set(cfg, "REAL_LLM");
            var f2 = AiProviderConfig.class.getDeclaredField("modelId");
            f2.setAccessible(true);
            f2.set(cfg, "test-model");
        } catch (Exception e) { fail(e); }
        AiDecisionProvider real = new RealLlmAiProvider(syntheticProxy, cfg, prompt -> validJson(sampleObs()));
        var comparison = auditService.runAudit(fixtures, synth, real);
        assertEquals(20, comparison.cases().size());
        // Each case must have gone through same downstream pipeline (estimator+EV+policy) regardless of selected action
        for (var c : comparison.cases()) {
            assertNotNull(c.estimatorResultSynthetic());
            assertNotNull(c.evRankingSynthetic());
            assertNotNull(c.estimatorResultReal());
            assertNotNull(c.evRankingReal());
            // Policy may be null if no permissible action (e.g., CARD_EXPIRED) – that's still valid pipeline execution
            assertNotNull(c.observable());
        }
    }

    // 10. policy remains independent of provider
    @Test
    void policyRemainsIndependentOfProvider() throws Exception {
        // PolicyEngine should not have provider-specific logic
        for (var m : com.recoverflow.policy.PolicyEngine.class.getDeclaredMethods()) {
            for (var p : m.getParameters()) {
                String t = p.getType().getSimpleName().toLowerCase();
                assertFalse(t.contains("synthetic"));
                assertFalse(t.contains("llm"));
                assertFalse(t.contains("aiprovider"));
            }
        }
        for (var m : com.recoverflow.decision.ExpectedNetRecoveryValueEngine.class.getDeclaredMethods()) {
            for (var p : m.getParameters()) {
                String t = p.getType().getSimpleName().toLowerCase();
                assertFalse(t.contains("synthetic"));
                assertFalse(t.contains("llm"));
            }
        }
        // Same observable, different providers should still go through same policy thresholds
        var fixtures = RealLlmAuditFixture.generate(generator, 20);
        var synth = new SyntheticAiProvider(syntheticProxy);
        var cfg = new AiProviderConfig();
        try {
            var f = AiProviderConfig.class.getDeclaredField("provider");
            f.setAccessible(true);
            f.set(cfg, "REAL_LLM");
            var f2 = AiProviderConfig.class.getDeclaredField("modelId");
            f2.setAccessible(true);
            f2.set(cfg, "test-model");
        } catch (Exception e) { fail(e); }
        var real = new RealLlmAiProvider(syntheticProxy, cfg, prompt -> validJson(sampleObs()));
        var comp = auditService.runAudit(fixtures, synth, real);
        // Policy decisions are independent – both use same PolicyEngine thresholds
        assertNotNull(comp);
        assertEquals(20, comp.cases().size());
    }

    // 11. same observable context gives deterministic behavior for the synthetic provider
    @Test
    void syntheticProviderDeterministic() {
        var p = new SyntheticAiProvider(syntheticProxy);
        var obs = sampleObs();
        var a1 = p.assess(obs);
        var a2 = p.assess(obs);
        assertEquals(a1.failureCategory(), a2.failureCategory());
        assertEquals(a1.recoverability(), a2.recoverability());
        assertEquals(a1.evidenceQuality(), a2.evidenceQuality());
        assertEquals(a1.riskLevel(), a2.riskLevel());
        assertEquals(a1.recommendedAction(), a2.recommendedAction());
    }

    // 12. real-LLM audit fixture can be replayed/recorded without changing hidden-world data
    @Test
    void auditFixtureReplayWithoutChangingHiddenWorld() {
        var fixtures1 = RealLlmAuditFixture.generate(generator, 25);
        var fixtures2 = RealLlmAuditFixture.generate(generator, 25);
        assertEquals(fixtures1.size(), fixtures2.size());
        for (int i = 0; i < fixtures1.size(); i++) {
            var c1 = fixtures1.get(i);
            var c2 = fixtures2.get(i);
            assertEquals(c1.caseId(), c2.caseId());
            assertEquals(c1.pTrue(), c2.pTrue());
            assertEquals(c1.groundTruthOutcomes(), c2.groundTruthOutcomes());
            assertEquals(c1.hiddenTruth().trueFailureCategory(), c2.hiddenTruth().trueFailureCategory());
            assertEquals(c1.observable().amount(), c2.observable().amount());
        }
        // Held-out not affected
        assertEquals(10, EvaluationPartitions.HELD_OUT.size());
        var heldBefore = List.copyOf(EvaluationPartitions.HELD_OUT);
        RealLlmAuditFixture.generate(generator, 20);
        assertEquals(heldBefore, EvaluationPartitions.HELD_OUT);
    }
}
