package com.recoverflow.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.recovery.RecoveryActionType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AiHelpHurtClassifierTest {

    @Test
    void helped() {
        var c = AiHelpHurtClassifier.classify(
                RecoveryActionType.RETRY_NOW, RecoveryActionType.SCHEDULE_RETRY,
                new BigDecimal("100.0000"), new BigDecimal("150.0000"));
        assertTrue(c.actionChanged());
        assertTrue(c.helped());
        assertFalse(c.hurt());
        assertFalse(c.neutral());
    }

    @Test
    void hurt() {
        var c = AiHelpHurtClassifier.classify(
                RecoveryActionType.SCHEDULE_RETRY, RecoveryActionType.RETRY_NOW,
                new BigDecimal("150.0000"), new BigDecimal("100.0000"));
        assertTrue(c.actionChanged());
        assertFalse(c.helped());
        assertTrue(c.hurt());
        assertFalse(c.neutral());
    }

    @Test
    void neutral() {
        var c = AiHelpHurtClassifier.classify(
                RecoveryActionType.RETRY_NOW, RecoveryActionType.SCHEDULE_RETRY,
                new BigDecimal("100.0000"), new BigDecimal("100.0000"));
        assertTrue(c.actionChanged());
        assertFalse(c.helped());
        assertFalse(c.hurt());
        assertTrue(c.neutral());
    }

    @Test
    void sameActionNeutral() {
        var c = AiHelpHurtClassifier.classify(
                RecoveryActionType.RETRY_NOW, RecoveryActionType.RETRY_NOW,
                new BigDecimal("100.0000"), new BigDecimal("100.0000"));
        assertFalse(c.actionChanged());
        assertFalse(c.helped());
        assertFalse(c.hurt());
        assertTrue(c.neutral());
    }

    @Test
    void policyNullAiActionHelpedWhenAiValuePositive() {
        var c = AiHelpHurtClassifier.classify(
                null, RecoveryActionType.RETRY_NOW,
                null, new BigDecimal("50.0000"));
        assertTrue(c.actionChanged());
        assertTrue(c.helped());
    }

    @Test
    void policyActionAiNullHurtWhenPolicyValuePositive() {
        var c = AiHelpHurtClassifier.classify(
                RecoveryActionType.RETRY_NOW, null,
                new BigDecimal("50.0000"), null);
        assertTrue(c.actionChanged());
        assertTrue(c.hurt());
    }

    @Test
    void bothNullNeutral() {
        var c = AiHelpHurtClassifier.classify(null, null, null, null);
        assertFalse(c.actionChanged());
        assertTrue(c.neutral());
        assertFalse(c.helped());
        assertFalse(c.hurt());
    }

    @Test
    void equalValuesDifferentActionsNeutral() {
        var c = AiHelpHurtClassifier.classify(
                RecoveryActionType.RETRY_NOW, RecoveryActionType.SCHEDULE_RETRY,
                new BigDecimal("100.0000"), new BigDecimal("100.0000"));
        assertTrue(c.actionChanged());
        assertTrue(c.neutral());
    }

    @Test
    void bernoulliOutcomeIrrelevant() {
        // Same true values, different hypothetical Bernoulli outcomes should not affect classification
        // This helper does not take outcome at all, so we just verify it ignores outcome
        var c1 = AiHelpHurtClassifier.classify(
                RecoveryActionType.RETRY_NOW, RecoveryActionType.SCHEDULE_RETRY,
                new BigDecimal("100.0000"), new BigDecimal("150.0000"));
        var c2 = AiHelpHurtClassifier.classify(
                RecoveryActionType.RETRY_NOW, RecoveryActionType.SCHEDULE_RETRY,
                new BigDecimal("100.0000"), new BigDecimal("150.0000"));
        assertEquals(c1.helped(), c2.helped());
        assertEquals(c1.hurt(), c2.hurt());
    }

    @Test
    void negativeZeroValuesHandledDeterministically() {
        var c = AiHelpHurtClassifier.classify(
                RecoveryActionType.RETRY_NOW, RecoveryActionType.SCHEDULE_RETRY,
                new BigDecimal("0.0000"), new BigDecimal("0.0000"));
        assertTrue(c.neutral());
        var c2 = AiHelpHurtClassifier.classify(
                RecoveryActionType.RETRY_NOW, RecoveryActionType.SCHEDULE_RETRY,
                new BigDecimal("-10.0000"), new BigDecimal("-5.0000"));
        assertTrue(c2.helped(), "-5 > -10 so helped");
    }
}
