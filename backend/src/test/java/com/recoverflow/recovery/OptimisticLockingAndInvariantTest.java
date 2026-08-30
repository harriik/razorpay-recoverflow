package com.recoverflow.recovery;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.customer.Customer;
import com.recoverflow.customer.CustomerRepository;
import com.recoverflow.merchant.Merchant;
import com.recoverflow.merchant.MerchantRepository;
import com.recoverflow.payment.Payment;
import com.recoverflow.payment.PaymentMethod;
import com.recoverflow.payment.PaymentRepository;
import com.recoverflow.payment.PaymentStatus;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class OptimisticLockingAndInvariantTest {

    @Autowired MerchantRepository merchantRepo;
    @Autowired CustomerRepository customerRepo;
    @Autowired PaymentRepository paymentRepo;
    @Autowired RecoveryCaseRepository caseRepo;
    @Autowired RecoveryActionRepository actionRepo;
    @Autowired EntityManager em;

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

        merchant = merchantRepo.save(new Merchant(UUID.randomUUID(), "Lock Test",
                new BigDecimal("10000.0000"), 3, 48));
        customer = customerRepo.save(new Customer(UUID.randomUUID(), merchant, "lock@test.com", "9999"));
        payment = paymentRepo.save(new Payment(UUID.randomUUID(), merchant, customer,
                new BigDecimal("1000.0000"), "INR", PaymentMethod.CARD, PaymentStatus.FAILED,
                "BANK_TIMEOUT", "pay_lock_" + UUID.randomUUID(), Instant.now()));
    }

    @Test
    void versionIncrementsOnUpdate() {
        RecoveryCase rc = caseRepo.save(new RecoveryCase(UUID.randomUUID(), payment, merchant, customer,
                new BigDecimal("1000.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.DETECTED));
        Integer v0 = rc.getVersion();
        rc.setStatus(RecoveryCaseStatus.CLASSIFYING);
        RecoveryCase saved = caseRepo.saveAndFlush(rc);
        assertNotNull(saved.getVersion());
        assertTrue(saved.getVersion() > v0, "Version should increment on update: " + v0 + " -> " + saved.getVersion());
    }

    @Test
    void optimisticLockPreventsConcurrentModification() {
        RecoveryCase rc = caseRepo.save(new RecoveryCase(UUID.randomUUID(), payment, merchant, customer,
                new BigDecimal("1000.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.DETECTED));
        UUID id = rc.getId();

        // Simulate two concurrent loads
        RecoveryCase a = caseRepo.findById(id).orElseThrow();
        RecoveryCase b = caseRepo.findById(id).orElseThrow();

        a.setStatus(RecoveryCaseStatus.CLASSIFYING);
        caseRepo.saveAndFlush(a); // version increments

        b.setStatus(RecoveryCaseStatus.ELIGIBILITY_CHECK);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            caseRepo.saveAndFlush(b);
        });
    }

    @Test
    void paymentAmountMustBePositiveInvariant() {
        // Domain invariant: amount > 0, enforced by CHECK and @DecimalMin validation
        Payment p = new Payment(UUID.randomUUID(), merchant, customer,
                new BigDecimal("-5.0000"), "INR", PaymentMethod.UPI, PaymentStatus.FAILED,
                "BANK_TIMEOUT", "pay_neg_" + UUID.randomUUID(), Instant.now());
        assertTrue(p.getAmount().compareTo(BigDecimal.ZERO) < 0, "Negative amount should be detectable");
        // Bean Validation should reject; DataIntegrityViolation is also acceptable for DB CHECK
        assertThrows(Exception.class, () -> paymentRepo.saveAndFlush(p));
    }

    @Test
    void idempotencyKeyUniqueConstraintPreventsDuplicateAction() {
        RecoveryCase rc = caseRepo.save(new RecoveryCase(UUID.randomUUID(), payment, merchant, customer,
                new BigDecimal("1000.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.DETECTED));
        String key = "idem-" + UUID.randomUUID();
        RecoveryAction a1 = new RecoveryAction(UUID.randomUUID(), rc, RecoveryActionType.RETRY_NOW,
                RecoveryActionStatus.PENDING, key);
        actionRepo.saveAndFlush(a1);

        RecoveryAction a2 = new RecoveryAction(UUID.randomUUID(), rc, RecoveryActionType.RETRY_NOW,
                RecoveryActionStatus.PENDING, key); // same key
        assertThrows(DataIntegrityViolationException.class, () -> {
            actionRepo.saveAndFlush(a2);
        });
    }

    @Test
    void paymentIdUniqueConstraintPreventsDuplicateRecoveryCase() {
        RecoveryCase rc1 = caseRepo.save(new RecoveryCase(UUID.randomUUID(), payment, merchant, customer,
                new BigDecimal("1000.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.DETECTED));
        // Second case for same payment should violate UNIQUE(payment_id)
        RecoveryCase rc2 = new RecoveryCase(UUID.randomUUID(), payment, merchant, customer,
                new BigDecimal("1000.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.DETECTED);
        assertThrows(DataIntegrityViolationException.class, () -> {
            caseRepo.saveAndFlush(rc2);
        });
    }

    @Test
    void monetaryPrecisionFourDecimals() {
        Payment p = new Payment(UUID.randomUUID(), merchant, customer,
                new BigDecimal("1234.5678"), "INR", PaymentMethod.WALLET, PaymentStatus.FAILED,
                "TEST", "pay_prec_" + UUID.randomUUID(), Instant.now());
        Payment saved = paymentRepo.saveAndFlush(p);
        Payment reloaded = paymentRepo.findById(saved.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1234.5678").compareTo(reloaded.getAmount()),
                "Amount must preserve 4 decimal places");
        assertEquals(4, reloaded.getAmount().scale(), "Scale must be 4 per NUMERIC(19,4)");
    }
}
