package com.recoverflow.failurelab;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FailureLabControllerTest {

    @Autowired FailureLabController controller;
    @Autowired FailureScenarioRegistry registry;

    @Test
    void listScenarios() {
        var res = controller.listScenarios();
        assertEquals(200, res.getStatusCode().value());
        assertEquals(10, res.getBody().size());
        assertTrue(res.getBody().stream().anyMatch(m -> "GATEWAY_TIMEOUT".equals(m.get("scenarioId"))));
    }

    @Test
    void getScenario() {
        var res = controller.getScenario("GATEWAY_TIMEOUT");
        assertEquals(200, res.getStatusCode().value());
        assertEquals("GATEWAY_TIMEOUT", res.getBody().get("scenarioId"));
        assertNotNull(res.getBody().get("expectedStateTransitions"));
    }

    @Test
    void runScenario() {
        var res = controller.runScenario("GATEWAY_TIMEOUT");
        assertEquals(200, res.getStatusCode().value());
        assertEquals("GATEWAY_TIMEOUT", res.getBody().scenarioId());
        assertNotNull(res.getBody().caseId());
        assertEquals("ACTION_APPROVED", res.getBody().beforeState());
        assertNotNull(res.getBody().afterState());
        assertTrue(res.getBody().gatewayCallCount() >= 0);
        assertNotNull(res.getBody().auditEvents());
    }

    @Test
    void runScenarioNotFound() {
        var res = controller.runScenario("UNKNOWN");
        assertEquals(404, res.getStatusCode().value());
    }

    @Test
    void resetScenario() {
        var res = controller.resetScenario("GATEWAY_TIMEOUT");
        assertEquals(200, res.getStatusCode().value());
        assertEquals("RESET", res.getBody().get("status"));
    }

    @Test
    void runAllScenariosViaApi() {
        String[] ids = {"GATEWAY_TIMEOUT","GATEWAY_FAILURE_RETRYABLE","GATEWAY_FAILURE_TERMINAL","DUPLICATE_EXECUTION","CONCURRENT_EXECUTION","UNKNOWN_RECONCILIATION_SUCCESS","UNKNOWN_RECONCILIATION_FAILURE","STALE_POLICY_APPROVAL","CUSTOMER_OPT_OUT_BEFORE_EXECUTION","AI_RECOMMENDS_BLOCKED_ACTION"};
        for (String id : ids) {
            var res = controller.runScenario(id);
            assertEquals(200, res.getStatusCode().value(), "Failed for " + id);
            assertEquals(id, res.getBody().scenarioId());
        }
    }
}
