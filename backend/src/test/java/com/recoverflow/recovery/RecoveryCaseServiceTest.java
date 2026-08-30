package com.recoverflow.recovery;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.audit.AuditEvent;
import com.recoverflow.audit.AuditEventRepository;
import com.recoverflow.customer.Customer;
import com.recoverflow.customer.CustomerRepository;
import com.recoverflow.merchant.Merchant;
import com.recoverflow.merchant.MerchantRepository;
import com.recoverflow.payment.Payment;
import com.recoverflow.payment.PaymentMethod;
import com.recoverflow.payment.PaymentRepository;
import com.recoverflow.payment.PaymentStatus;
import com.recoverflow.audit.AuditActor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RecoveryCaseServiceTest {

    @Autowired RecoveryCaseService service;
    @Autowired RecoveryCaseRepository caseRepo;
    @Autowired PaymentRepository paymentRepo;
    @Autowired CustomerRepository customerRepo;
    @Autowired MerchantRepository merchantRepo;
    @Autowired AuditEventRepository auditRepo;

    private Merchant merchant;
    private Customer customer;
    private Payment payment;
    private RecoveryCase recoveryCase;

    @BeforeEach
    void setUp() {
        auditRepo.deleteAll();
        caseRepo.deleteAll();
        paymentRepo.deleteAll();
        customerRepo.deleteAll();
        merchantRepo.deleteAll();

        merchant = merchantRepo.save(new Merchant(UUID.randomUUID(), "Test Merchant",
                new BigDecimal("10000.0000"), 3, 48));
        customer = customerRepo.save(new Customer(UUID.randomUUID(), merchant, "test@example.com", "9999999999"));
        payment = paymentRepo.save(new Payment(UUID.randomUUID(), merchant, customer,
                new BigDecimal("6500.0000"), "INR", PaymentMethod.CARD, PaymentStatus.FAILED,
                "BANK_TIMEOUT", "pay_" + UUID.randomUUID(), Instant.now()));
        recoveryCase = caseRepo.save(new RecoveryCase(UUID.randomUUID(), payment, merchant, customer,
                new BigDecimal("6500.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.DETECTED));
    }

    @Test
    void validTransitionPersistsAndCreatesAudit() {
        UUID corr = UUID.randomUUID();
        RecoveryCase updated = service.transition(recoveryCase, RecoveryCaseStatus.CLASSIFYING, AuditActor.SYSTEM, corr, "TEST");
        assertEquals(RecoveryCaseStatus.CLASSIFYING, updated.getStatus());

        List<AuditEvent> audits = auditRepo.findByCaseIdOrderByCreatedAtAsc(recoveryCase.getId());
        assertEquals(1, audits.size());
        AuditEvent ae = audits.get(0);
        assertEquals(corr, ae.getCorrelationId());
        assertEquals("DETECTED", ae.getFromState());
        assertEquals("CLASSIFYING", ae.getToState());
        assertEquals(AuditActor.SYSTEM, ae.getActor());
    }

    @Test
    void invalidTransitionThrows() {
        // DETECTED -> RECOVERED is invalid
        assertThrows(InvalidTransitionException.class,
                () -> service.transition(recoveryCase, RecoveryCaseStatus.RECOVERED, AuditActor.SYSTEM, UUID.randomUUID(), "TEST"));
        // No audit created on invalid
        assertEquals(0, auditRepo.findByCaseIdOrderByCreatedAtAsc(recoveryCase.getId()).size());
        // Status unchanged
        RecoveryCase reloaded = caseRepo.findById(recoveryCase.getId()).orElseThrow();
        assertEquals(RecoveryCaseStatus.DETECTED, reloaded.getStatus());
    }

    @Test
    void terminalStateHasNoOutgoing() {
        recoveryCase.setStatus(RecoveryCaseStatus.RECOVERED);
        recoveryCase = caseRepo.save(recoveryCase);
        assertThrows(InvalidTransitionException.class,
                () -> service.transition(recoveryCase, RecoveryCaseStatus.ACTION_FAILED, AuditActor.SYSTEM, UUID.randomUUID(), "TEST"));
        assertThrows(InvalidTransitionException.class,
                () -> service.transition(recoveryCase, RecoveryCaseStatus.STOPPED, AuditActor.SYSTEM, UUID.randomUUID(), "TEST"));
    }

    @Test
    void actionFailedCanLoopToActionEvaluation() {
        recoveryCase.setStatus(RecoveryCaseStatus.ACTION_FAILED);
        recoveryCase = caseRepo.save(recoveryCase);

        RecoveryCase updated = service.transition(recoveryCase, RecoveryCaseStatus.ACTION_EVALUATION, AuditActor.SYSTEM, UUID.randomUUID(), "RETRY");
        assertEquals(RecoveryCaseStatus.ACTION_EVALUATION, updated.getStatus());

        List<AuditEvent> audits = auditRepo.findByCaseIdOrderByCreatedAtAsc(recoveryCase.getId());
        assertEquals(1, audits.size());
        assertEquals("ACTION_FAILED", audits.get(0).getFromState());
        assertEquals("ACTION_EVALUATION", audits.get(0).getToState());
    }

    @Test
    void unknownHandlingSetsTimestamp() {
        recoveryCase.setStatus(RecoveryCaseStatus.EXECUTING);
        recoveryCase = caseRepo.save(recoveryCase);

        RecoveryCase unknown = service.transition(recoveryCase, RecoveryCaseStatus.UNKNOWN, AuditActor.GATEWAY, UUID.randomUUID(), "TIMEOUT");
        assertEquals(RecoveryCaseStatus.UNKNOWN, unknown.getStatus());
        assertNotNull(unknown.getUnknownSince());

        // Reconcile back to RECOVERED clears timestamp
        RecoveryCase recovered = service.transition(unknown, RecoveryCaseStatus.RECOVERED, AuditActor.GATEWAY, UUID.randomUUID(), "RECONCILE");
        assertEquals(RecoveryCaseStatus.RECOVERED, recovered.getStatus());
        assertNull(recovered.getUnknownSince());
    }

    @Test
    void fullHappyPath() {
        UUID corr = UUID.randomUUID();
        service.transition(recoveryCase, RecoveryCaseStatus.CLASSIFYING, AuditActor.SYSTEM, corr, "TEST");
        RecoveryCase c1 = caseRepo.findById(recoveryCase.getId()).orElseThrow();
        service.transition(c1, RecoveryCaseStatus.ELIGIBILITY_CHECK, AuditActor.SYSTEM, corr, "TEST");
        RecoveryCase c2 = caseRepo.findById(recoveryCase.getId()).orElseThrow();
        service.transition(c2, RecoveryCaseStatus.AI_ANALYSIS, AuditActor.SYSTEM, corr, "TEST");
        RecoveryCase c3 = caseRepo.findById(recoveryCase.getId()).orElseThrow();
        service.transition(c3, RecoveryCaseStatus.ACTION_EVALUATION, AuditActor.AI, corr, "TEST");
        RecoveryCase c4 = caseRepo.findById(recoveryCase.getId()).orElseThrow();
        service.transition(c4, RecoveryCaseStatus.POLICY_EVALUATION, AuditActor.SYSTEM, corr, "TEST");
        RecoveryCase c5 = caseRepo.findById(recoveryCase.getId()).orElseThrow();
        service.transition(c5, RecoveryCaseStatus.ACTION_APPROVED, AuditActor.POLICY, corr, "TEST");
        RecoveryCase c6 = caseRepo.findById(recoveryCase.getId()).orElseThrow();
        service.transition(c6, RecoveryCaseStatus.EXECUTING, AuditActor.SYSTEM, corr, "TEST");
        RecoveryCase c7 = caseRepo.findById(recoveryCase.getId()).orElseThrow();
        service.transition(c7, RecoveryCaseStatus.RECOVERED, AuditActor.GATEWAY, corr, "TEST");

        RecoveryCase finalCase = caseRepo.findById(recoveryCase.getId()).orElseThrow();
        assertEquals(RecoveryCaseStatus.RECOVERED, finalCase.getStatus());

        List<AuditEvent> audits = auditRepo.findByCaseIdOrderByCreatedAtAsc(recoveryCase.getId());
        assertEquals(8, audits.size());
        assertEquals(corr, audits.get(0).getCorrelationId());
    }

    @Test
    void failedTerminalIsTerminal() {
        recoveryCase.setStatus(RecoveryCaseStatus.FAILED_TERMINAL);
        recoveryCase = caseRepo.save(recoveryCase);
        assertThrows(InvalidTransitionException.class,
                () -> service.transition(recoveryCase, RecoveryCaseStatus.ACTION_EVALUATION, AuditActor.SYSTEM, UUID.randomUUID(), "TEST"));
    }
}
