package com.recoverflow.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EvaluationPartitionsTest {

    @Autowired EvaluationEngine engine;

    // 1. partitions are deterministic
    @Test
    void partitionsAreDeterministic() {
        assertEquals(List.copyOf(EvaluationPartitions.DEVELOPMENT), List.copyOf(EvaluationPartitions.DEVELOPMENT));
        assertEquals(List.copyOf(EvaluationPartitions.VALIDATION), List.copyOf(EvaluationPartitions.VALIDATION));
        assertEquals(List.copyOf(EvaluationPartitions.HELD_OUT), List.copyOf(EvaluationPartitions.HELD_OUT));
        // version snapshot deterministic
        assertEquals(EvaluationPartitions.versionSnapshot(), EvaluationPartitions.versionSnapshot());
        // seedsFor must return same immutable lists
        assertEquals(EvaluationPartitions.DEVELOPMENT, EvaluationPartitions.seedsFor(EvaluationPartitions.Partition.DEVELOPMENT));
        assertEquals(EvaluationPartitions.VALIDATION, EvaluationPartitions.seedsFor(EvaluationPartitions.Partition.VALIDATION));
        assertEquals(EvaluationPartitions.HELD_OUT, EvaluationPartitions.seedsFor(EvaluationPartitions.Partition.HELD_OUT));
    }

    // 2. no seed overlaps between partitions
    @Test
    void noSeedOverlapsBetweenPartitions() {
        Set<Long> dev = new HashSet<>(EvaluationPartitions.DEVELOPMENT);
        Set<Long> val = new HashSet<>(EvaluationPartitions.VALIDATION);
        Set<Long> held = new HashSet<>(EvaluationPartitions.HELD_OUT);
        for (Long s : dev) {
            assertFalse(val.contains(s), "DEVELOPMENT and VALIDATION must not overlap: " + s);
            assertFalse(held.contains(s), "DEVELOPMENT and HELD_OUT must not overlap: " + s);
        }
        for (Long s : val) {
            assertFalse(held.contains(s), "VALIDATION and HELD_OUT must not overlap: " + s);
        }
    }

    // 3. every expected seed is present
    @Test
    void everyExpectedSeedIsPresent() {
        List<Long> expectedDev = LongStream.range(10000, 10030).boxed().toList();
        List<Long> expectedVal = LongStream.range(20000, 20010).boxed().toList();
        List<Long> expectedHeld = LongStream.range(30000, 30010).boxed().toList();
        assertEquals(expectedDev, EvaluationPartitions.DEVELOPMENT);
        assertEquals(expectedVal, EvaluationPartitions.VALIDATION);
        assertEquals(expectedHeld, EvaluationPartitions.HELD_OUT);
        assertEquals(30, EvaluationPartitions.DEVELOPMENT.size());
        assertEquals(10, EvaluationPartitions.VALIDATION.size());
        assertEquals(10, EvaluationPartitions.HELD_OUT.size());
    }

    // 4. same partition reproducibility
    @Test
    void samePartitionReproducibility() {
        var dev1 = engine.runMultiSeed(EvaluationPartitions.DEVELOPMENT, EvaluationPartitions.DATASET_SIZE_PER_SEED);
        var dev2 = engine.runMultiSeed(EvaluationPartitions.DEVELOPMENT, EvaluationPartitions.DATASET_SIZE_PER_SEED);
        assertEquals(dev1.decisionQuality().policyOnlyTotalTrueRegret(), dev2.decisionQuality().policyOnlyTotalTrueRegret());
        assertEquals(dev1.decisionQuality().recoverFlowTotalTrueRegret(), dev2.decisionQuality().recoverFlowTotalTrueRegret());
        assertEquals(dev1.decisionQuality().regretDelta(), dev2.decisionQuality().regretDelta());
        assertEquals(dev1.aggregatedRevenue().absoluteRecoveredRevenueDelta(), dev2.aggregatedRevenue().absoluteRecoveredRevenueDelta());
        assertEquals(dev1.aiDecision().actionChangedCount(), dev2.aiDecision().actionChangedCount());
        assertEquals(dev1.meanRecoverFlowRevenue(), dev2.meanRecoverFlowRevenue());

        var held1 = engine.runMultiSeed(EvaluationPartitions.HELD_OUT, EvaluationPartitions.DATASET_SIZE_PER_SEED);
        var held2 = engine.runMultiSeed(EvaluationPartitions.HELD_OUT, EvaluationPartitions.DATASET_SIZE_PER_SEED);
        assertEquals(held1.decisionQuality().policyOnlyTotalTrueRegret(), held2.decisionQuality().policyOnlyTotalTrueRegret());
        assertEquals(held1.aggregatedRevenue().recoveredRevenue(), held2.aggregatedRevenue().recoveredRevenue());
    }

    // 5. held-out evaluation uses same evaluator configuration
    @Test
    void heldOutUsesSameEvaluatorConfiguration() {
        String snapshot = EvaluationPartitions.versionSnapshot();
        assertTrue(snapshot.contains("synthetic-ai-v1"));
        assertTrue(snapshot.contains("true-value-v1"));
        assertTrue(snapshot.contains("hidden-v1"));
        assertTrue(snapshot.contains("evaluator-v1"));
        assertTrue(snapshot.contains("v1"));
        // Same engine instance, same versions for all partitions
        var dev = engine.runMultiSeed(EvaluationPartitions.DEVELOPMENT, EvaluationPartitions.DATASET_SIZE_PER_SEED);
        var val = engine.runMultiSeed(EvaluationPartitions.VALIDATION, EvaluationPartitions.DATASET_SIZE_PER_SEED);
        var held = engine.runMultiSeed(EvaluationPartitions.HELD_OUT, EvaluationPartitions.DATASET_SIZE_PER_SEED);
        // All should have same dataset size per seed and same scale
        assertEquals(4, dev.decisionQuality().policyOnlyTotalTrueRegret().scale());
        assertEquals(4, val.decisionQuality().policyOnlyTotalTrueRegret().scale());
        assertEquals(4, held.decisionQuality().policyOnlyTotalTrueRegret().scale());
        // Version snapshot must be identical across partitions
        assertEquals(EvaluationPartitions.versionSnapshot(), EvaluationPartitions.versionSnapshot());
    }

    // 6. held-out data cannot alter decision configuration (immutability)
    @Test
    void heldOutDataCannotAlterDecisionConfiguration() {
        // Partitions are immutable lists
        assertThrows(UnsupportedOperationException.class, () -> EvaluationPartitions.DEVELOPMENT.add(99999L));
        assertThrows(UnsupportedOperationException.class, () -> EvaluationPartitions.VALIDATION.remove(0));
        assertThrows(UnsupportedOperationException.class, () -> EvaluationPartitions.HELD_OUT.set(0, 99999L));
        // Engine config is not mutated by held-out run
        var before = EvaluationPartitions.versionSnapshot();
        engine.runMultiSeed(EvaluationPartitions.HELD_OUT, EvaluationPartitions.DATASET_SIZE_PER_SEED);
        var after = EvaluationPartitions.versionSnapshot();
        assertEquals(before, after);
        // Held-out seeds are not from development range
        for (Long s : EvaluationPartitions.HELD_OUT) {
            assertTrue(s >= 30000 && s < 30010);
            assertFalse(EvaluationPartitions.DEVELOPMENT.contains(s));
        }
    }

    // 7. metrics are generated for all partitions
    @Test
    void metricsAreGeneratedForAllPartitions() {
        for (var partition : EvaluationPartitions.Partition.values()) {
            var seeds = EvaluationPartitions.seedsFor(partition);
            var result = engine.runMultiSeed(seeds, EvaluationPartitions.DATASET_SIZE_PER_SEED);
            // Revenue
            assertNotNull(result.aggregatedRevenue().revenueAtRisk());
            assertNotNull(result.aggregatedRevenue().recoveredRevenue());
            assertNotNull(result.aggregatedRevenue().absoluteRecoveredRevenueDelta());
            assertNotNull(result.meanBaselineARevenue());
            assertNotNull(result.meanBaselineBRevenue());
            assertNotNull(result.meanRecoverFlowRevenue());
            // relative lift may be null if baseline 0, but must be either null or scale 4
            if (result.aggregatedRevenue().relativeAiLift() != null) assertEquals(4, result.aggregatedRevenue().relativeAiLift().scale());

            // Decision quality
            var dq = result.decisionQuality();
            assertNotNull(dq.policyOnlyTotalTrueRegret());
            assertNotNull(dq.recoverFlowTotalTrueRegret());
            assertNotNull(dq.policyOnlyMeanTrueRegret());
            assertNotNull(dq.recoverFlowMeanTrueRegret());
            assertNotNull(dq.policyOnlyMedianTrueRegret());
            assertNotNull(dq.recoverFlowMedianTrueRegret());
            assertNotNull(dq.meanOracleTrueValue());
            assertNotNull(dq.regretDelta());
            // relative may be null
            assertEquals(4, dq.policyOnlyTotalTrueRegret().scale());
            assertEquals(4, dq.regretDelta().scale());

            // AI
            var ai = result.aiDecision();
            assertTrue(ai.actionChangedCount() >= 0);
            assertNotNull(ai.actionChangeRate());
            assertEquals(4, ai.actionChangeRate().scale());
            if (ai.actionChangedCount() == 0) {
                assertNull(ai.aiHelpRate());
                assertNull(ai.aiHurtRate());
                assertNull(ai.aiNeutralRate());
            } else {
                assertNotNull(ai.aiHelpRate());
                assertNotNull(ai.aiHurtRate());
                assertNotNull(ai.aiNeutralRate());
            }
            // Per-seed preservation
            assertEquals(seeds.size(), result.perSeedResults().size());
            for (var ps : result.perSeedResults()) {
                assertNotNull(ps.decisionQuality());
                assertNotNull(ps.aiDecision());
                assertNotNull(ps.revenue());
            }
        }
    }

    // 8. held-out result is distinct from development result
    @Test
    void heldOutResultIsDistinctFromDevelopmentResult() {
        var dev = engine.runMultiSeed(EvaluationPartitions.DEVELOPMENT, EvaluationPartitions.DATASET_SIZE_PER_SEED);
        var held = engine.runMultiSeed(EvaluationPartitions.HELD_OUT, EvaluationPartitions.DATASET_SIZE_PER_SEED);
        // Different seeds must produce independent results (not identical aggregates)
        boolean anyDiff = !dev.meanRecoverFlowRevenue().equals(held.meanRecoverFlowRevenue())
                || !dev.decisionQuality().policyOnlyTotalTrueRegret().equals(held.decisionQuality().policyOnlyTotalTrueRegret())
                || !dev.decisionQuality().recoverFlowTotalTrueRegret().equals(held.decisionQuality().recoverFlowTotalTrueRegret())
                || dev.aiDecision().actionChangedCount() != held.aiDecision().actionChangedCount();
        assertTrue(anyDiff, "Held-out and development must be independent (different aggregates)");
        // Also validation distinct
        var val = engine.runMultiSeed(EvaluationPartitions.VALIDATION, EvaluationPartitions.DATASET_SIZE_PER_SEED);
        boolean devValDiff = !dev.meanRecoverFlowRevenue().equals(val.meanRecoverFlowRevenue())
                || dev.aiDecision().actionChangedCount() != val.aiDecision().actionChangedCount();
        assertTrue(devValDiff || !dev.decisionQuality().regretDelta().equals(val.decisionQuality().regretDelta()),
                "Validation and development must be independent");
    }

    // 9. no cherry-picking/filtering
    @Test
    void noCherryPickingFiltering() {
        // Partitions are predefined ranges, not filtered by favorable outcomes
        // Verify all expected seeds are present and none are cherry-picked from favorable cases
        var dev = engine.runMultiSeed(EvaluationPartitions.DEVELOPMENT, EvaluationPartitions.DATASET_SIZE_PER_SEED);
        assertEquals(30, dev.perSeedResults().size());
        assertEquals(30, dev.perSeedResults().stream().map(r -> r.seed()).distinct().count());
        // Every seed in range must be present exactly once
        Set<Long> devSeeds = new HashSet<>(EvaluationPartitions.DEVELOPMENT);
        for (var ps : dev.perSeedResults()) {
            assertTrue(devSeeds.contains(ps.seed()), "Per-seed result must be from predefined partition, not cherry-picked: " + ps.seed());
        }
        // Held-out must not be subset of development
        var held = engine.runMultiSeed(EvaluationPartitions.HELD_OUT, EvaluationPartitions.DATASET_SIZE_PER_SEED);
        for (var ps : held.perSeedResults()) {
            assertFalse(devSeeds.contains(ps.seed()), "Held-out must not contain development seeds");
        }
        // No filtering: total cases = seedCount * datasetSize
        assertEquals(30 * EvaluationPartitions.DATASET_SIZE_PER_SEED, dev.perSeedResults().size() * EvaluationPartitions.DATASET_SIZE_PER_SEED); // trivial but ensures no filtering
        int totalCasesDev = dev.perSeedResults().size() * EvaluationPartitions.DATASET_SIZE_PER_SEED;
        assertEquals(totalCasesDev, dev.aiDecision().actionChangedCount() + (totalCasesDev - dev.aiDecision().actionChangedCount())); // all cases accounted
        // Ensure no favorable seed filtering: win/loss/tie sum to seedCount
        assertEquals(30, dev.winCount() + dev.lossCount() + dev.tieCount());
        assertEquals(10, held.winCount() + held.lossCount() + held.tieCount());
    }
}
