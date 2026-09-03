package com.recoverflow.failurelab;

import com.recoverflow.gateway.GatewayResult;
import com.recoverflow.policy.PolicyDecision;
import com.recoverflow.recovery.RecoveryActionStatus;
import com.recoverflow.recovery.RecoveryCaseStatus;
import java.util.List;
import java.util.UUID;

/**
 * Structured scenario result for API.
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
        int gatewayCallCount,
        String idempotencyResult, // DUPLICATE, NEW, CONCURRENT, etc.
        List<String> auditEvents,
        String correlationId
) {}
