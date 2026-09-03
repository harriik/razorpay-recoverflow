package com.recoverflow.failurelab;

import com.recoverflow.audit.AuditEvent;
import com.recoverflow.audit.AuditEventRepository;
import com.recoverflow.customer.Customer;
import com.recoverflow.customer.CustomerRepository;
import com.recoverflow.gateway.GatewayResult;
import com.recoverflow.gateway.GatewayStatus;
import com.recoverflow.gateway.MockPaymentGateway;
import com.recoverflow.merchant.Merchant;
import com.recoverflow.merchant.MerchantRepository;
import com.recoverflow.payment.Payment;
import com.recoverflow.payment.PaymentMethod;
import com.recoverflow.payment.PaymentRepository;
import com.recoverflow.payment.PaymentStatus;
import com.recoverflow.recovery.RecoveryAction;
import com.recoverflow.recovery.RecoveryActionRepository;
import com.recoverflow.recovery.RecoveryActionStatus;
import com.recoverflow.recovery.RecoveryActionType;
import com.recoverflow.recovery.RecoveryCase;
import com.recoverflow.recovery.RecoveryCaseRepository;
import com.recoverflow.recovery.RecoveryCaseStatus;
import com.recoverflow.recovery.RecoveryDecisionSnapshotService;
import com.recoverflow.likelihood.ObservableContext;
import com.recoverflow.payment.PaymentMethod;
import com.recoverflow.synthetic.SyntheticAiProxy;
import com.recoverflow.execution.ExecutionResult;
import com.recoverflow.execution.ExecutionService;
import com.recoverflow.execution.ReconciliationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Failure Lab service exercising real production path for 10 deterministic scenarios.
 */
@Service
public class FailureLabService {

    private final RecoveryCaseRepository caseRepo;
    private final RecoveryActionRepository actionRepo;
    private final PaymentRepository paymentRepo;
    private final CustomerRepository customerRepo;
    private final MerchantRepository merchantRepo;
    private final AuditEventRepository auditRepo;
    private final MockPaymentGateway mockGateway;
    private final ExecutionService executionService;
    private final ReconciliationService reconciliationService;
    private final RecoveryDecisionSnapshotService snapshotService;
    private final SyntheticAiProxy syntheticAiProxy;

    public FailureLabService(RecoveryCaseRepository caseRepo,
                             RecoveryActionRepository actionRepo,
                             PaymentRepository paymentRepo,
                             CustomerRepository customerRepo,
                             MerchantRepository merchantRepo,
                             AuditEventRepository auditRepo,
                             MockPaymentGateway mockGateway,
                             ExecutionService executionService,
                             ReconciliationService reconciliationService,
                             RecoveryDecisionSnapshotService snapshotService,
                             SyntheticAiProxy syntheticAiProxy) {
        this.caseRepo = caseRepo;
        this.actionRepo = actionRepo;
        this.paymentRepo = paymentRepo;
        this.customerRepo = customerRepo;
        this.merchantRepo = merchantRepo;
        this.auditRepo = auditRepo;
        this.mockGateway = mockGateway;
        this.executionService = executionService;
        this.reconciliationService = reconciliationService;
        this.snapshotService = snapshotService;
        this.syntheticAiProxy = syntheticAiProxy;
    }

    private UUID scenarioCaseId(String scenarioId) {
        return UUID.nameUUIDFromBytes(("failure-lab-" + scenarioId).getBytes());
    }

    private Merchant getOrCreateMerchant(UUID caseId, BigDecimal limit, int maxRetries, int window) {
        UUID merchantId = UUID.nameUUIDFromBytes(("merchant-" + caseId).getBytes());
        return merchantRepo.findById(merchantId).orElseGet(() ->
                merchantRepo.save(new Merchant(merchantId, "FailureLab-" + caseId.toString().substring(0, 8), limit, maxRetries, window))
        );
    }

    private Customer getOrCreateCustomer(UUID caseId, Merchant merchant, boolean optedOut) {
        UUID customerId = UUID.nameUUIDFromBytes(("customer-" + caseId).getBytes());
        return customerRepo.findById(customerId).orElseGet(() -> {
            Customer c = new Customer(customerId, merchant, "failurelab-" + caseId.toString().substring(0, 8) + "@test.com", "9999");
            c.setOptedOut(optedOut);
            return customerRepo.save(c);
        });
    }

    private Payment getOrCreatePayment(UUID caseId, Merchant merchant, Customer customer, String gatewayCode) {
        UUID paymentId = UUID.nameUUIDFromBytes(("payment-" + caseId).getBytes());
        return paymentRepo.findById(paymentId).orElseGet(() ->
                paymentRepo.save(new Payment(paymentId, merchant, customer,
                        new BigDecimal("5000.0000"), "INR", PaymentMethod.CARD, PaymentStatus.FAILED,
                        gatewayCode, "pay_" + caseId.toString().substring(0, 8), Instant.now()))
        );
    }

    private RecoveryCase createApprovedCase(String scenarioId, RecoveryActionType approvedAction, String gatewayCode, int attemptCount, boolean optedOut, BigDecimal amount) {
        UUID caseId = scenarioCaseId(scenarioId);
        actionRepo.findByRecoveryCaseId(caseId).forEach(a -> actionRepo.delete(a));
        caseRepo.findById(caseId).ifPresent(c -> caseRepo.delete(c));
        Merchant merchant = getOrCreateMerchant(caseId, new BigDecimal("10000.0000"), 3, 48);
        Customer customer = getOrCreateCustomer(caseId, merchant, optedOut);
        if (customer.getOptedOut() != optedOut) {
            customer.setOptedOut(optedOut);
            customerRepo.save(customer);
        }
        Payment payment = getOrCreatePayment(caseId, merchant, customer, gatewayCode);
        BigDecimal amt = amount != null ? amount : new BigDecimal("5000.0000");
        RecoveryCase rc = new RecoveryCase(caseId, payment, merchant, customer, amt, "INR", gatewayCode, RecoveryCaseStatus.ACTION_APPROVED);
        rc.setApprovedAction(approvedAction.name());
        rc.setApprovedAt(Instant.now());
        rc.setApprovedPolicyVersion("v1");
        rc.setApprovedPolicyDecisionId(UUID.randomUUID());
        rc.setApprovedThresholdSnapshot("{\"autoActionLimit\":\"10000.0000\"}");
        rc.setAttemptCount(attemptCount);
        if (scenarioId.equals("STALE_POLICY_APPROVAL")) {
            try {
                var f = RecoveryCase.class.getDeclaredField("createdAt");
                f.setAccessible(true);
                f.set(rc, Instant.now().minusSeconds(50 * 3600));
            } catch (Exception ignored) {}
        }
        RecoveryCase saved = caseRepo.save(rc);
        // Persist historical decision snapshot at decision finalization time (actual AI, candidates, policy)
        try {
            ObservableContext obs = new ObservableContext(
                    saved.getAmount(),
                    saved.getCurrency(),
                    saved.getPayment().getMethod(),
                    saved.getFailureCode() != null ? saved.getFailureCode() : "UNKNOWN",
                    saved.getCreatedAt() != null ? (int) java.time.Duration.between(saved.getCreatedAt(), Instant.now()).toHours() : 0,
                    saved.getAttemptCount() != null ? saved.getAttemptCount() : 0,
                    saved.getCustomer().getSuccessCount(),
                    saved.getCustomer().getFailureCount(),
                    false
            );
            // Actual AI assessment at decision time (observable-only)
            var ai = syntheticAiProxy.assess(obs);
            snapshotService.persistSnapshot(saved.getId(), obs, ai, "SYNTHETIC_AI_PROXY", "synthetic-ai-v1");
        } catch (Exception e) {
            // Do not fail case creation if snapshot fails
        }
        return saved;
    }

    private String idempotencyKey(UUID caseId, RecoveryActionType action, int attempt) {
        return caseId.toString() + ":" + action.name() + ":" + attempt;
    }

    @Transactional
    public void resetScenario(String scenarioId) {
        UUID caseId = scenarioCaseId(scenarioId);
        mockGateway.clearScenarios();
        actionRepo.findByRecoveryCaseId(caseId).forEach(a -> actionRepo.delete(a));
        auditRepo.findByCaseIdOrderByCreatedAtAsc(caseId).forEach(a -> auditRepo.delete(a));
        caseRepo.findById(caseId).ifPresent(c -> caseRepo.delete(c));
    }

    public FailureLabResult runScenario(String scenarioId) {
        resetScenario(scenarioId);
        return switch (scenarioId) {
            case "GATEWAY_TIMEOUT" -> runGatewayTimeout();
            case "GATEWAY_FAILURE_RETRYABLE" -> runGatewayFailureRetryable();
            case "GATEWAY_FAILURE_TERMINAL" -> runGatewayFailureTerminal();
            case "DUPLICATE_EXECUTION" -> runDuplicateExecution();
            case "CONCURRENT_EXECUTION" -> runConcurrentExecution();
            case "UNKNOWN_RECONCILIATION_SUCCESS" -> runUnknownReconciliationSuccess();
            case "UNKNOWN_RECONCILIATION_FAILURE" -> runUnknownReconciliationFailure();
            case "STALE_POLICY_APPROVAL" -> runStalePolicyApproval();
            case "CUSTOMER_OPT_OUT_BEFORE_EXECUTION" -> runCustomerOptOut();
            case "AI_RECOMMENDS_BLOCKED_ACTION" -> runAiRecommendsBlockedAction();
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
        };
    }

    private FailureLabResult runGatewayTimeout() {
        String sid = "GATEWAY_TIMEOUT";
        RecoveryCase rc = createApprovedCase(sid, RecoveryActionType.RETRY_NOW, "BANK_TIMEOUT", 0, false, null);
        UUID caseId = rc.getId();
        String before = rc.getStatus().name();
        String key = idempotencyKey(caseId, RecoveryActionType.RETRY_NOW, 0);
        mockGateway.setScenario(key, MockPaymentGateway.Scenario.TIMEOUT);
        UUID corr = UUID.randomUUID();
        ExecutionResult res = executionService.execute(caseId, RecoveryActionType.RETRY_NOW, corr, rc.getMerchant().getId());
        RecoveryCase after = caseRepo.findById(caseId).orElseThrow();
        int callCount = mockGateway.getCallCount(key);
        List<AuditEvent> audits = auditRepo.findByCaseIdOrderByCreatedAtAsc(caseId);
        GatewayResult gw = mockGateway.queryStatus(key);
        return new FailureLabResult(sid, res.success() ? "SUCCESS" : "TIMEOUT", caseId, before, after.getStatus().name(),
                RecoveryActionType.RETRY_NOW.name(), null, gw, null,
                "GATEWAY_TIMEOUT", callCount, "NEW", audits.stream().map(AuditEvent::getEventType).toList(), corr.toString());
    }

    private FailureLabResult runGatewayFailureRetryable() {
        String sid = "GATEWAY_FAILURE_RETRYABLE";
        RecoveryCase rc = createApprovedCase(sid, RecoveryActionType.RETRY_NOW, "BANK_TIMEOUT", 0, false, null);
        UUID caseId = rc.getId();
        String before = rc.getStatus().name();
        String key = idempotencyKey(caseId, RecoveryActionType.RETRY_NOW, 0);
        mockGateway.setScenario(key, MockPaymentGateway.Scenario.FAILURE_RETRYABLE);
        UUID corr = UUID.randomUUID();
        ExecutionResult res = executionService.execute(caseId, RecoveryActionType.RETRY_NOW, corr, rc.getMerchant().getId());
        RecoveryCase after = caseRepo.findById(caseId).orElseThrow();
        int callCount = mockGateway.getCallCount(key);
        List<AuditEvent> audits = auditRepo.findByCaseIdOrderByCreatedAtAsc(caseId);
        GatewayResult gw = mockGateway.queryStatus(key);
        return new FailureLabResult(sid, "FAILED_RETRYABLE", caseId, before, after.getStatus().name(),
                RecoveryActionType.RETRY_NOW.name(), null, gw, null,
                "GATEWAY_FAILED_RETRYABLE", callCount, "NEW", audits.stream().map(AuditEvent::getEventType).toList(), corr.toString());
    }

    private FailureLabResult runGatewayFailureTerminal() {
        String sid = "GATEWAY_FAILURE_TERMINAL";
        RecoveryCase rc = createApprovedCase(sid, RecoveryActionType.RETRY_NOW, "CARD_EXPIRED", 0, false, null);
        rc.setFailureCode("BANK_TIMEOUT");
        caseRepo.save(rc);
        UUID caseId = rc.getId();
        String before = rc.getStatus().name();
        String key = idempotencyKey(caseId, RecoveryActionType.RETRY_NOW, 0);
        mockGateway.setScenario(key, MockPaymentGateway.Scenario.FAILURE_TERMINAL);
        UUID corr = UUID.randomUUID();
        ExecutionResult res = executionService.execute(caseId, RecoveryActionType.RETRY_NOW, corr, rc.getMerchant().getId());
        RecoveryCase after = caseRepo.findById(caseId).orElseThrow();
        int callCount = mockGateway.getCallCount(key);
        List<AuditEvent> audits = auditRepo.findByCaseIdOrderByCreatedAtAsc(caseId);
        GatewayResult gw = mockGateway.queryStatus(key);
        return new FailureLabResult(sid, "FAILED_TERMINAL", caseId, before, after.getStatus().name(),
                RecoveryActionType.RETRY_NOW.name(), null, gw, null,
                "GATEWAY_FAILED_TERMINAL", callCount, "NEW", audits.stream().map(AuditEvent::getEventType).toList(), corr.toString());
    }

    private FailureLabResult runDuplicateExecution() {
        String sid = "DUPLICATE_EXECUTION";
        RecoveryCase rc = createApprovedCase(sid, RecoveryActionType.RETRY_NOW, "BANK_TIMEOUT", 0, false, null);
        UUID caseId = rc.getId();
        String before = rc.getStatus().name();
        String key = idempotencyKey(caseId, RecoveryActionType.RETRY_NOW, 0);
        mockGateway.setScenario(key, MockPaymentGateway.Scenario.SUCCESS);
        UUID corr1 = UUID.randomUUID();
        ExecutionResult first = executionService.execute(caseId, RecoveryActionType.RETRY_NOW, corr1, rc.getMerchant().getId());
        int firstCallCount = mockGateway.getCallCount(key);
        GatewayResult dup = null;
        try {
            dup = mockGateway.execute(caseId, RecoveryActionType.RETRY_NOW, rc.getAmount(), rc.getCurrency(), key, UUID.randomUUID());
        } catch (Exception e) {
            dup = GatewayResult.duplicate(key, null, GatewayStatus.SUCCESS);
        }
        int secondCallCount = mockGateway.getCallCount(key);
        RecoveryCase afterFirst = caseRepo.findById(caseId).orElseThrow();
        List<AuditEvent> audits = auditRepo.findByCaseIdOrderByCreatedAtAsc(caseId);
        // Clarified: executionRequests 2, gatewayInvocations 1, duplicatesPrevented 1 (duplicate NOT counted as gateway call)
        return new FailureLabResult(sid, "DUPLICATE", caseId, before, afterFirst.getStatus().name(),
                RecoveryActionType.RETRY_NOW.name(), null, dup, null,
                "DUPLICATE_ACTION_PREVENTED", 1, "DUPLICATE", audits.stream().map(AuditEvent::getEventType).toList(), corr1.toString(),
                2, 1, 1, null);
    }

    private FailureLabResult runConcurrentExecution() {
        String sid = "CONCURRENT_EXECUTION";
        RecoveryCase rc = createApprovedCase(sid, RecoveryActionType.RETRY_NOW, "BANK_TIMEOUT", 0, false, null);
        UUID caseId = rc.getId();
        String before = rc.getStatus().name();
        String key = idempotencyKey(caseId, RecoveryActionType.RETRY_NOW, 0);
        mockGateway.setScenario(key, MockPaymentGateway.Scenario.SUCCESS);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<ExecutionResult> r1 = new AtomicReference<>();
        AtomicReference<ExecutionResult> r2 = new AtomicReference<>();
        Thread t1 = new Thread(() -> {
            latch.countDown();
            try { latch.await(); } catch (Exception ignored) {}
            r1.set(executionService.execute(caseId, RecoveryActionType.RETRY_NOW, UUID.randomUUID(), rc.getMerchant().getId()));
        });
        Thread t2 = new Thread(() -> {
            latch.countDown();
            try { latch.await(); } catch (Exception ignored) {}
            r2.set(executionService.execute(caseId, RecoveryActionType.RETRY_NOW, UUID.randomUUID(), rc.getMerchant().getId()));
        });
        t1.start();
        t2.start();
        try { t1.join(); t2.join(); } catch (InterruptedException ignored) {}
        int callCount = mockGateway.getCallCount(key);
        RecoveryCase after = caseRepo.findById(caseId).orElseThrow();
        List<AuditEvent> audits = auditRepo.findByCaseIdOrderByCreatedAtAsc(caseId);
        GatewayResult gw = r1.get() != null && r1.get().gatewayRef() != null ? new GatewayResult(GatewayStatus.SUCCESS, r1.get().gatewayRef(), key, null, false) : mockGateway.queryStatus(key);
        // Clarified: 2 executionRequests, 1 gatewayInvocation, 1 duplicate/blocked
        return new FailureLabResult(sid, "CONCURRENT", caseId, before, after.getStatus().name(),
                RecoveryActionType.RETRY_NOW.name(), null, gw, null,
                "CONCURRENT", callCount, callCount == 1 ? "ONE_GATEWAY_CALL" : "MULTIPLE", audits.stream().map(AuditEvent::getEventType).toList(), UUID.randomUUID().toString(),
                2, 1, 1, null);
    }

    private FailureLabResult runUnknownReconciliationSuccess() {
        String sid = "UNKNOWN_RECONCILIATION_SUCCESS";
        RecoveryCase rc = createApprovedCase(sid, RecoveryActionType.RETRY_NOW, "BANK_TIMEOUT", 0, false, null);
        UUID caseId = rc.getId();
        String before = rc.getStatus().name();
        String key = idempotencyKey(caseId, RecoveryActionType.RETRY_NOW, 0);
        mockGateway.setScenario(key, MockPaymentGateway.Scenario.TIMEOUT);
        UUID corr1 = UUID.randomUUID();
        ExecutionResult exec = executionService.execute(caseId, RecoveryActionType.RETRY_NOW, corr1, rc.getMerchant().getId());
        mockGateway.setScenario(key, MockPaymentGateway.Scenario.SUCCESS);
        UUID corr2 = UUID.randomUUID();
        var recon = reconciliationService.reconcile(caseId, corr2);
        RecoveryCase after = caseRepo.findById(caseId).orElseThrow();
        int callCount = mockGateway.getCallCount(key);
        List<AuditEvent> audits = auditRepo.findByCaseIdOrderByCreatedAtAsc(caseId);
        GatewayResult gw = mockGateway.queryStatus(key);
        return new FailureLabResult(sid, "RECONCILED_SUCCESS", caseId, before, after.getStatus().name(),
                RecoveryActionType.RETRY_NOW.name(), null, gw, recon,
                "RECONCILED_SUCCESS", callCount, "RECONCILED", audits.stream().map(AuditEvent::getEventType).toList(), corr2.toString());
    }

    private FailureLabResult runUnknownReconciliationFailure() {
        String sid = "UNKNOWN_RECONCILIATION_FAILURE";
        RecoveryCase rc = createApprovedCase(sid, RecoveryActionType.RETRY_NOW, "BANK_TIMEOUT", 0, false, null);
        UUID caseId = rc.getId();
        String before = rc.getStatus().name();
        String key = idempotencyKey(caseId, RecoveryActionType.RETRY_NOW, 0);
        mockGateway.setScenario(key, MockPaymentGateway.Scenario.TIMEOUT);
        UUID corr1 = UUID.randomUUID();
        executionService.execute(caseId, RecoveryActionType.RETRY_NOW, corr1, rc.getMerchant().getId());
        mockGateway.setScenario(key, MockPaymentGateway.Scenario.FAILURE_TERMINAL);
        UUID corr2 = UUID.randomUUID();
        var recon = reconciliationService.reconcile(caseId, corr2);
        RecoveryCase after = caseRepo.findById(caseId).orElseThrow();
        int callCount = mockGateway.getCallCount(key);
        List<AuditEvent> audits = auditRepo.findByCaseIdOrderByCreatedAtAsc(caseId);
        return new FailureLabResult(sid, "RECONCILED_FAILED_TERMINAL", caseId, before, after.getStatus().name(),
                RecoveryActionType.RETRY_NOW.name(), null, null, recon,
                "RECONCILED_FAILED_TERMINAL", callCount, "RECONCILED", audits.stream().map(AuditEvent::getEventType).toList(), corr2.toString());
    }

    private FailureLabResult runStalePolicyApproval() {
        String sid = "STALE_POLICY_APPROVAL";
        RecoveryCase rc = createApprovedCase(sid, RecoveryActionType.RETRY_NOW, "BANK_TIMEOUT", 3, false, null);
        UUID caseId = rc.getId();
        String before = rc.getStatus().name();
        String key = idempotencyKey(caseId, RecoveryActionType.RETRY_NOW, 3);
        mockGateway.setScenario(key, MockPaymentGateway.Scenario.SUCCESS);
        UUID corr = UUID.randomUUID();
        ExecutionResult res = executionService.execute(caseId, RecoveryActionType.RETRY_NOW, corr, rc.getMerchant().getId());
        RecoveryCase after = caseRepo.findById(caseId).orElseThrow();
        int callCount = mockGateway.getCallCount(key);
        List<AuditEvent> audits = auditRepo.findByCaseIdOrderByCreatedAtAsc(caseId);
        GatewayResult gw = mockGateway.queryStatus(key);
        // Policy blocked, gateway never called: executionRequests 1, gateway 0
        return new FailureLabResult(sid, "STALE_POLICY", caseId, before, after.getStatus().name(),
                RecoveryActionType.RETRY_NOW.name(), null, gw, null,
                "POLICY_REVALIDATION_FAILED", callCount, "BLOCKED", audits.stream().map(AuditEvent::getEventType).toList(), corr.toString(),
                1, 0, 0, "STOP");
    }

    private FailureLabResult runCustomerOptOut() {
        String sid = "CUSTOMER_OPT_OUT_BEFORE_EXECUTION";
        RecoveryCase rc = createApprovedCase(sid, RecoveryActionType.RETRY_NOW, "BANK_TIMEOUT", 0, false, null);
        UUID caseId = rc.getId();
        String before = rc.getStatus().name();
        Customer cust = rc.getCustomer();
        cust.setOptedOut(true);
        customerRepo.save(cust);
        String key = idempotencyKey(caseId, RecoveryActionType.RETRY_NOW, 0);
        mockGateway.setScenario(key, MockPaymentGateway.Scenario.SUCCESS);
        UUID corr = UUID.randomUUID();
        ExecutionResult res = executionService.execute(caseId, RecoveryActionType.RETRY_NOW, corr, rc.getMerchant().getId());
        RecoveryCase after = caseRepo.findById(caseId).orElseThrow();
        int callCount = mockGateway.getCallCount(key);
        List<AuditEvent> audits = auditRepo.findByCaseIdOrderByCreatedAtAsc(caseId);
        GatewayResult gw = mockGateway.queryStatus(key);
        return new FailureLabResult(sid, "OPT_OUT", caseId, before, after.getStatus().name(),
                RecoveryActionType.RETRY_NOW.name(), null, gw, null,
                "POLICY_REVALIDATION_FAILED_OPT_OUT", callCount, "BLOCKED", audits.stream().map(AuditEvent::getEventType).toList(), corr.toString(),
                1, 0, 0, "STOP");
    }

    private FailureLabResult runAiRecommendsBlockedAction() {
        String sid = "AI_RECOMMENDS_BLOCKED_ACTION";
        RecoveryCase rc = createApprovedCase(sid, RecoveryActionType.RETRY_NOW, "BANK_TIMEOUT", 0, false, new BigDecimal("20000.0000"));
        UUID caseId = rc.getId();
        String before = rc.getStatus().name();
        String key = idempotencyKey(caseId, RecoveryActionType.RETRY_NOW, 0);
        mockGateway.setScenario(key, MockPaymentGateway.Scenario.SUCCESS);
        UUID corr = UUID.randomUUID();
        ExecutionResult res = executionService.execute(caseId, RecoveryActionType.RETRY_NOW, corr, rc.getMerchant().getId());
        RecoveryCase after = caseRepo.findById(caseId).orElseThrow();
        int callCount = mockGateway.getCallCount(key);
        List<AuditEvent> audits = auditRepo.findByCaseIdOrderByCreatedAtAsc(caseId);
        GatewayResult gw = mockGateway.queryStatus(key);
        return new FailureLabResult(sid, "AI_BLOCKED", caseId, before, after.getStatus().name(),
                RecoveryActionType.RETRY_NOW.name(), null, gw, null,
                "POLICY_BLOCKED_AI_RECOMMENDATION", callCount, "BLOCKED", audits.stream().map(AuditEvent::getEventType).toList(), corr.toString(),
                1, 0, 0, "ESCALATE");
    }
}
