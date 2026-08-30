package com.recoverflow.policy;

import com.recoverflow.recovery.RecoveryActionType;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Deterministic policy context built from observable data only.
 * Must never contain AI self-assessment fields (riskLevel, evidenceQuality, recoverability).
 * All fields are verifiable at decision time without LLM.
 */
public record PolicyContext(
        BigDecimal amount,
        String gatewayCode,
        int attemptCount,
        int elapsedHours,
        boolean optedOut,
        boolean linkAlreadySent,
        RecoveryActionType candidateAction,
        // Merchant/policy thresholds (snapshot)
        BigDecimal autoActionLimit,
        int maxRetries,
        int recoveryWindowHours
) {
    public PolicyContext {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(gatewayCode, "gatewayCode");
        Objects.requireNonNull(candidateAction, "candidateAction");
        Objects.requireNonNull(autoActionLimit, "autoActionLimit");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
    }
}
