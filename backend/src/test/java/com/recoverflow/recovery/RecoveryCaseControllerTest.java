package com.recoverflow.recovery;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RecoveryCaseControllerTest {

    @Autowired RecoveryCaseController controller;
    @Autowired RecoveryCaseRepository caseRepo;
    @Autowired RecoveryActionRepository actionRepo;
    @Autowired PaymentRepository paymentRepo;
    @Autowired CustomerRepository customerRepo;
    @Autowired MerchantRepository merchantRepo;
    @Autowired com.recoverflow.audit.AuditEventRepository auditRepo;

    private Merchant merchant;
    private Customer customer;
    private Payment payment;

    @BeforeEach
    void setUp() {
        actionRepo.deleteAll();
        auditRepo.deleteAll();
        caseRepo.deleteAll();
        paymentRepo.deleteAll();
        customerRepo.deleteAll();
        merchantRepo.deleteAll();
        merchant = merchantRepo.save(new Merchant(UUID.randomUUID(), "TestMerchant", new BigDecimal("10000.0000"), 3, 48));
        customer = customerRepo.save(new Customer(UUID.randomUUID(), merchant, "test@example.com", "9999"));
        payment = paymentRepo.save(new Payment(UUID.randomUUID(), merchant, customer, new BigDecimal("5000.0000"), "INR", PaymentMethod.CARD, PaymentStatus.FAILED, "BANK_TIMEOUT", "pay_test", Instant.now()));
    }

    private RecoveryCase createCase(RecoveryCaseStatus status, String gatewayCode, BigDecimal amount) {
        Payment p = paymentRepo.save(new Payment(UUID.randomUUID(), merchant, customer, amount, "INR", PaymentMethod.CARD, PaymentStatus.FAILED, gatewayCode, "pay_" + UUID.randomUUID(), Instant.now()));
        RecoveryCase rc = new RecoveryCase(UUID.randomUUID(), p, merchant, customer, amount, "INR", gatewayCode, status);
        rc.setApprovedAction(RecoveryActionType.RETRY_NOW.name());
        return caseRepo.save(rc);
    }

    @Test
    void listReturnsOk() {
        createCase(RecoveryCaseStatus.ACTION_APPROVED, "BANK_TIMEOUT", new BigDecimal("1000.0000"));
        ResponseEntity<Map<String, Object>> res = controller.list(0, 20, "updatedAt", "DESC", null, null, null, null, null, null);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody().get("content"));
        assertTrue((Integer) res.getBody().get("totalElements") >= 1);
    }

    @Test
    void paginationWorks() {
        for (int i = 0; i < 5; i++) createCase(RecoveryCaseStatus.DETECTED, "BANK_TIMEOUT", new BigDecimal("1000.0000"));
        ResponseEntity<Map<String, Object>> r0 = controller.list(0, 2, "updatedAt", "DESC", null, null, null, null, null, null);
        assertEquals(2, ((java.util.List) r0.getBody().get("content")).size());
        assertEquals(5, r0.getBody().get("totalElements"));
        assertEquals(3, r0.getBody().get("totalPages"));
        ResponseEntity<Map<String, Object>> r1 = controller.list(1, 2, "updatedAt", "DESC", null, null, null, null, null, null);
        assertEquals(2, ((java.util.List) r1.getBody().get("content")).size());
    }

    @Test
    void sortingWorks() throws Exception {
        createCase(RecoveryCaseStatus.DETECTED, "BANK_TIMEOUT", new BigDecimal("1000.0000"));
        Thread.sleep(10);
        createCase(RecoveryCaseStatus.DETECTED, "BANK_TIMEOUT", new BigDecimal("2000.0000"));
        ResponseEntity<Map<String, Object>> res = controller.list(0, 20, "amount", "ASC", null, null, null, null, null, null);
        java.util.List<Map<String, Object>> content = (java.util.List<Map<String, Object>>) res.getBody().get("content");
        assertEquals(0, new BigDecimal(content.get(0).get("amount").toString()).compareTo(new BigDecimal("1000.0000")));
    }

    @Test
    void stateFilterWorks() {
        createCase(RecoveryCaseStatus.RECOVERED, "BANK_TIMEOUT", new BigDecimal("1000.0000"));
        createCase(RecoveryCaseStatus.ACTION_FAILED, "BANK_TIMEOUT", new BigDecimal("1000.0000"));
        ResponseEntity<Map<String, Object>> res = controller.list(0, 20, "updatedAt", "DESC", "RECOVERED", null, null, null, null, null);
        java.util.List<Map<String, Object>> content = (java.util.List<Map<String, Object>>) res.getBody().get("content");
        assertEquals(1, content.size());
        assertEquals("RECOVERED", content.get(0).get("status"));
    }

    @Test
    void detailReturnsOk() {
        RecoveryCase rc = createCase(RecoveryCaseStatus.RECOVERED, "BANK_TIMEOUT", new BigDecimal("5000.0000"));
        ResponseEntity<Map<String, Object>> res = controller.detail(rc.getId());
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(rc.getId().toString(), res.getBody().get("caseId"));
        assertEquals("RECOVERED", res.getBody().get("status"));
        assertNotNull(res.getBody().get("recoveryActions"));
        assertNotNull(res.getBody().get("auditEvents"));
    }

    @Test
    void detailNotFound() {
        UUID random = UUID.randomUUID();
        ResponseEntity<Map<String, Object>> res = controller.detail(random);
        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertTrue(res.getBody().containsKey("error"));
    }

    @Test
    void noSensitiveFieldsExposed() {
        RecoveryCase rc = createCase(RecoveryCaseStatus.DETECTED, "BANK_TIMEOUT", new BigDecimal("1000.0000"));
        ResponseEntity<Map<String, Object>> res = controller.detail(rc.getId());
        String body = res.getBody().toString().toLowerCase();
        assertFalse(body.contains("apikey"));
        assertFalse(body.contains("secret"));
        assertFalse(body.contains("credentials"));
        assertFalse(body.contains("authorization"));
    }
}
