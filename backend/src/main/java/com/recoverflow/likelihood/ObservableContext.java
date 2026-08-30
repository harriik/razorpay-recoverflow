package com.recoverflow.likelihood;

import com.recoverflow.payment.PaymentMethod;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Allowlisted observable features available at decision time.
 * Must never contain HiddenTruth fields: trueFailureCategory, latentRecoveryPropensity, groundTruthOutcome, etc.
 * Built from RecoveryCase + Payment + Customer history via explicit mapper.
 */
public record ObservableContext(
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        String gatewayCode,
        int elapsedHours,
        int attemptCount,
        int priorSuccessCount,
        int priorFailureCount,
        boolean linkAlreadySent
) {
    public ObservableContext {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(method);
        Objects.requireNonNull(gatewayCode);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
    }

    public String amountBucket() {
        // Tiers for base table lookup; kept coarse to avoid overfitting synthetic data
        if (amount.compareTo(new BigDecimal("2000")) < 0) return "LOW";
        if (amount.compareTo(new BigDecimal("10000")) < 0) return "MEDIUM";
        if (amount.compareTo(new BigDecimal("30000")) < 0) return "HIGH";
        return "VERY_HIGH";
    }

    public String historyBucket() {
        if (priorSuccessCount >= 10) return "STRONG_HISTORY";
        if (priorFailureCount >= 3) return "REPEAT_FAILURE";
        if (priorSuccessCount == 0 && priorFailureCount == 0) return "NEW_CUSTOMER";
        return "AVERAGE";
    }

    public String elapsedBucket() {
        if (elapsedHours <= 2) return "FRESH";
        if (elapsedHours <= 24) return "WITHIN_DAY";
        return "AGED";
    }
}
