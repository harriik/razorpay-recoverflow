package com.recoverflow.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverflow.evaluation.EvaluationEngine.EvaluationResult;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationService {

    private final EvaluationEngine engine;
    private final EvaluationRunRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    // In-memory cache for ablation per run (since hidden dataset not persisted, we keep last result)
    private final Map<UUID, EvaluationResult> cache = new HashMap<>();

    public EvaluationService(EvaluationEngine engine, EvaluationRunRepository repo) {
        this.engine = engine;
        this.repo = repo;
    }

    @Transactional
    public EvaluationRun run(long seed, int datasetSize, String estimatorVersion, String policyVersion) {
        EvaluationResult result = engine.run(seed, datasetSize);

        EvaluationRun run = new EvaluationRun(UUID.randomUUID(), seed, datasetSize, estimatorVersion, policyVersion, "hidden-v1");
        try {
            Map<String, Object> config = Map.of("seed", seed, "datasetSize", datasetSize, "estimatorVersion", estimatorVersion, "policyVersion", policyVersion);
            run.setConfigSnapshot(mapper.writeValueAsString(config));
            run.setBaselineAResult(mapper.writeValueAsString(toMap(result.baselineA())));
            run.setBaselineBResult(mapper.writeValueAsString(toMap(result.baselineB())));
            run.setRecoverflowResult(mapper.writeValueAsString(toMap(result.recoverFlow())));
            run.setAblationSummary(mapper.writeValueAsString(Map.of(
                    "changed", result.ablation().changed(),
                    "helped", result.ablation().helped(),
                    "hurt", result.ablation().hurt(),
                    "total", result.ablation().total()
            )));
            // Incremental
            if (result.baselineA() != null && result.recoverFlow() != null) {
                run.setIncrementalRevenueA(result.recoverFlow().recovered().subtract(result.baselineA().recovered()));
            }
            if (result.baselineB() != null && result.recoverFlow() != null) {
                run.setIncrementalRevenueB(result.recoverFlow().recovered().subtract(result.baselineB().recovered()));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        EvaluationRun saved = repo.save(run);
        cache.put(saved.getId(), result);
        return saved;
    }

    public Optional<EvaluationRun> getRun(UUID id) {
        return repo.findById(id);
    }

    public Optional<EvaluationResult> getCachedResult(UUID id) {
        return Optional.ofNullable(cache.get(id));
    }

    private Map<String, Object> toMap(EvaluationEngine.MetricsHolder m) {
        if (m == null) return Map.of();
        Map<String, Object> map = new HashMap<>();
        map.put("revenueAtRisk", m.revenueAtRisk());
        map.put("recoveredRevenue", m.recovered());
        map.put("recoveryRate", m.revenueAtRisk().compareTo(java.math.BigDecimal.ZERO)==0 ? 0 : m.recovered().multiply(new java.math.BigDecimal("100")).divide(m.revenueAtRisk(), 2, java.math.RoundingMode.HALF_UP));
        map.put("attempts", m.attempts());
        map.put("successfulRecoveries", m.successes());
        map.put("escalations", m.escalations());
        map.put("stopped", m.stopped());
        map.put("failedTerminal", m.failedTerminal());
        map.put("syntheticFrictionProxy", m.friction());
        map.put("policyBlocks", m.policyBlocks());
        return map;
    }
}
