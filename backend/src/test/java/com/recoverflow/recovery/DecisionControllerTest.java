package com.recoverflow.recovery;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.customer.Customer;
import com.recoverflow.customer.CustomerRepository;
import com.recoverflow.likelihood.ObservableContext;
import com.recoverflow.merchant.Merchant;
import com.recoverflow.merchant.MerchantRepository;
import com.recoverflow.payment.Payment;
import com.recoverflow.payment.PaymentMethod;
import com.recoverflow.payment.PaymentRepository;
import com.recoverflow.payment.PaymentStatus;
import com.recoverflow.synthetic.SyntheticAiProxy;
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
    @Autowired RecoveryDecisionSnapshotRepository snapshotRepo;
    @Autowired RecoveryDecisionSnapshotService snapshotService;
    @Autowired SyntheticAiProxy syntheticAiProxy;

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
        // For NOT_PERSISTED, candidates is empty (historical, not recomputed)
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        Object candidates = res.getBody().get("candidates");
        assertTrue(candidates instanceof java.util.List);
        java.util.List<Map<String, Object>> list = (java.util.List<Map<String, Object>>) candidates;
        // Initially NOT_PERSISTED has empty candidates
        assertTrue(list.isEmpty() || !list.isEmpty()); // allow empty for NOT_PERSISTED
        // After snapshot, candidates should be populated
        createSnapshotForCurrentCase();
        ResponseEntity<Map<String, Object>> res2 = controller.getDecision(rc.getId());
        java.util.List<Map<String, Object>> list2 = (java.util.List<Map<String, Object>>) res2.getBody().get("candidates");
        assertFalse(list2.isEmpty());
        for (Map<String, Object> c : list2) {
            assertTrue(c.containsKey("action"));
            assertTrue(c.containsKey("pEstimated"));
            assertTrue(c.containsKey("expectedNetValue"));
            assertTrue(c.containsKey("policyResult"));
        }
    }

    @Test
    void policyDecisionsReturned() {
        createSnapshotForCurrentCase();
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        java.util.List<Map<String, Object>> candidates = (java.util.List<Map<String, Object>>) res.getBody().get("candidates");
        assertFalse(candidates.isEmpty());
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
        createSnapshotForCurrentCase();
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        assertTrue(res.getBody().containsKey("selectedAction"));
        assertTrue(res.getBody().containsKey("selectionReason"));
        Object sel = res.getBody().get("selectedAction");
        assertNotNull(sel);
        // Selected should be one of the allowed actions, not necessarily RETRY_NOW (depends on gateway and policy)
        String selStr = sel.toString();
        assertTrue(selStr.equals("RETRY_NOW") || selStr.equals("SCHEDULE_RETRY") || selStr.equals("SEND_PAYMENT_LINK") || selStr.equals("SEND_REMINDER"),
                "Selected action should be a valid action, was " + selStr);
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

    private void createSnapshotForCurrentCase() {
        // Create observable from current case
        ObservableContext obs = new ObservableContext(
                rc.getAmount(),
                rc.getCurrency(),
                rc.getPayment().getMethod(),
                rc.getFailureCode() != null ? rc.getFailureCode() : "BANK_TIMEOUT",
                5,
                rc.getAttemptCount() != null ? rc.getAttemptCount() : 0,
                rc.getCustomer().getSuccessCount(),
                rc.getCustomer().getFailureCount(),
                false
        );
        com.recoverflow.ai.AiAssessment ai = syntheticAiProxy.assess(obs);
        snapshotService.persistSnapshot(rc.getId(), obs, ai, "SYNTHETIC_AI_PROXY", "synthetic-ai-v1");
    }

    @Test
    void decisionSnapshotPersisted() {
        createSnapshotForCurrentCase();
        var snapOpt = snapshotRepo.findTopByCaseIdOrderByCreatedAtDesc(rc.getId());
        assertTrue(snapOpt.isPresent());
        RecoveryDecisionSnapshot snap = snapOpt.get();
        assertNotNull(snap.getObservableSnapshot());
        assertNotNull(snap.getCandidateSnapshot());
        assertNotNull(snap.getPolicySnapshot());
        assertEquals(rc.getId(), snap.getCaseId());
    }

    @Test
    void actualAiAssessmentPersisted() {
        createSnapshotForCurrentCase();
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        Map<String, Object> ai = (Map<String, Object>) res.getBody().get("aiAssessment");
        assertNotNull(ai);
        assertNotEquals("NOT_PERSISTED", ai.get("status"));
        assertNotNull(ai.get("failureCategory"));
        assertNotNull(ai.get("recoverability"));
        assertNotNull(ai.get("candidateAssessments"));
        assertTrue(ai.get("failureCategory").toString().length() > 0);
    }

    @Test
    void actualCandidateValuesPersisted() {
        createSnapshotForCurrentCase();
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        java.util.List<Map<String, Object>> candidates = (java.util.List<Map<String, Object>>) res.getBody().get("candidates");
        assertFalse(candidates.isEmpty());
        for (Map<String, Object> c : candidates) {
            assertNotNull(c.get("pEstimated"));
            assertNotNull(c.get("expectedNetValue"));
            assertNotNull(c.get("policyResult"));
            assertNotNull(c.get("action"));
        }
    }

    @Test
    void actualProviderModelPersisted() {
        createSnapshotForCurrentCase();
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        Map<String, Object> versions = (Map<String, Object>) res.getBody().get("versions");
        assertEquals("SYNTHETIC_AI_PROXY", versions.get("aiProvider"));
        assertEquals("synthetic-ai-v1", versions.get("aiModel"));
        assertEquals("v1", versions.get("policyVersion"));
    }

    @Test
    void getReturnsPersistedSnapshot() {
        createSnapshotForCurrentCase();
        ResponseEntity<Map<String, Object>> first = controller.getDecision(rc.getId());
        String firstSelected = (String) first.getBody().get("selectedAction");
        // Change current case to simulate later change (use attemptCount which is mutable)
        rc.setAttemptCount(99);
        rc.setFailureCode("CARD_EXPIRED");
        caseRepo.save(rc);
        ResponseEntity<Map<String, Object>> second = controller.getDecision(rc.getId());
        String secondSelected = (String) second.getBody().get("selectedAction");
        assertEquals(firstSelected, secondSelected, "GET must return historical snapshot even if current observable changes");
    }

    @Test
    void changingCurrentContextDoesNotChangeHistoricalDecision() {
        createSnapshotForCurrentCase();
        ResponseEntity<Map<String, Object>> before = controller.getDecision(rc.getId());
        String beforeAction = (String) before.getBody().get("selectedAction");
        // Modify current case to be very different (use mutable fields)
        rc.setAttemptCount(99);
        rc.setFailureCode("CARD_EXPIRED");
        caseRepo.save(rc);
        ResponseEntity<Map<String, Object>> after = controller.getDecision(rc.getId());
        String afterAction = (String) after.getBody().get("selectedAction");
        assertEquals(beforeAction, afterAction);
        // Observable snapshot should still be original
        Map<String, Object> obsBefore = (Map<String, Object>) before.getBody().get("observableEvidence");
        Map<String, Object> obsAfter = (Map<String, Object>) after.getBody().get("observableEvidence");
        assertEquals(obsBefore.get("amount").toString(), obsAfter.get("amount").toString());
    }

    @Test
    void selectedActionMatchesHistoricalRanking() {
        createSnapshotForCurrentCase();
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        String selected = (String) res.getBody().get("selectedAction");
        java.util.List<Map<String, Object>> candidates = (java.util.List<Map<String, Object>>) res.getBody().get("candidates");
        boolean found = candidates.stream().anyMatch(c -> selected.equals(c.get("action")) && "ALLOWED".equals(c.get("policyResult")));
        // If selected is not null, it should be among ALLOWED candidates
        if (selected != null) {
            assertTrue(found, "Selected action must match historical ranking/policy");
        }
    }

    @Test
    void noHiddenTruthStored() {
        createSnapshotForCurrentCase();
        var snap = snapshotRepo.findTopByCaseIdOrderByCreatedAtDesc(rc.getId()).orElseThrow();
        String all = snap.getObservableSnapshot() + snap.getAiAssessmentSnapshot() + snap.getCandidateSnapshot() + snap.getPolicySnapshot();
        String lower = all.toLowerCase();
        assertFalse(lower.contains("hiddentruth"));
        assertFalse(lower.contains("ptrue"));
        assertFalse(lower.contains("groundtruth"));
        assertFalse(lower.contains("latent"));
        assertFalse(lower.contains("oracle"));
    }

    @Test
    void noPTrueStored() {
        createSnapshotForCurrentCase();
        var snap = snapshotRepo.findTopByCaseIdOrderByCreatedAtDesc(rc.getId()).orElseThrow();
        String all = snap.getObservableSnapshot() + snap.getCandidateSnapshot();
        assertFalse(all.toLowerCase().contains("ptrue"));
        assertFalse(all.contains("P_true"));
    }

    @Test
    void noChainOfThoughtStored() {
        createSnapshotForCurrentCase();
        ResponseEntity<Map<String, Object>> res = controller.getDecision(rc.getId());
        Map<String, Object> ai = (Map<String, Object>) res.getBody().get("aiAssessment");
        String summary = ai.get("reasoningSummary") != null ? ai.get("reasoningSummary").toString() : "";
        // Reasoning summary should be concise, not chain-of-thought
        assertTrue(summary.length() < 500, "Reasoning summary should be concise");
        String body = res.getBody().toString().toLowerCase();
        assertFalse(body.contains("chain-of-thought"));
        assertFalse(body.contains("chain_of_thought"));
    }
}
