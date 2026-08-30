package com.recoverflow.synthetic;

import com.recoverflow.ai.FailureCategory;
import com.recoverflow.payment.PaymentMethod;
import com.recoverflow.recovery.RecoveryActionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deterministic synthetic generator for 3000+ cases.
 * - Hidden world generated first, then observable derived via noisy projection
 * - Ground truth sampled via Bernoulli(P_true) with deterministic PRNG per caseId+action
 * - No leakage: decision service never receives HiddenTruth
 */
@Component
public class SyntheticWorldGenerator {

    // Amount tiers for realism: lognormal-ish via uniform in log space
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("500.00");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("50000.00");

    public List<SyntheticCase> generate(long seed, int datasetSize) {
        Random rnd = new Random(seed);
        List<SyntheticCase> cases = new ArrayList<>(datasetSize);

        for (int i = 0; i < datasetSize; i++) {
            UUID caseId = new UUID(rnd.nextLong(), rnd.nextLong());
            // Ensure positive UUID for caseId? Not needed, but keep deterministic
            caseId = UUID.nameUUIDFromBytes(("case-" + seed + "-" + i).getBytes());

            // Hidden: true failure category weighted distribution
            FailureCategory trueCat = sampleTrueCategory(rnd);
            CustomerBehaviorProfile profile = sampleProfile(rnd);
            LatentRecoveryPropensity latent = sampleLatent(rnd);

            // Observable amount: log-uniform 500-50000, 2 decimal
            BigDecimal amount = sampleAmount(rnd);
            PaymentMethod method = sampleMethod(rnd);
            String trueGateway = mapCategoryToGateway(trueCat);
            // Observable gateway is noisy: 85% correct, 15% random other
            String observedGateway = rnd.nextDouble() < 0.85 ? trueGateway : sampleRandomGateway(rnd, trueGateway);

            int priorSuccess = samplePriorSuccess(rnd, profile);
            int priorFailure = samplePriorFailure(rnd, profile);
            int elapsedHours = rnd.nextInt(49); // 0-48
            int attemptCount = rnd.nextInt(4); // 0-3
            boolean linkAlreadySent = rnd.nextDouble() < 0.2;

            Instant failedAt = Instant.now().minus(elapsedHours, ChronoUnit.HOURS);
            UUID paymentId = UUID.nameUUIDFromBytes(("pay-" + caseId).getBytes());
            UUID merchantId = UUID.nameUUIDFromBytes(("merchant-" + (i % 3)).getBytes());
            UUID customerId = UUID.nameUUIDFromBytes(("cust-" + caseId).getBytes());

            ObservableCase observable = new ObservableCase(
                    caseId, paymentId, merchantId, customerId,
                    amount, "INR", method, observedGateway, failedAt,
                    attemptCount, elapsedHours, priorSuccess, priorFailure, linkAlreadySent
            );

            String trueGatewayCode = trueGateway;
            // Build P_true per action via hidden registry
            Map<RecoveryActionType, Double> pTrue = new EnumMap<>(RecoveryActionType.class);
            for (RecoveryActionType action : List.of(RecoveryActionType.RETRY_NOW, RecoveryActionType.SCHEDULE_RETRY, RecoveryActionType.SEND_PAYMENT_LINK, RecoveryActionType.SEND_REMINDER)) {
                double p = HiddenWorldRegistry.pTrue(trueCat, profile, latent, action);
                // Amount tier modifier: very high amount slightly lower P
                if (amount.compareTo(new BigDecimal("30000")) > 0) p -= 0.05;
                else if (amount.compareTo(new BigDecimal("2000")) < 0) p += 0.02;
                // Elapsed modifier
                if (elapsedHours > 24) p -= 0.07;
                else if (elapsedHours <= 2) p += 0.03;
                // Clamp
                if (p < 0.02) p = 0.02;
                if (p > 0.85) p = 0.85;
                pTrue.put(action, p);
            }

            // Sample ground truth outcomes Bernoulli per action, deterministic per seed+caseId+action
            Map<RecoveryActionType, Boolean> groundTruth = new EnumMap<>(RecoveryActionType.class);
            for (RecoveryActionType action : pTrue.keySet()) {
                double p = pTrue.get(action);
                // Deterministic Bernoulli using caseId+action hash
                long actionSeed = seed ^ caseId.getMostSignificantBits() ^ action.hashCode() ^ 0x9E3779B97F4A7C15L;
                Random perActionRnd = new Random(actionSeed);
                boolean outcome = perActionRnd.nextDouble() < p;
                groundTruth.put(action, outcome);
            }

            HiddenTruth hidden = new HiddenTruth(trueCat, profile, latent, trueGatewayCode, pTrue);

            SyntheticCase sc = new SyntheticCase(caseId, hidden, observable, pTrue, groundTruth);
            cases.add(sc);
        }
        return List.copyOf(cases);
    }

    private FailureCategory sampleTrueCategory(Random rnd) {
        double r = rnd.nextDouble();
        if (r < 0.20) return FailureCategory.TEMPORARY_BANK_FAILURE;
        if (r < 0.35) return FailureCategory.NETWORK_ERROR;
        if (r < 0.60) return FailureCategory.INSUFFICIENT_FUNDS;
        if (r < 0.75) return FailureCategory.AUTH_FAILED;
        if (r < 0.85) return FailureCategory.CARD_EXPIRED;
        if (r < 0.95) return FailureCategory.LIMIT_EXCEEDED;
        return FailureCategory.UNKNOWN;
    }

    private CustomerBehaviorProfile sampleProfile(Random rnd) {
        double r = rnd.nextDouble();
        if (r < 0.25) return CustomerBehaviorProfile.STRONG_HISTORY;
        if (r < 0.40) return CustomerBehaviorProfile.NEW_CUSTOMER;
        if (r < 0.55) return CustomerBehaviorProfile.REPEAT_FAILURE;
        if (r < 0.70) return CustomerBehaviorProfile.HIGH_FRICTION;
        return CustomerBehaviorProfile.AVERAGE;
    }

    private LatentRecoveryPropensity sampleLatent(Random rnd) {
        double r = rnd.nextDouble();
        if (r < 0.30) return LatentRecoveryPropensity.HIGH;
        if (r < 0.70) return LatentRecoveryPropensity.MEDIUM;
        return LatentRecoveryPropensity.LOW;
    }

    private BigDecimal sampleAmount(Random rnd) {
        // Log-uniform: exp( log(min) + r*(log(max)-log(min)) )
        double logMin = Math.log(MIN_AMOUNT.doubleValue());
        double logMax = Math.log(MAX_AMOUNT.doubleValue());
        double logVal = logMin + rnd.nextDouble() * (logMax - logMin);
        double val = Math.exp(logVal);
        return new BigDecimal(val).setScale(4, RoundingMode.HALF_UP);
    }

    private PaymentMethod sampleMethod(Random rnd) {
        double r = rnd.nextDouble();
        if (r < 0.50) return PaymentMethod.CARD;
        if (r < 0.85) return PaymentMethod.UPI;
        if (r < 0.95) return PaymentMethod.NB;
        return PaymentMethod.WALLET;
    }

    private String mapCategoryToGateway(FailureCategory cat) {
        return switch (cat) {
            case TEMPORARY_BANK_FAILURE -> "BANK_TIMEOUT";
            case NETWORK_ERROR -> "NETWORK_ERROR";
            case INSUFFICIENT_FUNDS -> "INSUFFICIENT_FUNDS";
            case AUTH_FAILED -> "AUTH_FAILED";
            case CARD_EXPIRED -> "CARD_EXPIRED";
            case LIMIT_EXCEEDED -> "LIMIT_EXCEEDED";
            case UNKNOWN -> "UNKNOWN";
        };
    }

    private String sampleRandomGateway(Random rnd, String exclude) {
        List<String> all = List.of("BANK_TIMEOUT","NETWORK_ERROR","INSUFFICIENT_FUNDS","AUTH_FAILED","CARD_EXPIRED","LIMIT_EXCEEDED","UNKNOWN");
        String g;
        do { g = all.get(rnd.nextInt(all.size())); } while (g.equals(exclude));
        return g;
    }

    private int samplePriorSuccess(Random rnd, CustomerBehaviorProfile profile) {
        return switch (profile) {
            case STRONG_HISTORY -> 10 + rnd.nextInt(10);
            case NEW_CUSTOMER -> 0;
            case REPEAT_FAILURE -> rnd.nextInt(3);
            case HIGH_FRICTION -> rnd.nextInt(5);
            case AVERAGE -> rnd.nextInt(8);
        };
    }

    private int samplePriorFailure(Random rnd, CustomerBehaviorProfile profile) {
        return switch (profile) {
            case STRONG_HISTORY -> rnd.nextInt(2);
            case NEW_CUSTOMER -> 0;
            case REPEAT_FAILURE -> 3 + rnd.nextInt(5);
            case HIGH_FRICTION -> 1 + rnd.nextInt(4);
            case AVERAGE -> rnd.nextInt(3);
        };
    }
}
