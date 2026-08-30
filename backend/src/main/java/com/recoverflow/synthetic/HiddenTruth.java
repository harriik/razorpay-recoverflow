package com.recoverflow.synthetic;

import com.recoverflow.ai.FailureCategory;
import java.util.Map;
import com.recoverflow.recovery.RecoveryActionType;

/**
 * Hidden world truth — never reaches decision service.
 * Determines P_true and ground truth outcomes.
 */
public record HiddenTruth(
        FailureCategory trueFailureCategory,
        CustomerBehaviorProfile customerBehaviorProfile,
        LatentRecoveryPropensity latentRecoveryPropensity,
        String trueGatewayCode, // hidden true code (may differ from observed gatewayCode)
        Map<RecoveryActionType, Double> actionOutcomeDistribution // P_true per action
) {}
