package com.recoverflow.recovery;

import com.recoverflow.customer.Customer;
import com.recoverflow.merchant.Merchant;
import com.recoverflow.payment.Payment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recovery_cases")
public class RecoveryCase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_category")
    private String failureCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecoveryCaseStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "recovered_amount", precision = 19, scale = 4)
    private BigDecimal recoveredAmount;

    @Column(name = "escalated_reason")
    private String escalatedReason;

    @Column(name = "stopped_reason")
    private String stoppedReason;

    @Column(name = "unknown_since")
    private Instant unknownSince;

    @Column(name = "approved_action")
    private String approvedAction;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_policy_version")
    private String approvedPolicyVersion;

    @Column(name = "approved_policy_decision_id")
    private UUID approvedPolicyDecisionId;

    @Column(name = "approved_threshold_snapshot", columnDefinition = "TEXT")
    private String approvedThresholdSnapshot;

    @Column(name = "pending_action")
    private String pendingAction;

    @Column(name = "pending_reason")
    private String pendingReason;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RecoveryCase() {}

    public RecoveryCase(UUID id, Payment payment, Merchant merchant, Customer customer,
                        BigDecimal amount, String currency, String failureCode, RecoveryCaseStatus status) {
        this.id = id != null ? id : UUID.randomUUID();
        this.payment = payment;
        this.merchant = merchant;
        this.customer = customer;
        this.amount = amount;
        this.currency = currency != null ? currency : "INR";
        this.failureCode = failureCode;
        this.status = status != null ? status : RecoveryCaseStatus.DETECTED;
        this.attemptCount = 0;
        this.version = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Payment getPayment() { return payment; }
    public Merchant getMerchant() { return merchant; }
    public Customer getCustomer() { return customer; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getFailureCode() { return failureCode; }
    public String getFailureCategory() { return failureCategory; }
    public RecoveryCaseStatus getStatus() { return status; }
    public Integer getAttemptCount() { return attemptCount; }
    public BigDecimal getRecoveredAmount() { return recoveredAmount; }
    public String getEscalatedReason() { return escalatedReason; }
    public String getStoppedReason() { return stoppedReason; }
    public Instant getUnknownSince() { return unknownSince; }
    public Integer getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(RecoveryCaseStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
    public void setAttemptCount(Integer c) { this.attemptCount = c; this.updatedAt = Instant.now(); }
    public void setFailureCategory(String cat) { this.failureCategory = cat; }
    public void setRecoveredAmount(BigDecimal amt) { this.recoveredAmount = amt; }
    public void setEscalatedReason(String r) { this.escalatedReason = r; }
    public void setStoppedReason(String r) { this.stoppedReason = r; }
    public void setUnknownSince(Instant t) { this.unknownSince = t; }
    public void setFailureCode(String code) { this.failureCode = code; }
    public String getApprovedAction() { return approvedAction; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getApprovedPolicyVersion() { return approvedPolicyVersion; }
    public UUID getApprovedPolicyDecisionId() { return approvedPolicyDecisionId; }
    public String getApprovedThresholdSnapshot() { return approvedThresholdSnapshot; }
    public String getPendingAction() { return pendingAction; }
    public String getPendingReason() { return pendingReason; }
    public void setApprovedAction(String a) { this.approvedAction = a; this.updatedAt = Instant.now(); }
    public void setApprovedAt(Instant t) { this.approvedAt = t; this.updatedAt = Instant.now(); }
    public void setApprovedPolicyVersion(String v) { this.approvedPolicyVersion = v; this.updatedAt = Instant.now(); }
    public void setApprovedPolicyDecisionId(UUID id) { this.approvedPolicyDecisionId = id; this.updatedAt = Instant.now(); }
    public void setApprovedThresholdSnapshot(String s) { this.approvedThresholdSnapshot = s; this.updatedAt = Instant.now(); }
    public void setPendingAction(String a) { this.pendingAction = a; this.updatedAt = Instant.now(); }
    public void setPendingReason(String r) { this.pendingReason = r; this.updatedAt = Instant.now(); }
}
