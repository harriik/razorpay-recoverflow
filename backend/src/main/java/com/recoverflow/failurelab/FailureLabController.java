package com.recoverflow.failurelab;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/failure-lab")
public class FailureLabController {

    private final FailureScenarioRegistry registry;
    private final FailureLabService service;

    public FailureLabController(FailureScenarioRegistry registry, FailureLabService service) {
        this.registry = registry;
        this.service = service;
    }

    @GetMapping("/scenarios")
    public ResponseEntity<List<Map<String, Object>>> listScenarios() {
        List<Map<String, Object>> list = registry.getAll().stream()
                .map(s -> Map.<String, Object>of(
                        "scenarioId", s.scenarioId(),
                        "name", s.name(),
                        "description", s.description(),
                        "setup", s.setup(),
                        "trigger", s.trigger(),
                        "expectedOutcome", s.expectedOutcome(),
                        "expectedStateTransitions", s.expectedStateTransitions(),
                        "expectedAuditEvents", s.expectedAuditEvents()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/scenarios/{scenarioId}")
    public ResponseEntity<Map<String, Object>> getScenario(@PathVariable String scenarioId) {
        try {
            var s = registry.getById(scenarioId);
            return ResponseEntity.ok(Map.of(
                    "scenarioId", s.scenarioId(),
                    "name", s.name(),
                    "description", s.description(),
                    "setup", s.setup(),
                    "trigger", s.trigger(),
                    "expectedOutcome", s.expectedOutcome(),
                    "expectedStateTransitions", s.expectedStateTransitions(),
                    "expectedAuditEvents", s.expectedAuditEvents()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/scenarios/{scenarioId}/run")
    public ResponseEntity<FailureLabResult> runScenario(@PathVariable String scenarioId) {
        try {
            registry.getById(scenarioId); // validate exists
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        FailureLabResult result = service.runScenario(scenarioId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/scenarios/{scenarioId}/reset")
    public ResponseEntity<Map<String, String>> resetScenario(@PathVariable String scenarioId) {
        try {
            registry.getById(scenarioId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        service.resetScenario(scenarioId);
        return ResponseEntity.ok(Map.of("scenarioId", scenarioId, "status", "RESET"));
    }
}
