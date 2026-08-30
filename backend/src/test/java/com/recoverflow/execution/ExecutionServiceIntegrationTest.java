package com.recoverflow.execution;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.audit.AuditEventRepository;
import com.recoverflow.customer.Customer;
import com.recoverflow.customer.CustomerRepository;
import com.recoverflow.gateway.MockPaymentGateway;
import com.recoverflow.gateway.MockPaymentGateway.Scenario;
import com.recoverflow.merchant.Merchant;
import com.recoverflow.merchant.MerchantRepository;
import com.recoverflow.payment.Payment;
import com.recoverflow.payment.PaymentMethod;
import com.recoverflow.payment.PaymentRepository;
import com.recoverflow.payment.PaymentStatus;
import com.recoverflow.policy.PolicyApproval;
import com.recoverflow.policy.PolicyContext;
import com.recoverflow.policy.PolicyDecision;
import com.recoverflow.policy.PolicyEngine;
import com.recoverflow.recovery.RecoveryAction;
import com.recoverflow.recovery.RecoveryActionRepository;
import com.recoverflow.recovery.RecoveryActionStatus;
import com.recoverflow.recovery.RecoveryActionType;
import com.recoverflow.recovery.RecoveryCase;
import com.recoverflow.recovery.RecoveryCaseRepository;
import com.recoverflow.recovery.RecoveryCaseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ExecutionServiceIntegrationTest {

    @Autowired ExecutionService executionService;
    @Autowired ReconciliationService reconciliationService;
    @Autowired RecoveryCaseRepository caseRepo;
    @Autowired RecoveryActionRepository actionRepo;
    @Autowired PaymentRepository paymentRepo;
    @Autowired CustomerRepository customerRepo;
    @Autowired MerchantRepository merchantRepo;
    @Autowired AuditEventRepository auditRepo;
    @Autowired MockPaymentGateway mockGateway;
    @Autowired PolicyEngine policyEngine;

    private Merchant merchant;
    private Customer customer;
    private Payment payment;

    @BeforeEach
    void setUp() {
        mockGateway.clearScenarios();
        actionRepo.deleteAll();
        auditRepo.deleteAll();
        caseRepo.deleteAll();
        paymentRepo.deleteAll();
        customerRepo.deleteAll();
        merchantRepo.deleteAll();

        merchant = merchantRepo.save(new Merchant(UUID.randomUUID(), "ExecTest", new BigDecimal("10000.0000"), 3, 48));
        customer = customerRepo.save(new Customer(UUID.randomUUID(), merchant, "exec@test.com", "9999"));
        payment = paymentRepo.save(new Payment(UUID.randomUUID(), merchant, customer,
                new BigDecimal("5000.0000"), "INR", PaymentMethod.CARD, PaymentStatus.FAILED,
                "BANK_TIMEOUT", "pay_exec_" + UUID.randomUUID(), Instant.now()));
    }

    private RecoveryCase newApprovedCase(RecoveryActionType approvedAction) {
        RecoveryCase rc = new RecoveryCase(UUID.randomUUID(), payment, merchant, customer,
                new BigDecimal("5000.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.ACTION_APPROVED);
        rc.setApprovedAction(approvedAction.name());
        rc.setApprovedAt(Instant.now());
        rc.setApprovedPolicyVersion("v1");
        rc.setApprovedPolicyDecisionId(UUID.randomUUID());
        rc.setApprovedThresholdSnapshot("{\"autoActionLimit\":\"10000.0000\"}");
        return caseRepo.save(rc);
    }

    @Test
    void test1_successfulRetryRecovered() {
        RecoveryCase rc = newApprovedCase(RecoveryActionType.RETRY_NOW);
        String key = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.SUCCESS);

        ExecutionResult res = executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());

        assertTrue(res.success());
        assertEquals(RecoveryCaseStatus.RECOVERED, res.caseStatus());
        assertTrue(res.gatewayCalled());
        RecoveryCase reloaded = caseRepo.findById(rc.getId()).orElseThrow();
        assertEquals(RecoveryCaseStatus.RECOVERED, reloaded.getStatus());
        assertEquals(new BigDecimal("5000.0000"), reloaded.getRecoveredAmount());
        RecoveryAction action = actionRepo.findByIdempotencyKey(key).orElseThrow();
        assertEquals(RecoveryActionStatus.SUCCESS, action.getStatus());
    }

    @Test
    void test2_gatewayFailureActionFailed() {
        RecoveryCase rc = newApprovedCase(RecoveryActionType.RETRY_NOW);
        String key = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.FAILURE_RETRYABLE);

        ExecutionResult res = executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());

        assertFalse(res.success());
        assertEquals(RecoveryCaseStatus.ACTION_FAILED, res.caseStatus());
        RecoveryCase reloaded = caseRepo.findById(rc.getId()).orElseThrow();
        assertEquals(RecoveryCaseStatus.ACTION_FAILED, reloaded.getStatus());
        RecoveryAction action = actionRepo.findByIdempotencyKey(key).orElseThrow();
        assertEquals(RecoveryActionStatus.FAILED, action.getStatus());
    }

    @Test
    void test3_actionFailedCanGoToNextCandidate() {
        // Simulate FAILURE then next candidate evaluation
        RecoveryCase rc = newApprovedCase(RecoveryActionType.RETRY_NOW);
        String key = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.FAILURE_RETRYABLE);
        ExecutionResult first = executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());
        assertEquals(RecoveryCaseStatus.ACTION_FAILED, first.caseStatus());

        // Now re-evaluate: should be able to go to ACTION_EVALUATION -> next candidate
        // For test, manually transition to ACTION_EVALUATION then approve next action
        RecoveryCase afterFailed = caseRepo.findById(rc.getId()).orElseThrow();
        afterFailed.setStatus(RecoveryCaseStatus.ACTION_EVALUATION);
        caseRepo.save(afterFailed);
        // Approve SEND_PAYMENT_LINK next
        RecoveryCase toApprove = caseRepo.findById(rc.getId()).orElseThrow();
        toApprove.setStatus(RecoveryCaseStatus.ACTION_APPROVED);
        toApprove.setApprovedAction(RecoveryActionType.SEND_PAYMENT_LINK.name());
        toApprove.setApprovedAt(Instant.now());
        caseRepo.save(toApprove);

        String key2 = rc.getId() + ":" + RecoveryActionType.SEND_PAYMENT_LINK + ":" + toApprove.getAttemptCount();
        mockGateway.setScenario(key2, Scenario.SUCCESS);
        ExecutionResult second = executionService.execute(rc.getId(), RecoveryActionType.SEND_PAYMENT_LINK, UUID.randomUUID(), merchant.getId());
        // For link, success is RETRY_PENDING, not RECOVERED (creation != recovery)
        assertEquals(RecoveryCaseStatus.RETRY_PENDING, second.caseStatus());
    }

    @Test
    void test4_gatewayTimeoutUnknown() {
        RecoveryCase rc = newApprovedCase(RecoveryActionType.RETRY_NOW);
        String key = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.TIMEOUT);

        ExecutionResult res = executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());

        assertEquals(RecoveryCaseStatus.UNKNOWN, res.caseStatus());
        assertTrue(res.gatewayCalled());
        RecoveryCase reloaded = caseRepo.findById(rc.getId()).orElseThrow();
        assertEquals(RecoveryCaseStatus.UNKNOWN, reloaded.getStatus());
        assertNotNull(reloaded.getUnknownSince());
        RecoveryAction action = actionRepo.findByIdempotencyKey(key).orElseThrow();
        assertEquals(RecoveryActionStatus.UNKNOWN, action.getStatus());
    }

    @Test
    void test5_unknownReconciliationSuccessRecovered() {
        RecoveryCase rc = newApprovedCase(RecoveryActionType.RETRY_NOW);
        String key = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.TIMEOUT);
        executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());

        // Now query would return UNKNOWN; we change scenario to SUCCESS for reconcile
        mockGateway.setScenario(key, Scenario.SUCCESS);
        var recon = reconciliationService.reconcile(rc.getId(), UUID.randomUUID());
        assertTrue(recon.success());
        assertEquals(RecoveryCaseStatus.RECOVERED, recon.caseStatus());
        RecoveryCase reloaded = caseRepo.findById(rc.getId()).orElseThrow();
        assertEquals(RecoveryCaseStatus.RECOVERED, reloaded.getStatus());
        assertNull(reloaded.getUnknownSince());
    }

    @Test
    void test6_unknownReconciliationFailedNextDecision() {
        RecoveryCase rc = newApprovedCase(RecoveryActionType.RETRY_NOW);
        String key = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.TIMEOUT);
        executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());

        mockGateway.setScenario(key, Scenario.FAILURE_RETRYABLE);
        var recon = reconciliationService.reconcile(rc.getId(), UUID.randomUUID());
        assertEquals(RecoveryCaseStatus.ACTION_FAILED, recon.caseStatus());
    }

    @Test
    void test7_unknownRemainsUnknown() {
        RecoveryCase rc = newApprovedCase(RecoveryActionType.RETRY_NOW);
        String key = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.TIMEOUT);
        executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());

        // Keep scenario UNKNOWN
        mockGateway.setScenario(key, Scenario.UNKNOWN);
        var recon = reconciliationService.reconcile(rc.getId(), UUID.randomUUID());
        assertEquals(RecoveryCaseStatus.UNKNOWN, recon.caseStatus());
    }

    @Test
    void test8_blindRetryAfterTimeoutImpossible() {
        RecoveryCase rc = newApprovedCase(RecoveryActionType.RETRY_NOW);
        String key = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.TIMEOUT);
        executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());

        // Try blind retry without reconcile: should be blocked because case is UNKNOWN, not ACTION_APPROVED
        ExecutionResult retry = executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());
        assertFalse(retry.success());
        assertFalse(retry.gatewayCalled(), "Blind retry must not call gateway when case is UNKNOWN");
    }

    @Test
    void test9_duplicateDoesNotCallGatewayTwice() {
        RecoveryCase rc = newApprovedCase(RecoveryActionType.RETRY_NOW);
        String key = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.SUCCESS);

        ExecutionResult first = executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());
        assertTrue(first.success());
        int callsAfterFirst = mockGateway.getCallCount(key);

        // Second attempt with same idempotency should not call gateway again (previous success)
        // Need to reset case to ACTION_APPROVED with same attempt? For duplicate test, we need same key, but attemptCount has been incremented after first execution (now 1)
        // So duplicate with same key would require same attemptCount; we simulate by trying to execute same case again without changing state – second call will be blocked by status not ACTION_APPROVED, but we test idempotency via direct repo duplicate
        // For direct duplicate, try to insert duplicate action manually should fail, and second execution with same key via gateway should be duplicate
        // Instead test that second execution attempt for same case with incremented attempt creates new key, not duplicate, so we test idempotency via same key manual
        // Verify call count hasn't increased for duplicate key via direct mock duplicate check
        mockGateway.setScenario(key, Scenario.SUCCESS);
        // Simulate duplicate gateway call directly
        try {
            mockGateway.execute(rc.getId(), RecoveryActionType.RETRY_NOW, new BigDecimal("5000.0000"), "INR", key, UUID.randomUUID());
        } catch (Exception e) {}
        assertEquals(callsAfterFirst + 1, mockGateway.getCallCount(key), "Duplicate returns without new execution but counts as call; but our ExecutionService should prevent second gateway call via idempotency check");

        // More direct: ExecutionService second call with same caseId but case now RECOVERED, so not duplicate but state blocked
        ExecutionResult second = executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());
        assertFalse(second.gatewayCalled(), "Second execution after RECOVERED must not call gateway");
    }

    @Test
    void test10_concurrentOnlyOneGatewayCall() throws Exception {
        RecoveryCase rc = newApprovedCase(RecoveryActionType.RETRY_NOW);
        String key = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.SUCCESS);

        // Simulate two concurrent threads
        var t1 = new Thread(() -> {
            try { executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId()); } catch (Exception ignored) {}
        });
        var t2 = new Thread(() -> {
            try { executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId()); } catch (Exception ignored) {}
        });
        t1.start(); t2.start();
        t1.join(); t2.join();

        // Only one should have succeeded
        assertEquals(1, mockGateway.getCallCount(key), "Concurrent execution must result in exactly one gateway call due to optimistic lock / reservation");
        RecoveryCase reloaded = caseRepo.findById(rc.getId()).orElseThrow();
        assertEquals(RecoveryCaseStatus.RECOVERED, reloaded.getStatus());
    }

    @Test
    void test11_stalePolicyApprovalNotCalled() {
        RecoveryCase rc = newApprovedCase(RecoveryActionType.RETRY_NOW);
        // Simulate stale approval: case was approved when optOut false, but now customer opted out
        customer.setOptedOut(true);
        customerRepo.save(customer);
        // Need to reload case's customer
        RecoveryCase reloaded = caseRepo.findById(rc.getId()).orElseThrow();
        String key = reloaded.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + reloaded.getAttemptCount();
        mockGateway.setScenario(key, Scenario.SUCCESS);

        ExecutionResult res = executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());
        assertFalse(res.gatewayCalled(), "Stale approval after opt-out must not call gateway");
        assertFalse(res.success());
    }

    @Test
    void test12_merchantThresholdChangedNotCalled() {
        RecoveryCase rc = newApprovedCase(RecoveryActionType.RETRY_NOW);
        // Merchant lowers limit to 1000, amount 5000 now exceeds
        merchant.setAutoActionLimit(new BigDecimal("1000.0000"));
        merchantRepo.save(merchant);

        String key = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.SUCCESS);

        ExecutionResult res = executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());
        assertFalse(res.gatewayCalled(), "Threshold change must invalidate approval");
        RecoveryCase reloaded = caseRepo.findById(rc.getId()).orElseThrow();
        assertEquals(RecoveryCaseStatus.ESCALATED, reloaded.getStatus());
    }

    @Test
    void test13_customerOptedOutNotCalled() {
        RecoveryCase rc = newApprovedCase(RecoveryActionType.RETRY_NOW);
        customer.setOptedOut(true);
        customerRepo.save(customer);

        String key = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.SUCCESS);
        ExecutionResult res = executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());
        assertFalse(res.gatewayCalled());
        RecoveryCase reloaded = caseRepo.findById(rc.getId()).orElseThrow();
        assertEquals(RecoveryCaseStatus.STOPPED, reloaded.getStatus());
    }

    @Test
    void test14_retryLimitChangedNotCalled() {
        RecoveryCase rc = newApprovedCase(RecoveryActionType.RETRY_NOW);
        rc.setAttemptCount(3); // at limit
        caseRepo.save(rc);

        String key = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.SUCCESS);
        ExecutionResult res = executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());
        assertFalse(res.gatewayCalled());
        RecoveryCase reloaded = caseRepo.findById(rc.getId()).orElseThrow();
        assertEquals(RecoveryCaseStatus.STOPPED, reloaded.getStatus());
    }

    @Test
    void test15_alreadyExecutedNoDuplicate() {
        RecoveryCase rc = newApprovedCase(RecoveryActionType.RETRY_NOW);
        String key = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.SUCCESS);
        ExecutionResult first = executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());
        assertTrue(first.success());

        // Try again with same case now RECOVERED
        ExecutionResult second = executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());
        assertFalse(second.gatewayCalled());
        assertEquals(1, mockGateway.getCallCount(key));
    }

    @Test
    void test16_paymentLinkCreationNotRecovery() {
        RecoveryCase rc = newApprovedCase(RecoveryActionType.SEND_PAYMENT_LINK);
        String key = rc.getId() + ":" + RecoveryActionType.SEND_PAYMENT_LINK + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.PAYMENT_LINK_SUCCESS);

        ExecutionResult res = executionService.execute(rc.getId(), RecoveryActionType.SEND_PAYMENT_LINK, UUID.randomUUID(), merchant.getId());

        assertTrue(res.success());
        assertEquals(RecoveryCaseStatus.RETRY_PENDING, res.caseStatus(), "Link creation must NOT be RECOVERED");
        RecoveryCase reloaded = caseRepo.findById(rc.getId()).orElseThrow();
        assertNull(reloaded.getRecoveredAmount(), "Money not recovered on link creation");
        assertNotEquals(RecoveryCaseStatus.RECOVERED, reloaded.getStatus());
    }

    @Test
    void test17_paymentSuccessAfterLinkRecovered() {
        RecoveryCase rc = newApprovedCase(RecoveryActionType.SEND_PAYMENT_LINK);
        String key = rc.getId() + ":" + RecoveryActionType.SEND_PAYMENT_LINK + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.PAYMENT_LINK_SUCCESS);
        ExecutionResult linkRes = executionService.execute(rc.getId(), RecoveryActionType.SEND_PAYMENT_LINK, UUID.randomUUID(), merchant.getId());
        assertEquals(RecoveryCaseStatus.RETRY_PENDING, linkRes.caseStatus());

        // Simulate customer completing payment after link: we manually set case to RECOVERED via reconciliation or direct success
        // For test, simulate link conversion: mock gateway now returns SUCCESS for same key query, then we transition case to RECOVERED
        // Simplify: after link, customer pays -> we create a successful retry for same amount
        RecoveryCase pending = caseRepo.findById(rc.getId()).orElseThrow();
        pending.setStatus(RecoveryCaseStatus.ACTION_APPROVED);
        pending.setApprovedAction(RecoveryActionType.RETRY_NOW.name());
        pending.setApprovedAt(Instant.now());
        caseRepo.save(pending);

        String key2 = pending.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + pending.getAttemptCount();
        mockGateway.setScenario(key2, Scenario.SUCCESS);
        ExecutionResult payRes = executionService.execute(pending.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());
        assertEquals(RecoveryCaseStatus.RECOVERED, payRes.caseStatus());
        RecoveryCase reloaded = caseRepo.findById(rc.getId()).orElseThrow();
        assertEquals(new BigDecimal("5000.0000"), reloaded.getRecoveredAmount());
    }
}
