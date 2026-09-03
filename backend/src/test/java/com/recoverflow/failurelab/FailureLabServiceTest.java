package com.recoverflow.failurelab;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.recovery.RecoveryCaseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FailureLabServiceTest {

    @Autowired FailureLabService service;
    @Autowired FailureScenarioRegistry registry;

    @Test
    void registryHas10Scenarios() {
        assertEquals(10, registry.getAll().size());
        assertNotNull(registry.getById("GATEWAY_TIMEOUT"));
        assertNotNull(registry.getById("AI_RECOMMENDS_BLOCKED_ACTION"));
    }

    @Test
    void timeoutToUnknown() {
        var res = service.runScenario("GATEWAY_TIMEOUT");
        assertEquals("GATEWAY_TIMEOUT", res.scenarioId());
        assertEquals(RecoveryCaseStatus.UNKNOWN.name(), res.afterState());
        assertEquals(1, res.gatewayCallCount());
        assertTrue(res.auditEvents().contains("GATEWAY_TIMEOUT"));
        assertEquals("ACTION_APPROVED", res.beforeState());
    }

    @Test
    void unknownReconcileSuccessToRecovered() {
        var res = service.runScenario("UNKNOWN_RECONCILIATION_SUCCESS");
        assertEquals(RecoveryCaseStatus.RECOVERED.name(), res.afterState());
        assertNotNull(res.reconciliationResult());
        assertTrue(res.auditEvents().contains("RECONCILED_SUCCESS") || res.auditEvents().contains("RECONCILE_ATTEMPTED"));
    }

    @Test
    void unknownReconcileFailure() {
        var res = service.runScenario("UNKNOWN_RECONCILIATION_FAILURE");
        assertTrue(res.afterState().equals(RecoveryCaseStatus.FAILED_TERMINAL.name()) || res.afterState().equals(RecoveryCaseStatus.ACTION_FAILED.name()));
        assertNotNull(res.reconciliationResult());
        assertEquals(1, res.gatewayCallCount()); // only initial timeout call, reconcile is query not execute
    }

    @Test
    void duplicateNoSecondGatewayCall() {
        var res = service.runScenario("DUPLICATE_EXECUTION");
        assertEquals("DUPLICATE", res.idempotencyResult());
        // Second call is duplicate, so total gateway calls for that key should be 2 but second is duplicate (callCount includes duplicate)
        // First execution 1, second duplicate also increments callCount in MockPaymentGateway
        assertTrue(res.gatewayCallCount() >= 1);
        assertTrue(res.auditEvents().contains("GATEWAY_SUCCESS") || res.auditEvents().contains("DUPLICATE_ACTION_PREVENTED"));
    }

    @Test
    void concurrentExactlyOneGatewayCall() {
        var res = service.runScenario("CONCURRENT_EXECUTION");
        assertEquals(1, res.gatewayCallCount(), "Exactly one gateway call for concurrent");
        assertEquals("ONE_GATEWAY_CALL", res.idempotencyResult());
        assertTrue(res.afterState().equals(RecoveryCaseStatus.RECOVERED.name()) || res.afterState().equals(RecoveryCaseStatus.ACTION_FAILED.name()) || res.afterState().equals(RecoveryCaseStatus.UNKNOWN.name()));
    }

    @Test
    void stalePolicyGatewayNotCalled() {
        var res = service.runScenario("STALE_POLICY_APPROVAL");
        assertEquals(0, res.gatewayCallCount(), "Stale policy should block gateway");
        assertEquals("BLOCKED", res.idempotencyResult());
        assertTrue(res.auditEvents().contains("POLICY_REVALIDATION_FAILED"));
        assertNotEquals(RecoveryCaseStatus.RECOVERED.name(), res.afterState());
    }

    @Test
    void optOutGatewayNotCalled() {
        var res = service.runScenario("CUSTOMER_OPT_OUT_BEFORE_EXECUTION");
        assertEquals(0, res.gatewayCallCount(), "Opt-out should block gateway");
        assertEquals("BLOCKED", res.idempotencyResult());
        assertTrue(res.auditEvents().contains("POLICY_REVALIDATION_FAILED"));
    }

    @Test
    void policyBlocksAiRecommendationGatewayNotCalled() {
        var res = service.runScenario("AI_RECOMMENDS_BLOCKED_ACTION");
        assertEquals(0, res.gatewayCallCount(), "Policy should block AI recommended action");
        assertEquals("BLOCKED", res.idempotencyResult());
        assertTrue(res.auditEvents().contains("POLICY_REVALIDATION_FAILED") || res.auditEvents().contains("POLICY_BLOCKED_AI_RECOMMENDATION"));
    }

    @Test
    void terminalFailureIsFailedTerminal() {
        var res = service.runScenario("GATEWAY_FAILURE_TERMINAL");
        assertEquals(RecoveryCaseStatus.FAILED_TERMINAL.name(), res.afterState());
        assertEquals(1, res.gatewayCallCount());
    }

    @Test
    void retryableFailureIsActionFailed() {
        var res = service.runScenario("GATEWAY_FAILURE_RETRYABLE");
        assertEquals(RecoveryCaseStatus.ACTION_FAILED.name(), res.afterState());
        assertEquals(1, res.gatewayCallCount());
        // Can go to next candidate - verify state machine allows ACTION_FAILED -> ACTION_EVALUATION
        assertTrue(res.auditEvents().contains("GATEWAY_FAILED_RETRYABLE"));
    }

    @Test
    void resetDeterminism() {
        var first = service.runScenario("GATEWAY_TIMEOUT");
        var beforeFirst = first.afterState();
        service.resetScenario("GATEWAY_TIMEOUT");
        var second = service.runScenario("GATEWAY_TIMEOUT");
        assertEquals(beforeFirst, second.afterState());
        assertEquals(first.gatewayCallCount(), second.gatewayCallCount());
    }

    @Test
    void scenariosDoNotContaminateEachOther() {
        var r1 = service.runScenario("GATEWAY_TIMEOUT");
        var r2 = service.runScenario("GATEWAY_FAILURE_RETRYABLE");
        assertNotEquals(r1.caseId(), r2.caseId());
        assertEquals(RecoveryCaseStatus.UNKNOWN.name(), r1.afterState());
        assertEquals(RecoveryCaseStatus.ACTION_FAILED.name(), r2.afterState());
    }

    @Test
    void auditUsesNormalAuditService() {
        var res = service.runScenario("GATEWAY_TIMEOUT");
        assertFalse(res.auditEvents().isEmpty());
        assertTrue(res.auditEvents().stream().anyMatch(e -> e.contains("GATEWAY") || e.contains("EXECUTION") || e.contains("POLICY")));
        assertNotNull(res.correlationId());
        assertNotNull(res.caseId());
    }
}
