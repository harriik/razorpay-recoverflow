package com.recoverflow.decision;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.ai.RiskLevel;
import com.recoverflow.recovery.RecoveryActionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExpectedNetRecoveryValueEngineTest {

    private ExpectedNetRecoveryValueEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ExpectedNetRecoveryValueEngine();
    }

    @Test
    void formulaExampleFromPhase0() {
        // Amount 5000, P: RETRY_NOW 0.15, SCHEDULE 0.58, LINK 0.42
        // Costs: retry 0, schedule 0, link 10; Friction: 50,20,100; Risk LOW 5
        BigDecimal amount = new BigDecimal("5000.0000");
        Map<RecoveryActionType, BigDecimal> p = Map.of(
                RecoveryActionType.RETRY_NOW, new BigDecimal("0.150"),
                RecoveryActionType.SCHEDULE_RETRY, new BigDecimal("0.580"),
                RecoveryActionType.SEND_PAYMENT_LINK, new BigDecimal("0.420")
        );
        List<ExpectedNetRecoveryValueEngine.RankedCandidate> ranked = engine.rank(amount, p, RiskLevel.LOW);
        // ExpectedNet: RETRY 750-0-50-5=695, SCHEDULE 2900-0-20-5=2875, LINK 2100-10-100-5=1985
        assertEquals(RecoveryActionType.SCHEDULE_RETRY, ranked.get(0).action());
        assertEquals(new BigDecimal("2875.0000"), ranked.get(0).expectedNet());
        assertEquals(RecoveryActionType.SEND_PAYMENT_LINK, ranked.get(1).action());
        assertEquals(new BigDecimal("1985.0000"), ranked.get(1).expectedNet());
        assertEquals(RecoveryActionType.RETRY_NOW, ranked.get(2).action());
        assertEquals(new BigDecimal("695.0000"), ranked.get(2).expectedNet());
    }

    @Test
    void includesFrictionProxyAndRisk() {
        BigDecimal amount = new BigDecimal("10000.0000");
        Map<RecoveryActionType, BigDecimal> p = Map.of(RecoveryActionType.RETRY_NOW, new BigDecimal("0.500"));
        List<ExpectedNetRecoveryValueEngine.RankedCandidate> ranked = engine.rank(amount, p, RiskLevel.HIGH);
        // 5000 -0 -50 -120 = 4830
        var c = ranked.get(0);
        assertEquals(new BigDecimal("5000.0000"), c.expectedRecovered());
        assertEquals(new BigDecimal("0.00"), c.cost());
        assertEquals(new BigDecimal("50.00"), c.syntheticFrictionProxy());
        assertEquals(new BigDecimal("120.00"), c.riskPenalty());
        assertEquals(new BigDecimal("4830.0000"), c.expectedNet());
    }

    @Test
    void deterministicTieBreaking() {
        // Create two actions with same EV by adjusting P to compensate for friction/cost
        // Amount 1000: need P such that EV equal.
        // For RETRY_NOW: EV = P*1000 -0 -50 -5 => EV = 1000P -55
        // For SCHEDULE: EV = P*1000 -0 -20 -5 => EV = 1000P -25
        // To tie: 1000*P_retry -55 = 1000*P_sched -25 => P_retry = P_sched +0.03
        // So if P_retry 0.33 and P_sched 0.30, both EV = 275
        BigDecimal amount = new BigDecimal("1000.0000");
        Map<RecoveryActionType, BigDecimal> p = Map.of(
                RecoveryActionType.RETRY_NOW, new BigDecimal("0.330"),
                RecoveryActionType.SCHEDULE_RETRY, new BigDecimal("0.300")
        );
        List<ExpectedNetRecoveryValueEngine.RankedCandidate> r1 = engine.rank(amount, p, RiskLevel.LOW);
        List<ExpectedNetRecoveryValueEngine.RankedCandidate> r2 = engine.rank(amount, p, RiskLevel.LOW);
        // Both have EV 275, tie breaker: lower friction wins (SCHEDULE 20 < RETRY 50)
        assertEquals(RecoveryActionType.SCHEDULE_RETRY, r1.get(0).action());
        assertEquals(RecoveryActionType.RETRY_NOW, r1.get(1).action());
        // Deterministic across runs
        assertEquals(r1.get(0).action(), r2.get(0).action());
    }

    @Test
    void pureAndReproducible() {
        BigDecimal amount = new BigDecimal("6500.0000");
        Map<RecoveryActionType, BigDecimal> p = Map.of(
                RecoveryActionType.RETRY_NOW, new BigDecimal("0.140"),
                RecoveryActionType.SCHEDULE_RETRY, new BigDecimal("0.560"),
                RecoveryActionType.SEND_PAYMENT_LINK, new BigDecimal("0.400"),
                RecoveryActionType.SEND_REMINDER, new BigDecimal("0.150")
        );
        List<ExpectedNetRecoveryValueEngine.RankedCandidate> a = engine.rank(amount, p, RiskLevel.LOW);
        List<ExpectedNetRecoveryValueEngine.RankedCandidate> b = engine.rank(amount, p, RiskLevel.LOW);
        assertEquals(a, b);
        // Verify no floating: all have scale 4
        for (var c : a) {
            assertEquals(4, c.expectedNet().scale());
            assertEquals(4, c.expectedRecovered().scale());
        }
    }

    @Test
    void policyIndependentRanking() {
        // Engine ranks without policy; policy would filter later
        BigDecimal amount = new BigDecimal("20000.0000");
        Map<RecoveryActionType, BigDecimal> p = Map.of(
                RecoveryActionType.RETRY_NOW, new BigDecimal("0.10"),
                RecoveryActionType.SCHEDULE_RETRY, new BigDecimal("0.40"),
                RecoveryActionType.SEND_PAYMENT_LINK, new BigDecimal("0.35")
        );
        var ranked = engine.rank(amount, p, RiskLevel.LOW);
        // Even though high amount might be escalated by policy, engine still ranks SCHEDULE highest
        assertEquals(RecoveryActionType.SCHEDULE_RETRY, ranked.get(0).action());
        // 8000 -20 -5 =7975 vs 7000 -110? Let's compute: SCHEDULE 8000-0-20-5=7975, LINK 7000-10-100-5=6885
        assertTrue(ranked.get(0).expectedNet().compareTo(ranked.get(1).expectedNet()) > 0);
    }

    @Test
    void amountTimesProbabilityPrecision() {
        BigDecimal amount = new BigDecimal("5000.0000");
        BigDecimal p = new BigDecimal("0.580");
        BigDecimal expected = p.multiply(amount).setScale(4, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("2900.0000"), expected);
        // Through engine
        Map<RecoveryActionType, BigDecimal> m = Map.of(RecoveryActionType.SCHEDULE_RETRY, p);
        var ranked = engine.rank(amount, m, RiskLevel.LOW);
        assertEquals(new BigDecimal("2900.0000"), ranked.get(0).expectedRecovered());
    }

    @Test
    void operationalCostAndFrictionSeparate() {
        // Ensure they are queried separately and not conflated
        assertEquals(new BigDecimal("0.00"), engine.costFor(RecoveryActionType.RETRY_NOW));
        assertEquals(new BigDecimal("10.00"), engine.costFor(RecoveryActionType.SEND_PAYMENT_LINK));
        assertEquals(new BigDecimal("50.00"), engine.frictionFor(RecoveryActionType.RETRY_NOW));
        assertEquals(new BigDecimal("100.00"), engine.frictionFor(RecoveryActionType.SEND_PAYMENT_LINK));
        assertEquals(new BigDecimal("5.00"), engine.riskFor(RiskLevel.LOW));
        assertEquals(new BigDecimal("120.00"), engine.riskFor(RiskLevel.HIGH));
    }

    @Test
    void negativeEvStillRanked() {
        // Small amount, low P, high friction/risk => negative EV still ranked, but policy would STOP
        BigDecimal amount = new BigDecimal("100.0000");
        Map<RecoveryActionType, BigDecimal> p = Map.of(RecoveryActionType.SEND_PAYMENT_LINK, new BigDecimal("0.10"));
        var ranked = engine.rank(amount, p, RiskLevel.HIGH); // 10 -10 -100 -120 = -220
        assertEquals(new BigDecimal("-220.0000"), ranked.get(0).expectedNet());
    }
}
