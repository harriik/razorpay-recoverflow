package com.recoverflow.gateway;

import com.recoverflow.recovery.RecoveryActionType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Deterministic mock gateway for simulation and tests.
 * Supports SUCCESS (PAYMENT_RECOVERED), FAILURE, TIMEOUT, TRANSIENT_FAILURE, DUPLICATE, UNKNOWN, PAYMENT_LINK_SUCCESS (LINK_CREATED).
 * Behavior is configured per idempotencyKey via setScenario, or defaults to SUCCESS for unspecified keys.
 * Thread-safe.
 * No Razorpay details leak: this is pure simulation.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    public enum Scenario {
        SUCCESS,
        FAILURE_RETRYABLE,
        FAILURE_TERMINAL,
        TIMEOUT,
        TRANSIENT_FAILURE,
        UNKNOWN,
        PAYMENT_LINK_SUCCESS,
        PAYMENT_LINK_FAILURE
    }

    private final Map<String, Scenario> scenarios = new ConcurrentHashMap<>();
    private final Map<String, GatewayResult> results = new ConcurrentHashMap<>();
    private final Map<String, Integer> callCounts = new ConcurrentHashMap<>();

    @Override
    public String getMode() {
        return "SIMULATION";
    }

    /**
     * Configure deterministic scenario for a given idempotencyKey. For tests.
     */
    public void setScenario(String idempotencyKey, Scenario scenario) {
        scenarios.put(idempotencyKey, scenario);
    }

    public void clearScenarios() {
        scenarios.clear();
        results.clear();
        callCounts.clear();
    }

    public int getCallCount(String idempotencyKey) {
        return callCounts.getOrDefault(idempotencyKey, 0);
    }

    @Override
    public GatewayResult execute(UUID caseId, RecoveryActionType action, BigDecimal amount, String currency,
                                 String idempotencyKey, UUID correlationId) throws GatewayTimeoutException {
        // Idempotency: if already executed, return original DUPLICATE
        if (results.containsKey(idempotencyKey)) {
            GatewayResult original = results.get(idempotencyKey);
            callCounts.merge(idempotencyKey, 1, Integer::sum);
            return GatewayResult.duplicate(idempotencyKey, original.gatewayRef(), original.status());
        }

        callCounts.merge(idempotencyKey, 1, Integer::sum);
        Scenario scenario = scenarios.getOrDefault(idempotencyKey, inferDefaultScenario(action));

        GatewayResult result;
        String ref = "mock_" + UUID.randomUUID().toString().substring(0, 8);
        switch (scenario) {
            case SUCCESS -> result = GatewayResult.paymentRecovered(idempotencyKey, ref);
            case FAILURE_RETRYABLE -> result = GatewayResult.failure(idempotencyKey, ref, true);
            case FAILURE_TERMINAL -> result = GatewayResult.failure(idempotencyKey, ref, false);
            case TRANSIENT_FAILURE -> result = new GatewayResult(GatewayStatus.TRANSIENT_FAILURE, ref, idempotencyKey, "Transient", true);
            case TIMEOUT -> {
                // Store as UNKNOWN for reconciliation
                result = GatewayResult.unknown(idempotencyKey);
                results.put(idempotencyKey, result);
                throw new GatewayTimeoutException(idempotencyKey, "Simulated timeout for " + idempotencyKey);
            }
            case UNKNOWN -> result = GatewayResult.unknown(idempotencyKey);
            case PAYMENT_LINK_SUCCESS -> result = GatewayResult.linkCreated(idempotencyKey, "plink_" + ref);
            case PAYMENT_LINK_FAILURE -> result = GatewayResult.failure(idempotencyKey, "plink_" + ref, false);
            default -> result = GatewayResult.paymentRecovered(idempotencyKey, ref);
        }
        results.put(idempotencyKey, result);
        return result;
    }

    @Override
    public GatewayResult queryStatus(String idempotencyKey) {
        GatewayResult stored = results.get(idempotencyKey);
        if (stored == null) {
            return GatewayResult.unknown(idempotencyKey);
        }
        // For TIMEOUT/UNKNOWN scenarios, query can be configured to return SUCCESS/FAILURE via scenario override
        // We treat stored UNKNOWN as still unknown unless scenario changed to SUCCESS/FAILURE
        Scenario scenario = scenarios.get(idempotencyKey);
        if (stored.status() == GatewayStatus.UNKNOWN && scenario != null) {
            switch (scenario) {
                case SUCCESS -> {
                    GatewayResult success = GatewayResult.paymentRecovered(idempotencyKey, "mock_q_" + UUID.randomUUID().toString().substring(0, 8));
                    results.put(idempotencyKey, success);
                    return success;
                }
                case FAILURE_RETRYABLE -> {
                    GatewayResult f = GatewayResult.failure(idempotencyKey, "mock_qf_" + UUID.randomUUID().toString().substring(0, 8), true);
                    results.put(idempotencyKey, f);
                    return f;
                }
                case FAILURE_TERMINAL -> {
                    GatewayResult f = GatewayResult.failure(idempotencyKey, "mock_qf_" + UUID.randomUUID().toString().substring(0, 8), false);
                    results.put(idempotencyKey, f);
                    return f;
                }
                default -> { return stored; }
            }
        }
        return stored;
    }

    @Override
    public GatewayResult createPaymentLink(UUID caseId, BigDecimal amount, String currency,
                                           String idempotencyKey, UUID correlationId) throws GatewayTimeoutException {
        if (results.containsKey(idempotencyKey)) {
            GatewayResult original = results.get(idempotencyKey);
            return GatewayResult.duplicate(idempotencyKey, original.gatewayRef(), original.status());
        }
        callCounts.merge(idempotencyKey, 1, Integer::sum);
        Scenario scenario = scenarios.getOrDefault(idempotencyKey, Scenario.PAYMENT_LINK_SUCCESS);
        String ref = "plink_" + UUID.randomUUID().toString().substring(0, 8);
        GatewayResult result;
        switch (scenario) {
            case TIMEOUT -> {
                result = GatewayResult.unknown(idempotencyKey);
                results.put(idempotencyKey, result);
                throw new GatewayTimeoutException(idempotencyKey, "Payment link timeout");
            }
            case PAYMENT_LINK_FAILURE, FAILURE_TERMINAL, FAILURE_RETRYABLE -> result = GatewayResult.failure(idempotencyKey, ref, false);
            default -> result = GatewayResult.linkCreated(idempotencyKey, ref);
        }
        results.put(idempotencyKey, result);
        return result;
    }

    private Scenario inferDefaultScenario(RecoveryActionType action) {
        // Default: retry actions succeed, link succeeds. Tests override explicitly.
        return Scenario.SUCCESS;
    }

    /**
     * For tests: simulate customer completing payment after link created.
     * Marks the link's idempotency key as PAYMENT_RECOVERED (money recovered).
     */
    public void simulateLinkConversion(String linkIdempotencyKey, boolean success) {
        if (success) {
            results.put(linkIdempotencyKey, GatewayResult.paymentRecovered(linkIdempotencyKey, "pay_after_link_" + UUID.randomUUID().toString().substring(0, 8)));
        } else {
            results.put(linkIdempotencyKey, GatewayResult.failure(linkIdempotencyKey, null, false));
        }
    }
}
