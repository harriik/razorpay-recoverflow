package com.recoverflow.recovery;

import com.recoverflow.audit.AuditActor;
import com.recoverflow.audit.AuditService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecoveryCaseService {

    private final RecoveryCaseRepository caseRepository;
    private final RecoveryCaseStateMachine stateMachine;
    private final AuditService auditService;

    public RecoveryCaseService(RecoveryCaseRepository caseRepository,
                               RecoveryCaseStateMachine stateMachine,
                               AuditService auditService) {
        this.caseRepository = caseRepository;
        this.stateMachine = stateMachine;
        this.auditService = auditService;
    }

    /**
     * Transition a case to a new status with validation and audit.
     * Persists the case and creates an audit event atomically.
     */
    @Transactional
    public RecoveryCase transition(RecoveryCase recoveryCase,
                                   RecoveryCaseStatus newStatus,
                                   AuditActor actor,
                                   UUID correlationId,
                                   String eventType) {
        RecoveryCaseStatus from = recoveryCase.getStatus();
        stateMachine.validate(from, newStatus);

        String payload = "{\"from\":\"" + from + "\",\"to\":\"" + newStatus + "\"}";
        recoveryCase.setStatus(newStatus);

        // Handle UNKNOWN timestamp
        if (newStatus == RecoveryCaseStatus.UNKNOWN) {
            recoveryCase.setUnknownSince(Instant.now());
        } else if (from == RecoveryCaseStatus.UNKNOWN) {
            recoveryCase.setUnknownSince(null);
        }

        RecoveryCase saved = caseRepository.save(recoveryCase);

        UUID corr = correlationId != null ? correlationId : UUID.randomUUID();
        auditService.record(
                corr,
                saved.getId(),
                saved.getPayment() != null ? saved.getPayment().getId() : null,
                saved.getMerchant() != null ? saved.getMerchant().getId() : null,
                eventType != null ? eventType : "STATE_TRANSITION",
                from.name(),
                newStatus.name(),
                actor != null ? actor : AuditActor.SYSTEM,
                payload
        );

        return saved;
    }

    @Transactional
    public RecoveryCase transition(UUID caseId, RecoveryCaseStatus newStatus, AuditActor actor, UUID correlationId) {
        RecoveryCase rc = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("RecoveryCase not found: " + caseId));
        return transition(rc, newStatus, actor, correlationId, "STATE_TRANSITION");
    }

    public RecoveryCaseStateMachine getStateMachine() {
        return stateMachine;
    }
}
