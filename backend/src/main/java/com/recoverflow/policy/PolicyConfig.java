package com.recoverflow.policy;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Versioned policy configuration. All thresholds are deterministic and auditable.
 * No AI fields influence these values.
 */
@Component
public class PolicyConfig {

    public static final String VERSION = "v1";

    private BigDecimal autoActionLimit = new BigDecimal("10000.0000");
    private int maxRetries = 3;
    private int recoveryWindowHours = 48;

    public String getVersion() { return VERSION; }

    public BigDecimal getAutoActionLimit() { return autoActionLimit; }
    public int getMaxRetries() { return maxRetries; }
    public int getRecoveryWindowHours() { return recoveryWindowHours; }

    // For testing / merchant overrides
    public PolicyConfig withAutoLimit(BigDecimal limit) {
        this.autoActionLimit = limit;
        return this;
    }
    public PolicyConfig withMaxRetries(int max) { this.maxRetries = max; return this; }
    public PolicyConfig withWindow(int hours) { this.recoveryWindowHours = hours; return this; }
}
