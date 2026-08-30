package com.recoverflow.synthetic;

import com.recoverflow.recovery.RecoveryActionType;
import java.util.Map;
import java.util.UUID;

/**
 * Combined synthetic case: hidden truth + observable + ground truth outcomes (hidden sidecar).
 * Observable is used for decision; hidden/groundTruth is used only by evaluator after selection.
 */
public record SyntheticCase(
        UUID caseId,
        HiddenTruth hiddenTruth,
        ObservableCase observable,
        Map<RecoveryActionType, Double> pTrue, // P_true per action (from hidden registry)
        Map<RecoveryActionType, Boolean> groundTruthOutcomes // sampled Bernoulli per action, deterministic per seed
) {}
