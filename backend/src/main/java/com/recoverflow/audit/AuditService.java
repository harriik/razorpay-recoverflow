package com.recoverflow.audit;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AuditEvent record(UUID correlationId, UUID caseId, UUID paymentId, UUID merchantId,
                             String eventType, String fromState, String toState,
                             AuditActor actor, String payload) {
        AuditEvent event = new AuditEvent(
                UUID.randomUUID(),
                correlationId != null ? correlationId : UUID.randomUUID(),
                caseId,
                paymentId,
                merchantId,
                eventType,
                fromState,
                toState,
                actor,
                payload
        );
        return repository.save(event);
    }
}
