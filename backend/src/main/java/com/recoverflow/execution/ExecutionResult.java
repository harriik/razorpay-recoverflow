package com.recoverflow.execution;

import com.recoverflow.recovery.RecoveryActionType;
import com.recoverflow.recovery.RecoveryCaseStatus;
import java.util.UUID;

public record ExecutionResult(
        boolean success,
        String message,
        UUID caseId,
        RecoveryActionType action,
        String idempotencyKey,
        String gatewayRef,
        RecoveryCaseStatus caseStatus,
        RecoveryActionStatus actionStatus,
        UUID correlationId,
        boolean gatewayCalled
) {
    public enum RecoveryActionStatus {
        PENDING, SUCCESS, FAILED, UNKNOWN
    }

    public static ExecutionResult blocked(String msg, UUID caseId, RecoveryActionType action, UUID corr) {
        return new ExecutionResult(false, msg, caseId, action, null, null, null, null, corr, false);
    }

    public static ExecutionResult duplicate(String msg, UUID caseId, RecoveryActionType action, String key, String ref, UUID corr) {
        return new ExecutionResult(false, msg, caseId, action, key, ref, null, RecoveryActionStatus.SUCCESS, corr, false);
    }
}
