package com.recoverflow.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.policy.PolicyConfig;
import com.recoverflow.policy.PolicyContext;
import com.recoverflow.policy.PolicyEngine;
import com.recoverflow.recovery.RecoveryActionType;
import com.recoverflow.synthetic.CustomerBehaviorProfile;
import com.recoverflow.synthetic.HiddenTruth;
import com.recoverflow.synthetic.LatentRecoveryPropensity;
import com.recoverflow.synthetic.ObservableCase;
import com.recoverflow.synthetic.SyntheticCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrueOraclePolicyConsistencyTest {

    private final PolicyConfig configA = new PolicyConfig().withAutoLimit(new BigDecimal("10000.0000"));
    private final PolicyConfig configB = new PolicyConfig().withAutoLimit(new BigDecimal("20000.0000"));
    private final PolicyEngine engineA = new PolicyEngine(configA);
    private final PolicyEngine engineB = new PolicyEngine(configB);
    private final TrueDecisionValueCalculator calc = new TrueDecisionValueCalculator();
    private final TrueOracleEvaluator oracleEvalA = new TrueOracleEvaluator(engineA, calc, configA);
    private final TrueOracleEvaluator oracleEvalB = new TrueOracleEvaluator(engineB, calc, configB);

    private SyntheticCase caseFor(BigDecimal amount, String gateway) {
        var obs = new ObservableCase(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                amount, "INR", com.recoverflow.payment.PaymentMethod.CARD, gateway, Instant.now(), 0, 1, 5, 1, false);
        Map<RecoveryActionType, Double> pTrue = Map.of(
                RecoveryActionType.RETRY_NOW, 0.8,
                RecoveryActionType.SCHEDULE_RETRY, 0.8,
                RecoveryActionType.SEND_PAYMENT_LINK, 0.8,
                RecoveryActionType.SEND_REMINDER, 0.8);
        var hidden = new HiddenTruth(com.recoverflow.ai.FailureCategory.TEMPORARY_BANK_FAILURE, CustomerBehaviorProfile.AVERAGE, LatentRecoveryPropensity.MEDIUM, gateway, pTrue);
        return new SyntheticCase(UUID.randomUUID(), hidden, obs, pTrue, Map.of(RecoveryActionType.RETRY_NOW, true, RecoveryActionType.SCHEDULE_RETRY, true, RecoveryActionType.SEND_PAYMENT_LINK, true, RecoveryActionType.SEND_REMINDER, false));
    }

    @Test
    void merchantAThresholdBlocks12000() {
        SyntheticCase sc = caseFor(new BigDecimal("12000.0000"), "BANK_TIMEOUT");
        PolicyContext baseA = new PolicyContext(sc.observable().amount(), sc.observable().gatewayCode(), 0, 1, false, false, RecoveryActionType.RETRY_NOW, configA.getAutoActionLimit(), 3, 48);
        var decisionA = engineA.evaluate(baseA);
        assertEquals(com.recoverflow.policy.PolicyDecisionType.ESCALATE, decisionA.result(), "12000 > 10000 should escalate for Merchant A");
        var oracleResultA = oracleEvalA.evaluate(sc, baseA);
        assertFalse(oracleResultA.permissibleActions().contains(RecoveryActionType.RETRY_NOW), "Oracle must not choose blocked RETRY for Merchant A");
    }

    @Test
    void merchantBThresholdAllows12000() {
        SyntheticCase sc = caseFor(new BigDecimal("12000.0000"), "BANK_TIMEOUT");
        PolicyContext baseB = new PolicyContext(sc.observable().amount(), sc.observable().gatewayCode(), 0, 1, false, false, RecoveryActionType.RETRY_NOW, configB.getAutoActionLimit(), 3, 48);
        var decisionB = engineB.evaluate(baseB);
        assertEquals(com.recoverflow.policy.PolicyDecisionType.ALLOWED, decisionB.result(), "12000 < 20000 should be allowed for Merchant B");
        var oracleResultB = oracleEvalB.evaluate(sc, baseB);
        assertTrue(oracleResultB.permissibleActions().contains(RecoveryActionType.RETRY_NOW), "Oracle should allow RETRY for Merchant B");
    }

    @Test
    void oracleUsesCasesPolicySnapshot() {
        SyntheticCase sc = caseFor(new BigDecimal("12000.0000"), "BANK_TIMEOUT");
        PolicyContext baseA = new PolicyContext(sc.observable().amount(), sc.observable().gatewayCode(), 0, 1, false, false, RecoveryActionType.RETRY_NOW, new BigDecimal("10000.0000"), 3, 48);
        PolicyContext baseB = new PolicyContext(sc.observable().amount(), sc.observable().gatewayCode(), 0, 1, false, false, RecoveryActionType.RETRY_NOW, new BigDecimal("20000.0000"), 3, 48);
        var resA = oracleEvalA.evaluate(sc, baseA);
        var resB = oracleEvalB.evaluate(sc, baseB);
        // Merchant A: 12000 > 10000 -> RETRY_NOW must NOT be permissible for Oracle
        assertFalse(resA.permissibleActions().contains(RecoveryActionType.RETRY_NOW), "Oracle for Merchant A must not contain blocked RETRY_NOW");
        // Merchant B: 12000 < 20000 -> RETRY_NOW must be permissible for Oracle
        assertTrue(resB.permissibleActions().contains(RecoveryActionType.RETRY_NOW), "Oracle for Merchant B must contain allowed RETRY_NOW");
        // Oracle result must reflect policy difference
        assertNotEquals(resA.oracleAction(), RecoveryActionType.RETRY_NOW, "Oracle A must not select blocked RETRY_NOW");
        // For B, with uniform high P_true, oracle may select RETRY_NOW as highest true value among permissible
        // At least verify that B's oracle permissible set differs from A's
        assertTrue(!resA.permissibleActions().equals(resB.permissibleActions()) || resA.permissibleActions().size() != resB.permissibleActions().size());
    }

    @Test
    void practicalAndOracleUseSameConfig() {
        SyntheticCase sc = caseFor(new BigDecimal("5000.0000"), "BANK_TIMEOUT");
        PolicyContext base = new PolicyContext(sc.observable().amount(), sc.observable().gatewayCode(), 0, 1, false, false, RecoveryActionType.RETRY_NOW, configA.getAutoActionLimit(), 3, 48);
        var practicalDecision = engineA.evaluate(base);
        var oracleResult = oracleEvalA.evaluate(sc, base);
        // Both use same PolicyEngine, so a blocked action cannot be practical allowed and also oracle permissible
        if (practicalDecision.result() != com.recoverflow.policy.PolicyDecisionType.ALLOWED) {
            assertFalse(oracleResult.permissibleActions().contains(RecoveryActionType.RETRY_NOW));
        }
    }

    @Test
    void noDuplicatedPolicyRules() throws Exception {
        // Ensure TrueOracleEvaluator does not duplicate policy logic: it should delegate to PolicyEngine.evaluate
        // Use robust path resolution: when Maven is executed with -f backend/pom.xml, working dir is backend, so src/main/... is correct
        java.nio.file.Path[] candidates = new java.nio.file.Path[]{
                java.nio.file.Path.of("src/main/java/com/recoverflow/evaluation/TrueOracleEvaluator.java"),
                java.nio.file.Path.of("backend/src/main/java/com/recoverflow/evaluation/TrueOracleEvaluator.java")
        };
        java.nio.file.Path filePath = null;
        for (var p : candidates) {
            if (java.nio.file.Files.exists(p)) { filePath = p; break; }
        }
        assertNotNull(filePath, "TrueOracleEvaluator.java should be found via src/main or backend/src/main path");
        String file = new String(java.nio.file.Files.readAllBytes(filePath));
        assertTrue(file.contains("policyEngine.evaluate"), "Oracle must reuse PolicyEngine, not duplicate rules");
        // Verify no direct duplication of core policy thresholds/rules (amount threshold, retry limit etc. should not be reimplemented)
        // The file should reference policyConfig or basePolicyContext for thresholds, not hardcode 10000 as a rule
        assertTrue(file.contains("basePolicyContext.autoActionLimit()") || file.contains("policyConfig.getAutoActionLimit()"),
                "Oracle must use per-case policy snapshot or PolicyConfig, not hardcoded thresholds");
        // Ensure no direct string duplication of policy rule names beyond delegation
        assertFalse(file.contains("if (ctx.amount().compareTo"), "Oracle should not duplicate PolicyEngine's amount-threshold if logic");
        assertFalse(file.contains("if (ctx.attemptCount() >= ctx.maxRetries())"), "Oracle should not duplicate retry-limit logic");
    }
}
