package com.recoverflow.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import com.recoverflow.merchant.Merchant;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "opted_out", nullable = false)
    private Boolean optedOut = false;

    @Column(name = "success_count", nullable = false)
    private Integer successCount = 0;

    @Column(name = "failure_count", nullable = false)
    private Integer failureCount = 0;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Customer() {}

    public Customer(UUID id, Merchant merchant, String email, String phone) {
        this.id = id != null ? id : UUID.randomUUID();
        this.merchant = merchant;
        this.email = email;
        this.phone = phone;
        this.optedOut = false;
        this.successCount = 0;
        this.failureCount = 0;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Merchant getMerchant() { return merchant; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public Boolean getOptedOut() { return optedOut; }
    public Integer getSuccessCount() { return successCount; }
    public Integer getFailureCount() { return failureCount; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setOptedOut(Boolean optedOut) { this.optedOut = optedOut; }
    public void setSuccessCount(Integer c) { this.successCount = c; }
    public void setFailureCount(Integer c) { this.failureCount = c; }
    public void setLastSuccessAt(Instant t) { this.lastSuccessAt = t; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
}
