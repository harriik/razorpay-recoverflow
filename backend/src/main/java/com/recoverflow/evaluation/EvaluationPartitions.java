package com.recoverflow.evaluation;

import com.recoverflow.synthetic.SyntheticAiProxy;
import java.util.List;
import java.util.stream.LongStream;

/**
 * Immutable seed-level evaluation partitions.
 *
 * <p>DEVELOPMENT: 10000–10029 (30 seeds) — primary development / tuning
 * <p>VALIDATION:  20000–20009 (10 seeds) — validation, still not held-out
 * <p>HELD_OUT:    30000–30009 (10 seeds) — final held-out, never used for tuning
 *
 * <p>Held-out is defined as independent seeds, NOT as last N cases of one generated dataset.
 * The per-dataset 80/20 split inside {@code EvaluationEngine.evaluateDataset} is a separate
 * intra-dataset held-out for single-seed debugging and is NOT the seed-level held-out partition.
 *
 * <p>All partitions run with IDENTICAL evaluator configuration; only seed set changes:
 * <ul>
 *   <li>SyntheticWorldGenerator version: hidden-v1 (syntheticRegistryVersion)</li>
 *   <li>Estimator: InterventionLikelihoodEstimator v1</li>
 *   <li>Policy: PolicyConfig v1 (10000/3/48)</li>
 *   <li>Synthetic AI proxy: {@link SyntheticAiProxy#VERSION} (synthetic-ai-v1)</li>
 *   <li>True value calculator: {@link TrueDecisionValueCalculator#VERSION} (true-value-v1)</li>
 *   <li>Evaluator config: deterministic, versioned, no tuning from held-out</li>
 * </ul>
 * Dataset size per seed is fixed (200) for all partitions to ensure comparability.
 */
public final class EvaluationPartitions {

    public static final String SYNTHETIC_WORLD_VERSION = "hidden-v1";
    public static final String ESTIMATOR_VERSION = "v1";
    public static final String POLICY_VERSION = "v1";
    public static final String SYNTHETIC_AI_PROXY_VERSION = SyntheticAiProxy.VERSION;
    public static final String TRUE_VALUE_VERSION = TrueDecisionValueCalculator.VERSION;
    public static final String EVALUATOR_VERSION = "evaluator-v1";
    public static final int DATASET_SIZE_PER_SEED = 200;

    public static final List<Long> DEVELOPMENT = LongStream.range(10000, 10030).boxed().toList(); // 30
    public static final List<Long> VALIDATION = LongStream.range(20000, 20010).boxed().toList(); // 10
    public static final List<Long> HELD_OUT = LongStream.range(30000, 30010).boxed().toList(); // 10

    public enum Partition {
        DEVELOPMENT,
        VALIDATION,
        HELD_OUT
    }

    private EvaluationPartitions() {}

    public static List<Long> seedsFor(Partition partition) {
        return switch (partition) {
            case DEVELOPMENT -> DEVELOPMENT;
            case VALIDATION -> VALIDATION;
            case HELD_OUT -> HELD_OUT;
        };
    }

    public static String versionSnapshot() {
        return String.format("syntheticWorld=%s estimator=%s policy=%s aiProxy=%s trueValue=%s evaluator=%s datasetSize=%d",
                SYNTHETIC_WORLD_VERSION, ESTIMATOR_VERSION, POLICY_VERSION,
                SYNTHETIC_AI_PROXY_VERSION, TRUE_VALUE_VERSION, EVALUATOR_VERSION, DATASET_SIZE_PER_SEED);
    }
}
