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

@SpringBootTest
@ActiveProfiles("test")
class DecisionControllerTest {

    @Autowired DecisionController controller;
    @Autowired RecoveryCaseRepository caseRepo;
    @Autowired RecoveryActionRepository actionRepo;
    @Autowired PaymentRepository paymentRepo;
    @Autowired CustomerRepository customerRepo;
    @Autowired MerchantRepository merchantRepo;
    @Autowired com.recoverflow.audit.AuditEventRepository auditRepo;

    private Merchant merchant;
    private Customer customer;
    private Payment payment;
    private RecoveryCase rc;

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
        payment = paymentRepo.save(new Payment(UUID.randomUUID(), merchant, customer, new BigDecimal("5000.0000"), "INR", PaymentMethod.CARD, PaymentStatus.FAILED, "BANK_TIMEOUT", "pay_test_" + UUID.randomUUID(), Instant.now()));
        rc = new RecoveryCase(UUID.randomUUID(), payment, merchant, customer, new BigDecimal("5000.0000"), "INR", "BANK_TIMEOUT", RecoveryCaseStatus.ACTION_APPROVED);
        rc.setApprovedAction(RecoveryActionType.RETRY_NOW.name());
        rc.setApprovedAt(Instant.now());
        rc.setApprovedPolicyVersion("v1");
        rc = caseRepo.save(rc);
    }

    @Test
    void decisionEndpointReturnsCase() {
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertTrue(res.getBody().containsKey("case"));
        Map<String, Object> caseMap = (Map<String, Object>) res.getBody().get("case");
        assertEquals(rc.getId().toString(), caseMap.get("caseId"));
    }

    @Test
    void observableFieldsReturned() {
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        Map<String, Object> obs = (Map<String, Object>) res.getBody().get("observableEvidence");
        assertNotNull(obs);
        assertTrue(obs.containsKey("amount"));
        assertTrue(obs.containsKey("gatewayCode"));
        assertTrue(obs.containsKey("attemptCount"));
        assertTrue(obs.containsKey("priorSuccessCount"));
        assertTrue(obs.containsKey("linkAlreadySent"));
        assertEquals("BANK_TIMEOUT", obs.get("gatewayCode"));
    }

    @Test
    void hiddenTruthNotReturned() {
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        String body = res.getBody().toString().toLowerCase();
        assertFalse(body.contains("hiddentruth"));
        assertFalse(body.contains("latentrecovery"));
        Map<String, Object> obs = (Map<String, Object>) res.getBody().get("observableEvidence");
        String obsStr = obs.toString().toLowerCase();
        assertFalse(obsStr.contains("ptrue"));
        assertFalse(obsStr.contains("hidden"));
    }

    @Test
    void pTrueNotReturned() {
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        String body = res.getBody().toString();
        // Ensure no P_true field at top level
        assertFalse(body.contains("P_true"));
        assertFalse(body.contains("pTrue"));
        // Observable should not have P_true
        Map<String, Object> obs = (Map<String, Object>) res.getBody().get("observableEvidence");
        assertFalse(obs.containsKey("pTrue"));
        assertFalse(obs.containsKey("P_true"));
    }

    @Test
    void groundTruthNotReturned() {
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        String body = res.getBody().toString().toLowerCase();
        assertFalse(body.contains("groundtruthoutcomes"));
        assertFalse(body.contains("ground_truth"));
    }

    @Test
    void candidateListReturned() {
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        Object candidates = res.getBody().get("candidates");
        assertTrue(candidates instanceof java.util.List);
        java.util.List<Map<String, Object>> list = (java.util.List<Map<String, Object>>) candidates;
        assertFalse(list.isEmpty());
        for (Map<String, Object> c : list) {
            assertTrue(c.containsKey("action"));
            assertTrue(c.containsKey("pEstimated"));
            assertTrue(c.containsKey("expectedNetValue"));
            assertTrue(c.containsKey("policyResult"));
        }
    }

    @Test
    void policyDecisionsReturned() {
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        java.util.List<Map<String, Object>> candidates = (java.util.List<Map<String, Object>>) res.getBody().get("candidates");
        for (Map<String, Object> c : candidates) {
            assertTrue(c.containsKey("policyResult"));
            String pr = (String) c.get("policyResult");
            assertTrue(pr.equals("ALLOWED") || pr.equals("BLOCKED") || pr.equals("ESCALATE") || pr.equals("STOP"));
            assertTrue(c.containsKey("policyRuleId"));
            assertTrue(c.containsKey("policyReason"));
        }
        Map<String, Object> policySummary = (Map<String, Object>) res.getBody().get("policySummary");
        assertNotNull(policySummary);
    }

    @Test
    void selectedActionReturned() {
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        assertTrue(res.getBody().containsKey("selectedAction"));
        assertTrue(res.getBody().containsKey("selectionReason"));
        // selectedAction should be from persisted approvedAction or from current decision
        Object sel = res.getBody().get("selectedAction");
        assertNotNull(sel);
        assertEquals("RETRY_NOW", sel.toString());
    }

    @Test
    void notFoundWorks() {
        UUID random = UUID.randomUUID();
        ResponseEntity<Map<String, Object>> res = controller.getDecision(random);
        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertTrue(res.getBody().containsKey("error"));
    }

    @Test
    void sensitiveFieldsAbsent() {
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        String body = res.getBody().toString().toLowerCase();
        assertFalse(body.contains("apikey"));
        assertFalse(body.contains("secret"));
        assertFalse(body.contains("credentials"));
        assertFalse(body.contains("authorization"));
        assertFalse(body.contains("gateway_ref") && body.contains("secret")); // gatewayRef is safe token, not secret
    }

    @Test
    void aiAssessmentNotPersistedStatus() {
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        Map<String, Object> ai = (Map<String, Object>) res.getBody().get("aiAssessment");
        assertNotNull(ai);
        assertEquals("NOT_PERSISTED", ai.get("status"));
        assertTrue(ai.containsKey("description"));
        // Ensure not fabricated from gatewayCode
        assertNull(ai.get("failureCategory"));
    }

    @Test
    void estimatorBreakdownNotFabricated() {
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        java.util.List<Map<String, Object>> candidates = (java.util.List<Map<String, Object>>) res.getBody().get("candidates");
        for (Map<String, Object> c : candidates) {
            // Breakdown fields should be null (not persisted, not fabricated)
            assertNull(c.get("baseContribution"));
            assertNull(c.get("aiContribution"));
        }
        // But authoritative fields should be present
        for (Map<String, Object> c : candidates) {
            assertNotNull(c.get("pEstimated"));
            assertNotNull(c.get("expectedNetValue"));
        }
    }

    @Test
    void hiddenDataLeakageChecks() {
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        String body = res.getBody().toString().toLowerCase();
        // Ensure no hidden truth fields at any level
        assertFalse(body.contains("truefailurecategory"));
        assertFalse(body.contains("latentrecoverypropensity"));
        assertFalse(body.contains("ptrue"));
        // Observable should not leak
        Map<String, Object> obs = (Map<String, Object>) res.getBody().get("observableEvidence");
        assertFalse(obs.toString().toLowerCase().contains("true"));
    }
}
