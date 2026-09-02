package com.recoverflow.evaluation;

import com.recoverflow.ai.AiAssessment;
import com.recoverflow.ai.CandidateAssessment;
import com.recoverflow.ai.CandidateAssessmentLevel;
import com.recoverflow.ai.EvidenceQuality;
import com.recoverflow.ai.FailureCategory;
import com.recoverflow.ai.Recoverability;
import com.recoverflow.ai.RiskLevel;
import com.recoverflow.decision.ExpectedNetRecoveryValueEngine;
import com.recoverflow.decision.RecoveryDecisionService;
import com.recoverflow.likelihood.InterventionLikelihoodEstimator;
import com.recoverflow.likelihood.ObservableContext;
import com.recoverflow.payment.PaymentMethod;
import com.recoverflow.policy.PolicyConfig;
import com.recoverflow.policy.PolicyContext;
import com.recoverflow.policy.PolicyDecisionType;
import com.recoverflow.policy.PolicyEngine;
import com.recoverflow.recovery.RecoveryActionType;
import com.recoverflow.synthetic.SyntheticAiProxy;
import com.recoverflow.synthetic.SyntheticCase;
import com.recoverflow.synthetic.SyntheticWorldGenerator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Runs evaluation over deterministic synthetic dataset for 3 methods:
 * Baseline A (naive), Baseline B (policy-only), RecoverFlow (AI-enabled).
 * Ablation is POLICY_ONLY vs AI_ENABLED on same hidden ground truth.
 * Prevents leakage: decision never receives HiddenTruth.
 */
@Component
public class EvaluationEngine {

    private final SyntheticWorldGenerator generator;
    private final InterventionLikelihoodEstimator estimator;
    private final ExpectedNetRecoveryValueEngine evEngine;
    private final PolicyEngine policyEngine;
    private final RecoveryDecisionService decisionService;
    private final PolicyConfig policyConfig;
    private final SyntheticAiProxy syntheticAiProxy;
    private final TrueDecisionValueCalculator trueCalculator;
    private final TrueOracleEvaluator oracleEvaluator;

    public EvaluationEngine(SyntheticWorldGenerator generator,
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
        this.syntheticAiProxy = syntheticAiProxy;
        this.trueCalculator = trueCalculator;
        this.oracleEvaluator = oracleEvaluator;
    }

    public EvaluationResult run(long seed, int datasetSize) {
        List<SyntheticCase> dataset = generator.generate(seed, datasetSize);
        return evaluateDataset(dataset, seed);
    }

    /**
     * Multi-seed evaluation foundation: runs same methodology across independent seeds.
     * Each seed generates its own world; all three strategies see same world per seed.
     * Structure allows 30 → 50+ via config only (pass larger list).
     */
    public MultiSeedResult runMultiSeed(List<Long> seeds, int datasetSizePerSeed) {
        if (seeds == null || seeds.isEmpty()) throw new IllegalArgumentException("seeds required");
        List<MultiSeedResult.PerSeedResult> perSeed = new ArrayList<>();
        List<BigDecimal> lifts = new ArrayList<>();
        List<BigDecimal> recoverFlowRevenues = new ArrayList<>();
        List<BigDecimal> baselineBRevenues = new ArrayList<>();
        int wins = 0, losses = 0, ties = 0;

        for (long seed : seeds) {
            List<SyntheticCase> dataset = generator.generate(seed, datasetSizePerSeed);
            EvaluationResult result = evaluateDataset(dataset, seed);
            BigDecimal baseB = result.baselineB().recovered();
            BigDecimal rec = result.recoverFlow().recovered();
            BigDecimal lift = computeAiLift(rec, baseB);
            lifts.add(lift);
            recoverFlowRevenues.add(rec);
            baselineBRevenues.add(baseB);
            int cmp = rec.compareTo(baseB);
            if (cmp > 0) wins++;
            else if (cmp < 0) losses++;
            else ties++;
            perSeed.add(new MultiSeedResult.PerSeedResult(seed, baseB, rec, lift));
        }

        BigDecimal meanRec = mean(recoverFlowRevenues);
        BigDecimal medianRec = median(recoverFlowRevenues);
        BigDecimal meanBaseB = mean(baselineBRevenues);
        BigDecimal meanLift = mean(lifts);
        BigDecimal medianLift = median(lifts);
        BigDecimal stdLift = stdDev(lifts, meanLift);
        BigDecimal minLift = lifts.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maxLift = lifts.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        return new MultiSeedResult(seeds.size(), perSeed, meanRec, medianRec, meanBaseB, meanLift, medianLift, stdLift, wins, losses, ties, minLift, maxLift);
    }

    private BigDecimal computeAiLift(BigDecimal recoverFlow, BigDecimal baselineB) {
        if (baselineB.compareTo(BigDecimal.ZERO) == 0) {
            if (recoverFlow.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
            // Baseline 0 but RecoverFlow >0: define as 1 (100% lift) to avoid division by zero, explicitly documented
            return BigDecimal.ONE;
        }
        return recoverFlow.subtract(baselineB).divide(baselineB, 4, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal mean(List<BigDecimal> vals) {
        if (vals.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = vals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(new BigDecimal(vals.size()), 4, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal median(List<BigDecimal> vals) {
        if (vals.isEmpty()) return BigDecimal.ZERO;
        List<BigDecimal> sorted = vals.stream().sorted().toList();
        int n = sorted.size();
        if (n % 2 == 1) return sorted.get(n / 2);
        BigDecimal a = sorted.get(n / 2 - 1);
        BigDecimal b = sorted.get(n / 2);
        return a.add(b).divide(new BigDecimal("2"), 4, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal stdDev(List<BigDecimal> vals, BigDecimal mean) {
        if (vals.isEmpty() || vals.size() == 1) return BigDecimal.ZERO;
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal v : vals) {
            BigDecimal diff = v.subtract(mean);
            variance = variance.add(diff.multiply(diff));
        }
        variance = variance.divide(new BigDecimal(vals.size()), 8, java.math.RoundingMode.HALF_UP);
        double std = Math.sqrt(variance.doubleValue());
        return new BigDecimal(std).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    public EvaluationResult evaluateDataset(List<SyntheticCase> dataset, long seed) {
        // Held-out: last 20% is final benchmark (not used for tuning)
        int heldOutStart = (int) (dataset.size() * 0.8);
        List<SyntheticCase> heldOut = dataset.subList(heldOutStart, dataset.size());

        MetricsHolder baselineA = evaluateBaselineA(dataset);
        MetricsHolder baselineB = evaluateBaselineB(dataset);
        MetricsHolder recoverFlow = evaluateRecoverFlow(dataset, seed);

        MetricsHolder baselineA_HeldOut = evaluateBaselineA(heldOut);
        MetricsHolder baselineB_HeldOut = evaluateBaselineB(heldOut);
        MetricsHolder recoverFlow_HeldOut = evaluateRecoverFlow(heldOut, seed);

        // Ablation: per-case comparison POLICY_ONLY vs AI_ENABLED on same dataset
        AblationSummary ablation = computeAblation(dataset, seed);

        return new EvaluationResult(dataset.size(), baselineA, baselineB, recoverFlow,
                baselineA_HeldOut, baselineB_HeldOut, recoverFlow_HeldOut, ablation,
                dataset, seed);
    }

    // Baseline A: naive fixed retry (RETRY_NOW if not at limit/window, else STOP)
    private MetricsHolder evaluateBaselineA(List<SyntheticCase> dataset) {
        BigDecimal revenueAtRisk = BigDecimal.ZERO;
        BigDecimal recovered = BigDecimal.ZERO;
        BigDecimal friction = BigDecimal.ZERO;
        int attempts = 0, successes = 0, escalations = 0, stopped = 0, failedTerminal = 0, policyBlocks = 0;

        for (SyntheticCase sc : dataset) {
            revenueAtRisk = revenueAtRisk.add(sc.observable().amount());
            // Naive: always try RETRY_NOW if attemptCount <3 and elapsed <=48 and not CARD_EXPIRED
            String gateway = sc.observable().gatewayCode();
            int attempt = sc.observable().attemptCount();
            int elapsed = sc.observable().elapsedHours();
            boolean isPermanent = "CARD_EXPIRED".equals(gateway) || "AUTH_FAILED".equals(gateway);
            boolean canRetry = attempt < 3 && elapsed <= 48 && !isPermanent;
            if (!canRetry) {
                // Naive would stop/escalate? For simplicity, count as stopped if permanent/window, else not attempt
                if (isPermanent || elapsed > 48 || attempt >= 3) stopped++;
                continue;
            }
            attempts++;
            // Check ground truth for RETRY_NOW
            boolean outcome = sc.groundTruthOutcomes().getOrDefault(RecoveryActionType.RETRY_NOW, false);
            if (outcome) {
                recovered = recovered.add(sc.observable().amount());
                successes++;
            } else {
                // Naive failure -> no second candidate, just failed
                failedTerminal++;
            }
            // Friction: RETRY_NOW 50
            friction = friction.add(new BigDecimal("50.00"));
        }
        return new MetricsHolder(revenueAtRisk, recovered, attempts, successes, escalations, stopped, failedTerminal, friction, policyBlocks);
    }

    private MetricsHolder evaluateBaselineB(List<SyntheticCase> dataset) {
        BigDecimal revenueAtRisk = BigDecimal.ZERO;
        BigDecimal recovered = BigDecimal.ZERO;
        BigDecimal friction = BigDecimal.ZERO;
        int attempts = 0, successes = 0, escalations = 0, stopped = 0, failedTerminal = 0, policyBlocks = 0;

        for (SyntheticCase sc : dataset) {
            revenueAtRisk = revenueAtRisk.add(sc.observable().amount());
            ObservableContext obs = toObservableContext(sc.observable());
            var pObs = estimator.estimateObservableOnly(obs);
            var ranked = evEngine.rank(sc.observable().amount(), pObs, RiskLevel.LOW);
            // Build base policy context
            PolicyContext base = toPolicyContext(sc.observable(), ranked.get(0).action());
            var decision = decisionService.decide(obs, null, sc.observable().amount(), base, Set.of());
            if (!decision.hasSelection()) {
                if (decision.selectedPolicyDecision() != null && decision.selectedPolicyDecision().result() == PolicyDecisionType.ESCALATE) escalations++;
                else stopped++;
                continue;
            }
            // Check if policy blocked highest EV
            if (decision.policyDecisions().get(0).result() != PolicyDecisionType.ALLOWED) policyBlocks++;
            RecoveryActionType selected = decision.selected().action();
            attempts++;
            boolean outcome = sc.groundTruthOutcomes().getOrDefault(selected, false);
            // Friction per selected
            BigDecimal f = evEngine.frictionFor(selected);
            friction = friction.add(f);
            if (outcome) {
                recovered = recovered.add(sc.observable().amount());
                successes++;
            } else {
                // Check if next would be terminal? For simplicity count as failedTerminal if no more candidates
                failedTerminal++;
            }
            if (decision.selectedPolicyDecision().result() == PolicyDecisionType.ESCALATE) escalations++;
            else if (decision.selectedPolicyDecision().result() == PolicyDecisionType.STOP) stopped++;
        }
        return new MetricsHolder(revenueAtRisk, recovered, attempts, successes, escalations, stopped, failedTerminal, friction, policyBlocks);
    }

    private MetricsHolder evaluateRecoverFlow(List<SyntheticCase> dataset, long seed) {
        BigDecimal revenueAtRisk = BigDecimal.ZERO;
        BigDecimal recovered = BigDecimal.ZERO;
        BigDecimal friction = BigDecimal.ZERO;
        int attempts = 0, successes = 0, escalations = 0, stopped = 0, failedTerminal = 0, policyBlocks = 0;

        for (SyntheticCase sc : dataset) {
            revenueAtRisk = revenueAtRisk.add(sc.observable().amount());
            ObservableContext obs = toObservableContext(sc.observable());
            AiAssessment ai = generateSyntheticAi(obs);
            var pEst = estimator.estimate(obs, ai);
            var ranked = evEngine.rank(sc.observable().amount(), pEst, ai.riskLevel());
            PolicyContext base = toPolicyContext(sc.observable(), ranked.isEmpty() ? RecoveryActionType.RETRY_NOW : ranked.get(0).action());
            var decision = decisionService.decide(obs, ai, sc.observable().amount(), base, Set.of());
            if (!decision.hasSelection()) {
                if (decision.selectedPolicyDecision() != null && decision.selectedPolicyDecision().result() == PolicyDecisionType.ESCALATE) escalations++;
                else stopped++;
                continue;
            }
            if (decision.policyDecisions().get(0).result() != PolicyDecisionType.ALLOWED) policyBlocks++;
            RecoveryActionType selected = decision.selected().action();
            attempts++;
            boolean outcome = sc.groundTruthOutcomes().getOrDefault(selected, false);
            friction = friction.add(evEngine.frictionFor(selected));
            if (outcome) {
                recovered = recovered.add(sc.observable().amount());
                successes++;
            } else {
                failedTerminal++;
            }
        }
        return new MetricsHolder(revenueAtRisk, recovered, attempts, successes, escalations, stopped, failedTerminal, friction, policyBlocks);
    }

    private AblationSummary computeAblation(List<SyntheticCase> dataset, long seed) {
        int changed = 0, helped = 0, hurt = 0, total = dataset.size();
        List<PerCaseAblation> perCase = new ArrayList<>();

        for (SyntheticCase sc : dataset) {
            ObservableContext obs = toObservableContext(sc.observable());
            AiAssessment ai = generateSyntheticAi(obs);
            var pObs = estimator.estimateObservableOnly(obs);
            var pAi = estimator.estimate(obs, ai);
            PolicyContext baseObs = toPolicyContext(sc.observable(), RecoveryActionType.RETRY_NOW);
            PolicyContext baseAi = toPolicyContext(sc.observable(), RecoveryActionType.RETRY_NOW);

            var decisionObs = decisionService.decide(obs, null, sc.observable().amount(), baseObs, Set.of());
            var decisionAi = decisionService.decide(obs, ai, sc.observable().amount(), baseAi, Set.of());

            RecoveryActionType selectedObs = decisionObs.hasSelection() ? decisionObs.selected().action() : null;
            RecoveryActionType selectedAi = decisionAi.hasSelection() ? decisionAi.selected().action() : null;

            boolean changedFlag = !java.util.Objects.equals(selectedObs, selectedAi);
            if (changedFlag) changed++;

            boolean outcomeObs = selectedObs != null && sc.groundTruthOutcomes().getOrDefault(selectedObs, false);
            boolean outcomeAi = selectedAi != null && sc.groundTruthOutcomes().getOrDefault(selectedAi, false);

            boolean helpedFlag = changedFlag && outcomeAi && !outcomeObs;
            boolean hurtFlag = changedFlag && outcomeObs && !outcomeAi;
            if (helpedFlag) helped++;
            if (hurtFlag) hurt++;

            boolean isWrongAi = !ai.failureCategory().name().equals(sc.hiddenTruth().trueFailureCategory().name());

            // --- Evaluator-only true-value computation (after decisions, never before) ---
            // Use same policy thresholds for oracle as for practical (evaluation-wide 10000/3/48 via toPolicyContext)
            // Compute true values for selected actions and oracle — canonical BigDecimal, not String
            PolicyContext baseForOracle = toPolicyContext(sc.observable(), RecoveryActionType.RETRY_NOW);
            var oracleRes = new TrueOracleEvaluator(policyEngine, trueCalculator).evaluate(sc, baseForOracle);
            String oracleActionStr = oracleRes.oracleAction() != null ? oracleRes.oracleAction().name() : "NONE";
            BigDecimal oracleTrueVal = oracleRes.oracleTrueValue(); // BigDecimal canonical, null if no oracle

            BigDecimal trueValObs = selectedObs != null ? trueCalculator.calculate(selectedObs, sc.observable().amount(), sc.pTrue().getOrDefault(selectedObs, 0.0)) : null;
            BigDecimal trueValAi = selectedAi != null ? trueCalculator.calculate(selectedAi, sc.observable().amount(), sc.pTrue().getOrDefault(selectedAi, 0.0)) : null;
            // Keep BigDecimal canonical; string display would be derived via toPlainString() if needed

            // Regret via StrategyDecisionQuality (evaluator-only)
            BigDecimal regretObsVal = null;
            BigDecimal regretAiVal = null;
            try {
                var qObs = evaluateQuality(sc, selectedObs);
                var qAi = evaluateQuality(sc, selectedAi);
                regretObsVal = qObs.decisionRegret();
                regretAiVal = qAi.decisionRegret();
            } catch (Exception ignored) {}

            // AI help/hurt via TRUE expected value (not Bernoulli outcome), using pure classifier
            var classification = AiHelpHurtClassifier.classify(selectedObs, selectedAi, trueValObs, trueValAi);
            boolean aiHelpedTrue = classification.helped();
            boolean aiHurtTrue = classification.hurt();
            boolean aiNeutralTrue = classification.neutral();
            boolean actionChangedTrue = classification.actionChanged();

            perCase.add(new PerCaseAblation(
                    sc.caseId().toString(), sc.observable().gatewayCode(), sc.hiddenTruth().trueFailureCategory().name(),
                    ai.failureCategory().name(), isWrongAi,
                    selectedObs != null ? selectedObs.name() : "NONE", selectedAi != null ? selectedAi.name() : "NONE",
                    pObs.toString(), pAi.toString(),
                    outcomeObs, outcomeAi, changedFlag, helpedFlag, hurtFlag,
                    ai.evidenceQuality().name(),
                    trueValObs, trueValAi, oracleActionStr, oracleTrueVal, regretObsVal, regretAiVal,
                    aiHelpedTrue, aiHurtTrue, aiNeutralTrue, actionChangedTrue));
        }
        return new AblationSummary(changed, helped, hurt, total, perCase);
    }

    private ObservableContext toObservableContext(com.recoverflow.synthetic.ObservableCase obs) {
        return new ObservableContext(
                obs.amount(), obs.currency(), obs.method(), obs.gatewayCode(),
                obs.elapsedHours(), obs.attemptCount(), obs.priorSuccessCount(), obs.priorFailureCount(), obs.linkAlreadySent());
    }

    private PolicyContext toPolicyContext(com.recoverflow.synthetic.ObservableCase obs, RecoveryActionType action) {
        // For synthetic, merchant thresholds are fixed as in PolicyConfig defaults
        return new PolicyContext(
                obs.amount(), obs.gatewayCode(), obs.attemptCount(), obs.elapsedHours(),
                false, obs.linkAlreadySent(), action,
                new BigDecimal("10000.0000"), 3, 48);
    }

    /**
     * Synthetic AI proxy — observable-only, versioned, never reads HiddenTruth.
     * Uses SyntheticAiProxy (synthetic-ai-v1) which maps observable gateway, amount bucket, history, elapsed to qualitative assessment.
     * This is the deterministic proxy for large-scale benchmark; real LLM mode uses same ObservableContext and same schema.
     */
    private AiAssessment generateSyntheticAi(ObservableContext obs) {
        return syntheticAiProxy.assess(obs);
    }

    /**
     * Evaluator-only helper: compute StrategyDecisionQuality for a single case and selected action.
     * Must be called ONLY AFTER the strategy has selected an action (decision already made).
     * Uses HiddenTruth/P_true via TrueDecisionValueCalculator and TrueOracleEvaluator, never passed into decision path.
     * Handles no-action semantics: if selected is null, selectedTrueValue is 0, oracle may still exist.
     */
    StrategyDecisionQuality evaluateQuality(SyntheticCase sc, RecoveryActionType selectedAction) {
        // Build base policy context for oracle (same thresholds as practical)
        PolicyContext base = new PolicyContext(
                sc.observable().amount(), sc.observable().gatewayCode(), sc.observable().attemptCount(), sc.observable().elapsedHours(),
                false, sc.observable().linkAlreadySent(), RecoveryActionType.RETRY_NOW,
                policyConfig.getAutoActionLimit(), policyConfig.getMaxRetries(), policyConfig.getRecoveryWindowHours());
        // Oracle is evaluator-only, uses hidden P_true
        var oracleRes = oracleEvaluator.evaluate(sc, base);
        RecoveryActionType oracleAction = oracleRes.oracleAction();
        BigDecimal oracleTrueValue = oracleRes.oracleTrueValue();
        // Selected true value
        BigDecimal selectedTrueValue = null;
        if (selectedAction != null) {
            Double pTrue = sc.pTrue().get(selectedAction);
            if (pTrue != null) {
                selectedTrueValue = trueCalculator.calculate(selectedAction, sc.observable().amount(), pTrue);
            }
        }
        // Normalize nulls to 0 for regret calculation, but keep record null for explicit no-action semantics
        BigDecimal selectedForRegret = selectedTrueValue != null ? selectedTrueValue : BigDecimal.ZERO;
        BigDecimal oracleForRegret = oracleTrueValue != null ? oracleTrueValue : BigDecimal.ZERO;
        BigDecimal regret;
        if (oracleTrueValue == null) {
            // No permissible oracle -> regret 0
            regret = BigDecimal.ZERO;
        } else if (selectedAction == null) {
            // Strategy has no action but oracle exists -> regret = oracleTrueValue
            regret = oracleTrueValue;
        } else if (selectedAction == oracleAction) {
            regret = BigDecimal.ZERO;
        } else {
            BigDecimal diff = oracleForRegret.subtract(selectedForRegret);
            regret = diff.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : diff;
        }
        regret = regret.setScale(4, java.math.RoundingMode.HALF_UP);
        // For record, keep null true values as null to preserve no-action semantics, but regret as above
        return new StrategyDecisionQuality(selectedAction, oracleAction, selectedTrueValue, oracleTrueValue, regret);
    }

    public record MetricsHolder(
            BigDecimal revenueAtRisk,
            BigDecimal recovered,
            int attempts,
            int successes,
            int escalations,
            int stopped,
            int failedTerminal,
            BigDecimal friction,
            int policyBlocks
    ) {}

    public record PerCaseAblation(
            String caseId,
            String observedGateway,
            String trueCategory,
            String aiCategory,
            boolean wrongAi,
            String selectedPolicyOnly,
            String selectedAi,
            String pObs,
            String pAi,
            boolean outcomePolicyOnly,
            boolean outcomeAi,
            boolean changed,
            boolean helped,
            boolean hurt,
            String evidenceQuality,
            // New true-value fields (evaluator-only, after decision) — canonical BigDecimal
            BigDecimal trueValuePolicyOnly,
            BigDecimal trueValueAi,
            String oracleAction,
            BigDecimal oracleTrueValue,
            BigDecimal regretPolicyOnly,
            BigDecimal regretAi,
            boolean aiHelpedTrue,
            boolean aiHurtTrue,
            boolean aiNeutralTrue,
            boolean actionChangedTrue
    ) {}

    public record AblationSummary(
            int changed,
            int helped,
            int hurt,
            int total,
            List<PerCaseAblation> perCase
    ) {}

    public record EvaluationResult(
            int datasetSize,
            MetricsHolder baselineA,
            MetricsHolder baselineB,
            MetricsHolder recoverFlow,
            MetricsHolder baselineA_HeldOut,
            MetricsHolder baselineB_HeldOut,
            MetricsHolder recoverFlow_HeldOut,
            AblationSummary ablation,
            List<SyntheticCase> dataset,
            long seed
    ) {}
}
