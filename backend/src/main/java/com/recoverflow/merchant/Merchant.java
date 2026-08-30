package com.recoverflow.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "auto_action_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal autoActionLimit;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    @Column(name = "recovery_window_hours", nullable = false)
    private Integer recoveryWindowHours;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Merchant() {}

    public Merchant(UUID id, String name, BigDecimal autoActionLimit, Integer maxRetries, Integer recoveryWindowHours) {
        this.id = id != null ? id : UUID.randomUUID();
        this.name = name;
        this.autoActionLimit = autoActionLimit;
        this.maxRetries = maxRetries;
        this.recoveryWindowHours = recoveryWindowHours;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getAutoActionLimit() { return autoActionLimit; }
    public Integer getMaxRetries() { return maxRetries; }
    public Integer getRecoveryWindowHours() { return recoveryWindowHours; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setAutoActionLimit(BigDecimal limit) { this.autoActionLimit = limit; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
    public void setRecoveryWindowHours(Integer hours) { this.recoveryWindowHours = hours; }
}
