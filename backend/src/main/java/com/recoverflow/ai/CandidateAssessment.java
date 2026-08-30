package com.recoverflow.ai;

import com.recoverflow.recovery.RecoveryActionType;
import java.util.Objects;

/**
 * Qualitative assessment per candidate. Never a numeric P.
 * Applicable=false means the candidate is not relevant for this case (e.g. SEND_REMINDER without prior link).
 */
public record CandidateAssessment(
        RecoveryActionType action,
        CandidateAssessmentLevel assessment,
        boolean applicable,
        String inapplicableReason
) {
    public CandidateAssessment {
        Objects.requireNonNull(action, "action");
        if (applicable) {
            Objects.requireNonNull(assessment, "assessment required when applicable");
        }
    }

    public static CandidateAssessment applicable(RecoveryActionType action, CandidateAssessmentLevel level) {
        return new CandidateAssessment(action, level, true, null);
    }

    public static CandidateAssessment notApplicable(RecoveryActionType action, String reason) {
        return new CandidateAssessment(action, null, false, reason);
    }
}
