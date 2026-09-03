package com.recoverflow.analytics;

import com.recoverflow.evaluation.AiQualityExperiment;
import com.recoverflow.evaluation.EvaluationEngine;
import com.recoverflow.evaluation.EvaluationMetricsAggregator;
import com.recoverflow.evaluation.EvaluationPartitions;
import com.recoverflow.evaluation.MultiSeedResult;
import com.recoverflow.synthetic.AiQuality;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analytics evidence endpoint - exposes deterministic synthetic evaluation aggregates
 * for /analytics page. No evaluation logic here; delegates to EvaluationEngine and EvaluationPartitions.
 * All data is SYNTHETIC EVALUATION, never real merchant revenue.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final EvaluationEngine engine;
    private final AiQualityExperiment aiQualityExperiment;

    public AnalyticsController(EvaluationEngine engine, AiQualityExperiment aiQualityExperiment) {
        this.engine = engine;
        this.aiQualityExperiment = aiQualityExperiment;
    }

    @GetMapping
    public Map<String, Object> analytics() {
        // Primary development partition is the main evidence (30 seeds, 200 per seed = 6000 cases)
        MultiSeedResult development = engine.runMultiSeed(EvaluationPartitions.DEVELOPMENT, EvaluationPartitions.DATASET_SIZE_PER_SEED);
        MultiSeedResult heldOut = engine.runMultiSeed(EvaluationPartitions.HELD_OUT, EvaluationPartitions.DATASET_SIZE_PER_SEED);
        MultiSeedResult validation = engine.runMultiSeed(EvaluationPartitions.VALIDATION, EvaluationPartitions.DATASET_SIZE_PER_SEED);

        // Totals for baselines (sum across perSeed)
        BigDecimal totalBaselineADev = sumBaselineA(development);
        BigDecimal totalBaselineBDev = sumBaselineB(development);
        BigDecimal totalRecoverFlowDev = development.aggregatedRevenue().recoveredRevenue();

        BigDecimal totalBaselineAHeld = sumBaselineA(heldOut);
        BigDecimal totalBaselineBHeld = sumBaselineB(heldOut);
        BigDecimal totalRecoverFlowHeld = heldOut.aggregatedRevenue().recoveredRevenue();

        Map<String, Object> methodology = new LinkedHashMap<>();
        methodology.put("evaluatorVersion", EvaluationPartitions.EVALUATOR_VERSION);
        methodology.put("estimatorVersion", EvaluationPartitions.ESTIMATOR_VERSION);
        methodology.put("policyVersion", EvaluationPartitions.POLICY_VERSION);
        methodology.put("syntheticAiProxyVersion", EvaluationPartitions.SYNTHETIC_AI_PROXY_VERSION);
        methodology.put("trueValueVersion", EvaluationPartitions.TRUE_VALUE_VERSION);
        methodology.put("syntheticRegistryVersion", EvaluationPartitions.SYNTHETIC_WORLD_VERSION);
        methodology.put("evVersion", "ev-v1");
        methodology.put("decisionVersion", "decision-v1");
        methodology.put("datasetSizePerSeed", EvaluationPartitions.DATASET_SIZE_PER_SEED);
        methodology.put("datasetSizeTotalDevelopment", EvaluationPartitions.DEVELOPMENT.size() * EvaluationPartitions.DATASET_SIZE_PER_SEED);
        methodology.put("datasetSizeTotalHeldOut", EvaluationPartitions.HELD_OUT.size() * EvaluationPartitions.DATASET_SIZE_PER_SEED);
        methodology.put("datasetSizeTotalValidation", EvaluationPartitions.VALIDATION.size() * EvaluationPartitions.DATASET_SIZE_PER_SEED);
        methodology.put("seedInfo", "Development 30 seeds 10000-10029, Validation 10 seeds 20000-20009, Held-out 10 seeds 30000-30009, 200 cases per seed");
        methodology.put("developmentSeeds", "10000-10029");
        methodology.put("validationSeeds", "20000-20009");
        methodology.put("heldOutSeeds", "30000-30009");
        methodology.put("versionSnapshot", EvaluationPartitions.versionSnapshot());

        Map<String, Object> revenue = toRevenueMap(development.aggregatedRevenue(), totalBaselineADev, totalBaselineBDev);
        Map<String, Object> decisionQuality = toDecisionQualityMap(development.decisionQuality());
        Map<String, Object> aiBehavior = toAiBehaviorMap(development.aiDecision(), development.aggregatedRevenue().attempts());

        Map<String, Object> revenueHeld = toRevenueMap(heldOut.aggregatedRevenue(), totalBaselineAHeld, totalBaselineBHeld);
        Map<String, Object> dqHeld = toDecisionQualityMap(heldOut.decisionQuality());
        Map<String, Object> aiHeld = toAiBehaviorMap(heldOut.aiDecision(), heldOut.aggregatedRevenue().attempts());

        Map<String, Object> revenueVal = toRevenueMap(validation.aggregatedRevenue(), sumBaselineA(validation), sumBaselineB(validation));
        Map<String, Object> dqVal = toDecisionQualityMap(validation.decisionQuality());
        Map<String, Object> aiVal = toAiBehaviorMap(validation.aiDecision(), validation.aggregatedRevenue().attempts());

        // Comparison for chart: Baseline A, B, RecoverFlow (actual recoveredRevenue totals)
        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("baselineA", totalBaselineADev);
        comparison.put("baselineB", totalBaselineBDev);
        comparison.put("recoverFlow", totalRecoverFlowDev);
        comparison.put("oracleMeanTrueValue", development.decisionQuality().meanOracleTrueValue());
        comparison.put("revenueAtRisk", development.aggregatedRevenue().revenueAtRisk());

        Map<String, Object> heldOutMap = new LinkedHashMap<>();
        heldOutMap.put("syntheticLabel", "HELD-OUT SYNTHETIC EVALUATION");
        heldOutMap.put("note", "Seed-level held-out partition not used for tuning.");
        heldOutMap.put("revenue", revenueHeld);
        heldOutMap.put("decisionQuality", dqHeld);
        heldOutMap.put("aiBehavior", aiHeld);
        heldOutMap.put("comparison", Map.of(
                "baselineA", totalBaselineAHeld,
                "baselineB", totalBaselineBHeld,
                "recoverFlow", totalRecoverFlowHeld,
                "oracleMeanTrueValue", heldOut.decisionQuality().meanOracleTrueValue()
        ));
        heldOutMap.put("seedInfo", "10 seeds 30000-30009, 200 per seed = 2000 cases");
        heldOutMap.put("totalCases", heldOut.aggregatedRevenue().attempts());

        // Partitions detailed maps for /evaluation (each with full metrics)
        Map<String, Object> devPartition = toPartitionMap(development, "DEVELOPMENT");
        Map<String, Object> valPartition = toPartitionMap(validation, "VALIDATION");
        Map<String, Object> heldPartition = toPartitionMap(heldOut, "HELD_OUT");

        Map<String, Object> partitions = new LinkedHashMap<>();
        partitions.put("DEVELOPMENT", devPartition);
        partitions.put("VALIDATION", valPartition);
        partitions.put("HELD_OUT", heldPartition);

        // AI quality experiment — same worlds, only proxy quality varies (LOW/MEDIUM/HIGH ~44/65/77 measured)
        Map<String, Object> aiQuality = buildAiQualityMap();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("syntheticLabel", "SYNTHETIC EVALUATION");
        result.put("note", "All revenue is synthetic recovered revenue, not real merchant revenue.");
        result.put("methodology", methodology);
        result.put("revenue", revenue);
        result.put("decisionQuality", decisionQuality);
        result.put("aiBehavior", aiBehavior);
        result.put("comparison", comparison);
        result.put("heldOut", heldOutMap);
        result.put("validation", Map.of("revenue", revenueVal, "decisionQuality", dqVal, "aiBehavior", aiVal,
                "winCount", validation.winCount(), "lossCount", validation.lossCount(), "tieCount", validation.tieCount(),
                "seedCount", validation.seedCount(), "caseCount", validation.aggregatedRevenue().attempts()));
        result.put("development", Map.of(
                "seedCount", development.seedCount(),
                "caseCount", development.aggregatedRevenue().attempts(),
                "meanRecoverFlowRevenue", development.meanRecoverFlowRevenue(),
                "medianRecoverFlowRevenue", development.medianRecoverFlowRevenue(),
                "meanBaselineBRevenue", development.meanBaselineBRevenue(),
                "meanBaselineARevenue", development.meanBaselineARevenue(),
                "winCount", development.winCount(),
                "lossCount", development.lossCount(),
                "tieCount", development.tieCount()
        ));
        result.put("partitions", partitions);
        result.put("aiQuality", aiQuality);
        // Also expose raw aggregated for transparency
        result.put("aggregatedRevenue", revenue);
        return result;
    }

    private BigDecimal sumBaselineA(MultiSeedResult r) {
        return r.perSeedResults().stream()
                .map(MultiSeedResult.PerSeedResult::baselineARecovered)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal sumBaselineB(MultiSeedResult r) {
        return r.perSeedResults().stream()
                .map(MultiSeedResult.PerSeedResult::baselineBRecovered)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private Map<String, Object> toRevenueMap(EvaluationMetricsAggregator.RevenueMetrics m, BigDecimal totalBaselineA, BigDecimal totalBaselineB) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("revenueAtRisk", m.revenueAtRisk());
        map.put("recoveredRevenue", m.recoveredRevenue());
        map.put("baselineARecovered", totalBaselineA);
        map.put("baselineBRecovered", totalBaselineB);
        map.put("recoveryRate", m.recoveryRate());
        map.put("attempts", m.attempts());
        map.put("absoluteRecoveredRevenueDelta", m.absoluteRecoveredRevenueDelta());
        map.put("relativeAiLift", m.relativeAiLift());
        map.put("zeroBaselineRevenueCount", m.zeroBaselineRevenueCount());
        // Explicit aliases for frontend convenience
        map.put("absoluteDelta", m.absoluteRecoveredRevenueDelta());
        map.put("relativeLift", m.relativeAiLift());
        return map;
    }

    private Map<String, Object> toDecisionQualityMap(EvaluationMetricsAggregator.DecisionQualityMetrics dq) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("policyOnlyTotalTrueRegret", dq.policyOnlyTotalTrueRegret());
        map.put("recoverFlowTotalTrueRegret", dq.recoverFlowTotalTrueRegret());
        map.put("policyOnlyMeanTrueRegret", dq.policyOnlyMeanTrueRegret());
        map.put("recoverFlowMeanTrueRegret", dq.recoverFlowMeanTrueRegret());
        map.put("policyOnlyMedianTrueRegret", dq.policyOnlyMedianTrueRegret());
        map.put("recoverFlowMedianTrueRegret", dq.recoverFlowMedianTrueRegret());
        map.put("policyOnlyMeanSelectedTrueValue", dq.policyOnlyMeanSelectedTrueValue());
        map.put("recoverFlowMeanSelectedTrueValue", dq.recoverFlowMeanSelectedTrueValue());
        map.put("meanOracleTrueValue", dq.meanOracleTrueValue());
        map.put("regretDelta", dq.regretDelta());
        map.put("relativeRegretReduction", dq.relativeRegretReduction());
        // Aliases
        map.put("policyOnlyRegret", dq.policyOnlyTotalTrueRegret());
        map.put("recoverFlowRegret", dq.recoverFlowTotalTrueRegret());
        map.put("regretReduction", dq.regretDelta());
        map.put("relativeRegretReductionAlias", dq.relativeRegretReduction());
        map.put("meanRegretPolicyOnly", dq.policyOnlyMeanTrueRegret());
        map.put("meanRegretRecoverFlow", dq.recoverFlowMeanTrueRegret());
        return map;
    }

    private Map<String, Object> toAiBehaviorMap(EvaluationMetricsAggregator.AiDecisionMetrics ai, int totalCases) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("actionChangedCount", ai.actionChangedCount());
        map.put("aiHelpedCount", ai.aiHelpedCount());
        map.put("aiHurtCount", ai.aiHurtCount());
        map.put("aiNeutralCount", ai.aiNeutralCount());
        map.put("actionChangeRate", ai.actionChangeRate());
        map.put("aiHelpRate", ai.aiHelpRate());
        map.put("aiHurtRate", ai.aiHurtRate());
        map.put("aiNeutralRate", ai.aiNeutralRate());
        map.put("totalCases", totalCases);
        // Aliases for frontend
        map.put("changed", ai.actionChangedCount());
        map.put("helped", ai.aiHelpedCount());
        map.put("hurt", ai.aiHurtCount());
        map.put("neutral", ai.aiNeutralCount());
        map.put("changeRate", ai.actionChangeRate());
        map.put("helpRate", ai.aiHelpRate());
        map.put("hurtRate", ai.aiHurtRate());
        map.put("neutralRate", ai.aiNeutralRate());
        return map;
    }

    private Map<String, Object> toPartitionMap(MultiSeedResult r, String label) {
        Map<String, Object> revenue = toRevenueMap(r.aggregatedRevenue(), sumBaselineA(r), sumBaselineB(r));
        Map<String, Object> dq = toDecisionQualityMap(r.decisionQuality());
        Map<String, Object> ai = toAiBehaviorMap(r.aiDecision(), r.aggregatedRevenue().attempts());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("label", label);
        map.put("seedCount", r.seedCount());
        map.put("caseCount", r.aggregatedRevenue().attempts());
        map.put("recoveredRevenue", r.aggregatedRevenue().recoveredRevenue());
        map.put("baselineARecovered", sumBaselineA(r));
        map.put("baselineBRecovered", sumBaselineB(r));
        map.put("absoluteRevenueDelta", r.aggregatedRevenue().absoluteRecoveredRevenueDelta());
        map.put("relativeRevenueLift", r.aggregatedRevenue().relativeAiLift());
        map.put("meanTrueRegretPolicyOnly", r.decisionQuality().policyOnlyMeanTrueRegret());
        map.put("meanTrueRegretRecoverFlow", r.decisionQuality().recoverFlowMeanTrueRegret());
        map.put("medianTrueRegretPolicyOnly", r.decisionQuality().policyOnlyMedianTrueRegret());
        map.put("medianTrueRegretRecoverFlow", r.decisionQuality().recoverFlowMedianTrueRegret());
        map.put("regretReduction", r.decisionQuality().regretDelta());
        map.put("relativeRegretReduction", r.decisionQuality().relativeRegretReduction());
        map.put("actionChangeRate", r.aiDecision().actionChangeRate());
        map.put("helpRate", r.aiDecision().aiHelpRate());
        map.put("hurtRate", r.aiDecision().aiHurtRate());
        map.put("neutralRate", r.aiDecision().aiNeutralRate());
        map.put("winCount", r.winCount());
        map.put("lossCount", r.lossCount());
        map.put("tieCount", r.tieCount());
        map.put("revenue", revenue);
        map.put("decisionQuality", dq);
        map.put("aiBehavior", ai);
        map.put("meanRecoverFlowRevenue", r.meanRecoverFlowRevenue());
        map.put("meanBaselineBRevenue", r.meanBaselineBRevenue());
        map.put("meanBaselineARevenue", r.meanBaselineARevenue());
        return map;
    }

    private Map<String, Object> buildAiQualityMap() {
        try {
            var exp = aiQualityExperiment.run(EvaluationPartitions.DEVELOPMENT, EvaluationPartitions.DATASET_SIZE_PER_SEED);
            Map<String, Object> out = new LinkedHashMap<>();
            for (AiQuality q : AiQuality.values()) {
                var qr = exp.byQuality().get(q);
                if (qr == null) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("quality", q.name());
                m.put("measuredAccuracy", qr.measuredAccuracy());
                m.put("recoveredRevenue", qr.recoveredRevenue());
                m.put("revenueDeltaVsPolicy", qr.revenueDeltaVsPolicy());
                m.put("relativeRevenueLift", qr.relativeRevenueLift());
                m.put("meanRegret", qr.decisionQuality().recoverFlowMeanTrueRegret());
                m.put("meanRegretPolicyOnly", qr.decisionQuality().policyOnlyMeanTrueRegret());
                m.put("medianRegret", qr.decisionQuality().recoverFlowMedianTrueRegret());
                m.put("regretDelta", qr.decisionQuality().regretDelta());
                m.put("relativeRegretReduction", qr.decisionQuality().relativeRegretReduction());
                m.put("helpRate", qr.aiDecision().aiHelpRate());
                m.put("hurtRate", qr.aiDecision().aiHurtRate());
                m.put("neutralRate", qr.aiDecision().aiNeutralRate());
                m.put("actionChangeRate", qr.aiDecision().actionChangeRate());
                m.put("totalCases", qr.totalCases());
                m.put("decisionQuality", toDecisionQualityMap(qr.decisionQuality()));
                m.put("aiBehavior", toAiBehaviorMap(qr.aiDecision(), qr.totalCases()));
                out.put(q.name(), m);
            }
            out.put("policyOnlyRecovered", exp.policyOnlyRecovered());
            out.put("policyOnlyDecisionQuality", toDecisionQualityMap(exp.policyOnlyDecisionQuality()));
            out.put("experimentSeeds", "10000-10029 (DEVELOPMENT, 30 seeds, 200 per seed)");
            out.put("note", "Accuracy shown is measured true-category accuracy of the synthetic observable-only AI proxy under this evaluation configuration.");
            return out;
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", e.getMessage());
            err.put("note", "AI quality experiment failed — no fake values returned");
            return err;
        }
    }
}
