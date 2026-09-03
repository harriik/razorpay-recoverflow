package com.recoverflow.recovery;

import com.recoverflow.audit.AuditEventRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/recovery-cases")
public class DecisionController {

    private final DecisionQueryService queryService;
    private final RecoveryCaseRepository caseRepo;

    public DecisionController(DecisionQueryService queryService, RecoveryCaseRepository caseRepo) {
        this.queryService = queryService;
        this.caseRepo = caseRepo;
    }

    @GetMapping("/{id}/decision")
    public ResponseEntity<Map<String, Object>> getDecision(@PathVariable UUID id) {
        if (!caseRepo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "RecoveryCase not found", "caseId", id.toString()));
        }
        Map<String, Object> response = queryService.buildDecisionResponse(id);
        return ResponseEntity.ok(response);
    }
}
