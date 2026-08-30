package com.recoverflow.ai;

import com.recoverflow.recovery.RecoveryActionType;
import java.util.List;
import java.util.Objects;

/**
 * Strict structured output from AI. No numeric probability.
 * - failureCategory: qualitative classification
 * - recoverability: HIGH/MEDIUM/LOW
 * - candidateAssessments: one per automatic action in canonical universe (4 entries), with applicable flag
 * - recommendedAction: must be among applicable candidates or ESCALATE/STOP
 * - evidenceQuality: LOW/MEDIUM/HIGH (replaces numeric confidence)
 * - riskLevel: LOW/MEDIUM/HIGH
 * - reasoningSummary: concise human-readable explanation
 */
public record AiAssessment(
        FailureCategory failureCategory,
        Recoverability recoverability,
        List<CandidateAssessment> candidateAssessments,
        RecoveryActionType recommendedAction,
        EvidenceQuality evidenceQuality,
        RiskLevel riskLevel,
        String reasoningSummary,
        String modelId,
        String promptVersion
) {
    public AiAssessment {
        Objects.requireNonNull(failureCategory);
        Objects.requireNonNull(recoverability);
        Objects.requireNonNull(candidateAssessments);
        Objects.requireNonNull(evidenceQuality);
        Objects.requireNonNull(riskLevel);
        Objects.requireNonNull(reasoningSummary);
        if (candidateAssessments.size() != 4) {
            throw new IllegalArgumentException("candidateAssessments must have exactly 4 entries (canonical automatic actions)");
        }
    }
}
