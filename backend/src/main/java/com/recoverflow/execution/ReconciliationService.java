package com.recoverflow.execution;

import com.recoverflow.audit.AuditActor;
import com.recoverflow.audit.AuditService;
import com.recoverflow.gateway.GatewayResult;
import com.recoverflow.gateway.GatewayStatus;
import com.recoverflow.gateway.PaymentGateway;
import com.recoverflow.recovery.RecoveryAction;
import com.recoverflow.recovery.RecoveryActionRepository;
import com.recoverflow.recovery.RecoveryActionStatus;
import com.recoverflow.recovery.RecoveryCase;
import com.recoverflow.recovery.RecoveryCaseRepository;
import com.recoverflow.recovery.RecoveryCaseStateMachine;
import com.recoverflow.recovery.RecoveryCaseStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles reconciliation of UNKNOWN execution states.
 * Queries gateway for actual status and transitions case accordingly.
 * Never blindly retries.
 */
@Service
public class ReconciliationService {

    private final RecoveryCaseRepository caseRepo;
    private final RecoveryActionRepository actionRepo;
    private final RecoveryCaseStateMachine stateMachine;
    private final PaymentGateway gateway;
    private final AuditService auditService;

    public ReconciliationService(RecoveryCaseRepository caseRepo,
                                 RecoveryActionRepository actionRepo,
                                 RecoveryCaseStateMachine stateMachine,
                                 PaymentGateway gateway,
                                 AuditService auditService) {
        this.caseRepo = caseRepo;
        this.actionRepo = actionRepo;
        this.stateMachine = stateMachine;
        this.gateway = gateway;
        this.auditService = auditService;
    }

    @Transactional
    public ReconciliationResult reconcile(UUID caseId, UUID correlationId) {
        UUID corr = correlationId != null ? correlationId : UUID.randomUUID();
        RecoveryCase rc = caseRepo.findById(caseId).orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        if (rc.getStatus() != RecoveryCaseStatus.UNKNOWN) {
            return new ReconciliationResult(false, "Case not in UNKNOWN state: " + rc.getStatus(), rc.getStatus(), null, corr);
        }

        // Find the UNKNOWN action (most recent)
        Optional<RecoveryAction> unknownOpt = actionRepo.findByRecoveryCaseId(caseId).stream()
                .filter(a -> a.getStatus() == RecoveryActionStatus.UNKNOWN)
                .findFirst();

        if (unknownOpt.isEmpty()) {
            return new ReconciliationResult(false, "No UNKNOWN action found", rc.getStatus(), null, corr);
        }
        RecoveryAction unknownAction = unknownOpt.get();
        String key = unknownAction.getIdempotencyKey();

        auditService.record(corr, rc.getId(), rc.getPayment().getId(), rc.getMerchant().getId(),
                "RECONCILE_ATTEMPTED", "UNKNOWN", "UNKNOWN", AuditActor.SYSTEM,
                "{\"idempotencyKey\":\"" + key + "\"}");

        GatewayResult queried = gateway.queryStatus(key);

        // Revenue is recovered ONLY when payment success is confirmed (PAYMENT_RECOVERED or SUCCESS legacy)
        if (queried.isPaymentRecovered()) {
            // Was payment recovered despite timeout -> RECOVERED
            unknownAction.setStatus(RecoveryActionStatus.SUCCESS);
            unknownAction.setGatewayRef(queried.gatewayRef());
            unknownAction.setObservedAt(Instant.now());
            actionRepo.save(unknownAction);

            try {
                stateMachine.validate(rc.getStatus(), RecoveryCaseStatus.RECOVERED);
                rc.setStatus(RecoveryCaseStatus.RECOVERED);
                rc.setRecoveredAmount(rc.getAmount());
                rc.setUnknownSince(null);
                rc.setPendingAction(null);
                rc.setPendingReason(null);
                caseRepo.save(rc);
                auditService.record(corr, rc.getId(), rc.getPayment().getId(), rc.getMerchant().getId(),
                        "RECONCILED_SUCCESS", "UNKNOWN", "RECOVERED", AuditActor.GATEWAY,
                        "{\"gatewayRef\":\"" + queried.gatewayRef() + "\"}");
                return new ReconciliationResult(true, "Reconciled as PAYMENT_RECOVERED", RecoveryCaseStatus.RECOVERED, queried, corr);
            } catch (Exception e) {
                return new ReconciliationResult(false, "Transition failed: " + e.getMessage(), rc.getStatus(), queried, corr);
            }
        } else if (queried.isLinkCreated() || queried.status() == GatewayStatus.PENDING || queried.status() == GatewayStatus.CUSTOMER_ACTION_REQUIRED) {
            // Link still pending, not recovered — remain UNKNOWN or go to RETRY_PENDING if link was the original action
            auditService.record(corr, rc.getId(), rc.getPayment().getId(), rc.getMerchant().getId(),
                    "RECONCILED_LINK_PENDING", "UNKNOWN", "UNKNOWN", AuditActor.GATEWAY,
                    "{\"gatewayRef\":\"" + queried.gatewayRef() + "\"}");
            return new ReconciliationResult(true, "Link pending, not recovered", RecoveryCaseStatus.UNKNOWN, queried, corr);
        } else if (queried.status() == GatewayStatus.FAILURE) {
            boolean retryable = queried.retryable();
            unknownAction.setStatus(RecoveryActionStatus.FAILED);
            unknownAction.setGatewayRef(queried.gatewayRef());
            unknownAction.setObservedAt(Instant.now());
            actionRepo.save(unknownAction);

            RecoveryCaseStatus next = retryable ? RecoveryCaseStatus.ACTION_FAILED : RecoveryCaseStatus.FAILED_TERMINAL;
            try {
                stateMachine.validate(rc.getStatus(), next);
                rc.setStatus(next);
                rc.setUnknownSince(null);
                caseRepo.save(rc);
                auditService.record(corr, rc.getId(), rc.getPayment().getId(), rc.getMerchant().getId(),
                        retryable ? "RECONCILED_FAILED_RETRYABLE" : "RECONCILED_FAILED_TERMINAL",
                        "UNKNOWN", next.name(), AuditActor.GATEWAY,
                        "{\"retryable\":" + retryable + "}");
                return new ReconciliationResult(true, "Reconciled as " + next, next, queried, corr);
            } catch (Exception e) {
                return new ReconciliationResult(false, "Transition failed: " + e.getMessage(), rc.getStatus(), queried, corr);
            }

        } else if (queried.status() == GatewayStatus.UNKNOWN) {
            auditService.record(corr, rc.getId(), rc.getPayment().getId(), rc.getMerchant().getId(),
                    "RECONCILED_STILL_UNKNOWN", "UNKNOWN", "UNKNOWN", AuditActor.GATEWAY, "{}");
            return new ReconciliationResult(true, "Still UNKNOWN", RecoveryCaseStatus.UNKNOWN, queried, corr);
        } else {
            // For other statuses like TRANSIENT_FAILURE, treat as ACTION_FAILED
            unknownAction.setStatus(RecoveryActionStatus.FAILED);
            actionRepo.save(unknownAction);
            try {
                stateMachine.validate(rc.getStatus(), RecoveryCaseStatus.ACTION_FAILED);
                rc.setStatus(RecoveryCaseStatus.ACTION_FAILED);
                rc.setUnknownSince(null);
                caseRepo.save(rc);
            } catch (Exception ignored) {}
            return new ReconciliationResult(true, "Reconciled as ACTION_FAILED", RecoveryCaseStatus.ACTION_FAILED, queried, corr);
        }
    }

    public record ReconciliationResult(
            boolean success,
            String message,
            RecoveryCaseStatus caseStatus,
            GatewayResult gatewayResult,
            UUID correlationId
    ) {}
}
