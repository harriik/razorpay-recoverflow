package com.recoverflow.synthetic;

import com.recoverflow.payment.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Observable features available at decision time. Never contains hidden fields.
 */
public record ObservableCase(
        UUID caseId,
        UUID paymentId,
        UUID merchantId,
        UUID customerId,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        String gatewayCode, // observed, noisy version of trueGatewayCode
        Instant failedAt,
        int attemptCount,
        int elapsedHours,
        int priorSuccessCount,
        int priorFailureCount,
        boolean linkAlreadySent
) {}
