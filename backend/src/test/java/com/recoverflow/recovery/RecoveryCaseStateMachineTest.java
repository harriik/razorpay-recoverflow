package com.recoverflow.recovery;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RecoveryCaseStateMachineTest {

    private RecoveryCaseStateMachine sm;

    @BeforeEach
    void setUp() {
        sm = new RecoveryCaseStateMachine();
    }

    @Test
    void terminalStatesHaveNoOutgoing() {
        for (RecoveryCaseStatus terminal : sm.terminalStates()) {
            assertTrue(sm.allowedFrom(terminal).isEmpty(),
                    "Terminal " + terminal + " should have no outgoing transitions");
            for (RecoveryCaseStatus any : RecoveryCaseStatus.values()) {
                assertFalse(sm.isAllowed(terminal, any),
                        "Terminal " + terminal + " must not allow transition to " + any);
            }
        }
        assertEquals(4, sm.terminalStates().size(), "Expected 4 terminal states: RECOVERED, FAILED_TERMINAL, ESCALATED, STOPPED");
        assertTrue(sm.isTerminal(RecoveryCaseStatus.RECOVERED));
        assertTrue(sm.isTerminal(RecoveryCaseStatus.FAILED_TERMINAL));
        assertTrue(sm.isTerminal(RecoveryCaseStatus.ESCALATED));
        assertTrue(sm.isTerminal(RecoveryCaseStatus.STOPPED));
        assertFalse(sm.isTerminal(RecoveryCaseStatus.ACTION_FAILED));
        assertFalse(sm.isTerminal(RecoveryCaseStatus.UNKNOWN));
    }

    @ParameterizedTest
    @CsvSource({
            "DETECTED,CLASSIFYING",
            "CLASSIFYING,ELIGIBILITY_CHECK",
            "ELIGIBILITY_CHECK,AI_ANALYSIS",
            "ELIGIBILITY_CHECK,STOPPED",
            "ELIGIBILITY_CHECK,ESCALATED",
            "AI_ANALYSIS,ACTION_EVALUATION",
            "AI_ANALYSIS,ESCALATED",
            "ACTION_EVALUATION,POLICY_EVALUATION",
            "POLICY_EVALUATION,ACTION_APPROVED",
            "POLICY_EVALUATION,ESCALATED",
            "POLICY_EVALUATION,STOPPED",
            "POLICY_EVALUATION,FAILED_TERMINAL",
            "ACTION_APPROVED,EXECUTING",
            "EXECUTING,RECOVERED",
            "EXECUTING,ACTION_FAILED",
            "EXECUTING,FAILED_TERMINAL",
            "EXECUTING,RETRY_PENDING",
            "EXECUTING,UNKNOWN",
            "RETRY_PENDING,EXECUTING",
            "RETRY_PENDING,STOPPED",
            "RETRY_PENDING,ESCALATED",
            "RETRY_PENDING,FAILED_TERMINAL",
            "ACTION_FAILED,ACTION_EVALUATION",
            "ACTION_FAILED,ESCALATED",
            "ACTION_FAILED,STOPPED",
            "ACTION_FAILED,FAILED_TERMINAL",
            "UNKNOWN,RECOVERED",
            "UNKNOWN,ACTION_FAILED",
            "UNKNOWN,FAILED_TERMINAL",
            "UNKNOWN,ESCALATED",
            "UNKNOWN,EXECUTING"
    })
    void validTransitionsAreAllowed(String from, String to) {
        RecoveryCaseStatus f = RecoveryCaseStatus.valueOf(from);
        RecoveryCaseStatus t = RecoveryCaseStatus.valueOf(to);
        assertTrue(sm.isAllowed(f, t), "Expected allowed: " + f + " -> " + t);
        assertDoesNotThrow(() -> sm.validate(f, t));
    }

    @ParameterizedTest
    @CsvSource({
            "DETECTED,RECOVERED",
            "DETECTED,EXECUTING",
            "CLASSIFYING,RECOVERED",
            "AI_ANALYSIS,RECOVERED",
            "ACTION_EVALUATION,EXECUTING",
            "POLICY_EVALUATION,EXECUTING",
            "EXECUTING,ACTION_EVALUATION",
            "RECOVERED,ACTION_FAILED",
            "RECOVERED,STOPPED",
            "FAILED_TERMINAL,RECOVERED",
            "ESCALATED,EXECUTING",
            "STOPPED,DETECTED",
            "UNKNOWN,ACTION_EVALUATION",
            "UNKNOWN,STOPPED",
            "ACTION_FAILED,RECOVERED",
            "RETRY_PENDING,ACTION_FAILED",
            "RETRY_PENDING,UNKNOWN"
    })
    void invalidTransitionsAreRejected(String from, String to) {
        RecoveryCaseStatus f = RecoveryCaseStatus.valueOf(from);
        RecoveryCaseStatus t = RecoveryCaseStatus.valueOf(to);
        assertFalse(sm.isAllowed(f, t), "Expected rejected: " + f + " -> " + t);
        assertThrows(InvalidTransitionException.class, () -> sm.validate(f, t));
    }

    @Test
    void actionFailedCanLoopToActionEvaluation() {
        // Core fix from Phase 0 revision: ACTION_FAILED is non-terminal and can re-enter evaluation
        assertTrue(sm.isAllowed(RecoveryCaseStatus.ACTION_FAILED, RecoveryCaseStatus.ACTION_EVALUATION));
        assertFalse(sm.isTerminal(RecoveryCaseStatus.ACTION_FAILED));
    }

    @Test
    void unknownHandling() {
        // UNKNOWN only reachable from EXECUTING, and can go to 5 states but not to STOPPED or ACTION_EVALUATION directly
        assertTrue(sm.isAllowed(RecoveryCaseStatus.EXECUTING, RecoveryCaseStatus.UNKNOWN));
        assertTrue(sm.isAllowed(RecoveryCaseStatus.UNKNOWN, RecoveryCaseStatus.RECOVERED));
        assertTrue(sm.isAllowed(RecoveryCaseStatus.UNKNOWN, RecoveryCaseStatus.ESCALATED));
        assertFalse(sm.isAllowed(RecoveryCaseStatus.UNKNOWN, RecoveryCaseStatus.STOPPED));
        assertFalse(sm.isAllowed(RecoveryCaseStatus.UNKNOWN, RecoveryCaseStatus.ACTION_EVALUATION));
        assertFalse(sm.isAllowed(RecoveryCaseStatus.UNKNOWN, RecoveryCaseStatus.UNKNOWN));
    }

    @Test
    void allAllowedTransitionsAreDocumented() {
        // Ensure no extra undocumented transitions exist: size check guards against accidental additions
        int total = sm.allAllowed().values().stream().mapToInt(Set::size).sum();
        assertEquals(31, total, "Total valid transitions should be 31 per Phase 0 revision");
    }

    @Test
    void policyEvaluationFailedTerminalExists() {
        // FAILED_TERMINAL must be reachable but terminal
        assertTrue(sm.isAllowed(RecoveryCaseStatus.POLICY_EVALUATION, RecoveryCaseStatus.FAILED_TERMINAL));
        assertTrue(sm.isTerminal(RecoveryCaseStatus.FAILED_TERMINAL));
        assertTrue(sm.isAllowed(RecoveryCaseStatus.EXECUTING, RecoveryCaseStatus.FAILED_TERMINAL));
        assertTrue(sm.isAllowed(RecoveryCaseStatus.ACTION_FAILED, RecoveryCaseStatus.FAILED_TERMINAL));
    }
}
