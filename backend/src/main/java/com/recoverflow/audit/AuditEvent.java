package com.recoverflow.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "from_state")
    private String fromState;

    @Column(name = "to_state")
    private String toState;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor", nullable = false)
    private AuditActor actor;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {}

    public AuditEvent(UUID id, UUID correlationId, UUID caseId, UUID paymentId, UUID merchantId,
                      String eventType, String fromState, String toState, AuditActor actor, String payload) {
        this.id = id != null ? id : UUID.randomUUID();
        this.correlationId = correlationId;
        this.caseId = caseId;
        this.paymentId = paymentId;
        this.merchantId = merchantId;
        this.eventType = eventType;
        this.fromState = fromState;
        this.toState = toState;
        this.actor = actor;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCorrelationId() { return correlationId; }
    public UUID getCaseId() { return caseId; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getMerchantId() { return merchantId; }
    public String getEventType() { return eventType; }
    public String getFromState() { return fromState; }
    public String getToState() { return toState; }
    public AuditActor getActor() { return actor; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}
