package com.recoverflow.failurelab;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Registry of 10 deterministic failure scenarios. Immutable.
 */
@Component
public class FailureScenarioRegistry {

    public record ScenarioDefinition(
            String scenarioId,
            String name,
            String description,
            String setup,
            String trigger,
            String expectedOutcome,
            List<String> expectedStateTransitions,
            List<String> expectedAuditEvents
    ) {}

    private final List<ScenarioDefinition> scenarios = List.of(
            new ScenarioDefinition(
                    "GATEWAY_TIMEOUT",
                    "Gateway Timeout",
                    "Gateway call times out, case becomes UNKNOWN, no blind retry",
                    "Create ACTION_APPROVED case with RETRY_NOW, configure MockPaymentGateway TIMEOUT for idempotencyKey",
                    "ExecutionService.execute -> GatewayTimeoutException -> UNKNOWN",
                    "Case UNKNOWN, Action UNKNOWN, gatewayCallCount 1, requires reconciliation",
                    List.of("ACTION_APPROVED -> EXECUTING -> UNKNOWN"),
                    List.of("EXECUTION_RESERVED", "GATEWAY_TIMEOUT")
            ),
            new ScenarioDefinition(
                    "GATEWAY_FAILURE_RETRYABLE",
                    "Gateway Failure Retryable",
                    "Gateway returns retryable failure, case becomes ACTION_FAILED",
                    "Create ACTION_APPROVED RETRY_NOW, configure FAILURE_RETRYABLE",
                    "ExecutionService.execute -> FAILURE retryable -> ACTION_FAILED",
                    "Case ACTION_FAILED, Action FAILED retryable true, can try next candidate",
                    List.of("ACTION_APPROVED -> EXECUTING -> ACTION_FAILED"),
                    List.of("EXECUTION_RESERVED", "GATEWAY_FAILED_RETRYABLE")
            ),
            new ScenarioDefinition(
                    "GATEWAY_FAILURE_TERMINAL",
                    "Gateway Failure Terminal",
                    "Gateway returns terminal failure, case becomes FAILED_TERMINAL",
                    "Create ACTION_APPROVED RETRY_NOW, configure FAILURE_TERMINAL",
                    "ExecutionService.execute -> FAILURE terminal -> FAILED_TERMINAL",
                    "Case FAILED_TERMINAL, Action FAILED terminal false, no further retry",
                    List.of("ACTION_APPROVED -> EXECUTING -> FAILED_TERMINAL"),
                    List.of("EXECUTION_RESERVED", "GATEWAY_FAILED_TERMINAL")
            ),
            new ScenarioDefinition(
                    "DUPLICATE_EXECUTION",
                    "Duplicate Execution",
                    "Same idempotency key executed twice -> second returns duplicate, no second gateway call",
                    "Create ACTION_APPROVED RETRY_NOW, execute once SUCCESS, second execute with same key (attemptCount reset to 0)",
                    "ExecutionService.execute twice with same idempotencyKey -> DUPLICATE_ACTION_PREVENTED",
                    "First SUCCESS, second DUPLICATE, gatewayCallCount 1 for that key",
                    List.of("ACTION_APPROVED -> EXECUTING -> RECOVERED (first)", "DUPLICATE prevented (second)"),
                    List.of("EXECUTION_RESERVED", "GATEWAY_SUCCESS", "DUPLICATE_ACTION_PREVENTED")
            ),
            new ScenarioDefinition(
                    "CONCURRENT_EXECUTION",
                    "Concurrent Execution",
                    "Two concurrent requests for same case/action -> exactly one gateway call via optimistic locking",
                    "Create ACTION_APPROVED RETRY_NOW, two threads execute same case/action simultaneously",
                    "ExecutionService.execute concurrent -> one SUCCESS, one reservation failed (optimistic locking/duplicate)",
                    "Exactly one gateway call, one case RECOVERED or ACTION_FAILED, other blocked",
                    List.of("ACTION_APPROVED -> EXECUTING -> RECOVERED (winner)", "concurrent blocked"),
                    List.of("EXECUTION_RESERVED", "GATEWAY_SUCCESS")
            ),
            new ScenarioDefinition(
                    "UNKNOWN_RECONCILIATION_SUCCESS",
                    "Unknown Reconciliation Success",
                    "Timeout -> UNKNOWN, then reconciliation query returns SUCCESS -> RECOVERED",
                    "Execute TIMEOUT to get UNKNOWN, then MockPaymentGateway scenario SUCCESS for same key, ReconciliationService.reconcile",
                    "ReconciliationService.reconcile -> RECOVERED",
                    "Case UNKNOWN -> RECOVERED, Action UNKNOWN -> SUCCESS, no blind retry",
                    List.of("ACTION_APPROVED -> EXECUTING -> UNKNOWN -> RECOVERED"),
                    List.of("GATEWAY_TIMEOUT", "RECONCILE_ATTEMPTED", "RECONCILED_SUCCESS")
            ),
            new ScenarioDefinition(
                    "UNKNOWN_RECONCILIATION_FAILURE",
                    "Unknown Reconciliation Failure",
                    "Timeout -> UNKNOWN, then reconciliation returns FAILURE terminal -> FAILED_TERMINAL",
                    "Execute TIMEOUT to get UNKNOWN, then MockPaymentGateway FAILURE_TERMINAL, reconcile",
                    "ReconciliationService.reconcile -> FAILED_TERMINAL",
                    "Case UNKNOWN -> FAILED_TERMINAL, Action UNKNOWN -> FAILED",
                    List.of("ACTION_APPROVED -> EXECUTING -> UNKNOWN -> FAILED_TERMINAL"),
                    List.of("GATEWAY_TIMEOUT", "RECONCILE_ATTEMPTED", "RECONCILED_FAILED_TERMINAL")
            ),
            new ScenarioDefinition(
                    "STALE_POLICY_APPROVAL",
                    "Stale Policy Approval",
                    "AI recommends RETRY_NOW but attempt limit exceeded (stale approval) -> policy revalidation blocks, gateway never called",
                    "Create ACTION_APPROVED RETRY_NOW with attemptCount=3 (max 3), policy should block on revalidation",
                    "ExecutionService.execute -> POLICY_REVALIDATION_FAILED -> ESCALATED/STOPPED, gatewayCallCount 0",
                    "Case STALE policy blocked, gatewayCallCount 0",
                    List.of("ACTION_APPROVED -> (revalidation) -> ESCALATED/STOPPED (no EXECUTING)"),
                    List.of("POLICY_REVALIDATION_FAILED")
            ),
            new ScenarioDefinition(
                    "CUSTOMER_OPT_OUT_BEFORE_EXECUTION",
                    "Customer Opt-Out Before Execution",
                    "Customer opts out after approval -> final policy revalidation blocks, gateway never called",
                    "Create ACTION_APPROVED RETRY_NOW, set customer.optedOut=true before execution",
                    "ExecutionService.execute -> POLICY_REVALIDATION_FAILED -> case not EXECUTING",
                    "Gateway never called, case remains ACTION_APPROVED or goes to STOPPED/ESCALATED",
                    List.of("ACTION_APPROVED -> (opt-out) -> POLICY_REVALIDATION_FAILED"),
                    List.of("POLICY_REVALIDATION_FAILED")
            ),
            new ScenarioDefinition(
                    "AI_RECOMMENDS_BLOCKED_ACTION",
                    "AI Recommends Blocked Action",
                    "AI recommends RETRY_NOW but policy blocks due to amount over limit -> gateway never called",
                    "Create case with amount 20000 (>10000 limit), AI recommends RETRY_NOW, policy blocks, execution not called; simulate via direct execution with blocked action",
                    "ExecutionService.execute with blocked action -> POLICY_REVALIDATION_FAILED, gateway 0",
                    "Policy blocks AI recommendation, gatewayCallCount 0, case not RECOVERED",
                    List.of("ACTION_APPROVED (AI) -> POLICY_REVALIDATION_FAILED"),
                    List.of("POLICY_REVALIDATION_FAILED", "DUPLICATE_ACTION_PREVENTED if applicable")
            )
    );

    public List<ScenarioDefinition> getAll() {
        return scenarios;
    }

    public ScenarioDefinition getById(String scenarioId) {
        return scenarios.stream().filter(s -> s.scenarioId().equals(scenarioId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown scenario: " + scenarioId));
    }
}
