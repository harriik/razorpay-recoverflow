package com.recoverflow.evaluation;

import com.recoverflow.synthetic.SyntheticCase;
import com.recoverflow.synthetic.SyntheticWorldGenerator;
import java.util.ArrayList;
import java.util.List;

/**
 * Small deterministic audit fixture set (20-50 observable cases) for REAL_LLM audit.
 * Uses fixed seeds, not held-out 30000–30009, and does not regenerate per quality.
 * Can be replayed/recorded without changing hidden-world data.
 */
public class RealLlmAuditFixture {

    public static final int FIXTURE_SIZE = 30;
    public static final long FIXTURE_SEED_BASE = 50000L;

    public static List<SyntheticCase> generate(SyntheticWorldGenerator generator) {
        List<SyntheticCase> all = new ArrayList<>();
        // Deterministic: 30 cases from 30 seeds, 1 case per seed
        for (int i = 0; i < FIXTURE_SIZE; i++) {
            long seed = FIXTURE_SEED_BASE + i;
            var dataset = generator.generate(seed, 1);
            all.add(dataset.get(0));
        }
        return List.copyOf(all);
    }

    public static List<SyntheticCase> generate(SyntheticWorldGenerator generator, int size) {
        if (size < 20 || size > 50) throw new IllegalArgumentException("Fixture size must be 20-50");
        List<SyntheticCase> all = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            long seed = FIXTURE_SEED_BASE + i;
            var dataset = generator.generate(seed, 1);
            all.add(dataset.get(0));
        }
        return List.copyOf(all);
    }
}
