package com.recoverflow.failurelab;

import com.recoverflow.gateway.GatewayResult;
import com.recoverflow.policy.PolicyDecision;
import com.recoverflow.recovery.RecoveryActionStatus;
import com.recoverflow.recovery.RecoveryCaseStatus;
import java.util.List;
import java.util.UUID;

/**
 * Structured scenario result for API.
 * Clarified UI metrics: executionRequests vs gatewayInvocations vs duplicatesPrevented.
 * A duplicate must NOT be counted as a second gateway invocation.
 */
public record FailureLabResult(
        String scenarioId,
        String status, // SUCCESS, FAILED, etc.
        UUID caseId,
        String beforeState,
        String afterState,
        String selectedAction,
        PolicyDecision policyResult,
        GatewayResult gatewayResult,
        Object reconciliationResult, // ReconciliationResult or null
        String auditSummary,
        int gatewayCallCount, // legacy: actual gateway invocations (for backward compat)
        String idempotencyResult, // DUPLICATE, NEW, CONCURRENT, etc.
        List<String> auditEvents,
        String correlationId,
        int executionRequests,
        int gatewayInvocations,
        int duplicatesPrevented,
        String policyDecisionResult // exact ESCALATE/STOP/ALLOWED for AMOUNT_THRESHOLD
) {
    // Backward compat constructor for 14-field usage
    public FailureLabResult(String scenarioId, String status, UUID caseId, String beforeState, String afterState,
                            String selectedAction, PolicyDecision policyResult, GatewayResult gatewayResult,
                            Object reconciliationResult, String auditSummary, int gatewayCallCount,
                            String idempotencyResult, List<String> auditEvents, String correlationId) {
        this(scenarioId, status, caseId, beforeState, afterState, selectedAction, policyResult, gatewayResult,
                reconciliationResult, auditSummary, gatewayCallCount, idempotencyResult, auditEvents, correlationId,
                gatewayCallCount, gatewayCallCount, 0, policyResult != null ? policyResult.result().name() : null);
    }
}
