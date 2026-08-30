package com.recoverflow.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "razorpay")
public class RazorpayProperties {
    /**
     * Mode: simulation (default), test, production (blocked)
     */
    private String mode = "simulation";
    private String keyId = "";
    private String keySecret = "";
    private String baseUrl = "https://api.razorpay.com/v1";
    private int timeoutMs = 3000;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public String getKeySecret() { return keySecret; }
    public void setKeySecret(String keySecret) { this.keySecret = keySecret; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public boolean isTestMode() {
        return "test".equalsIgnoreCase(mode) && !keyId.isBlank() && !keySecret.isBlank();
    }

    public boolean isProductionBlocked() {
        return "production".equalsIgnoreCase(mode);
    }
}
