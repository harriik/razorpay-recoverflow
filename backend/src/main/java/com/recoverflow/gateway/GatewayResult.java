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
        // Backward alias: maps to PAYMENT_RECOVERED. New code should use paymentRecovered().
        return paymentRecovered(idem, ref);
    }

    public static GatewayResult paymentRecovered(String idem, String ref) {
        return new GatewayResult(GatewayStatus.PAYMENT_RECOVERED, ref, idem, "Payment recovered", false);
    }

    public static GatewayResult linkCreated(String idem, String ref) {
        return new GatewayResult(GatewayStatus.LINK_CREATED, ref, idem, "Payment link created, awaiting customer", false);
    }

    public static GatewayResult pending(String idem, String ref) {
        return new GatewayResult(GatewayStatus.PENDING, ref, idem, "Action accepted, pending", false);
    }

    public static GatewayResult customerActionRequired(String idem, String ref) {
        return new GatewayResult(GatewayStatus.CUSTOMER_ACTION_REQUIRED, ref, idem, "Customer action required", false);
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

    /** Revenue is recovered ONLY when status is PAYMENT_RECOVERED (or SUCCESS legacy). */
    public boolean isPaymentRecovered() {
        return status == GatewayStatus.PAYMENT_RECOVERED || status == GatewayStatus.SUCCESS;
    }

    public boolean isLinkCreated() {
        return status == GatewayStatus.LINK_CREATED;
    }
}
