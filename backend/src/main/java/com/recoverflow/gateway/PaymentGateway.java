package com.recoverflow.gateway;

import com.recoverflow.recovery.RecoveryActionType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Abstraction over payment execution. Only ExecutionService may call this.
 * No direct AI/EV -> gateway path.
 */
public interface PaymentGateway {

    /**
     * Execute a recovery action. Must not hold DB transaction over this call.
     * @param caseId recovery case id
     * @param action type to execute
     * @param amount recoverable amount
     * @param currency INR etc.
     * @param idempotencyKey unique key for this attempt (caseId:action:attempt)
     * @param correlationId for tracing
     * @return gateway result (SUCCESS/FAILURE/TIMEOUT etc.)
     */
    GatewayResult execute(UUID caseId, RecoveryActionType action, BigDecimal amount, String currency,
                          String idempotencyKey, UUID correlationId) throws GatewayTimeoutException;

    /**
     * Query status for reconciliation of UNKNOWN actions.
     */
    GatewayResult queryStatus(String idempotencyKey);

    /**
     * Create payment link (non-financial). Does not move money.
     */
    GatewayResult createPaymentLink(UUID caseId, BigDecimal amount, String currency,
                                    String idempotencyKey, UUID correlationId) throws GatewayTimeoutException;

    String getMode(); // SIMULATION / TEST / PRODUCTION
}
