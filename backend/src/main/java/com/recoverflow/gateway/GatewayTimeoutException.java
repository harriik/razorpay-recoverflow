package com.recoverflow.gateway;

public class GatewayTimeoutException extends Exception {
    private final String idempotencyKey;

    public GatewayTimeoutException(String idempotencyKey, String message) {
        super(message);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
}
