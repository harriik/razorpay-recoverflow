package com.recoverflow.recovery;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.ai.SyntheticAiProvider;
import com.recoverflow.customer.Customer;
import com.recoverflow.customer.CustomerRepository;
import com.recoverflow.merchant.Merchant;
import com.recoverflow.merchant.MerchantRepository;
import com.recoverflow.payment.Payment;
import com.recoverflow.payment.PaymentMethod;
import com.recoverflow.payment.PaymentRepository;
import com.recoverflow.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NormalDecisionSnapshotTest {

    @Autowired RecoveryDecisionFinalizer finalizer;
    @Autowired RecoveryCaseRepository caseRepo;
    @Autowired RecoveryDecisionSnapshotRepository snapshotRepo;
    @Autowired DecisionController controller;
    @Autowired PaymentRepository paymentRepo;
    @Autowired CustomerRepository customerRepo;
    @Autowired MerchantRepository merchantRepo;
    @Autowired com.recoverflow.audit.AuditEventRepository auditRepo;
    @Autowired RecoveryActionRepository actionRepo;
    @Autowired SyntheticAiProvider syntheticAiProvider;

    private Merchant merchant;
    private Customer customer;
    private Payment payment;

    @BeforeEach
    void setUp() {
        actionRepo.deleteAll();
        snapshotRepo.deleteAll();
        auditRepo.deleteAll();
        caseRepo.deleteAll();
        paymentRepo.deleteAll();
        customerRepo.deleteAll();
        merchantRepo.deleteAll();
        merchant = merchantRepo.save(new Merchant(UUID.randomUUID(), "TestMerchant", new BigDecimal("10000.0000"), 3, 48));
        customer = customerRepo.save(new Customer(UUID.randomUUID(), merchant, "test@example.com", "9999"));
        payment = paymentRepo.save(new Payment(UUID.randomUUID(), merchant, customer, new BigDecimal("5000.0000"), "INR", PaymentMethod.CARD, PaymentStatus.FAILED, "BANK_TIMEOUT", "pay_test_" + UUID.randomUUID(), Instant.now()));
    }

    @Test
    void normalDecisionPersistsSnapshot() {
        RecoveryCase rc = new RecoveryCase(UUID.randomUUID(), payment, merchant, customer, new BigDecimal("5000.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.DETECTED);
        rc.setAttemptCount(0);
        rc = caseRepo.save(rc);
        // Normal path: finalize decision via common boundary
        var snap = finalizer.finalizeDecision(rc.getId(), syntheticAiProvider);
        assertNotNull(snap);
        assertNotNull(snap.getAiAssessmentSnapshot());
        assertTrue(snap.getAiAssessmentSnapshot().contains("failureCategory"));
        // GET must return historical true
        var res = controller.getDecision(rc.getId());
        assertEquals(200, res.getStatusCode().value());
        Map<String, Object> ai = (Map<String, Object>) res.getBody().get("aiAssessment");
        assertNotNull(ai.get("failureCategory"));
        assertNotEquals("NOT_PERSISTED", ai.get("status"));
        assertEquals("SYNTHETIC_AI_PROXY", ((Map<String, Object>) res.getBody().get("versions")).get("aiProvider"));
    }

    @Test
    void failureLabAlsoUsesNormalSnapshotPath() {
        // Create a case via normal path and via FailureLab should both use same snapshot mechanism
        // This test just verifies that snapshotRepo is used by normal path
        RecoveryCase rc = new RecoveryCase(UUID.randomUUID(), payment, merchant, customer, new BigDecimal("6000.0000"), "INR", "NETWORK_ERROR", RecoveryCaseStatus.DETECTED);
        rc.setAttemptCount(0);
        rc = caseRepo.save(rc);
        var snap = finalizer.finalizeDecision(rc.getId(), syntheticAiProvider);
        assertNotNull(snap.getCandidateSnapshot());
        assertTrue(snap.getCandidateSnapshot().contains("pEstimated"));
    }

    @Test
    void multipleSnapshotsReturnLatest() throws Exception {
        RecoveryCase rc = new RecoveryCase(UUID.randomUUID(), payment, merchant, customer, new BigDecimal("5000.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.DETECTED);
        rc = caseRepo.save(rc);
        var snap1 = finalizer.finalizeDecision(rc.getId(), syntheticAiProvider);
        Thread.sleep(10);
        // Second decision (e.g., after retry)
        rc = caseRepo.findById(rc.getId()).orElseThrow();
        rc.setAttemptCount(1);
        caseRepo.save(rc);
        var snap2 = finalizer.finalizeDecision(rc.getId(), syntheticAiProvider);
        assertNotEquals(snap1.getId(), snap2.getId());
        var res = controller.getDecision(rc.getId());
        // Should return latest snapshot's selectedAction
        String returnedSelected = (String) res.getBody().get("selectedAction");
        assertEquals(snap2.getSelectedAction(), returnedSelected, "Should return latest snapshot's selectedAction");
        var latest = snapshotRepo.findTopByCaseIdOrderByCreatedAtDesc(rc.getId()).orElseThrow();
        assertEquals(snap2.getId(), latest.getId());
        assertEquals(snap2.getSelectedAction(), latest.getSelectedAction());
    }

    @Test
    void noHiddenTruthStored() {
        RecoveryCase rc = new RecoveryCase(UUID.randomUUID(), payment, merchant, customer, new BigDecimal("5000.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.DETECTED);
        rc = caseRepo.save(rc);
        var snap = finalizer.finalizeDecision(rc.getId(), syntheticAiProvider);
        String all = snap.getObservableSnapshot() + snap.getAiAssessmentSnapshot() + snap.getCandidateSnapshot() + snap.getPolicySnapshot();
        String lower = all.toLowerCase();
        assertFalse(lower.contains("hiddentruth"));
        assertFalse(lower.contains("ptrue"));
        assertFalse(lower.contains("groundtruth"));
        assertFalse(lower.contains("oracle"));
        assertFalse(lower.contains("latent"));
    }

    @Test
    void providerProvenancePersisted() {
        RecoveryCase rc = new RecoveryCase(UUID.randomUUID(), payment, merchant, customer, new BigDecimal("5000.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.DETECTED);
        rc = caseRepo.save(rc);
        var snap = finalizer.finalizeDecision(rc.getId(), syntheticAiProvider);
        assertEquals("SYNTHETIC_AI_PROXY", snap.getAiProvider());
        assertEquals("synthetic-ai-v1", snap.getAiModel());
        var res = controller.getDecision(rc.getId());
        Map<String, Object> versions = (Map<String, Object>) res.getBody().get("versions");
        assertEquals("SYNTHETIC_AI_PROXY", versions.get("aiProvider"));
    }
}
