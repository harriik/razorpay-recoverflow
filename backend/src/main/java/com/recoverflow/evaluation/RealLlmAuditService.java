package com.recoverflow.evaluation;

import com.recoverflow.ai.AiAssessment;
import com.recoverflow.ai.AiDecisionProvider;
import com.recoverflow.ai.RealLlmAiProvider;
import com.recoverflow.ai.SyntheticAiProvider;
import com.recoverflow.decision.ExpectedNetRecoveryValueEngine;
import com.recoverflow.decision.RecoveryDecisionService;
import com.recoverflow.likelihood.InterventionLikelihoodEstimator;
import com.recoverflow.likelihood.ObservableContext;
import com.recoverflow.policy.PolicyContext;
import com.recoverflow.policy.PolicyDecisionType;
import com.recoverflow.policy.PolicyEngine;
import com.recoverflow.recovery.RecoveryActionType;
import com.recoverflow.synthetic.SyntheticCase;
import com.recoverflow.synthetic.SyntheticWorldGenerator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Small real-LLM audit service for 20-50 deterministic fixtures.
 * For each case records observable input, LLM output, schema valid/invalid, fallback, selected action, policy decision, estimator, EV ranking.
 * Comparison between SYNTHETIC and REAL_LLM for same cases uses HiddenTruth only after both decisions.
 */
@Component
public class RealLlmAuditService {

    private final SyntheticWorldGenerator generator;
    private final InterventionLikelihoodEstimator estimator;
    private final ExpectedNetRecoveryValueEngine evEngine;
    private final PolicyEngine policyEngine;
    private final RecoveryDecisionService decisionService;
    private final TrueDecisionValueCalculator trueCalculator;
    private final TrueOracleEvaluator oracleEvaluator;

    public RealLlmAuditService(SyntheticWorldGenerator generator,
                               InterventionLikelihoodEstimator estimator,
                               ExpectedNetRecoveryValueEngine evEngine,
                               PolicyEngine policyEngine,
                               RecoveryDecisionService decisionService,
                               TrueDecisionValueCalculator trueCalculator,
                               TrueOracleEvaluator oracleEvaluator) {
        this.generator = generator;
        this.estimator = estimator;
        this.evEngine = evEngine;
        this.policyEngine = policyEngine;
        this.decisionService = decisionService;
        this.trueCalculator = trueCalculator;
        this.oracleEvaluator = oracleEvaluator;
    }

    public record AuditCaseResult(
            SyntheticCase syntheticCase,
            ObservableContext observable,
            AiAssessment syntheticAssessment,
            AiAssessment realLlmAssessment,
            boolean realLlmValid,
            boolean fallbackUsed,
            RecoveryActionType syntheticSelected,
            RecoveryActionType realLlmSelected,
            PolicyDecisionType syntheticPolicyDecision,
            PolicyDecisionType realLlmPolicyDecision,
            String estimatorResultSynthetic,
            String estimatorResultReal,
            List<String> evRankingSynthetic,
            List<String> evRankingReal,
            // Post-decision evaluator comparison (HiddenTruth only after)
            BigDecimal trueValueSynthetic,
            BigDecimal trueValueReal,
            BigDecimal oracleTrueValue,
            RecoveryActionType oracleAction,
            BigDecimal regretSynthetic,
            BigDecimal regretReal,
            String comparison // helped/hurt/neutral etc.
    ) {}

    public record AuditComparison(
            List<AuditCaseResult> cases,
            int syntheticValid,
            int realValid,
            int realFallbackCount,
            int agreementFailureCategory,
            int agreementRecommendedAction,
            int sameSelectedAction
    ) {}

    public AuditComparison runAudit(List<SyntheticCase> fixtures,
                                    AiDecisionProvider syntheticProvider,
                                    AiDecisionProvider realProvider) {
        List<AuditCaseResult> results = new ArrayList<>();
        int syntheticValid = 0, realValid = 0, fallback = 0, agreeCat = 0, agreeRec = 0, sameSelected = 0;

        for (SyntheticCase sc : fixtures) {
            ObservableContext obs = toObservableContext(sc.observable());

            // Both providers receive ONLY ObservableContext (no hidden)
            AiAssessment synth = syntheticProvider.assess(obs);
            AiAssessment real = realProvider.assess(obs);

            // Validate synthetic always valid (has 4 candidates etc.)
            boolean synthValid = isValid(synth);
            boolean realValidFlag = isValid(real);
            boolean isFallback = real.reasoningSummary() != null && real.reasoningSummary().contains("[fallback");
            if (synthValid) syntheticValid++;
            if (realValidFlag && !isFallback) realValid++;
            if (isFallback) fallback++;
            if (synth.failureCategory() == real.failureCategory()) agreeCat++;
            if (synth.recommendedAction() == real.recommendedAction()) agreeRec++;

            // Downstream pipeline identical for both
            var pSynth = estimator.estimate(obs, synth);
            var pReal = estimator.estimate(obs, real);
            var rankedSynth = evEngine.rank(sc.observable().amount(), pSynth, synth.riskLevel());
            var rankedReal = evEngine.rank(sc.observable().amount(), pReal, real.riskLevel());
            PolicyContext baseSynth = toPolicyContext(sc.observable(), rankedSynth.isEmpty() ? RecoveryActionType.RETRY_NOW : rankedSynth.get(0).action());
            PolicyContext baseReal = toPolicyContext(sc.observable(), rankedReal.isEmpty() ? RecoveryActionType.RETRY_NOW : rankedReal.get(0).action());
            var decisionSynth = decisionService.decide(obs, synth, sc.observable().amount(), baseSynth, Set.of());
            var decisionReal = decisionService.decide(obs, real, sc.observable().amount(), baseReal, Set.of());
            RecoveryActionType selSynth = decisionSynth.hasSelection() ? decisionSynth.selected().action() : null;
            RecoveryActionType selReal = decisionReal.hasSelection() ? decisionReal.selected().action() : null;
            if (java.util.Objects.equals(selSynth, selReal)) sameSelected++;

            PolicyDecisionType polSynth = decisionSynth.hasSelection() ? decisionSynth.selectedPolicyDecision().result() : null;
            PolicyDecisionType polReal = decisionReal.hasSelection() ? decisionReal.selectedPolicyDecision().result() : null;

            // Post-decision evaluator comparison (HiddenTruth only after)
            BigDecimal tvSynth = selSynth != null ? trueCalculator.calculate(selSynth, sc.observable().amount(), sc.pTrue().getOrDefault(selSynth, 0.0)) : null;
            BigDecimal tvReal = selReal != null ? trueCalculator.calculate(selReal, sc.observable().amount(), sc.pTrue().getOrDefault(selReal, 0.0)) : null;
            var oracleRes = oracleEvaluator.evaluate(sc, baseSynth); // same policy thresholds
            BigDecimal oracleVal = oracleRes.oracleTrueValue();
            RecoveryActionType oracleAct = oracleRes.oracleAction();
            BigDecimal regretSynth = computeRegret(oracleVal, tvSynth, selSynth, oracleAct);
            BigDecimal regretReal = computeRegret(oracleVal, tvReal, selReal, oracleAct);
            String comp;
            if (java.util.Objects.equals(selSynth, selReal)) comp = "SAME_ACTION";
            else {
                BigDecimal tvS = tvSynth != null ? tvSynth : BigDecimal.ZERO;
                BigDecimal tvR = tvReal != null ? tvReal : BigDecimal.ZERO;
                int cmp = tvR.compareTo(tvS);
                if (cmp > 0) comp = "REAL_HELPED";
                else if (cmp < 0) comp = "REAL_HURT";
                else comp = "REAL_NEUTRAL";
            }

            results.add(new AuditCaseResult(
                    sc, obs, synth, real, realValidFlag, isFallback,
                    selSynth, selReal, polSynth, polReal,
                    pSynth.toString(), pReal.toString(),
                    rankedSynth.stream().map(r -> r.action().name()).toList(),
                    rankedReal.stream().map(r -> r.action().name()).toList(),
                    tvSynth, tvReal, oracleVal, oracleAct, regretSynth, regretReal, comp
            ));
        }
        return new AuditComparison(results, syntheticValid, realValid, fallback, agreeCat, agreeRec, sameSelected);
    }

    public AuditComparison runAuditWithProviders(int fixtureSize, AiDecisionProvider synthetic, AiDecisionProvider real) {
        var fixtures = RealLlmAuditFixture.generate(generator, fixtureSize);
        return runAudit(fixtures, synthetic, real);
    }

    private boolean isValid(AiAssessment a) {
        try {
            return a != null && a.candidateAssessments() != null && a.candidateAssessments().size() == 4
                    && a.failureCategory() != null && a.recoverability() != null && a.evidenceQuality() != null && a.riskLevel() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private BigDecimal computeRegret(BigDecimal oracle, BigDecimal selected, RecoveryActionType selAct, RecoveryActionType oracleAct) {
        if (oracle == null) return BigDecimal.ZERO.setScale(4);
        if (selAct == null) return oracle.setScale(4, java.math.RoundingMode.HALF_UP);
        if (selAct == oracleAct) return BigDecimal.ZERO.setScale(4);
        BigDecimal sel = selected != null ? selected : BigDecimal.ZERO;
        BigDecimal diff = oracle.subtract(sel);
        if (diff.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO.setScale(4);
        return diff.setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private ObservableContext toObservableContext(com.recoverflow.synthetic.ObservableCase obs) {
        return new ObservableContext(
                obs.amount(), obs.currency(), obs.method(), obs.gatewayCode(),
                obs.elapsedHours(), obs.attemptCount(), obs.priorSuccessCount(), obs.priorFailureCount(), obs.linkAlreadySent());
    }

    private PolicyContext toPolicyContext(com.recoverflow.synthetic.ObservableCase obs, RecoveryActionType action) {
        return new PolicyContext(
                obs.amount(), obs.gatewayCode(), obs.attemptCount(), obs.elapsedHours(),
                false, obs.linkAlreadySent(), action,
                new BigDecimal("10000.0000"), 3, 48);
    }
}
