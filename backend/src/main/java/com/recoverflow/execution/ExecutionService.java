package com.recoverflow.execution;

import com.recoverflow.audit.AuditActor;
import com.recoverflow.audit.AuditService;
import com.recoverflow.gateway.GatewayResult;
import com.recoverflow.gateway.GatewayStatus;
import com.recoverflow.gateway.GatewayTimeoutException;
import com.recoverflow.gateway.MockPaymentGateway;
import com.recoverflow.gateway.PaymentGateway;
import com.recoverflow.policy.PolicyApproval;
import com.recoverflow.policy.PolicyContext;
import com.recoverflow.policy.PolicyDecision;
import com.recoverflow.policy.PolicyDecisionType;
import com.recoverflow.policy.PolicyEngine;
import com.recoverflow.policy.PolicyRevalidator;
import com.recoverflow.policy.PolicyRevalidationResult;
import com.recoverflow.recovery.InvalidTransitionException;
import com.recoverflow.recovery.RecoveryAction;
import com.recoverflow.recovery.RecoveryActionRepository;
import com.recoverflow.recovery.RecoveryActionStatus;
import com.recoverflow.recovery.RecoveryActionType;
import com.recoverflow.recovery.RecoveryCase;
import com.recoverflow.recovery.RecoveryCaseRepository;
import com.recoverflow.recovery.RecoveryCaseService;
import com.recoverflow.recovery.RecoveryCaseStateMachine;
import com.recoverflow.recovery.RecoveryCaseStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Execution boundary. No direct AI/EV -> gateway path.
 * Enforces: case exists, merchant auth, ACTION_APPROVED, approved action match,
 * current policy revalidation, idempotency, state invariants, concurrency via version.
 *
 * Transaction pattern: reserve (validate+revalidate+reserve+idempotency) COMMIT,
 * then gateway call outside transaction, then complete (observe+transition+audit) COMMIT.
 */
@Service
public class ExecutionService {

    private final RecoveryCaseRepository caseRepo;
    private final RecoveryActionRepository actionRepo;
    private final RecoveryCaseStateMachine stateMachine;
    private final PolicyEngine policyEngine;
    private final PolicyRevalidator revalidator;
    private final PaymentGateway gateway;
    private final AuditService auditService;

    public ExecutionService(RecoveryCaseRepository caseRepo,
                            RecoveryActionRepository actionRepo,
                            RecoveryCaseStateMachine stateMachine,
                            PolicyEngine policyEngine,
                            PolicyRevalidator revalidator,
                            PaymentGateway gateway,
                            AuditService auditService) {
        this.caseRepo = caseRepo;
        this.actionRepo = actionRepo;
        this.stateMachine = stateMachine;
        this.policyEngine = policyEngine;
        this.revalidator = revalidator;
        this.gateway = gateway;
        this.auditService = auditService;
    }

    /**
     * Main entry. Orchestrates without holding transaction over gateway.
     */
    public ExecutionResult execute(UUID caseId, RecoveryActionType requestedAction,
                                   UUID correlationId, UUID merchantId) {
        UUID corr = correlationId != null ? correlationId : UUID.randomUUID();

        // Phase: reload + validate + revalidate + reserve (transactional)
        ReservedExecution reserved;
        try {
            reserved = reserveExecution(caseId, requestedAction, corr, merchantId);
        } catch (IllegalArgumentException | InvalidTransitionException | ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
            auditService.record(corr, caseId, null, merchantId,
                    "EXECUTION_RESERVATION_FAILED", null, null, AuditActor.SYSTEM,
                    "{\"error\":\"" + e.getMessage() + "\"}");
            // Return safe blocked result without gateway call
            return new ExecutionResult(false, e.getMessage(), caseId, requestedAction, null, null, null, null, corr, false);
        }

        if (!reserved.success) {
            // Reservation indicated blocked (policy invalid, duplicate, etc.)
            return reserved.result;
        }

        // Gateway call outside transaction
        GatewayResult gatewayResult;
        try {
            if (requestedAction == RecoveryActionType.SEND_PAYMENT_LINK || requestedAction == RecoveryActionType.SEND_REMINDER) {
                gatewayResult = gateway.createPaymentLink(caseId, reserved.caseEntity.getAmount(),
                        reserved.caseEntity.getCurrency(), reserved.idempotencyKey, corr);
            } else {
                gatewayResult = gateway.execute(caseId, requestedAction, reserved.caseEntity.getAmount(),
                        reserved.caseEntity.getCurrency(), reserved.idempotencyKey, corr);
            }
        } catch (GatewayTimeoutException e) {
            // Timeout -> UNKNOWN
            return completeTimeout(reserved, corr, e);
        } catch (Exception e) {
            // Treat other gateway exceptions as failure
            gatewayResult = new GatewayResult(GatewayStatus.FAILURE, null, reserved.idempotencyKey, e.getMessage(), true);
        }

        // Complete observation (transactional)
        return completeExecution(reserved, gatewayResult, corr);
    }

    private record ReservedExecution(boolean success, ExecutionResult result, RecoveryCase caseEntity, RecoveryAction action, String idempotencyKey) {}

    @Transactional
    protected ReservedExecution reserveExecution(UUID caseId, RecoveryActionType requestedAction, UUID corr, UUID merchantId) {
        RecoveryCase rc = caseRepo.findById(caseId).orElseThrow(() -> new IllegalArgumentException("RecoveryCase not found: " + caseId));

        // 1. Merchant auth
        if (merchantId != null && !rc.getMerchant().getId().equals(merchantId)) {
            throw new IllegalArgumentException("Merchant not authorized for case: " + caseId);
        }

        // 2. Status must be ACTION_APPROVED
        if (rc.getStatus() != RecoveryCaseStatus.ACTION_APPROVED) {
            throw new InvalidTransitionException(rc.getStatus(), RecoveryCaseStatus.EXECUTING);
        }

        // 3. Approved action must match requested (if stored)
        if (rc.getApprovedAction() != null && !rc.getApprovedAction().equals(requestedAction.name())) {
            throw new IllegalArgumentException("Requested action " + requestedAction + " does not match approved " + rc.getApprovedAction());
        }

        // 4. Build current PolicyContext from latest state
        PolicyContext currentCtx = buildPolicyContext(rc, requestedAction);

        // 5. Revalidate: reconstruct approval from stored fields for audit
        PolicyDecision currentDecision = policyEngine.evaluate(currentCtx);
        if (currentDecision.result() != PolicyDecisionType.ALLOWED) {
            // Stale approval invalidated
            auditService.record(corr, rc.getId(), rc.getPayment().getId(), rc.getMerchant().getId(),
                    "POLICY_REVALIDATION_FAILED", rc.getStatus().name(), rc.getStatus().name(), AuditActor.POLICY,
                    "{\"reason\":\"" + currentDecision.reason() + "\",\"blockingRule\":\"" + currentDecision.blockingRule() + "\"}");

            // Transition to appropriate state based on current decision
            if (currentDecision.result() == PolicyDecisionType.ESCALATE) {
                rc.setStatus(RecoveryCaseStatus.ESCALATED);
                rc.setEscalatedReason(currentDecision.reason());
                caseRepo.save(rc);
                auditService.record(corr, rc.getId(), rc.getPayment().getId(), rc.getMerchant().getId(),
                        "CASE_ESCALATED_ON_REVALIDATION", "ACTION_APPROVED", "ESCALATED", AuditActor.POLICY, "{}");
            } else if (currentDecision.result() == PolicyDecisionType.STOP) {
                rc.setStatus(RecoveryCaseStatus.STOPPED);
                rc.setStoppedReason(currentDecision.reason());
                caseRepo.save(rc);
                auditService.record(corr, rc.getId(), rc.getPayment().getId(), rc.getMerchant().getId(),
                        "CASE_STOPPED_ON_REVALIDATION", "ACTION_APPROVED", "STOPPED", AuditActor.POLICY, "{}");
            }
            return new ReservedExecution(false,
                    new ExecutionResult(false, "Policy revalidation failed: " + currentDecision.reason(),
                            caseId, requestedAction, null, null, rc.getStatus(), null, corr, false),
                    rc, null, null);
        }

        // 6. Idempotency check: key = caseId:action:attemptCount
        String idempotencyKey = rc.getId().toString() + ":" + requestedAction.name() + ":" + rc.getAttemptCount();
        Optional<RecoveryAction> existing = actionRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            RecoveryAction prev = existing.get();
            if (prev.getStatus() == RecoveryActionStatus.SUCCESS) {
                auditService.record(corr, rc.getId(), rc.getPayment().getId(), rc.getMerchant().getId(),
                        "DUPLICATE_ACTION_PREVENTED", rc.getStatus().name(), rc.getStatus().name(), AuditActor.SYSTEM,
                        "{\"idempotencyKey\":\"" + idempotencyKey + "\"}");
                return new ReservedExecution(false,
                        ExecutionResult.duplicate("Duplicate idempotency key, returning existing", caseId, requestedAction, idempotencyKey, prev.getGatewayRef(), corr),
                        rc, prev, idempotencyKey);
            }
            // If previous is PENDING/UNKNOWN, treat as concurrent -> conflict
            throw new DataIntegrityViolationException("Action already in progress for key: " + idempotencyKey);
        }

        // 7. Atomic reservation: transition to EXECUTING + create PENDING action + increment attemptCount
        // Optimistic locking via version will handle concurrent reservation
        RecoveryAction action = new RecoveryAction(UUID.randomUUID(), rc, requestedAction, RecoveryActionStatus.PENDING, idempotencyKey);
        actionRepo.save(action);

        // State transition validation
        stateMachine.validate(rc.getStatus(), RecoveryCaseStatus.EXECUTING);
        rc.setStatus(RecoveryCaseStatus.EXECUTING);
        rc.setAttemptCount(rc.getAttemptCount() + 1);
        // If gateway is payment link, we keep approved fields for audit
        caseRepo.save(rc);

        auditService.record(corr, rc.getId(), rc.getPayment().getId(), rc.getMerchant().getId(),
                "EXECUTION_RESERVED", "ACTION_APPROVED", "EXECUTING", AuditActor.SYSTEM,
                "{\"idempotencyKey\":\"" + idempotencyKey + "\",\"action\":\"" + requestedAction + "\"}");

        return new ReservedExecution(true, null, rc, action, idempotencyKey);
    }

    private ExecutionResult completeTimeout(ReservedExecution reserved, UUID corr, GatewayTimeoutException e) {
        // Second transaction to mark UNKNOWN
        return completeTimeoutTransactional(reserved.caseEntity.getId(), reserved.idempotencyKey, corr, e.getMessage());
    }

    @Transactional
    protected ExecutionResult completeTimeoutTransactional(UUID caseId, String idempotencyKey, UUID corr, String msg) {
        RecoveryCase rc = caseRepo.findById(caseId).orElseThrow();
        Optional<RecoveryAction> opt = actionRepo.findByIdempotencyKey(idempotencyKey);
        if (opt.isPresent()) {
            RecoveryAction action = opt.get();
            action.setStatus(RecoveryActionStatus.UNKNOWN);
            actionRepo.save(action);
        }
        // Transition case to UNKNOWN
        try {
            stateMachine.validate(rc.getStatus(), RecoveryCaseStatus.UNKNOWN);
            rc.setStatus(RecoveryCaseStatus.UNKNOWN);
            rc.setUnknownSince(Instant.now());
            caseRepo.save(rc);
        } catch (InvalidTransitionException ex) {
            // If already UNKNOWN, ignore
        }
        auditService.record(corr, rc.getId(), rc.getPayment().getId(), rc.getMerchant().getId(),
                "GATEWAY_TIMEOUT", "EXECUTING", "UNKNOWN", AuditActor.GATEWAY,
                "{\"idempotencyKey\":\"" + idempotencyKey + "\"}");
        return new ExecutionResult(false, "Gateway timeout, case UNKNOWN", caseId, null, idempotencyKey, null, RecoveryCaseStatus.UNKNOWN, ExecutionResult.RecoveryActionStatus.UNKNOWN, corr, true);
    }

    @Transactional
    protected ExecutionResult completeExecution(ReservedExecution reserved, GatewayResult gatewayResult, UUID corr) {
        RecoveryCase rc = caseRepo.findById(reserved.caseEntity.getId()).orElseThrow();
        RecoveryAction action = actionRepo.findByIdempotencyKey(reserved.idempotencyKey).orElseThrow();

        boolean isLink = reserved.caseEntity.getAmount() != null && // check action type
                (reserved.action.getActionType() == RecoveryActionType.SEND_PAYMENT_LINK || reserved.action.getActionType() == RecoveryActionType.SEND_REMINDER);

        if (isLink) {
            // Payment link creation does NOT move money
            if (gatewayResult.status() == GatewayStatus.SUCCESS) {
                action.setStatus(RecoveryActionStatus.SUCCESS);
                action.setGatewayRef(gatewayResult.gatewayRef());
                action.setExecutedAt(Instant.now());
                actionRepo.save(action);

                // Link created -> case goes to RETRY_PENDING (waiting for customer), not RECOVERED
                try {
                    stateMachine.validate(rc.getStatus(), RecoveryCaseStatus.RETRY_PENDING);
                    rc.setStatus(RecoveryCaseStatus.RETRY_PENDING);
                    caseRepo.save(rc);
                    auditService.record(corr, rc.getId(), rc.getPayment().getId(), rc.getMerchant().getId(),
                            "PAYMENT_LINK_CREATED", "EXECUTING", "RETRY_PENDING", AuditActor.GATEWAY,
                            "{\"gatewayRef\":\"" + gatewayResult.gatewayRef() + "\"}");
                } catch (InvalidTransitionException ex) {
                    // If cannot go to RETRY_PENDING, keep EXECUTING -> ACTION_FAILED?
                }
                return new ExecutionResult(true, "Payment link created, awaiting customer", rc.getId(), action.getActionType(), reserved.idempotencyKey, gatewayResult.gatewayRef(), RecoveryCaseStatus.RETRY_PENDING, ExecutionResult.RecoveryActionStatus.SUCCESS, corr, true);
            } else {
                action.setStatus(RecoveryActionStatus.FAILED);
                actionRepo.save(action);
                // Link failure -> ACTION_FAILED (can re-evaluate next candidate)
                try {
                    stateMachine.validate(rc.getStatus(), RecoveryCaseStatus.ACTION_FAILED);
                    rc.setStatus(RecoveryCaseStatus.ACTION_FAILED);
                    caseRepo.save(rc);
                } catch (InvalidTransitionException ignored) {}
                auditService.record(corr, rc.getId(), rc.getPayment().getId(), rc.getMerchant().getId(),
                        "PAYMENT_LINK_FAILED", "EXECUTING", "ACTION_FAILED", AuditActor.GATEWAY, "{}");
                return new ExecutionResult(false, "Payment link failed", rc.getId(), action.getActionType(), reserved.idempotencyKey, null, RecoveryCaseStatus.ACTION_FAILED, ExecutionResult.RecoveryActionStatus.FAILED, corr, true);
            }
        }

        // Financial actions
        if (gatewayResult.status() == GatewayStatus.SUCCESS) {
            action.setStatus(RecoveryActionStatus.SUCCESS);
            action.setGatewayRef(gatewayResult.gatewayRef());
            action.setExecutedAt(Instant.now());
            action.setObservedAt(Instant.now());
            actionRepo.save(action);

            // Money recovered only on payment success confirmation
            rc.setRecoveredAmount(rc.getAmount());
            try {
                stateMachine.validate(rc.getStatus(), RecoveryCaseStatus.RECOVERED);
                rc.setStatus(RecoveryCaseStatus.RECOVERED);
                caseRepo.save(rc);
            } catch (InvalidTransitionException ex) {
                // Should not happen
            }
            auditService.record(corr, rc.getId(), rc.getPayment().getId(), rc.getMerchant().getId(),
                    "GATEWAY_SUCCESS", "EXECUTING", "RECOVERED", AuditActor.GATEWAY,
                    "{\"gatewayRef\":\"" + gatewayResult.gatewayRef() + "\"}");
            return new ExecutionResult(true, "Payment recovered", rc.getId(), action.getActionType(), reserved.idempotencyKey, gatewayResult.gatewayRef(), RecoveryCaseStatus.RECOVERED, ExecutionResult.RecoveryActionStatus.SUCCESS, corr, true);

        } else if (gatewayResult.status() == GatewayStatus.FAILURE || gatewayResult.status() == GatewayStatus.TRANSIENT_FAILURE) {
            action.setStatus(RecoveryActionStatus.FAILED);
            action.setGatewayRef(gatewayResult.gatewayRef());
            action.setExecutedAt(Instant.now());
            actionRepo.save(action);

            if (!gatewayResult.retryable()) {
                // Terminal failure
                try {
                    stateMachine.validate(rc.getStatus(), RecoveryCaseStatus.FAILED_TERMINAL);
                    rc.setStatus(RecoveryCaseStatus.FAILED_TERMINAL);
                    caseRepo.save(rc);
                    auditService.record(corr, rc.getId(), rc.getPayment().getId(), rc.getMerchant().getId(),
                            "GATEWAY_FAILED_TERMINAL", "EXECUTING", "FAILED_TERMINAL", AuditActor.GATEWAY, "{}");
                    return new ExecutionResult(false, "Gateway terminal failure", rc.getId(), action.getActionType(), reserved.idempotencyKey, gatewayResult.gatewayRef(), RecoveryCaseStatus.FAILED_TERMINAL, ExecutionResult.RecoveryActionStatus.FAILED, corr, true);
                } catch (InvalidTransitionException ignored) {}
            }
            // Retryable failure -> ACTION_FAILED (can loop to next candidate)
            try {
                stateMachine.validate(rc.getStatus(), RecoveryCaseStatus.ACTION_FAILED);
                rc.setStatus(RecoveryCaseStatus.ACTION_FAILED);
                caseRepo.save(rc);
                auditService.record(corr, rc.getId(), rc.getPayment().getId(), rc.getMerchant().getId(),
                        "GATEWAY_FAILED_RETRYABLE", "EXECUTING", "ACTION_FAILED", AuditActor.GATEWAY, "{}");
            } catch (InvalidTransitionException ignored) {}
            return new ExecutionResult(false, "Gateway failure, action failed", rc.getId(), action.getActionType(), reserved.idempotencyKey, gatewayResult.gatewayRef(), RecoveryCaseStatus.ACTION_FAILED, ExecutionResult.RecoveryActionStatus.FAILED, corr, true);

        } else if (gatewayResult.status() == GatewayStatus.UNKNOWN) {
            action.setStatus(RecoveryActionStatus.UNKNOWN);
            actionRepo.save(action);
            try {
                stateMachine.validate(rc.getStatus(), RecoveryCaseStatus.UNKNOWN);
                rc.setStatus(RecoveryCaseStatus.UNKNOWN);
                rc.setUnknownSince(Instant.now());
                caseRepo.save(rc);
            } catch (InvalidTransitionException ignored) {}
            auditService.record(corr, rc.getId(), rc.getPayment().getId(), rc.getMerchant().getId(),
                    "GATEWAY_UNKNOWN", "EXECUTING", "UNKNOWN", AuditActor.GATEWAY, "{}");
            return new ExecutionResult(false, "Gateway unknown", rc.getId(), action.getActionType(), reserved.idempotencyKey, null, RecoveryCaseStatus.UNKNOWN, ExecutionResult.RecoveryActionStatus.UNKNOWN, corr, true);
        }

        // Fallback
        action.setStatus(RecoveryActionStatus.FAILED);
        actionRepo.save(action);
        return new ExecutionResult(false, "Gateway failure", rc.getId(), action.getActionType(), reserved.idempotencyKey, null, RecoveryCaseStatus.ACTION_FAILED, ExecutionResult.RecoveryActionStatus.FAILED, corr, true);
    }

    private PolicyContext buildPolicyContext(RecoveryCase rc, RecoveryActionType action) {
        BigDecimal amount = rc.getAmount();
        String gatewayCode = rc.getFailureCode() != null ? rc.getFailureCode() : "UNKNOWN";
        int attempts = rc.getAttemptCount();
        int elapsed = (int) Duration.between(rc.getCreatedAt(), Instant.now()).toHours();
        boolean optedOut = rc.getCustomer().getOptedOut();
        // linkAlreadySent: check if any prior SEND_PAYMENT_LINK success exists
        boolean linkSent = actionRepo.findByRecoveryCaseId(rc.getId()).stream()
                .anyMatch(a -> a.getActionType() == RecoveryActionType.SEND_PAYMENT_LINK && a.getStatus() == RecoveryActionStatus.SUCCESS);
        BigDecimal limit = rc.getMerchant().getAutoActionLimit();
        int maxRetries = rc.getMerchant().getMaxRetries();
        int window = rc.getMerchant().getRecoveryWindowHours();
        return new PolicyContext(amount, gatewayCode, attempts, elapsed, optedOut, linkSent, action, limit, maxRetries, window);
    }
}
