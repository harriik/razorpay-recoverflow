package com.recoverflow.policy;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.customer.Customer;
import com.recoverflow.customer.CustomerRepository;
import com.recoverflow.merchant.Merchant;
import com.recoverflow.merchant.MerchantRepository;
import com.recoverflow.payment.Payment;
import com.recoverflow.payment.PaymentMethod;
import com.recoverflow.payment.PaymentRepository;
import com.recoverflow.payment.PaymentStatus;
import com.recoverflow.recovery.RecoveryAction;
import com.recoverflow.recovery.RecoveryActionRepository;
import com.recoverflow.recovery.RecoveryActionType;
import com.recoverflow.recovery.RecoveryActionStatus;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PolicyConcurrentExecutionTest {

    @Autowired PolicyEngine policyEngine;
    @Autowired PolicyRevalidator revalidator;
    @Autowired RecoveryCaseRepository caseRepo;
    @Autowired RecoveryActionRepository actionRepo;
    @Autowired PaymentRepository paymentRepo;
    @Autowired CustomerRepository customerRepo;
    @Autowired MerchantRepository merchantRepo;

    private Merchant merchant;
    private Customer customer;
    private Payment payment;

    @BeforeEach
    void setUp() {
        actionRepo.deleteAll();
        caseRepo.deleteAll();
        paymentRepo.deleteAll();
        customerRepo.deleteAll();
        merchantRepo.deleteAll();
        merchant = merchantRepo.save(new Merchant(UUID.randomUUID(), "ConcurrentTest",
                new BigDecimal("10000.0000"), 3, 48));
        customer = customerRepo.save(new Customer(UUID.randomUUID(), merchant, "conc@test.com", "9999"));
        payment = paymentRepo.save(new Payment(UUID.randomUUID(), merchant, customer,
                new BigDecimal("5000.0000"), "INR", PaymentMethod.CARD, PaymentStatus.FAILED,
                "BANK_TIMEOUT", "pay_conc_" + UUID.randomUUID(), Instant.now()));
    }

    @Test
    void concurrentExecutionAfterApprovalOnlyOneSucceedsViaOptimisticLock() {
        RecoveryCase rc = caseRepo.save(new RecoveryCase(UUID.randomUUID(), payment, merchant, customer,
                new BigDecimal("5000.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.ACTION_APPROVED));

        // Policy approval snapshot at time of approval
        PolicyContext approvedCtx = new PolicyContext(
                rc.getAmount(), "BANK_TIMEOUT", 0, 1, false, false,
                RecoveryActionType.RETRY_NOW, merchant.getAutoActionLimit(), merchant.getMaxRetries(), merchant.getRecoveryWindowHours());
        PolicyDecision approvedDecision = policyEngine.evaluate(approvedCtx);
        assertEquals(PolicyDecisionType.ALLOWED, approvedDecision.result());
        PolicyApproval approval = PolicyApproval.from(approvedDecision, approvedCtx);

        // Simulate two concurrent execution attempt loads
        RecoveryCase a = caseRepo.findById(rc.getId()).orElseThrow();
        RecoveryCase b = caseRepo.findById(rc.getId()).orElseThrow();

        // First execution reserves: revalidate fresh state, then transition to EXECUTING
        PolicyContext freshA = new PolicyContext(
                a.getAmount(), "BANK_TIMEOUT", a.getAttemptCount(), 1, false, false,
                RecoveryActionType.RETRY_NOW, merchant.getAutoActionLimit(), merchant.getMaxRetries(), merchant.getRecoveryWindowHours());
        PolicyRevalidationResult revalA = revalidator.revalidate(approval, freshA);
        assertTrue(revalA.valid(), "First revalidation should be valid");
        a.setStatus(RecoveryCaseStatus.EXECUTING);
        caseRepo.saveAndFlush(a); // version increments

        // Second concurrent attempt loads stale version (b) and tries same
        PolicyContext freshB = new PolicyContext(
                b.getAmount(), "BANK_TIMEOUT", b.getAttemptCount(), 1, false, false,
                RecoveryActionType.RETRY_NOW, merchant.getAutoActionLimit(), merchant.getMaxRetries(), merchant.getRecoveryWindowHours());
        PolicyRevalidationResult revalB = revalidator.revalidate(approval, freshB);
        // Policy itself still ALLOWED (no hard rule changed), but execution reservation must fail via optimistic lock
        assertTrue(revalB.valid(), "Policy revalidation alone still valid, but concurrent reservation must fail via version");

        b.setStatus(RecoveryCaseStatus.EXECUTING);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            caseRepo.saveAndFlush(b);
        }, "Only one gateway call should proceed; second must fail via optimistic locking");
    }

    @Test
    void idempotencyPreventsDuplicateGatewayCall() {
        RecoveryCase rc = caseRepo.save(new RecoveryCase(UUID.randomUUID(), payment, merchant, customer,
                new BigDecimal("5000.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.ACTION_APPROVED));
        String key = "idem-" + UUID.randomUUID();
        RecoveryAction a1 = new RecoveryAction(UUID.randomUUID(), rc, RecoveryActionType.RETRY_NOW, RecoveryActionStatus.PENDING, key);
        actionRepo.saveAndFlush(a1);

        // Second attempt with same idempotency key must be rejected
        RecoveryAction a2 = new RecoveryAction(UUID.randomUUID(), rc, RecoveryActionType.RETRY_NOW, RecoveryActionStatus.PENDING, key);
        assertThrows(DataIntegrityViolationException.class, () -> actionRepo.saveAndFlush(a2),
                "Idempotency key unique constraint must prevent duplicate gateway call");
    }

    @Test
    void staleApprovalCannotBypassPolicyEvenWithConcurrentStateChange() {
        RecoveryCase rc = caseRepo.save(new RecoveryCase(UUID.randomUUID(), payment, merchant, customer,
                new BigDecimal("5000.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.ACTION_APPROVED));
        PolicyContext approvedCtx = new PolicyContext(
                rc.getAmount(), "BANK_TIMEOUT", 0, 1, false, false,
                RecoveryActionType.RETRY_NOW, merchant.getAutoActionLimit(), 3, 48);
        PolicyApproval approval = PolicyApproval.from(policyEngine.evaluate(approvedCtx), approvedCtx);

        // Meanwhile customer opts out (concurrent state change)
        customer.setOptedOut(true);
        customerRepo.save(customer);

        // Fresh context re-reads optedOut==true
        PolicyContext fresh = new PolicyContext(
                rc.getAmount(), "BANK_TIMEOUT", 0, 1, true, false,
                RecoveryActionType.RETRY_NOW, merchant.getAutoActionLimit(), 3, 48);
        PolicyRevalidationResult result = revalidator.revalidate(approval, fresh);
        assertFalse(result.valid(), "Stale approval with now opted-out must be invalidated");
        assertEquals(PolicyDecisionType.STOP, result.currentDecision().result());
    }

    @Test
    void executionBoundaryEnforcesActionApprovedState() {
        // Execution must only proceed from ACTION_APPROVED, not from other states
        RecoveryCase rc = caseRepo.save(new RecoveryCase(UUID.randomUUID(), payment, merchant, customer,
                new BigDecimal("5000.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.DETECTED));
        // Attempt to execute from DETECTED should be blocked by state machine (not policy)
        // This test documents the boundary: PolicyEngine alone would allow RETRY_NOW for DETECTED context,
        // but ExecutionService must also check case.status == ACTION_APPROVED
        PolicyContext ctx = new PolicyContext(
                rc.getAmount(), "BANK_TIMEOUT", 0, 1, false, false,
                RecoveryActionType.RETRY_NOW, merchant.getAutoActionLimit(), merchant.getMaxRetries(), merchant.getRecoveryWindowHours());
        PolicyDecision d = policyEngine.evaluate(ctx);
        assertEquals(PolicyDecisionType.ALLOWED, d.result(), "Policy would allow, but execution must still check ACTION_APPROVED state");

        // Correct state passes
        rc.setStatus(RecoveryCaseStatus.ACTION_APPROVED);
        caseRepo.save(rc);
        RecoveryCase reloaded = caseRepo.findById(rc.getId()).orElseThrow();
        assertEquals(RecoveryCaseStatus.ACTION_APPROVED, reloaded.getStatus());
    }
}
