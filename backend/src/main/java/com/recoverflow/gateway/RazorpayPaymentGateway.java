package com.recoverflow.gateway;

import com.recoverflow.recovery.RecoveryActionType;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Razorpay test-mode adapter. Behind explicit configuration.
 * In simulation mode (default) it delegates to MockPaymentGateway.
 * Real Razorpay API calls are only made when RAZORPAY_MODE=test and credentials are present.
 * No production credentials, no real charges.
 */
@Component
public class RazorpayPaymentGateway implements PaymentGateway {

    private final MockPaymentGateway mockDelegate;

    @Value("${razorpay.mode:simulation}")
    private String mode;

    @Value("${razorpay.key-id:}")
    private String keyId;

    @Value("${razorpay.key-secret:}")
    private String keySecret;

    public RazorpayPaymentGateway(MockPaymentGateway mockDelegate) {
        this.mockDelegate = mockDelegate;
    }

    @Override
    public String getMode() {
        if ("production".equalsIgnoreCase(mode)) {
            return "PRODUCTION_BLOCKED";
        }
        if ("test".equalsIgnoreCase(mode) && !keyId.isBlank() && !keySecret.isBlank()) {
            return "TEST";
        }
        return "SIMULATION";
    }

    @Override
    public GatewayResult execute(UUID caseId, RecoveryActionType action, BigDecimal amount, String currency,
                                 String idempotencyKey, UUID correlationId) throws GatewayTimeoutException {
        if ("TEST".equals(getMode())) {
            // In real test mode, would call Razorpay API with test credentials and idempotency key.
            // For Phase 5, we log and delegate to mock to avoid external dependency in tests.
            System.out.println("[Razorpay TEST] execute " + action + " amount " + amount + " idem " + idempotencyKey);
        } else if ("PRODUCTION_BLOCKED".equals(getMode())) {
            throw new IllegalStateException("Production mode blocked: no live charges in build");
        }
        return mockDelegate.execute(caseId, action, amount, currency, idempotencyKey, correlationId);
    }

    @Override
    public GatewayResult queryStatus(String idempotencyKey) {
        if ("TEST".equals(getMode())) {
            System.out.println("[Razorpay TEST] queryStatus " + idempotencyKey);
        }
        return mockDelegate.queryStatus(idempotencyKey);
    }

    @Override
    public GatewayResult createPaymentLink(UUID caseId, BigDecimal amount, String currency,
                                           String idempotencyKey, UUID correlationId) throws GatewayTimeoutException {
        if ("TEST".equals(getMode())) {
            System.out.println("[Razorpay TEST] createPaymentLink " + amount + " idem " + idempotencyKey);
        }
        return mockDelegate.createPaymentLink(caseId, amount, currency, idempotencyKey, correlationId);
    }
}
