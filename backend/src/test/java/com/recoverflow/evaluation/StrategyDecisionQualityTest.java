package com.recoverflow.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.recovery.RecoveryActionType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StrategyDecisionQualityTest {

    @Test
    void normalSelectedOracleValues() {
        var q = new StrategyDecisionQuality(
                RecoveryActionType.RETRY_NOW,
                RecoveryActionType.SCHEDULE_RETRY,
                new BigDecimal("100.00"),
                new BigDecimal("150.00"),
                new BigDecimal("50.00"));
        assertEquals(RecoveryActionType.RETRY_NOW, q.selectedAction());
        assertEquals(RecoveryActionType.SCHEDULE_RETRY, q.oracleAction());
        assertEquals(new BigDecimal("100.0000"), q.selectedTrueValue());
        assertEquals(new BigDecimal("150.0000"), q.oracleTrueValue());
        assertEquals(new BigDecimal("50.0000"), q.decisionRegret());
    }

    @Test
    void zeroRegretWhenSelectedEqualsOracle() {
        var q = new StrategyDecisionQuality(
                RecoveryActionType.SEND_PAYMENT_LINK,
                RecoveryActionType.SEND_PAYMENT_LINK,
                new BigDecimal("200.00"),
                new BigDecimal("200.00"),
                BigDecimal.ZERO);
        assertEquals(BigDecimal.ZERO.setScale(4), q.decisionRegret());
    }

    @Test
    void zeroRegretEnforcedWhenActionsEqual() {
        assertThrows(IllegalArgumentException.class, () ->
                new StrategyDecisionQuality(
                        RecoveryActionType.RETRY_NOW,
                        RecoveryActionType.RETRY_NOW,
                        new BigDecimal("100.00"),
                        new BigDecimal("100.00"),
                        new BigDecimal("10.00"))
        );
    }

    @Test
    void positiveRegret() {
        var q = new StrategyDecisionQuality(
                RecoveryActionType.RETRY_NOW,
                RecoveryActionType.SEND_PAYMENT_LINK,
                new BigDecimal("50.00"),
                new BigDecimal("120.00"),
                new BigDecimal("70.00"));
        assertTrue(q.decisionRegret().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(new BigDecimal("70.0000"), q.decisionRegret());
    }

    @Test
    void nullableNoActionSemantics() {
        var q = StrategyDecisionQuality.noAction();
        assertNull(q.selectedAction());
        assertNull(q.oracleAction());
        assertNull(q.selectedTrueValue());
        assertNull(q.oracleTrueValue());
        assertEquals(BigDecimal.ZERO.setScale(4), q.decisionRegret());

        var q2 = new StrategyDecisionQuality(null, null, null, null, BigDecimal.ZERO);
        assertNull(q2.selectedAction());
        assertEquals(BigDecimal.ZERO.setScale(4), q2.decisionRegret());
    }

    @Test
    void bigDecimalPrecision() {
        var q = new StrategyDecisionQuality(
                RecoveryActionType.RETRY_NOW,
                RecoveryActionType.SCHEDULE_RETRY,
                new BigDecimal("100.1"),
                new BigDecimal("150.12345"),
                new BigDecimal("50.02345"));
        assertEquals(4, q.selectedTrueValue().scale());
        assertEquals(4, q.oracleTrueValue().scale());
        assertEquals(4, q.decisionRegret().scale());
        assertEquals(new BigDecimal("100.1000"), q.selectedTrueValue());
        assertEquals(new BigDecimal("150.1235"), q.oracleTrueValue()); // HALF_UP
        assertEquals(new BigDecimal("50.0235"), q.decisionRegret());
    }

    @Test
    void immutabilityRecordSemantics() {
        var q1 = new StrategyDecisionQuality(
                RecoveryActionType.RETRY_NOW,
                RecoveryActionType.SCHEDULE_RETRY,
                new BigDecimal("100.00"),
                new BigDecimal("150.00"),
                new BigDecimal("50.00"));
        var q2 = new StrategyDecisionQuality(
                RecoveryActionType.RETRY_NOW,
                RecoveryActionType.SCHEDULE_RETRY,
                new BigDecimal("100.00"),
                new BigDecimal("150.00"),
                new BigDecimal("50.00"));
        assertEquals(q1, q2);
        assertEquals(q1.hashCode(), q2.hashCode());
    }

    @Test
    void negativeRegretRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new StrategyDecisionQuality(
                        RecoveryActionType.RETRY_NOW,
                        RecoveryActionType.SCHEDULE_RETRY,
                        new BigDecimal("100.00"),
                        new BigDecimal("150.00"),
                        new BigDecimal("-10.00"))
        );
    }

    @Test
    void computeRegretHelper() {
        assertEquals(new BigDecimal("0.0000"), StrategyDecisionQuality.computeRegret(null, null));
        assertEquals(new BigDecimal("0.0000"), StrategyDecisionQuality.computeRegret(new BigDecimal("100.00"), new BigDecimal("100.00")));
        assertEquals(new BigDecimal("50.0000"), StrategyDecisionQuality.computeRegret(new BigDecimal("150.00"), new BigDecimal("100.00")));
        assertEquals(new BigDecimal("0.0000"), StrategyDecisionQuality.computeRegret(new BigDecimal("100.00"), new BigDecimal("150.00"))); // max(0, ...)
        assertEquals(new BigDecimal("0.0000"), StrategyDecisionQuality.computeRegret(null, new BigDecimal("100.00")));
    }
}
