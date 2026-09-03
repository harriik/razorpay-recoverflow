package com.recoverflow.synthetic;

import com.recoverflow.ai.AiAssessment;
import com.recoverflow.ai.CandidateAssessment;
import com.recoverflow.ai.CandidateAssessmentLevel;
import com.recoverflow.ai.EvidenceQuality;
import com.recoverflow.ai.FailureCategory;
import com.recoverflow.ai.Recoverability;
import com.recoverflow.ai.RiskLevel;
import com.recoverflow.likelihood.ObservableContext;
import com.recoverflow.recovery.RecoveryActionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Quality-aware synthetic AI proxy for AI-quality sanity experiment.
 * Still observable-only: receives ONLY ObservableContext + quality config.
 * Never receives HiddenTruth, P_true, groundTruthOutcomes, latentRecoveryPropensity, trueFailureCategory.
 * Quality is controlled via proxy-target 50%/75%/90% against observable gateway;
 * measured true-category accuracy (due to noisy observable gateway 85% correlated with hidden truth)
 * is approximately 44%/65%/77% and must be measured from experiment output, not assumed.
 */
public class QualityAwareSyntheticAiProxy {

    private final SyntheticAiProxy delegate = new SyntheticAiProxy();

    public AiAssessment assess(ObservableContext obs, AiQuality quality) {
        // Derive proxy seed from observable + quality, not hidden truth
        long proxySeed = quality.versionSuffix().hashCode() * 31L
                + obs.gatewayCode().hashCode() * 31L
                + obs.amount().stripTrailingZeros().hashCode()
                + obs.method().hashCode() * 31L
                + obs.elapsedHours() * 31L
                + obs.priorSuccessCount() * 31L
                + obs.priorFailureCount() * 31L
                + (obs.linkAlreadySent() ? 1 : 0) * 31L
                + obs.attemptCount() * 31L;
        proxySeed ^= 0x9E3779B97F4A7C15L;
        Random rnd = new Random(proxySeed);

        // Get baseline assessment from delegate (which is observable-only)
        // But we need to adjust failureCategory accuracy to match quality target
        FailureCategory observedCat = mapGatewayToCategory(obs.gatewayCode());
        double accuracy = quality.targetAccuracy();
        // Proxy target 50/75/90 against observable gateway; measured true-category ~44/65/77 due to 85% gateway-truth correlation.
        // No extra bucket adjustments — keep deterministic and measurable.
        if (accuracy < 0.30) accuracy = 0.30;
        if (accuracy > 0.95) accuracy = 0.95;

        FailureCategory aiCat;
        if (rnd.nextDouble() < accuracy) {
            aiCat = observedCat;
        } else {
            FailureCategory[] all = FailureCategory.values();
            do { aiCat = all[rnd.nextInt(all.length)]; } while (aiCat == observedCat);
        }

        // For other fields, delegate to same observable-only logic but using same rnd
        // Re-derive recoverability, evidence, risk, candidate levels with same seed
        Recoverability rec = deriveRecoverability(obs, rnd);
        EvidenceQuality eq = deriveEvidenceQuality(obs, rnd);
        RiskLevel risk = deriveRisk(obs, rnd);

        List<CandidateAssessment> list = new ArrayList<>();
        for (RecoveryActionType action : List.of(RecoveryActionType.RETRY_NOW, RecoveryActionType.SCHEDULE_RETRY, RecoveryActionType.SEND_PAYMENT_LINK, RecoveryActionType.SEND_REMINDER)) {
            CandidateAssessmentLevel level = deriveCandidateLevel(obs, action, rnd, quality);
            boolean applicable = true;
            String reason = null;
            if (action == RecoveryActionType.SEND_REMINDER && !obs.linkAlreadySent()) {
                applicable = false;
                reason = "reminder_requires_prior_link";
                list.add(new CandidateAssessment(action, null, false, reason));
            } else {
                list.add(new CandidateAssessment(action, level, true, null));
            }
        }

        RecoveryActionType recommended = list.stream()
                .filter(CandidateAssessment::applicable)
                .max((a,b) -> a.assessment().ordinal() - b.assessment().ordinal())
                .map(CandidateAssessment::action)
                .orElse(RecoveryActionType.SCHEDULE_RETRY);

        String version = SyntheticAiProxy.VERSION + "-" + quality.versionSuffix();
        return new AiAssessment(aiCat, rec, list, recommended, eq, risk,
                "Synthetic AI proxy " + quality.name() + " observable-only gateway=" + obs.gatewayCode(),
                "synthetic-ai-proxy-" + version, version);
    }

    private FailureCategory mapGatewayToCategory(String gateway) {
        return switch (gateway) {
            case "BANK_TIMEOUT" -> FailureCategory.TEMPORARY_BANK_FAILURE;
            case "NETWORK_ERROR" -> FailureCategory.NETWORK_ERROR;
            case "INSUFFICIENT_FUNDS" -> FailureCategory.INSUFFICIENT_FUNDS;
            case "AUTH_FAILED" -> FailureCategory.AUTH_FAILED;
            case "CARD_EXPIRED" -> FailureCategory.CARD_EXPIRED;
            case "LIMIT_EXCEEDED" -> FailureCategory.LIMIT_EXCEEDED;
            default -> FailureCategory.UNKNOWN;
        };
    }

    private Recoverability deriveRecoverability(ObservableContext obs, Random rnd) {
        String gw = obs.gatewayCode();
        double r = rnd.nextDouble();
        if ("BANK_TIMEOUT".equals(gw) || "NETWORK_ERROR".equals(gw)) {
            if (r < 0.5) return Recoverability.HIGH;
            if (r < 0.8) return Recoverability.MEDIUM;
            return Recoverability.LOW;
        } else if ("CARD_EXPIRED".equals(gw) || "AUTH_FAILED".equals(gw)) {
            if (r < 0.2) return Recoverability.HIGH;
            if (r < 0.5) return Recoverability.MEDIUM;
            return Recoverability.LOW;
        } else {
            if (r < 0.3) return Recoverability.HIGH;
            if (r < 0.7) return Recoverability.MEDIUM;
            return Recoverability.LOW;
        }
    }

    private EvidenceQuality deriveEvidenceQuality(ObservableContext obs, Random rnd) {
        String hist = obs.historyBucket();
        if ("STRONG_HISTORY".equals(hist) && rnd.nextDouble() < 0.6) return EvidenceQuality.HIGH;
        if ("REPEAT_FAILURE".equals(hist) && rnd.nextDouble() < 0.5) return EvidenceQuality.LOW;
        if ("AGED".equals(obs.elapsedBucket()) && rnd.nextDouble() < 0.5) return EvidenceQuality.LOW;
        double r = rnd.nextDouble();
        if (r < 0.4) return EvidenceQuality.MEDIUM;
        if (r < 0.7) return EvidenceQuality.HIGH;
        return EvidenceQuality.LOW;
    }

    private RiskLevel deriveRisk(ObservableContext obs, Random rnd) {
        if ("VERY_HIGH".equals(obs.amountBucket()) && rnd.nextDouble() < 0.5) return RiskLevel.HIGH;
        if ("HIGH".equals(obs.amountBucket()) && rnd.nextDouble() < 0.3) return RiskLevel.MEDIUM;
        double r = rnd.nextDouble();
        if (r < 0.6) return RiskLevel.LOW;
        if (r < 0.85) return RiskLevel.MEDIUM;
        return RiskLevel.HIGH;
    }

    private CandidateAssessmentLevel deriveCandidateLevel(ObservableContext obs, RecoveryActionType action, Random rnd, AiQuality quality) {
        String gw = obs.gatewayCode();
        boolean shouldBeHigh = false;
        if (("BANK_TIMEOUT".equals(gw) || "NETWORK_ERROR".equals(gw)) && action == RecoveryActionType.SCHEDULE_RETRY) shouldBeHigh = true;
        else if ("INSUFFICIENT_FUNDS".equals(gw) && action == RecoveryActionType.SEND_PAYMENT_LINK) shouldBeHigh = true;
        else if ("CARD_EXPIRED".equals(gw) && action == RecoveryActionType.SEND_PAYMENT_LINK) shouldBeHigh = true;
        else if ("AUTH_FAILED".equals(gw) && action == RecoveryActionType.SEND_PAYMENT_LINK) shouldBeHigh = true;

        // Quality affects candidate level noise: HIGH has less noise, LOW has more noise
        double pHigh = shouldBeHigh ? (quality == AiQuality.HIGH ? 0.75 : quality == AiQuality.MEDIUM ? 0.65 : 0.45)
                                    : (quality == AiQuality.HIGH ? 0.10 : quality == AiQuality.MEDIUM ? 0.15 : 0.25);
        double r = rnd.nextDouble();
        if (r < pHigh) return CandidateAssessmentLevel.HIGH;
        if (r < pHigh + 0.25) return CandidateAssessmentLevel.MEDIUM;
        return CandidateAssessmentLevel.LOW;
    }
}
