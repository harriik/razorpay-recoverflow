package com.recoverflow.execution;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.customer.Customer;
import com.recoverflow.customer.CustomerRepository;
import com.recoverflow.decision.RecoveryDecisionService;
import com.recoverflow.gateway.MockPaymentGateway;
import com.recoverflow.gateway.MockPaymentGateway.Scenario;
import com.recoverflow.ai.AiAssessment;
import com.recoverflow.ai.CandidateAssessment;
import com.recoverflow.ai.CandidateAssessmentLevel;
import com.recoverflow.ai.EvidenceQuality;
import com.recoverflow.ai.FailureCategory;
import com.recoverflow.ai.Recoverability;
import com.recoverflow.ai.RiskLevel;
import com.recoverflow.likelihood.ObservableContext;
import com.recoverflow.merchant.Merchant;
import com.recoverflow.merchant.MerchantRepository;
import com.recoverflow.payment.Payment;
import com.recoverflow.payment.PaymentMethod;
import com.recoverflow.payment.PaymentRepository;
import com.recoverflow.payment.PaymentStatus;
import com.recoverflow.policy.PolicyContext;
import com.recoverflow.recovery.RecoveryActionType;
import com.recoverflow.recovery.RecoveryCase;
import com.recoverflow.recovery.RecoveryCaseRepository;
import com.recoverflow.recovery.RecoveryCaseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class Phase5RevisionTest {

    @Autowired ExecutionService executionService;
    @Autowired ReconciliationService reconciliationService;
    @Autowired RecoveryDecisionService decisionService;
    @Autowired RecoveryCaseRepository caseRepo;
    @Autowired MerchantRepository merchantRepo;
    @Autowired CustomerRepository customerRepo;
    @Autowired PaymentRepository paymentRepo;
    @Autowired MockPaymentGateway mockGateway;
    @Autowired com.recoverflow.recovery.RecoveryActionRepository actionRepo;
    @Autowired com.recoverflow.audit.AuditEventRepository auditRepo;

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
        merchant = merchantRepo.save(new Merchant(UUID.randomUUID(), "RevTest", new BigDecimal("10000.0000"), 3, 48));
        customer = customerRepo.save(new Customer(UUID.randomUUID(), merchant, "rev@test.com", "9999"));
        payment = paymentRepo.save(new Payment(UUID.randomUUID(), merchant, customer,
                new BigDecimal("5000.0000"), "INR", PaymentMethod.CARD, PaymentStatus.FAILED,
                "BANK_TIMEOUT", "pay_rev_" + UUID.randomUUID(), Instant.now()));
    }

    private RecoveryCase newApproved(RecoveryActionType action) {
        RecoveryCase rc = new RecoveryCase(UUID.randomUUID(), payment, merchant, customer,
                new BigDecimal("5000.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.ACTION_APPROVED);
        rc.setApprovedAction(action.name());
        rc.setApprovedAt(Instant.now());
        rc.setApprovedPolicyVersion("v1");
        rc.setApprovedPolicyDecisionId(UUID.randomUUID());
        return caseRepo.save(rc);
    }

    private AiAssessment ai(FailureCategory fc, Recoverability rec, EvidenceQuality eq, RiskLevel risk, Map<RecoveryActionType, CandidateAssessmentLevel> levels) {
        List<CandidateAssessment> list = List.of(
                new CandidateAssessment(RecoveryActionType.RETRY_NOW, levels.get(RecoveryActionType.RETRY_NOW), true, null),
                new CandidateAssessment(RecoveryActionType.SCHEDULE_RETRY, levels.get(RecoveryActionType.SCHEDULE_RETRY), true, null),
                new CandidateAssessment(RecoveryActionType.SEND_PAYMENT_LINK, levels.get(RecoveryActionType.SEND_PAYMENT_LINK), true, null),
                new CandidateAssessment(RecoveryActionType.SEND_REMINDER, levels.get(RecoveryActionType.SEND_REMINDER), true, null)
        );
        return new AiAssessment(fc, rec, list, RecoveryActionType.SCHEDULE_RETRY, eq, risk, "test", "mock", "v1");
    }

    // 2. Clarify payment-link pending state
    @Test
    void pendingStateDistinguishesLinkVsSchedule() {
        // Link
        RecoveryCase rcLink = newApproved(RecoveryActionType.SEND_PAYMENT_LINK);
        String kLink = rcLink.getId() + ":" + RecoveryActionType.SEND_PAYMENT_LINK + ":" + rcLink.getAttemptCount();
        mockGateway.setScenario(kLink, Scenario.PAYMENT_LINK_SUCCESS);
        ExecutionResult rLink = executionService.execute(rcLink.getId(), RecoveryActionType.SEND_PAYMENT_LINK, UUID.randomUUID(), merchant.getId());
        assertEquals(RecoveryCaseStatus.RETRY_PENDING, rLink.caseStatus());
        RecoveryCase afterLink = caseRepo.findById(rcLink.getId()).orElseThrow();
        assertEquals("SEND_PAYMENT_LINK", afterLink.getPendingAction());
        assertEquals("WAITING_CUSTOMER_PAYMENT", afterLink.getPendingReason());
        assertNull(afterLink.getRecoveredAmount(), "Link creation must not count as recovered");

        // Schedule retry
        Payment p2 = paymentRepo.save(new Payment(UUID.randomUUID(), merchant, customer,
                new BigDecimal("5000.0000"), "INR", PaymentMethod.CARD, PaymentStatus.FAILED,
                "BANK_TIMEOUT", "pay_rev2_" + UUID.randomUUID(), Instant.now()));
        RecoveryCase rcSched = new RecoveryCase(UUID.randomUUID(), p2, merchant, customer,
                new BigDecimal("5000.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.ACTION_APPROVED);
        rcSched.setApprovedAction(RecoveryActionType.SCHEDULE_RETRY.name());
        rcSched.setApprovedAt(Instant.now());
        rcSched.setApprovedPolicyVersion("v1");
        rcSched.setApprovedPolicyDecisionId(UUID.randomUUID());
        rcSched = caseRepo.save(rcSched);
        String kSched = rcSched.getId() + ":" + RecoveryActionType.SCHEDULE_RETRY + ":" + rcSched.getAttemptCount();
        mockGateway.setScenario(kSched, Scenario.SUCCESS);
        ExecutionResult rSched = executionService.execute(rcSched.getId(), RecoveryActionType.SCHEDULE_RETRY, UUID.randomUUID(), merchant.getId());
        assertEquals(RecoveryCaseStatus.RETRY_PENDING, rSched.caseStatus());
        RecoveryCase afterSched = caseRepo.findById(rcSched.getId()).orElseThrow();
        assertEquals("SCHEDULE_RETRY", afterSched.getPendingAction());
        assertEquals("SCHEDULED_RETRY_PENDING", afterSched.getPendingReason());
        // Both RETRY_PENDING but distinguishable
        assertNotEquals(afterLink.getPendingAction(), afterSched.getPendingAction());
    }

    // 3. Recalculate after ACTION_FAILED
    @Test
    void recalculateAfterActionFailedExcludesFailedAndUsesFreshContext() {
        ObservableContext obs = new ObservableContext(new BigDecimal("5000.0000"), "INR", PaymentMethod.CARD, "BANK_TIMEOUT", 1, 0, 12, 1, false);
        AiAssessment ai = ai(FailureCategory.TEMPORARY_BANK_FAILURE, Recoverability.HIGH, EvidenceQuality.HIGH, RiskLevel.LOW,
                Map.of(RecoveryActionType.RETRY_NOW, CandidateAssessmentLevel.LOW,
                       RecoveryActionType.SCHEDULE_RETRY, CandidateAssessmentLevel.HIGH,
                       RecoveryActionType.SEND_PAYMENT_LINK, CandidateAssessmentLevel.MEDIUM,
                       RecoveryActionType.SEND_REMINDER, CandidateAssessmentLevel.LOW));
        PolicyContext base = new PolicyContext(new BigDecimal("5000.0000"), "BANK_TIMEOUT", 0, 1, false, false, RecoveryActionType.RETRY_NOW, new BigDecimal("10000.0000"), 3, 48);

        // First decision: should pick SCHEDULE_RETRY as highest EV allowed
        var first = decisionService.decide(obs, ai, new BigDecimal("5000.0000"), base, Set.of());
        assertEquals(RecoveryActionType.SCHEDULE_RETRY, first.selected().action());

        // Simulate SCHEDULE_RETRY failed, now attemptCount increments to 1, elapsed to 5h, and exclude failed
        ObservableContext obs2 = new ObservableContext(new BigDecimal("5000.0000"), "INR", PaymentMethod.CARD, "BANK_TIMEOUT", 5, 1, 12, 1, false);
        PolicyContext base2 = new PolicyContext(new BigDecimal("5000.0000"), "BANK_TIMEOUT", 1, 5, false, false, RecoveryActionType.RETRY_NOW, new BigDecimal("10000.0000"), 3, 48);
        var second = decisionService.decide(obs2, ai, new BigDecimal("5000.0000"), base2, Set.of(RecoveryActionType.SCHEDULE_RETRY));
        // Must not select SCHEDULE_RETRY again
        assertNotEquals(RecoveryActionType.SCHEDULE_RETRY, second.selected().action());
        // And should respect updated attemptCount (still within limit)
        assertNotNull(second.selected());
    }

    @Test
    void attemptCountChangeAltersNextDecisionViaPolicy() {
        // Initially attempt 0 -> some action allowed (SCHEDULE typically highest for BANK_TIMEOUT)
        ObservableContext obs = new ObservableContext(new BigDecimal("5000.0000"), "INR", PaymentMethod.CARD, "BANK_TIMEOUT", 1, 0, 0, 0, false);
        AiAssessment ai = ai(FailureCategory.TEMPORARY_BANK_FAILURE, Recoverability.HIGH, EvidenceQuality.HIGH, RiskLevel.LOW,
                Map.of(RecoveryActionType.RETRY_NOW, CandidateAssessmentLevel.HIGH,
                       RecoveryActionType.SCHEDULE_RETRY, CandidateAssessmentLevel.MEDIUM,
                       RecoveryActionType.SEND_PAYMENT_LINK, CandidateAssessmentLevel.LOW,
                       RecoveryActionType.SEND_REMINDER, CandidateAssessmentLevel.LOW));
        PolicyContext base0 = new PolicyContext(new BigDecimal("5000.0000"), "BANK_TIMEOUT", 0, 1, false, false, RecoveryActionType.RETRY_NOW, new BigDecimal("10000.0000"), 3, 48);
        var r0 = decisionService.decide(obs, ai, new BigDecimal("5000.0000"), base0, Set.of());
        assertNotNull(r0.selected(), "Initial decision must have selection");
        // After 3 attempts, retry-type actions should be blocked by RETRY_LIMIT, so next selection must differ and not be a retry
        PolicyContext base3 = new PolicyContext(new BigDecimal("5000.0000"), "BANK_TIMEOUT", 3, 1, false, false, RecoveryActionType.RETRY_NOW, new BigDecimal("10000.0000"), 3, 48);
        var r3 = decisionService.decide(obs, ai, new BigDecimal("5000.0000"), base3, Set.of());
        // At retry limit 3/3, all automatic actions are STOP per hard rule, so no selection
        assertFalse(r3.hasSelection(), "At retry limit, no automatic action should be allowed");
        assertEquals(com.recoverflow.policy.PolicyDecisionType.STOP, r3.selectedPolicyDecision().result());
    }

    // 4. New idempotency key after UNKNOWN safe retry
    @Test
    void newIdempotencyKeyAfterUnknownSafeRetry() {
        RecoveryCase rc = newApproved(RecoveryActionType.RETRY_NOW);
        String key1 = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key1, Scenario.TIMEOUT);
        ExecutionResult first = executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());
        assertEquals(RecoveryCaseStatus.UNKNOWN, first.caseStatus());
        assertEquals(key1, first.idempotencyKey());

        // Reconcile confirms NOT executed (we set scenario to FAILURE_RETRYABLE which will make reconcile go to ACTION_FAILED)
        // For safe retry test, we want reconcile to indicate NOT_EXECUTED: we keep scenario as UNKNOWN then manually transition?
        // Simulate: query returns UNKNOWN -> still UNKNOWN, but we will move case to ACTION_FAILED via manual to allow retry
        // More accurate: mock query for UNKNOWN returns UNKNOWN, we then transition case via reconciliation that returns ACTION_FAILED
        mockGateway.setScenario(key1, Scenario.FAILURE_RETRYABLE);
        var recon = reconciliationService.reconcile(rc.getId(), UUID.randomUUID());
        assertEquals(RecoveryCaseStatus.ACTION_FAILED, recon.caseStatus());

        // Now fresh decision: case is ACTION_FAILED, need to go to ACTION_EVALUATION -> approve again
        RecoveryCase after = caseRepo.findById(rc.getId()).orElseThrow();
        after.setStatus(RecoveryCaseStatus.ACTION_APPROVED);
        after.setApprovedAction(RecoveryActionType.RETRY_NOW.name());
        after.setApprovedAt(Instant.now());
        // attemptCount was incremented to 1 on first execution, so next key should be ...:1
        caseRepo.save(after);
        RecoveryCase reloaded = caseRepo.findById(rc.getId()).orElseThrow();
        String key2 = reloaded.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + reloaded.getAttemptCount();
        assertNotEquals(key1, key2, "Every new financial attempt must get new idempotency key");
        mockGateway.setScenario(key2, Scenario.SUCCESS);
        ExecutionResult second = executionService.execute(reloaded.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());
        assertEquals(RecoveryCaseStatus.RECOVERED, second.caseStatus());
        assertEquals(key2, second.idempotencyKey());
        assertEquals(1, mockGateway.getCallCount(key1));
        assertEquals(1, mockGateway.getCallCount(key2));
        // Ensure no duplicate reuse
        assertNotEquals(key1, second.idempotencyKey());
    }

    @Test
    void duplicateGatewayNotExecutedForSameKey() {
        RecoveryCase rc = newApproved(RecoveryActionType.RETRY_NOW);
        String key = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.SUCCESS);
        ExecutionResult first = executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());
        assertTrue(first.success());
        // Second attempt with same key via direct gateway should be DUPLICATE
        try {
            var dup = mockGateway.execute(rc.getId(), RecoveryActionType.RETRY_NOW, new BigDecimal("5000.0000"), "INR", key, UUID.randomUUID());
            assertEquals(com.recoverflow.gateway.GatewayStatus.DUPLICATE, dup.status());
        } catch (Exception e) {
            fail("Duplicate should not throw timeout");
        }
    }

    @Test
    void timeoutAlwaysProducesUnknown() {
        RecoveryCase rc = newApproved(RecoveryActionType.RETRY_NOW);
        String key = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.TIMEOUT);
        ExecutionResult res = executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());
        assertEquals(RecoveryCaseStatus.UNKNOWN, res.caseStatus());
        RecoveryCase reloaded = caseRepo.findById(rc.getId()).orElseThrow();
        assertNotNull(reloaded.getUnknownSince());
    }

    @Test
    void unknownLeavesOnlyThroughReconciliationOrEscalation() {
        RecoveryCase rc = newApproved(RecoveryActionType.RETRY_NOW);
        String key = rc.getId() + ":" + RecoveryActionType.RETRY_NOW + ":" + rc.getAttemptCount();
        mockGateway.setScenario(key, Scenario.TIMEOUT);
        executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());
        RecoveryCase unknown = caseRepo.findById(rc.getId()).orElseThrow();
        assertEquals(RecoveryCaseStatus.UNKNOWN, unknown.getStatus());
        // Attempt direct retry without reconciliation must be blocked
        ExecutionResult retry = executionService.execute(rc.getId(), RecoveryActionType.RETRY_NOW, UUID.randomUUID(), merchant.getId());
        assertFalse(retry.gatewayCalled(), "Unknown must not allow blind retry, only reconciliation");

        // Reconciliation is the only exit
        mockGateway.setScenario(key, Scenario.SUCCESS);
        var recon = reconciliationService.reconcile(rc.getId(), UUID.randomUUID());
        assertEquals(RecoveryCaseStatus.RECOVERED, recon.caseStatus());
    }
}
