package com.recoverflow.evaluation;

import com.recoverflow.recovery.RecoveryActionType;
import java.math.BigDecimal;

/**
 * Pure evaluator-side helper for comparing true expected values of
 * Policy-only vs AI-enabled selected actions.
 * No HiddenTruth, no P_true calculation, no Bernoulli outcome, no AI logic.
 * Uses already-computed trueValuePolicyOnly and trueValueAi.
 */
public class AiHelpHurtClassifier {

    public record Classification(
            boolean actionChanged,
            boolean helped,
            boolean hurt,
            boolean neutral
    ) {}

    /**
     * Classify AI decision quality via true expected values.
     * No-action is treated as true value 0 for comparison, per Slice 3B semantics.
     */
    public static Classification classify(
            RecoveryActionType selectedPolicyOnly,
            RecoveryActionType selectedAi,
            BigDecimal trueValuePolicyOnly,
            BigDecimal trueValueAi) {

        boolean changed = !java.util.Objects.equals(selectedPolicyOnly, selectedAi);

        BigDecimal tvPolicy = trueValuePolicyOnly != null ? trueValuePolicyOnly : BigDecimal.ZERO;
        BigDecimal tvAi = trueValueAi != null ? trueValueAi : BigDecimal.ZERO;

        boolean helped = false;
        boolean hurt = false;
        boolean neutral = false;

        if (!changed) {
            // Same action (including both null) -> neutral, per invariant
            neutral = true;
        } else {
            int cmp = tvAi.compareTo(tvPolicy);
            if (cmp > 0) helped = true;
            else if (cmp < 0) hurt = true;
            else neutral = true;
        }

        // Enforce invariant: if not changed, must be neutral
        if (!changed) {
            helped = false;
            hurt = false;
            neutral = true;
        }

        return new Classification(changed, helped, hurt, neutral);
    }
}
