package com.recoverflow.synthetic;

import com.recoverflow.ai.FailureCategory;
import com.recoverflow.recovery.RecoveryActionType;
import java.util.EnumMap;
import java.util.Map;

/**
 * Hidden world registry: P_true(action) deterministically derived from hidden truth.
 * Versioned, independent of AI output, estimator, or policy.
 * This registry is the synthetic ground truth, not a learned model.
 */
public class HiddenWorldRegistry {

    public static final String VERSION = "hidden-v1";

    // Base P_true per trueFailureCategory x customerProfile x latentPropensity -> per action
    // Simplified deterministic table: values chosen to create realistic causal separations
    public static double pTrue(FailureCategory trueCategory, CustomerBehaviorProfile profile, LatentRecoveryPropensity latent, RecoveryActionType action) {
        double base = baseForCategory(trueCategory, action);
        double profileMod = profileModifier(profile, action);
        double latentMod = latentModifier(latent, action);
        double p = base + profileMod + latentMod;
        // Clamp 0.02 - 0.85, same bounds as estimator for comparability
        if (p < 0.02) p = 0.02;
        if (p > 0.85) p = 0.85;
        return p;
    }

    private static double baseForCategory(FailureCategory cat, RecoveryActionType action) {
        // Base mirrors realistic recoverability: temporary failures recover better via schedule, card expired only via link
        return switch (cat) {
            case TEMPORARY_BANK_FAILURE -> switch (action) {
                case RETRY_NOW -> 0.12; case SCHEDULE_RETRY -> 0.55; case SEND_PAYMENT_LINK -> 0.38; case SEND_REMINDER -> 0.15; default -> 0.05;
            };
            case NETWORK_ERROR -> switch (action) {
                case RETRY_NOW -> 0.10; case SCHEDULE_RETRY -> 0.52; case SEND_PAYMENT_LINK -> 0.35; case SEND_REMINDER -> 0.13; default -> 0.05;
            };
            case INSUFFICIENT_FUNDS -> switch (action) {
                case RETRY_NOW -> 0.05; case SCHEDULE_RETRY -> 0.20; case SEND_PAYMENT_LINK -> 0.42; case SEND_REMINDER -> 0.18; default -> 0.05;
            };
            case AUTH_FAILED -> switch (action) {
                case RETRY_NOW -> 0.03; case SCHEDULE_RETRY -> 0.06; case SEND_PAYMENT_LINK -> 0.36; case SEND_REMINDER -> 0.12; default -> 0.05;
            };
            case CARD_EXPIRED -> switch (action) {
                case RETRY_NOW -> 0.02; case SCHEDULE_RETRY -> 0.02; case SEND_PAYMENT_LINK -> 0.45; case SEND_REMINDER -> 0.14; default -> 0.05;
            };
            case LIMIT_EXCEEDED -> switch (action) {
                case RETRY_NOW -> 0.04; case SCHEDULE_RETRY -> 0.12; case SEND_PAYMENT_LINK -> 0.32; case SEND_REMINDER -> 0.10; default -> 0.05;
            };
            case UNKNOWN -> switch (action) {
                case RETRY_NOW -> 0.07; case SCHEDULE_RETRY -> 0.28; case SEND_PAYMENT_LINK -> 0.30; case SEND_REMINDER -> 0.11; default -> 0.05;
            };
        };
    }

    private static double profileModifier(CustomerBehaviorProfile profile, RecoveryActionType action) {
        return switch (profile) {
            case STRONG_HISTORY -> switch (action) {
                case RETRY_NOW -> 0.06; case SCHEDULE_RETRY -> 0.08; case SEND_PAYMENT_LINK -> 0.04; case SEND_REMINDER -> 0.03; default -> 0;
            };
            case REPEAT_FAILURE -> switch (action) {
                case RETRY_NOW -> -0.05; case SCHEDULE_RETRY -> -0.08; case SEND_PAYMENT_LINK -> -0.03; case SEND_REMINDER -> -0.04; default -> 0;
            };
            case NEW_CUSTOMER -> switch (action) {
                case RETRY_NOW -> -0.02; case SCHEDULE_RETRY -> -0.03; case SEND_PAYMENT_LINK -> 0.00; case SEND_REMINDER -> -0.02; default -> 0;
            };
            case HIGH_FRICTION -> switch (action) {
                case RETRY_NOW -> -0.01; case SCHEDULE_RETRY -> -0.02; case SEND_PAYMENT_LINK -> -0.04; case SEND_REMINDER -> -0.05; default -> 0;
            };
            case AVERAGE -> 0;
        };
    }

    private static double latentModifier(LatentRecoveryPropensity latent, RecoveryActionType action) {
        return switch (latent) {
            case HIGH -> switch (action) {
                case RETRY_NOW -> 0.04; case SCHEDULE_RETRY -> 0.10; case SEND_PAYMENT_LINK -> 0.08; case SEND_REMINDER -> 0.06; default -> 0;
            };
            case MEDIUM -> 0;
            case LOW -> switch (action) {
                case RETRY_NOW -> -0.04; case SCHEDULE_RETRY -> -0.10; case SEND_PAYMENT_LINK -> -0.08; case SEND_REMINDER -> -0.06; default -> 0;
            };
        };
    }
}
