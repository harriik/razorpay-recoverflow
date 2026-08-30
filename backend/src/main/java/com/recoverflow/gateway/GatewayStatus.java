package com.recoverflow.gateway;

public enum GatewayStatus {
    SUCCESS,           // Payment recovered / link created successfully
    FAILURE,           // Definitive failure (may be retryable or terminal)
    TRANSIENT_FAILURE, // Temporary failure, retryable
    TIMEOUT,           // Request timed out, outcome unknown
    UNKNOWN,           // Status cannot be determined
    DUPLICATE          // Duplicate idempotency key, original result should be returned
}
