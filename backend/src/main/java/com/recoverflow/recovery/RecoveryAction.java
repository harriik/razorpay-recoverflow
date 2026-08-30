package com.recoverflow.recovery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recovery_actions")
public class RecoveryAction {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private RecoveryCase recoveryCase;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private RecoveryActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecoveryActionStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "gateway_ref")
    private String gatewayRef;

    @Column(name = "estimated_likelihood", precision = 4, scale = 3)
    private BigDecimal estimatedLikelihood;

    @Column(name = "expected_value", precision = 19, scale = 4)
    private BigDecimal expectedValue;

    @Column(name = "cost_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal costAmount = BigDecimal.ZERO;

    @Column(name = "synthetic_friction_proxy", nullable = false, precision = 19, scale = 4)
    private BigDecimal syntheticFrictionProxy = BigDecimal.ZERO;

    @Column(name = "risk_penalty", nullable = false, precision = 19, scale = 4)
    private BigDecimal riskPenalty = BigDecimal.ZERO;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "observed_at")
    private Instant observedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RecoveryAction() {}

    public RecoveryAction(UUID id, RecoveryCase recoveryCase, RecoveryActionType actionType,
                          RecoveryActionStatus status, String idempotencyKey) {
        this.id = id != null ? id : UUID.randomUUID();
        this.recoveryCase = recoveryCase;
        this.actionType = actionType;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public RecoveryCase getRecoveryCase() { return recoveryCase; }
    public RecoveryActionType getActionType() { return actionType; }
    public RecoveryActionStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getGatewayRef() { return gatewayRef; }
    public BigDecimal getEstimatedLikelihood() { return estimatedLikelihood; }
    public BigDecimal getExpectedValue() { return expectedValue; }
    public BigDecimal getCostAmount() { return costAmount; }
    public BigDecimal getSyntheticFrictionProxy() { return syntheticFrictionProxy; }
    public BigDecimal getRiskPenalty() { return riskPenalty; }
    public Instant getExecutedAt() { return executedAt; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(RecoveryActionStatus status) { this.status = status; }
    public void setGatewayRef(String ref) { this.gatewayRef = ref; }
    public void setEstimatedLikelihood(BigDecimal v) { this.estimatedLikelihood = v; }
    public void setExpectedValue(BigDecimal v) { this.expectedValue = v; }
    public void setCostAmount(BigDecimal v) { this.costAmount = v; }
    public void setSyntheticFrictionProxy(BigDecimal v) { this.syntheticFrictionProxy = v; }
    public void setRiskPenalty(BigDecimal v) { this.riskPenalty = v; }
    public void setExecutedAt(Instant t) { this.executedAt = t; }
    public void setObservedAt(Instant t) { this.observedAt = t; }
}
