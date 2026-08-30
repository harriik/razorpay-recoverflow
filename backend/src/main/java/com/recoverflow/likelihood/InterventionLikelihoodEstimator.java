package com.recoverflow.likelihood;

import com.recoverflow.ai.AiAssessment;
import com.recoverflow.ai.CandidateAssessment;
import com.recoverflow.ai.EvidenceQuality;
import com.recoverflow.recovery.RecoveryActionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Deterministic estimator that produces P_estimated per action from observable + AI qualitative signals.
 * Never reads HiddenTruth. Never uses LLM numeric probability.
 * Versioned via EstimatorConfig.
 */
@Component
public class InterventionLikelihoodEstimator {

    private final EstimatorConfig config;

    public InterventionLikelihoodEstimator(EstimatorConfig config) {
        this.config = config;
    }

    public InterventionLikelihoodEstimator() {
        this(new EstimatorConfig());
    }

    public String getVersion() {
        return config.getVersion();
    }

    /**
     * Observable-only estimation (ablation mode POLICY_ONLY). No AI.
     * Uses base + history + elapsed only.
     */
    public Map<RecoveryActionType, BigDecimal> estimateObservableOnly(ObservableContext obs) {
        Map<RecoveryActionType, BigDecimal> result = new EnumMap<>(RecoveryActionType.class);
        for (RecoveryActionType action : automaticActions()) {
            BigDecimal base = config.getBase(obs.gatewayCode(), obs.amountBucket(), action);
            BigDecimal hist = config.getHistoryModifier(obs.historyBucket(), action);
            BigDecimal elapsed = config.getElapsedModifier(obs.elapsedBucket(), action);
            BigDecimal p = base.add(hist).add(elapsed);
            result.put(action, clamp(p));
        }
        return result;
    }

    /**
     * AI-aware estimation: observable + AI qualitative signals.
     * Formula: P = clamp( base + (aiContribution * evidenceWeight) + evidenceBias + recoverabilityBias + history + elapsed )
     */
    public Map<RecoveryActionType, BigDecimal> estimate(ObservableContext obs, AiAssessment ai) {
        if (ai == null) {
            return estimateObservableOnly(obs);
        }

        // Build map from action -> assessment level for this AI
        Map<RecoveryActionType, String> assessmentByAction = ai.candidateAssessments().stream()
                .filter(CandidateAssessment::applicable)
                .collect(Collectors.toMap(CandidateAssessment::action, ca -> ca.assessment().name()));

        Map<RecoveryActionType, BigDecimal> result = new EnumMap<>(RecoveryActionType.class);
        for (RecoveryActionType action : automaticActions()) {
            if (!assessmentByAction.containsKey(action)) {
                // Not applicable -> skip or assign minimal; we still estimate but mark as not applicable in ranking
                // For now, skip storing; caller will filter
                continue;
            }
            String level = assessmentByAction.get(action);
            BigDecimal base = config.getBase(obs.gatewayCode(), obs.amountBucket(), action);
            BigDecimal aiDelta = config.getAiContribution(ai.failureCategory(), level, action);
            BigDecimal weight = config.getEvidenceWeight(ai.evidenceQuality());
            BigDecimal weightedAi = aiDelta.multiply(weight);
            BigDecimal bias = config.getEvidenceBias(ai.evidenceQuality());
            BigDecimal recBias = config.getRecoverabilityBias(ai.recoverability());
            BigDecimal hist = config.getHistoryModifier(obs.historyBucket(), action);
            BigDecimal elapsed = config.getElapsedModifier(obs.elapsedBucket(), action);

            BigDecimal p = base.add(weightedAi).add(bias).add(recBias).add(hist).add(elapsed);
            result.put(action, clamp(p));
        }
        // Ensure at least one entry even if all marked not applicable (fallback to observable-only)
        if (result.isEmpty()) {
            return estimateObservableOnly(obs);
        }
        return result;
    }

    /**
     * Detailed breakdown for Decision View explainability.
     */
    public EstimationBreakdown breakdown(ObservableContext obs, AiAssessment ai, RecoveryActionType action) {
        String level = null;
        if (ai != null) {
            level = ai.candidateAssessments().stream()
                    .filter(ca -> ca.action() == action && ca.applicable())
                    .map(ca -> ca.assessment().name())
                    .findFirst().orElse(null);
        }
        BigDecimal base = config.getBase(obs.gatewayCode(), obs.amountBucket(), action);
        BigDecimal aiDelta = BigDecimal.ZERO;
        BigDecimal weightedAi = BigDecimal.ZERO;
        BigDecimal bias = BigDecimal.ZERO;
        BigDecimal recBias = BigDecimal.ZERO;
        if (ai != null && level != null) {
            aiDelta = config.getAiContribution(ai.failureCategory(), level, action);
            weightedAi = aiDelta.multiply(config.getEvidenceWeight(ai.evidenceQuality()));
            bias = config.getEvidenceBias(ai.evidenceQuality());
            recBias = config.getRecoverabilityBias(ai.recoverability());
        }
        BigDecimal hist = config.getHistoryModifier(obs.historyBucket(), action);
        BigDecimal elapsed = config.getElapsedModifier(obs.elapsedBucket(), action);
        BigDecimal raw = base.add(weightedAi).add(bias).add(recBias).add(hist).add(elapsed);
        BigDecimal clamped = clamp(raw);
        return new EstimationBreakdown(action, base, aiDelta, weightedAi, bias, recBias, hist, elapsed, raw, clamped);
    }

    private BigDecimal clamp(BigDecimal p) {
        if (p.compareTo(config.getMin()) < 0) return config.getMin();
        if (p.compareTo(config.getMax()) > 0) return config.getMax();
        return p.setScale(3, RoundingMode.HALF_UP);
    }

    private RecoveryActionType[] automaticActions() {
        return new RecoveryActionType[]{
                RecoveryActionType.RETRY_NOW,
                RecoveryActionType.SCHEDULE_RETRY,
                RecoveryActionType.SEND_PAYMENT_LINK,
                RecoveryActionType.SEND_REMINDER
        };
    }

    public record EstimationBreakdown(
            RecoveryActionType action,
            BigDecimal base,
            BigDecimal aiDelta,
            BigDecimal weightedAiDelta,
            BigDecimal evidenceBias,
            BigDecimal recoverabilityBias,
            BigDecimal historyModifier,
            BigDecimal elapsedModifier,
            BigDecimal raw,
            BigDecimal clamped
    ) {}
}
