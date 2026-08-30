package com.recoverflow.gateway;

public enum GatewayStatus {
    PAYMENT_RECOVERED,          // Actual payment success/capture confirmed — money recovered
    LINK_CREATED,               // Payment link created, not yet paid — customer action required
    PENDING,                    // Action accepted, pending execution
    CUSTOMER_ACTION_REQUIRED,   // Requires customer-initiated action (e.g., checkout)
    SUCCESS,                    // Deprecated alias for PAYMENT_RECOVERED; retained for backward mock compatibility
    FAILURE,                    // Definitive failure (may be retryable or terminal)
    TRANSIENT_FAILURE,          // Temporary failure, retryable
    TIMEOUT,                    // Request timed out, outcome unknown — must reconcile
    UNKNOWN,                    // Status cannot be determined — must reconcile
    DUPLICATE                   // Duplicate idempotency key, original result should be returned
}
