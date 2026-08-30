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
import org.springframework.stereotype.Component;

/**
 * Deterministic SYNTHETIC AI PROXY — versioned, auditable, observable-only.
 * NEVER reads HiddenTruth, P_true, latentRecoveryPropensity, groundTruthOutcomes, trueFailureCategory.
 * Input: ObservableContext only (gatewayCode, method, amount bucket, history, elapsed, attemptCount, linkAlreadySent).
 * Output: qualitative AiAssessment (failureCategory, recoverability, candidate assessments, evidenceQuality, riskLevel).
 *
 * This proxy enables reproducible large-scale evaluation (3000 cases) without requiring a live LLM.
 * Real LLM mode (REAL_LLM) uses same ObservableContext and same output schema but via actual model API.
 *
 * Large-scale benchmark results are labeled "Synthetic AI Proxy" and are NOT claimed as real LLM performance.
 */
@Component
public class SyntheticAiProxy {

    public static final String VERSION = "synthetic-ai-v1";

    /**
     * Produce AI assessment from observable only. Deterministic per (observable + seed).
     * The proxy is intentionally imperfect: it is correct with observable-dependent accuracy, wrong otherwise.
     */
    public AiAssessment assess(ObservableContext obs, long seed) {
        // Deterministic per case: use observable hash + seed
        long perCaseSeed = seed ^ obs.gatewayCode().hashCode() ^ obs.amount().hashCode() ^ obs.elapsedHours() ^ 0x9E3779B97F4A7C15L;
        Random rnd = new Random(perCaseSeed);

        FailureCategory observedCat = mapGatewayToCategory(obs.gatewayCode());
        // Proxy accuracy depends on observable clarity: 70-80% correct, else random wrong
        // Amount/History/Elapsed do NOT give hidden truth, but influence proxy's confidence
        double accuracy = 0.72;
        if (obs.historyBucket().equals("STRONG_HISTORY")) accuracy += 0.08;
        if (obs.elapsedBucket().equals("AGED")) accuracy -= 0.10;
        if (obs.amountBucket().equals("VERY_HIGH")) accuracy -= 0.05;

        FailureCategory aiCat;
        if (rnd.nextDouble() < accuracy) {
            aiCat = observedCat;
        } else {
            FailureCategory[] all = FailureCategory.values();
            do { aiCat = all[rnd.nextInt(all.length)]; } while (aiCat == observedCat);
        }

        // Recoverability derived from observable gateway + amount + history (not hidden)
        Recoverability rec = deriveRecoverability(obs, rnd);
        EvidenceQuality eq = deriveEvidenceQuality(obs, rnd);
        RiskLevel risk = deriveRisk(obs, rnd);

        // Candidate assessments derived from observable features only, with versioned table
        // Each action's assessment is HEIGH if that action's observable base is highest for this gateway bucket, else random
        // This is observable-only, not P_true dependent
        List<CandidateAssessment> list = new ArrayList<>();
        for (RecoveryActionType action : List.of(RecoveryActionType.RETRY_NOW, RecoveryActionType.SCHEDULE_RETRY, RecoveryActionType.SEND_PAYMENT_LINK, RecoveryActionType.SEND_REMINDER)) {
            CandidateAssessmentLevel level = deriveCandidateLevel(obs, action, rnd);
            // Applicability: SEND_REMINDER only applicable if linkAlreadySent
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

        return new AiAssessment(aiCat, rec, list, recommended, eq, risk,
                "Synthetic AI proxy v1 observable-only for gateway=" + obs.gatewayCode() + " bucket=" + obs.amountBucket(),
                "synthetic-ai-proxy-" + VERSION, VERSION);
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
        // Observable-driven: BANK_TIMEOUT/NETWORK_ERROR -> more HIGH, CARD_EXPIRED -> more LOW
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
        // Strong history -> HIGH, aged/new -> LOW
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
        // Very high amount -> higher risk
        if ("VERY_HIGH".equals(obs.amountBucket()) && rnd.nextDouble() < 0.5) return RiskLevel.HIGH;
        if ("HIGH".equals(obs.amountBucket()) && rnd.nextDouble() < 0.3) return RiskLevel.MEDIUM;
        double r = rnd.nextDouble();
        if (r < 0.6) return RiskLevel.LOW;
        if (r < 0.85) return RiskLevel.MEDIUM;
        return RiskLevel.HIGH;
    }

    private CandidateAssessmentLevel deriveCandidateLevel(ObservableContext obs, RecoveryActionType action, Random rnd) {
        // Versioned observable-only table: which action is plausible for which gateway bucket?
        // This table is auditable and does NOT use P_true.
        String gw = obs.gatewayCode();
        String bucket = obs.amountBucket();
        // Deterministic mapping: for each gateway, define plausible HIGH actions
        boolean shouldBeHigh = false;
        if (("BANK_TIMEOUT".equals(gw) || "NETWORK_ERROR".equals(gw)) && action == RecoveryActionType.SCHEDULE_RETRY) shouldBeHigh = true;
        else if ("INSUFFICIENT_FUNDS".equals(gw) && action == RecoveryActionType.SEND_PAYMENT_LINK) shouldBeHigh = true;
        else if ("CARD_EXPIRED".equals(gw) && action == RecoveryActionType.SEND_PAYMENT_LINK) shouldBeHigh = true;
        else if ("AUTH_FAILED".equals(gw) && action == RecoveryActionType.SEND_PAYMENT_LINK) shouldBeHigh = true;

        double pHigh = shouldBeHigh ? 0.65 : 0.15;
        double r = rnd.nextDouble();
        if (r < pHigh) return CandidateAssessmentLevel.HIGH;
        if (r < pHigh + 0.25) return CandidateAssessmentLevel.MEDIUM;
        return CandidateAssessmentLevel.LOW;
    }

    public String getVersion() { return VERSION; }
}
