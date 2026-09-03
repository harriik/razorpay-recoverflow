package com.recoverflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverflow.likelihood.ObservableContext;
import com.recoverflow.recovery.RecoveryActionType;
import com.recoverflow.synthetic.SyntheticAiProxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Real LLM provider – structured output only, observable-only input.
 * Validates LLM JSON against AiAssessment schema, falls back deterministically on failure.
 * Never receives HiddenTruth/P_true.
 */
public class RealLlmAiProvider implements AiDecisionProvider {

    private static final Logger log = LoggerFactory.getLogger(RealLlmAiProvider.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final SyntheticAiProxy fallbackProxy;
    private final AiProviderConfig config;
    private final LlmClient llmClient; // may be null if not configured – then fallback

    public RealLlmAiProvider(SyntheticAiProxy fallbackProxy, AiProviderConfig config) {
        this(fallbackProxy, config, null);
    }

    public RealLlmAiProvider(SyntheticAiProxy fallbackProxy, AiProviderConfig config, LlmClient llmClient) {
        this.fallbackProxy = fallbackProxy;
        this.config = config;
        this.llmClient = llmClient;
    }

    @Override
    public AiAssessment assess(ObservableContext context) {
        String correlationId = UUID.randomUUID().toString();
        Instant start = Instant.now();
        // Build prompt from ObservableContext only – never hidden
        String prompt = buildPrompt(context);
        String rawResponse = null;
        boolean valid = false;
        boolean fallbackUsed = false;
        AiAssessment result = null;
        String validationStatus = "UNKNOWN";

        try {
            if (llmClient == null || !config.isRealLlmConfigured()) {
                throw new IllegalStateException("Real LLM not configured – using fallback");
            }
            rawResponse = llmClient.call(prompt);
            if (rawResponse == null || rawResponse.isBlank()) {
                throw new IllegalArgumentException("Empty LLM response");
            }
            // Validate structured output
            JsonNode node = mapper.readTree(rawResponse);
            validationStatus = validate(node);
            if (!"VALID".equals(validationStatus)) {
                throw new IllegalArgumentException("Invalid LLM output: " + validationStatus);
            }
            result = mapToAiAssessment(node, correlationId);
            valid = true;
            validationStatus = "VALID";
        } catch (LlmTimeoutException e) {
            validationStatus = "TIMEOUT";
            log.warn("LLM timeout correlationId={} model={} latency={}", safeMeta(correlationId), safeModel(), java.time.Duration.between(start, Instant.now()).toMillis(), e);
            fallbackUsed = true;
        } catch (LlmNetworkException e) {
            validationStatus = "NETWORK_FAILURE";
            log.warn("LLM network failure correlationId={} model={}", safeMeta(correlationId), safeModel(), e);
            fallbackUsed = true;
        } catch (Exception e) {
            // malformed JSON, missing fields, invalid enum, numeric probability, etc.
            if (validationStatus == null || "UNKNOWN".equals(validationStatus)) validationStatus = "INVALID:" + e.getMessage();
            log.warn("LLM validation failed correlationId={} model={} status={} latency={}", safeMeta(correlationId), safeModel(), validationStatus, java.time.Duration.between(start, Instant.now()).toMillis(), e);
            fallbackUsed = true;
        }

        if (result == null || fallbackUsed) {
            // Deterministic fallback – never bypass policy
            result = fallbackProxy.assess(context);
            // Override modelId to indicate fallback but keep structure valid
            result = new AiAssessment(
                    result.failureCategory(),
                    result.recoverability(),
                    result.candidateAssessments(),
                    result.recommendedAction(),
                    result.evidenceQuality(),
                    result.riskLevel(),
                    result.reasoningSummary() + " [fallback due to " + validationStatus + " correlationId=" + correlationId + "]",
                    config.getModelId() != null ? config.getModelId() : "fallback",
                    result.promptVersion() + "-fallback"
            );
            fallbackUsed = true;
        }

        // Safe metadata logging – never API keys or hidden data
        long latency = java.time.Duration.between(start, Instant.now()).toMillis();
        log.info("LLM audit provider={} model={} correlationId={} latencyMs={} validation={} fallback={} timestamp={}",
                providerName(), safeModel(), safeMeta(correlationId), latency, validationStatus, fallbackUsed, Instant.now());

        return result;
    }

    private String buildPrompt(ObservableContext obs) {
        // ONLY observable fields – no HiddenTruth, P_true, latent, oracle, groundTruth
        return String.format(
                "You are a recovery advisor. Given observable payment context: amount=%s currency=%s method=%s gatewayCode=%s elapsedHours=%d attemptCount=%d priorSuccess=%d priorFailure=%d linkAlreadySent=%b . "
                + "Respond ONLY with JSON matching schema: {failureCategory, recoverability, candidateAssessments[4]{action, assessment, applicable}, recommendedAction, evidenceQuality, riskLevel, reasoningSummary}. "
                + "Do NOT return numeric fields or extra structures beyond the schema.",
                obs.amount().toPlainString(), obs.currency(), obs.method().name(), obs.gatewayCode(),
                obs.elapsedHours(), obs.attemptCount(), obs.priorSuccessCount(), obs.priorFailureCount(), obs.linkAlreadySent()
        );
    }

    String validate(JsonNode node) {
        // Check forbidden fields
        List<String> forbidden = List.of("probability", "p_true", "pTrue", "expectedValue", "expected_value", "policyDecision", "executionPermission", "hidden", "oracle", "latent");
        for (String f : forbidden) {
            if (node.has(f)) return "FORBIDDEN_FIELD:" + f;
        }
        // Required fields
        List<String> required = List.of("failureCategory", "recoverability", "candidateAssessments", "recommendedAction", "evidenceQuality", "riskLevel", "reasoningSummary");
        for (String r : required) {
            if (!node.has(r) || node.get(r).isNull() || (node.get(r).isTextual() && node.get(r).asText().isBlank())) {
                return "MISSING_FIELD:" + r;
            }
            if (r.equals("candidateAssessments") && !node.get(r).isArray()) return "INVALID_FIELD:candidateAssessments not array";
        }
        JsonNode ca = node.get("candidateAssessments");
        if (ca.size() != 4) return "INVALID_FIELD:candidateAssessments must have 4 entries";
        // Validate enums
        try {
            FailureCategory.valueOf(node.get("failureCategory").asText());
        } catch (Exception e) { return "INVALID_ENUM:failureCategory"; }
        try {
            Recoverability.valueOf(node.get("recoverability").asText());
        } catch (Exception e) { return "INVALID_ENUM:recoverability"; }
        try {
            EvidenceQuality.valueOf(node.get("evidenceQuality").asText());
        } catch (Exception e) { return "INVALID_ENUM:evidenceQuality"; }
        try {
            RiskLevel.valueOf(node.get("riskLevel").asText());
        } catch (Exception e) { return "INVALID_ENUM:riskLevel"; }
        try {
            RecoveryActionType.valueOf(node.get("recommendedAction").asText());
        } catch (Exception e) { return "INVALID_ENUM:recommendedAction"; }
        // Candidate assessments validation
        for (JsonNode c : ca) {
            if (!c.has("action")) return "MISSING_FIELD:candidateAssessments.action";
            try { RecoveryActionType.valueOf(c.get("action").asText()); } catch (Exception e) { return "INVALID_ENUM:candidateAssessments.action"; }
            if (!c.has("applicable")) return "MISSING_FIELD:candidateAssessments.applicable";
            if (c.get("applicable").asBoolean() && c.has("assessment")) {
                try { CandidateAssessmentLevel.valueOf(c.get("assessment").asText()); } catch (Exception e) { return "INVALID_ENUM:candidateAssessments.assessment"; }
            }
        }
        // Extra unexpected fields are allowed but ignored (not forbidden) – we already checked forbidden
        // Check numeric probability not present as number
        if (node.has("probability") || node.has("confidenceScore")) return "FORBIDDEN_NUMERIC_PROBABILITY";
        // Empty check already done
        return "VALID";
    }

    private AiAssessment mapToAiAssessment(JsonNode node, String correlationId) {
        FailureCategory fc = FailureCategory.valueOf(node.get("failureCategory").asText());
        Recoverability rec = Recoverability.valueOf(node.get("recoverability").asText());
        EvidenceQuality eq = EvidenceQuality.valueOf(node.get("evidenceQuality").asText());
        RiskLevel risk = RiskLevel.valueOf(node.get("riskLevel").asText());
        RecoveryActionType recommended = RecoveryActionType.valueOf(node.get("recommendedAction").asText());
        String reasoning = node.get("reasoningSummary").asText();
        List<CandidateAssessment> list = new ArrayList<>();
        for (JsonNode c : node.get("candidateAssessments")) {
            RecoveryActionType action = RecoveryActionType.valueOf(c.get("action").asText());
            boolean applicable = c.get("applicable").asBoolean();
            CandidateAssessmentLevel level = null;
            if (applicable && c.has("assessment") && !c.get("assessment").isNull()) {
                level = CandidateAssessmentLevel.valueOf(c.get("assessment").asText());
            }
            String reason = c.has("reason") && !c.get("reason").isNull() ? c.get("reason").asText() : null;
            list.add(new CandidateAssessment(action, level, applicable, reason));
        }
        // Ensure 4 entries
        if (list.size() != 4) throw new IllegalArgumentException("candidateAssessments must have 4");
        return new AiAssessment(fc, rec, list, recommended, eq, risk, reasoning, config.getModelId(), "real-llm-v1");
    }

    private String safeModel() {
        return config.getModelId() != null ? config.getModelId() : "unknown";
    }

    private String safeMeta(String correlationId) {
        return correlationId != null ? correlationId : "unknown";
    }

    @Override
    public String providerName() {
        return "REAL_LLM";
    }

    @Override
    public String modelId() {
        return config.getModelId();
    }

    // For testing: expose validation
    public String validateForTest(JsonNode node) {
        return validate(node);
    }
}
