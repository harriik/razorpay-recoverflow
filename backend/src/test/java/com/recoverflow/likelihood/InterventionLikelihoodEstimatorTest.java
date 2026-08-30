package com.recoverflow.likelihood;

import static org.junit.jupiter.api.Assertions.*;

import com.recoverflow.ai.AiAssessment;
import com.recoverflow.ai.CandidateAssessment;
import com.recoverflow.ai.CandidateAssessmentLevel;
import com.recoverflow.ai.EvidenceQuality;
import com.recoverflow.ai.FailureCategory;
import com.recoverflow.ai.Recoverability;
import com.recoverflow.ai.RiskLevel;
import com.recoverflow.recovery.RecoveryActionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterventionLikelihoodEstimatorTest {

    private InterventionLikelihoodEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new InterventionLikelihoodEstimator();
    }

    @Test
    void versionIsV1() {
        assertEquals("v1", estimator.getVersion());
    }

    @Test
    void observableOnlyWithinBounds() {
        ObservableContext obs = new ObservableContext(
                new BigDecimal("6500.0000"), "INR",
                com.recoverflow.payment.PaymentMethod.CARD,
                "BANK_TIMEOUT", 2, 0, 12, 1, false);
        Map<RecoveryActionType, BigDecimal> p = estimator.estimateObservableOnly(obs);
        assertEquals(4, p.size());
        for (BigDecimal v : p.values()) {
            assertTrue(v.compareTo(new BigDecimal("0.02")) >= 0, "min bound");
            assertTrue(v.compareTo(new BigDecimal("0.85")) <= 0, "max bound");
            assertEquals(3, v.scale(), "scale 3");
        }
    }

    @Test
    void baseDependsOnGatewayAndBucket() {
        ObservableContext low = new ObservableContext(new BigDecimal("1000.0000"), "INR",
                com.recoverflow.payment.PaymentMethod.CARD, "BANK_TIMEOUT", 1, 0, 0, 0, false);
        ObservableContext high = new ObservableContext(new BigDecimal("40000.0000"), "INR",
                com.recoverflow.payment.PaymentMethod.CARD, "BANK_TIMEOUT", 1, 0, 0, 0, false);
        Map<RecoveryActionType, BigDecimal> pLow = estimator.estimateObservableOnly(low);
        Map<RecoveryActionType, BigDecimal> pHigh = estimator.estimateObservableOnly(high);
        // VERY_HIGH bucket should have lower SCHEDULE_RETRY than LOW/MEDIUM for BANK_TIMEOUT
        assertNotEquals(pLow.get(RecoveryActionType.SCHEDULE_RETRY), pHigh.get(RecoveryActionType.SCHEDULE_RETRY));
    }

    @Test
    void aiMateriallyInfluencesPEstimated() {
        ObservableContext obs = new ObservableContext(new BigDecimal("6500.0000"), "INR",
                com.recoverflow.payment.PaymentMethod.CARD, "BANK_TIMEOUT", 2, 0, 12, 1, false);

        Map<RecoveryActionType, BigDecimal> pObs = estimator.estimateObservableOnly(obs);

        AiAssessment aiGood = aiFor(FailureCategory.TEMPORARY_BANK_FAILURE, Recoverability.HIGH, EvidenceQuality.HIGH, RiskLevel.LOW,
                Map.of(RecoveryActionType.RETRY_NOW, CandidateAssessmentLevel.LOW,
                       RecoveryActionType.SCHEDULE_RETRY, CandidateAssessmentLevel.HIGH,
                       RecoveryActionType.SEND_PAYMENT_LINK, CandidateAssessmentLevel.MEDIUM,
                       RecoveryActionType.SEND_REMINDER, CandidateAssessmentLevel.LOW));

        Map<RecoveryActionType, BigDecimal> pAi = estimator.estimate(obs, aiGood);

        // AI with TEMPORARY_BANK_FAILURE HIGH on SCHEDULE_RETRY should boost it materially
        assertTrue(pAi.get(RecoveryActionType.SCHEDULE_RETRY).compareTo(pObs.get(RecoveryActionType.SCHEDULE_RETRY)) > 0,
                "AI should increase SCHEDULE_RETRY: obs=" + pObs.get(RecoveryActionType.SCHEDULE_RETRY) + " ai=" + pAi.get(RecoveryActionType.SCHEDULE_RETRY));
        // SCHEDULE boost should be larger than RETRY boost (proves per-action qualitative influence)
        BigDecimal deltaSchedule = pAi.get(RecoveryActionType.SCHEDULE_RETRY).subtract(pObs.get(RecoveryActionType.SCHEDULE_RETRY));
        BigDecimal deltaRetry = pAi.get(RecoveryActionType.RETRY_NOW).subtract(pObs.get(RecoveryActionType.RETRY_NOW));
        assertTrue(deltaSchedule.compareTo(deltaRetry) > 0,
                "SCHEDULE boost must exceed RETRY boost: deltaSchedule=" + deltaSchedule + " deltaRetry=" + deltaRetry);
    }

    @Test
    void wrongAiProducesWorseRanking() {
        ObservableContext obs = new ObservableContext(new BigDecimal("6500.0000"), "INR",
                com.recoverflow.payment.PaymentMethod.CARD, "BANK_TIMEOUT", 2, 0, 12, 1, false);

        AiAssessment aiCorrect = aiFor(FailureCategory.TEMPORARY_BANK_FAILURE, Recoverability.HIGH, EvidenceQuality.HIGH, RiskLevel.LOW,
                Map.of(RecoveryActionType.RETRY_NOW, CandidateAssessmentLevel.LOW,
                       RecoveryActionType.SCHEDULE_RETRY, CandidateAssessmentLevel.HIGH,
                       RecoveryActionType.SEND_PAYMENT_LINK, CandidateAssessmentLevel.MEDIUM,
                       RecoveryActionType.SEND_REMINDER, CandidateAssessmentLevel.LOW));

        AiAssessment aiWrong = aiFor(FailureCategory.CARD_EXPIRED, Recoverability.LOW, EvidenceQuality.HIGH, RiskLevel.LOW,
                Map.of(RecoveryActionType.RETRY_NOW, CandidateAssessmentLevel.LOW,
                       RecoveryActionType.SCHEDULE_RETRY, CandidateAssessmentLevel.LOW,
                       RecoveryActionType.SEND_PAYMENT_LINK, CandidateAssessmentLevel.HIGH,
                       RecoveryActionType.SEND_REMINDER, CandidateAssessmentLevel.MEDIUM));

        Map<RecoveryActionType, BigDecimal> pCorrect = estimator.estimate(obs, aiCorrect);
        Map<RecoveryActionType, BigDecimal> pWrong = estimator.estimate(obs, aiWrong);

        // Correct AI should rank SCHEDULE_RETRY higher than wrong AI does
        assertTrue(pCorrect.get(RecoveryActionType.SCHEDULE_RETRY).compareTo(pWrong.get(RecoveryActionType.SCHEDULE_RETRY)) > 0,
                "Correct AI should give higher SCHEDULE_RETRY");
        // Wrong AI pushes SEND_PAYMENT_LINK higher
        assertTrue(pWrong.get(RecoveryActionType.SEND_PAYMENT_LINK).compareTo(pCorrect.get(RecoveryActionType.SEND_PAYMENT_LINK)) > 0);
    }

    @Test
    void evidenceQualityLowReducesAiWeight() {
        ObservableContext obs = new ObservableContext(new BigDecimal("6500.0000"), "INR",
                com.recoverflow.payment.PaymentMethod.CARD, "BANK_TIMEOUT", 2, 0, 12, 1, false);

        AiAssessment aiHigh = aiFor(FailureCategory.TEMPORARY_BANK_FAILURE, Recoverability.HIGH, EvidenceQuality.HIGH, RiskLevel.LOW,
                Map.of(RecoveryActionType.SCHEDULE_RETRY, CandidateAssessmentLevel.HIGH,
                       RecoveryActionType.RETRY_NOW, CandidateAssessmentLevel.LOW,
                       RecoveryActionType.SEND_PAYMENT_LINK, CandidateAssessmentLevel.MEDIUM,
                       RecoveryActionType.SEND_REMINDER, CandidateAssessmentLevel.LOW));

        AiAssessment aiLow = aiFor(FailureCategory.TEMPORARY_BANK_FAILURE, Recoverability.HIGH, EvidenceQuality.LOW, RiskLevel.LOW,
                Map.of(RecoveryActionType.SCHEDULE_RETRY, CandidateAssessmentLevel.HIGH,
                       RecoveryActionType.RETRY_NOW, CandidateAssessmentLevel.LOW,
                       RecoveryActionType.SEND_PAYMENT_LINK, CandidateAssessmentLevel.MEDIUM,
                       RecoveryActionType.SEND_REMINDER, CandidateAssessmentLevel.LOW));

        Map<RecoveryActionType, BigDecimal> pHigh = estimator.estimate(obs, aiHigh);
        Map<RecoveryActionType, BigDecimal> pLow = estimator.estimate(obs, aiLow);

        // HIGH evidence weight 1.0 vs LOW 0.3, so HIGH should give higher SCHEDULE_RETRY
        assertTrue(pHigh.get(RecoveryActionType.SCHEDULE_RETRY).compareTo(pLow.get(RecoveryActionType.SCHEDULE_RETRY)) > 0);
    }

    @Test
    void historyModifierStrongHistoryBoosts() {
        ObservableContext strong = new ObservableContext(new BigDecimal("6500.0000"), "INR",
                com.recoverflow.payment.PaymentMethod.CARD, "BANK_TIMEOUT", 2, 0, 12, 1, false);
        ObservableContext repeat = new ObservableContext(new BigDecimal("6500.0000"), "INR",
                com.recoverflow.payment.PaymentMethod.CARD, "BANK_TIMEOUT", 2, 0, 0, 5, false);
        Map<RecoveryActionType, BigDecimal> pStrong = estimator.estimateObservableOnly(strong);
        Map<RecoveryActionType, BigDecimal> pRepeat = estimator.estimateObservableOnly(repeat);
        assertTrue(pStrong.get(RecoveryActionType.SCHEDULE_RETRY).compareTo(pRepeat.get(RecoveryActionType.SCHEDULE_RETRY)) > 0,
                "Strong history should beat repeat failure");
    }

    @Test
    void deterministicGivenSameInputs() {
        ObservableContext obs = new ObservableContext(new BigDecimal("5000.0000"), "INR",
                com.recoverflow.payment.PaymentMethod.UPI, "INSUFFICIENT_FUNDS", 5, 1, 3, 2, false);
        AiAssessment ai = aiFor(FailureCategory.INSUFFICIENT_FUNDS, Recoverability.MEDIUM, EvidenceQuality.MEDIUM, RiskLevel.MEDIUM,
                Map.of(RecoveryActionType.RETRY_NOW, CandidateAssessmentLevel.LOW,
                       RecoveryActionType.SCHEDULE_RETRY, CandidateAssessmentLevel.MEDIUM,
                       RecoveryActionType.SEND_PAYMENT_LINK, CandidateAssessmentLevel.HIGH,
                       RecoveryActionType.SEND_REMINDER, CandidateAssessmentLevel.LOW));
        Map<RecoveryActionType, BigDecimal> p1 = estimator.estimate(obs, ai);
        Map<RecoveryActionType, BigDecimal> p2 = estimator.estimate(obs, ai);
        assertEquals(p1, p2);
    }

    @Test
    void nullAiFallsBackToObservableOnly() {
        ObservableContext obs = new ObservableContext(new BigDecimal("5000.0000"), "INR",
                com.recoverflow.payment.PaymentMethod.CARD, "UNKNOWN", 0, 0, 0, 0, false);
        Map<RecoveryActionType, BigDecimal> pObs = estimator.estimateObservableOnly(obs);
        Map<RecoveryActionType, BigDecimal> pNull = estimator.estimate(obs, null);
        assertEquals(pObs, pNull);
    }

    @Test
    void aiNeverProvidesNumericProbability() {
        // Verify AiAssessment has no BigDecimal probability field via reflection
        boolean hasProbability = false;
        for (var field : AiAssessment.class.getDeclaredFields()) {
            if (field.getType().equals(BigDecimal.class)) {
                hasProbability = true;
            }
            if (field.getName().toLowerCase().contains("probability") || field.getName().toLowerCase().contains("p_estimated")) {
                hasProbability = true;
            }
        }
        assertFalse(hasProbability, "AiAssessment must not contain numeric probability field");
        // Also ensure candidate assessment is qualitative enum, not BigDecimal
        assertEquals(CandidateAssessmentLevel.class, CandidateAssessment.class.getDeclaredFields()[1].getType() == CandidateAssessmentLevel.class ? CandidateAssessmentLevel.class : null);
    }

    @Test
    void ablationSupportObservableVsAi() {
        ObservableContext obs = new ObservableContext(new BigDecimal("6500.0000"), "INR",
                com.recoverflow.payment.PaymentMethod.CARD, "BANK_TIMEOUT", 2, 0, 12, 1, false);
        AiAssessment ai = aiFor(FailureCategory.TEMPORARY_BANK_FAILURE, Recoverability.HIGH, EvidenceQuality.HIGH, RiskLevel.LOW,
                Map.of(RecoveryActionType.RETRY_NOW, CandidateAssessmentLevel.LOW,
                       RecoveryActionType.SCHEDULE_RETRY, CandidateAssessmentLevel.HIGH,
                       RecoveryActionType.SEND_PAYMENT_LINK, CandidateAssessmentLevel.MEDIUM,
                       RecoveryActionType.SEND_REMINDER, CandidateAssessmentLevel.LOW));
        Map<RecoveryActionType, BigDecimal> pObs = estimator.estimateObservableOnly(obs);
        Map<RecoveryActionType, BigDecimal> pAi = estimator.estimate(obs, ai);
        assertNotEquals(pObs, pAi, "Ablation: observable-only vs AI-enabled must differ when AI adds signal");
    }

    @Test
    void breakdownIsAuditable() {
        ObservableContext obs = new ObservableContext(new BigDecimal("6500.0000"), "INR",
                com.recoverflow.payment.PaymentMethod.CARD, "BANK_TIMEOUT", 2, 0, 12, 1, false);
        AiAssessment ai = aiFor(FailureCategory.TEMPORARY_BANK_FAILURE, Recoverability.HIGH, EvidenceQuality.HIGH, RiskLevel.LOW,
                Map.of(RecoveryActionType.SCHEDULE_RETRY, CandidateAssessmentLevel.HIGH,
                       RecoveryActionType.RETRY_NOW, CandidateAssessmentLevel.LOW,
                       RecoveryActionType.SEND_PAYMENT_LINK, CandidateAssessmentLevel.MEDIUM,
                       RecoveryActionType.SEND_REMINDER, CandidateAssessmentLevel.LOW));
        var br = estimator.breakdown(obs, ai, RecoveryActionType.SCHEDULE_RETRY);
        assertNotNull(br.base());
        assertNotNull(br.aiDelta());
        assertNotNull(br.clamped());
        assertTrue(br.base().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(3, br.clamped().scale());
    }

    private AiAssessment aiFor(FailureCategory fc, Recoverability rec, EvidenceQuality eq, RiskLevel risk,
                               Map<RecoveryActionType, CandidateAssessmentLevel> levels) {
        List<CandidateAssessment> list = List.of(
                new CandidateAssessment(RecoveryActionType.RETRY_NOW, levels.get(RecoveryActionType.RETRY_NOW), true, null),
                new CandidateAssessment(RecoveryActionType.SCHEDULE_RETRY, levels.get(RecoveryActionType.SCHEDULE_RETRY), true, null),
                new CandidateAssessment(RecoveryActionType.SEND_PAYMENT_LINK, levels.get(RecoveryActionType.SEND_PAYMENT_LINK), true, null),
                new CandidateAssessment(RecoveryActionType.SEND_REMINDER, levels.get(RecoveryActionType.SEND_REMINDER), true, null)
        );
        // Determine recommended as highest assessment
        RecoveryActionType recommended = list.stream()
                .filter(CandidateAssessment::applicable)
                .max((a,b) -> a.assessment().ordinal() - b.assessment().ordinal())
                .map(CandidateAssessment::action).orElse(RecoveryActionType.SCHEDULE_RETRY);
        return new AiAssessment(fc, rec, list, recommended, eq, risk, "test reasoning", "test-model", "v1");
    }
}
