package com.recoverflow.evaluation;

import com.recoverflow.ai.AiAssessment;
import com.recoverflow.decision.ExpectedNetRecoveryValueEngine;
import com.recoverflow.decision.RecoveryDecisionService;
import com.recoverflow.likelihood.InterventionLikelihoodEstimator;
import com.recoverflow.likelihood.ObservableContext;
import com.recoverflow.policy.PolicyConfig;
import com.recoverflow.policy.PolicyContext;
import com.recoverflow.policy.PolicyDecisionType;
import com.recoverflow.policy.PolicyEngine;
import com.recoverflow.recovery.RecoveryActionType;
import com.recoverflow.synthetic.AiQuality;
import com.recoverflow.synthetic.QualityAwareSyntheticAiProxy;
import com.recoverflow.synthetic.SyntheticAiProxy;
import com.recoverflow.synthetic.SyntheticCase;
import com.recoverflow.synthetic.SyntheticWorldGenerator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * AI-quality sanity experiment: compares LOW/MEDIUM/HIGH synthetic AI quality
 * on the SAME hidden worlds, seeds, cases, P_true, groundTruth, policy, estimator, EV.
 * Only AI proxy quality changes. Uses development/validation seeds, never held-out.
 * Measured true-category accuracies are approximately 44%/65%/77% (LOW/MEDIUM/HIGH)
 * due to noisy observable gateway (85% correlated with hidden truth); proxy targets
 * 50%/75%/90% against observable gateway. Exact measured accuracy must be reported
 * from experiment output, not assumed.
 */
@Component
public class AiQualityExperiment {

    private final SyntheticWorldGenerator generator;
    private final InterventionLikelihoodEstimator estimator;
    private final ExpectedNetRecoveryValueEngine evEngine;
    private final PolicyEngine policyEngine;
    private final RecoveryDecisionService decisionService;
    private final PolicyConfig policyConfig;
    private final TrueDecisionValueCalculator trueCalculator;
    private final TrueOracleEvaluator oracleEvaluator;
    private final QualityAwareSyntheticAiProxy qualityProxy = new QualityAwareSyntheticAiProxy();

    public AiQualityExperiment(SyntheticWorldGenerator generator,
                               InterventionLikelihoodEstimator estimator,
                               ExpectedNetRecoveryValueEngine evEngine,
                               PolicyEngine policyEngine,
                               RecoveryDecisionService decisionService,
                               PolicyConfig policyConfig,
                               SyntheticAiProxy syntheticAiProxy,
                               TrueDecisionValueCalculator trueCalculator,
                               TrueOracleEvaluator oracleEvaluator) {
        this.generator = generator;
        this.estimator = estimator;
        this.evEngine = evEngine;
        this.policyEngine = policyEngine;
        this.decisionService = decisionService;
        this.policyConfig = policyConfig;
        this.trueCalculator = trueCalculator;
        this.oracleEvaluator = oracleEvaluator;
    }

    public record QualityResult(
            AiQuality quality,
            double measuredAccuracy,
            BigDecimal recoveredRevenue,
            BigDecimal revenueDeltaVsPolicy,
            BigDecimal relativeRevenueLift,
            EvaluationMetricsAggregator.DecisionQualityMetrics decisionQuality,
            EvaluationMetricsAggregator.AiDecisionMetrics aiDecision,
            int totalCases
    ) {}

    public record ExperimentResult(
            Map<AiQuality, QualityResult> byQuality,
            // Policy-only baseline for reference (same worlds)
            BigDecimal policyOnlyRecovered,
            EvaluationMetricsAggregator.DecisionQualityMetrics policyOnlyDecisionQuality,
            List<SyntheticCase> allCases // for same-world proof
    ) {}

    public ExperimentResult run(List<Long> seeds, int datasetSizePerSeed) {
        if (seeds == null || seeds.isEmpty()) throw new IllegalArgumentException("seeds required");
        // Generate all worlds once, reuse for all quality levels (fairness)
        List<List<SyntheticCase>> allWorlds = new ArrayList<>();
        List<SyntheticCase> flatCases = new ArrayList<>();
        for (long seed : seeds) {
            List<SyntheticCase> dataset = generator.generate(seed, datasetSizePerSeed);
            allWorlds.add(dataset);
            flatCases.addAll(dataset);
        }

        // Policy-only baseline (same worlds, no AI)
        BigDecimal policyRecovered = BigDecimal.ZERO;
        List<BigDecimal> policyRegrets = new ArrayList<>();
        List<BigDecimal> policySelectedTrue = new ArrayList<>();
        List<BigDecimal> oracleVals = new ArrayList<>();
        for (var dataset : allWorlds) {
            for (SyntheticCase sc : dataset) {
                ObservableContext obs = toObservableContext(sc.observable());
                var pObs = estimator.estimateObservableOnly(obs);
                var ranked = evEngine.rank(sc.observable().amount(), pObs, com.recoverflow.ai.RiskLevel.LOW);
                PolicyContext base = toPolicyContext(sc.observable(), ranked.get(0).action());
                var decision = decisionService.decide(obs, null, sc.observable().amount(), base, Set.of());
                RecoveryActionType selected = decision.hasSelection() ? decision.selected().action() : null;
                // Evaluate quality after decision (evaluator-only)
                var q = evaluateQuality(sc, selected);
                if (q.selectedTrueValue() != null) policySelectedTrue.add(q.selectedTrueValue());
                if (q.oracleTrueValue() != null) oracleVals.add(q.oracleTrueValue());
                policyRegrets.add(q.decisionRegret());
                // Revenue: check outcome
                if (selected != null && sc.groundTruthOutcomes().getOrDefault(selected, false)) {
                    policyRecovered = policyRecovered.add(sc.observable().amount());
                }
            }
        }
        var policyDQ = EvaluationMetricsAggregator.aggregateDecisionQuality(
                policyRegrets, policyRegrets, policySelectedTrue, policySelectedTrue, oracleVals);

        Map<AiQuality, QualityResult> byQuality = new EnumMap<>(AiQuality.class);
        for (AiQuality quality : AiQuality.values()) {
            int correct = 0;
            int total = 0;
            BigDecimal recovered = BigDecimal.ZERO;
            List<BigDecimal> regrets = new ArrayList<>();
            List<BigDecimal> selectedTrueList = new ArrayList<>();
            List<BigDecimal> oracleList = new ArrayList<>();
            List<BigDecimal> policyRegretsForDelta = new ArrayList<>(policyRegrets); // same as above, but per quality we need policy regrets for comparison? Actually decisionQuality for quality level vs policy
            // For AI metrics
            int changed = 0, helped = 0, hurt = 0, neutral = 0;

            // For each case, generate AI with quality, evaluate both policy-only and AI decisions on SAME world
            for (var dataset : allWorlds) {
                for (SyntheticCase sc : dataset) {
                    ObservableContext obs = toObservableContext(sc.observable());
                    AiAssessment ai = qualityProxy.assess(obs, quality);
                    // Measure accuracy: failureCategory
                    if (ai.failureCategory().name().equals(sc.hiddenTruth().trueFailureCategory().name())) correct++;
                    total++;

                    // Policy-only decision (same as baseline)
                    var pObs = estimator.estimateObservableOnly(obs);
                    var rankedObs = evEngine.rank(sc.observable().amount(), pObs, com.recoverflow.ai.RiskLevel.LOW);
                    PolicyContext baseObs = toPolicyContext(sc.observable(), rankedObs.get(0).action());
                    var decisionObs = decisionService.decide(obs, null, sc.observable().amount(), baseObs, Set.of());
                    RecoveryActionType selObs = decisionObs.hasSelection() ? decisionObs.selected().action() : null;

                    // AI decision
                    var pAi = estimator.estimate(obs, ai);
                    var rankedAi = evEngine.rank(sc.observable().amount(), pAi, ai.riskLevel());
                    PolicyContext baseAi = toPolicyContext(sc.observable(), rankedAi.isEmpty() ? RecoveryActionType.RETRY_NOW : rankedAi.get(0).action());
                    var decisionAi = decisionService.decide(obs, ai, sc.observable().amount(), baseAi, Set.of());
                    RecoveryActionType selAi = decisionAi.hasSelection() ? decisionAi.selected().action() : null;

                    // True values and regret for AI decision (after decision)
                    var qAi = evaluateQuality(sc, selAi);
                    var qObs = evaluateQuality(sc, selObs);
                    if (qAi.selectedTrueValue() != null) selectedTrueList.add(qAi.selectedTrueValue());
                    if (qAi.oracleTrueValue() != null) oracleList.add(qAi.oracleTrueValue());
                    regrets.add(qAi.decisionRegret());

                    // Revenue for AI
                    if (selAi != null && sc.groundTruthOutcomes().getOrDefault(selAi, false)) {
                        recovered = recovered.add(sc.observable().amount());
                    }

                    // AI help/hurt via true value (after decision)
                    BigDecimal tvObs = selObs != null ? trueCalculator.calculate(selObs, sc.observable().amount(), sc.pTrue().getOrDefault(selObs, 0.0)) : null;
                    BigDecimal tvAi = selAi != null ? trueCalculator.calculate(selAi, sc.observable().amount(), sc.pTrue().getOrDefault(selAi, 0.0)) : null;
                    var cls = AiHelpHurtClassifier.classify(selObs, selAi, tvObs, tvAi);
                    if (cls.actionChanged()) changed++;
                    if (cls.helped()) helped++;
                    if (cls.hurt()) hurt++;
                    if (cls.actionChanged() && cls.neutral()) neutral++;
                }
            }
            double measuredAccuracy = total == 0 ? 0 : (double) correct / total;
            BigDecimal delta = recovered.subtract(policyRecovered);
            BigDecimal relLift = policyRecovered.compareTo(BigDecimal.ZERO) == 0 ? null : delta.divide(policyRecovered, 4, RoundingMode.HALF_UP);
            // Decision quality for this quality level: need policy vs recover comparison, but we already have policyRegrets vs regrets
            // For DecisionQualityMetrics, we need policyOnly and recoverFlow lists
            // Use previously collected policyRegrets (from policy-only baseline) and regrets (AI quality)
            // But policyRegrets we collected earlier is for all cases, same as policyRegretsForDelta
            var dq = EvaluationMetricsAggregator.aggregateDecisionQuality(
                    policyRegrets, regrets, policySelectedTrue, selectedTrueList, oracleList);
            var aiDecision = EvaluationMetricsAggregator.aggregateAiDecisions(total, changed, helped, hurt, neutral);
            // Canonical scale for revenue
            recovered = recovered.setScale(4, RoundingMode.HALF_UP);
            policyRecovered = policyRecovered.setScale(4, RoundingMode.HALF_UP);
            byQuality.put(quality, new QualityResult(quality, measuredAccuracy, recovered, delta.setScale(4, RoundingMode.HALF_UP), relLift, dq, aiDecision, total));
        }

        return new ExperimentResult(byQuality, policyRecovered, policyDQ, flatCases);
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

    StrategyDecisionQuality evaluateQuality(SyntheticCase sc, RecoveryActionType selectedAction) {
        PolicyContext base = new PolicyContext(
                sc.observable().amount(), sc.observable().gatewayCode(), sc.observable().attemptCount(), sc.observable().elapsedHours(),
                false, sc.observable().linkAlreadySent(), RecoveryActionType.RETRY_NOW,
                policyConfig.getAutoActionLimit(), policyConfig.getMaxRetries(), policyConfig.getRecoveryWindowHours());
        var oracleRes = oracleEvaluator.evaluate(sc, base);
        RecoveryActionType oracleAction = oracleRes.oracleAction();
        BigDecimal oracleTrueValue = oracleRes.oracleTrueValue();
        BigDecimal selectedTrueValue = null;
        if (selectedAction != null) {
            Double pTrue = sc.pTrue().get(selectedAction);
            if (pTrue != null) {
                selectedTrueValue = trueCalculator.calculate(selectedAction, sc.observable().amount(), pTrue);
            }
        }
        BigDecimal selectedForRegret = selectedTrueValue != null ? selectedTrueValue : BigDecimal.ZERO;
        BigDecimal oracleForRegret = oracleTrueValue != null ? oracleTrueValue : BigDecimal.ZERO;
        BigDecimal regret;
        if (oracleTrueValue == null) {
            regret = BigDecimal.ZERO;
        } else if (selectedAction == null) {
            regret = oracleTrueValue;
        } else if (selectedAction == oracleAction) {
            regret = BigDecimal.ZERO;
        } else {
            BigDecimal diff = oracleForRegret.subtract(selectedForRegret);
            regret = diff.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : diff;
        }
        regret = regret.setScale(4, RoundingMode.HALF_UP);
        return new StrategyDecisionQuality(selectedAction, oracleAction, selectedTrueValue, oracleTrueValue, regret);
    }
}
