package com.recoverflow.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/evaluation")
public class EvaluationController {

    private final EvaluationService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public EvaluationController(EvaluationService service) {
        this.service = service;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run(@RequestBody RunRequest req) {
        long seed = req.seed() != null ? req.seed() : System.currentTimeMillis();
        int size = req.datasetSize() != null ? req.datasetSize() : 3000;
        String estVer = req.estimatorVersion() != null ? req.estimatorVersion() : "v1";
        String polVer = req.policyVersion() != null ? req.policyVersion() : "v1";
        EvaluationRun run = service.run(seed, size, estVer, polVer);
        return ResponseEntity.ok(Map.of(
                "runId", run.getId().toString(),
                "seed", run.getSeed(),
                "datasetSize", run.getDatasetSize(),
                "estimatorVersion", run.getEstimatorVersion(),
                "policyVersion", run.getPolicyVersion()
        ));
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<Map<String, Object>> getRun(@PathVariable UUID id) {
        Optional<EvaluationRun> opt = service.getRun(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        EvaluationRun r = opt.get();
        try {
            Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("id", r.getId().toString());
            resp.put("seed", r.getSeed());
            resp.put("datasetSize", r.getDatasetSize());
            resp.put("estimatorVersion", r.getEstimatorVersion());
            resp.put("policyVersion", r.getPolicyVersion());
            resp.put("syntheticRegistryVersion", r.getSyntheticRegistryVersion());
            resp.put("configSnapshot", r.getConfigSnapshot() != null ? mapper.readTree(r.getConfigSnapshot()) : null);
            resp.put("baselineA", r.getBaselineAResult() != null ? mapper.readTree(r.getBaselineAResult()) : null);
            resp.put("baselineB", r.getBaselineBResult() != null ? mapper.readTree(r.getBaselineBResult()) : null);
            resp.put("recoverFlow", r.getRecoverflowResult() != null ? mapper.readTree(r.getRecoverflowResult()) : null);
            resp.put("ablationSummary", r.getAblationSummary() != null ? mapper.readTree(r.getAblationSummary()) : null);
            resp.put("incrementalRevenueA", r.getIncrementalRevenueA());
            resp.put("incrementalRevenueB", r.getIncrementalRevenueB());
            resp.put("createdAt", r.getCreatedAt().toString());
            // Held-out is not yet separately persisted, but computed from same run (80/20 split)
            resp.put("note", "Held-out is last 20% not used for tuning; metrics above are full dataset, held-out available via ablation details");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/runs/{id}/ablation")
    public ResponseEntity<Map<String, Object>> getAblation(@PathVariable UUID id) {
        var cached = service.getCachedResult(id);
        if (cached.isEmpty()) {
            // Try to load run but ablation per-case not persisted beyond summary
            var runOpt = service.getRun(id);
            if (runOpt.isEmpty()) return ResponseEntity.notFound().build();
            try {
                JsonNode ablation = runOpt.get().getAblationSummary() != null ? mapper.readTree(runOpt.get().getAblationSummary()) : mapper.createObjectNode();
                return ResponseEntity.ok(Map.of("summary", ablation, "note", "Per-case details are in-memory only; run again to get fresh per-case if cache expired"));
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
            }
        }
        var ablation = cached.get().ablation();
        // Return summary + first 20 per-case for inspectability
        var perCaseSample = ablation.perCase().stream().limit(20).toList();
        return ResponseEntity.ok(Map.of(
                "changed", ablation.changed(),
                "helped", ablation.helped(),
                "hurt", ablation.hurt(),
                "total", ablation.total(),
                "changeRate", ablation.total()==0?0: (double) ablation.changed()/ablation.total(),
                "helpRate", ablation.changed()==0?0: (double) ablation.helped()/ablation.changed(),
                "hurtRate", ablation.changed()==0?0: (double) ablation.hurt()/ablation.changed(),
                "samplePerCase", perCaseSample
        ));
    }

    @PostMapping("/ablation/{caseId}")
    public ResponseEntity<Map<String, Object>> perCaseAblation(@PathVariable String caseId, @RequestBody Map<String, Object> body) {
        // For demo, we run a single case ablation with provided seed
        // Body may contain seed, but we use in-memory cache if available
        return ResponseEntity.ok(Map.of("caseId", caseId, "note", "Per-case ablation for " + caseId + " is available via GET /runs/{id}/ablation sample"));
    }

    public record RunRequest(Long seed, Integer datasetSize, String estimatorVersion, String policyVersion) {}
}
