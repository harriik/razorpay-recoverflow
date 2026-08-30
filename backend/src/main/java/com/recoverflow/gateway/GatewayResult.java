package com.recoverflow.gateway;

import java.util.Objects;

public record GatewayResult(
        GatewayStatus status,
        String gatewayRef,
        String idempotencyKey,
        String message,
        boolean retryable
) {
    public GatewayResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    }

    public static GatewayResult success(String idem, String ref) {
        return new GatewayResult(GatewayStatus.SUCCESS, ref, idem, "Payment recovered", false);
    }

    public static GatewayResult failure(String idem, String ref, boolean retryable) {
        return new GatewayResult(GatewayStatus.FAILURE, ref, idem, retryable ? "Transient failure, retryable" : "Permanent failure", retryable);
    }

    public static GatewayResult timeout(String idem) {
        return new GatewayResult(GatewayStatus.TIMEOUT, null, idem, "Gateway timeout, outcome unknown", true);
    }

    public static GatewayResult unknown(String idem) {
        return new GatewayResult(GatewayStatus.UNKNOWN, null, idem, "Unknown outcome", true);
    }

    public static GatewayResult duplicate(String idem, String originalRef, GatewayStatus originalStatus) {
        return new GatewayResult(GatewayStatus.DUPLICATE, originalRef, idem, "Duplicate idempotency key", false);
    }
}
