package com.recoverflow.payment;

import com.recoverflow.customer.Customer;
import com.recoverflow.merchant.Merchant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @NotNull
    @DecimalMin(value = "0.01")
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "gateway_code")
    private String gatewayCode;

    @Column(name = "gateway_ref")
    private String gatewayRef;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {}

    public Payment(UUID id, Merchant merchant, Customer customer, BigDecimal amount, String currency,
                   PaymentMethod method, PaymentStatus status, String gatewayCode, String gatewayRef, Instant failedAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.merchant = merchant;
        this.customer = customer;
        this.amount = amount;
        this.currency = currency != null ? currency : "INR";
        this.method = method;
        this.status = status;
        this.gatewayCode = gatewayCode;
        this.gatewayRef = gatewayRef;
        this.failedAt = failedAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Merchant getMerchant() { return merchant; }
    public Customer getCustomer() { return customer; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public String getGatewayCode() { return gatewayCode; }
    public String getGatewayRef() { return gatewayRef; }
    public Instant getFailedAt() { return failedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setGatewayCode(String code) { this.gatewayCode = code; }
}
