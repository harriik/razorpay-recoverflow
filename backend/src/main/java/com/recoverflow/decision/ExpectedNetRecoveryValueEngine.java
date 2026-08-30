package com.recoverflow.decision;

import com.recoverflow.ai.RiskLevel;
import com.recoverflow.recovery.RecoveryActionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Pure deterministic engine. No AI, no policy, no DB.
 * Formula: ExpectedNet = P * amount - cost - syntheticFrictionProxy - riskPenalty
 * Ranking is deterministic with tie-breaking.
 */
@Component
public class ExpectedNetRecoveryValueEngine {

    // Operational cost per action (INR)
    private static final Map<RecoveryActionType, BigDecimal> COST = Map.of(
            RecoveryActionType.RETRY_NOW, new BigDecimal("0.00"),
            RecoveryActionType.SCHEDULE_RETRY, new BigDecimal("0.00"),
            RecoveryActionType.SEND_PAYMENT_LINK, new BigDecimal("10.00"),
            RecoveryActionType.SEND_REMINDER, new BigDecimal("2.00")
    );

    // Synthetic customer-friction proxy (not real monetary cost)
    private static final Map<RecoveryActionType, BigDecimal> FRICTION = Map.of(
            RecoveryActionType.RETRY_NOW, new BigDecimal("50.00"),
            RecoveryActionType.SCHEDULE_RETRY, new BigDecimal("20.00"),
            RecoveryActionType.SEND_PAYMENT_LINK, new BigDecimal("100.00"),
            RecoveryActionType.SEND_REMINDER, new BigDecimal("30.00")
    );

    // Risk penalty per AI riskLevel (INR)
    private static final Map<RiskLevel, BigDecimal> RISK = Map.of(
            RiskLevel.LOW, new BigDecimal("5.00"),
            RiskLevel.MEDIUM, new BigDecimal("40.00"),
            RiskLevel.HIGH, new BigDecimal("120.00")
    );

    public List<RankedCandidate> rank(BigDecimal amount, Map<RecoveryActionType, BigDecimal> likelihoods, RiskLevel riskLevel) {
        if (amount == null) throw new IllegalArgumentException("amount required");
        if (likelihoods == null || likelihoods.isEmpty()) throw new IllegalArgumentException("likelihoods required");
        RiskLevel effectiveRisk = riskLevel != null ? riskLevel : RiskLevel.LOW;

        List<RankedCandidate> candidates = new ArrayList<>();
        for (Map.Entry<RecoveryActionType, BigDecimal> e : likelihoods.entrySet()) {
            RecoveryActionType action = e.getKey();
            BigDecimal p = e.getValue();
            BigDecimal cost = COST.getOrDefault(action, BigDecimal.ZERO);
            BigDecimal friction = FRICTION.getOrDefault(action, BigDecimal.ZERO);
            BigDecimal risk = RISK.get(effectiveRisk);

            BigDecimal expectedRecovered = p.multiply(amount).setScale(4, RoundingMode.HALF_UP);
            BigDecimal expectedNet = expectedRecovered.subtract(cost).subtract(friction).subtract(risk)
                    .setScale(4, RoundingMode.HALF_UP);

            candidates.add(new RankedCandidate(action, p, expectedRecovered, cost, friction, risk, expectedNet));
        }

        // Sort: highest EV desc, then lower friction, then lower cost, then lexicographic action name (deterministic)
        candidates.sort(Comparator
                .comparing(RankedCandidate::expectedNet).reversed()
                .thenComparing(RankedCandidate::syntheticFrictionProxy)
                .thenComparing(RankedCandidate::cost)
                .thenComparing(c -> c.action().name()));

        return List.copyOf(candidates);
    }

    /**
     * Ablation helper: rank with and without risk adjustment? For now same.
     */
    public BigDecimal costFor(RecoveryActionType action) { return COST.getOrDefault(action, BigDecimal.ZERO); }
    public BigDecimal frictionFor(RecoveryActionType action) { return FRICTION.getOrDefault(action, BigDecimal.ZERO); }
    public BigDecimal riskFor(RiskLevel level) { return RISK.getOrDefault(level, BigDecimal.ZERO); }

    public record RankedCandidate(
            RecoveryActionType action,
            BigDecimal likelihood,
            BigDecimal expectedRecovered,
            BigDecimal cost,
            BigDecimal syntheticFrictionProxy,
            BigDecimal riskPenalty,
            BigDecimal expectedNet
    ) {}
}
