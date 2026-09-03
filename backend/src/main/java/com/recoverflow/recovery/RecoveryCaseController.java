package com.recoverflow.recovery;

import com.recoverflow.audit.AuditEvent;
import com.recoverflow.audit.AuditEventRepository;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/recovery-cases")
public class RecoveryCaseController {

    private final RecoveryCaseRepository caseRepo;
    private final RecoveryActionRepository actionRepo;
    private final AuditEventRepository auditRepo;

    public RecoveryCaseController(RecoveryCaseRepository caseRepo,
                                  RecoveryActionRepository actionRepo,
                                  AuditEventRepository auditRepo) {
        this.caseRepo = caseRepo;
        this.actionRepo = actionRepo;
        this.auditRepo = auditRepo;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String gatewayCode,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) BigDecimal amountMin,
            @RequestParam(required = false) BigDecimal amountMax,
            @RequestParam(required = false) String q
    ) {
        // Fetch all and filter in memory (small dataset, avoids complex spec)
        List<RecoveryCase> all = caseRepo.findAll();
        // Filtering
        List<RecoveryCase> filtered = all.stream()
                .filter(c -> status == null || c.getStatus().name().equalsIgnoreCase(status))
                .filter(c -> gatewayCode == null || (c.getFailureCode() != null && c.getFailureCode().equalsIgnoreCase(gatewayCode)))
                .filter(c -> {
                    if (action == null) return true;
                    // Filter by approvedAction or any action type
                    if (action.equalsIgnoreCase(c.getApprovedAction())) return true;
                    // Also check recovery actions
                    return actionRepo.findByRecoveryCaseId(c.getId()).stream().anyMatch(a -> a.getActionType().name().equalsIgnoreCase(action));
                })
                .filter(c -> amountMin == null || c.getAmount().compareTo(amountMin) >= 0)
                .filter(c -> amountMax == null || c.getAmount().compareTo(amountMax) <= 0)
                .filter(c -> {
                    if (q == null || q.isBlank()) return true;
                    String lower = q.toLowerCase();
                    return c.getId().toString().toLowerCase().contains(lower)
                            || (c.getPayment() != null && c.getPayment().getId().toString().toLowerCase().contains(lower))
                            || (c.getApprovedAction() != null && c.getApprovedAction().toLowerCase().contains(lower));
                })
                .collect(Collectors.toList());

        // Sorting - default updatedAt DESC
        Comparator<RecoveryCase> comparator;
        switch (sort) {
            case "amount" -> comparator = Comparator.comparing(RecoveryCase::getAmount, Comparator.nullsLast(BigDecimal::compareTo));
            case "createdAt" -> comparator = Comparator.comparing(RecoveryCase::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> comparator = Comparator.comparing(c -> c.getStatus().name());
            default -> comparator = Comparator.comparing(RecoveryCase::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        }
        if ("DESC".equalsIgnoreCase(direction)) comparator = comparator.reversed();
        filtered.sort(comparator);

        // Pagination
        int total = filtered.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        List<RecoveryCase> pageContent = filtered.subList(from, to);

        List<Map<String, Object>> content = pageContent.stream().map(this::toListDto).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", content);
        response.put("page", page);
        response.put("size", size);
        response.put("totalElements", total);
        response.put("totalPages", (int) Math.ceil((double) total / size));
        response.put("sort", sort);
        response.put("direction", direction);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID id) {
        Optional<RecoveryCase> opt = caseRepo.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "RecoveryCase not found", "caseId", id.toString()));
        }
        RecoveryCase rc = opt.get();
        List<RecoveryAction> actions = actionRepo.findByRecoveryCaseId(id);
        List<AuditEvent> audits = auditRepo.findByCaseIdOrderByCreatedAtAsc(id);

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("caseId", rc.getId().toString());
        dto.put("paymentId", rc.getPayment() != null ? rc.getPayment().getId().toString() : null);
        dto.put("amount", rc.getAmount());
        dto.put("currency", rc.getCurrency());
        dto.put("gatewayCode", rc.getFailureCode());
        dto.put("failureCategory", rc.getFailureCategory());
        dto.put("paymentMethod", rc.getPayment() != null ? rc.getPayment().getMethod().name() : null);
        dto.put("customerId", rc.getCustomer() != null ? rc.getCustomer().getId().toString() : null);
        dto.put("customerEmail", rc.getCustomer() != null ? rc.getCustomer().getEmail() : null);
        dto.put("merchantId", rc.getMerchant() != null ? rc.getMerchant().getId().toString() : null);
        dto.put("merchantName", rc.getMerchant() != null ? rc.getMerchant().getName() : null);
        dto.put("status", rc.getStatus().name());
        dto.put("attemptCount", rc.getAttemptCount());
        dto.put("recoveredAmount", rc.getRecoveredAmount());
        dto.put("approvedAction", rc.getApprovedAction());
        dto.put("pendingAction", rc.getPendingAction());
        dto.put("pendingReason", rc.getPendingReason());
        dto.put("escalatedReason", rc.getEscalatedReason());
        dto.put("stoppedReason", rc.getStoppedReason());
        dto.put("unknownSince", rc.getUnknownSince());
        dto.put("createdAt", rc.getCreatedAt());
        dto.put("updatedAt", rc.getUpdatedAt());
        // Actions
        List<Map<String, Object>> actionDtos = actions.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId().toString());
            m.put("actionType", a.getActionType().name());
            m.put("status", a.getStatus().name());
            m.put("idempotencyKey", a.getIdempotencyKey());
            m.put("gatewayRef", a.getGatewayRef());
            m.put("estimatedLikelihood", a.getEstimatedLikelihood());
            m.put("expectedValue", a.getExpectedValue());
            m.put("costAmount", a.getCostAmount());
            // syntheticFrictionProxy only for internal/demo display, not real cost
            m.put("syntheticFrictionProxy", a.getSyntheticFrictionProxy());
            m.put("riskPenalty", a.getRiskPenalty());
            m.put("executedAt", a.getExecutedAt());
            m.put("observedAt", a.getObservedAt());
            m.put("createdAt", a.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        dto.put("recoveryActions", actionDtos);
        // Audit
        List<Map<String, Object>> auditDtos = audits.stream().map(ev -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ev.getId().toString());
            m.put("correlationId", ev.getCorrelationId() != null ? ev.getCorrelationId().toString() : null);
            m.put("eventType", ev.getEventType());
            m.put("fromState", ev.getFromState());
            m.put("toState", ev.getToState());
            m.put("actor", ev.getActor() != null ? ev.getActor().name() : null);
            m.put("createdAt", ev.getCreatedAt());
            // payload is safe metadata, not secrets
            m.put("payload", ev.getPayload());
            return m;
        }).collect(Collectors.toList());
        dto.put("auditEvents", auditDtos);
        dto.put("auditCount", auditDtos.size());
        return ResponseEntity.ok(dto);
    }

    private Map<String, Object> toListDto(RecoveryCase rc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("caseId", rc.getId().toString());
        m.put("paymentId", rc.getPayment() != null ? rc.getPayment().getId().toString() : null);
        m.put("amount", rc.getAmount());
        m.put("currency", rc.getCurrency());
        m.put("gatewayCode", rc.getFailureCode());
        m.put("failureCategory", rc.getFailureCategory());
        m.put("paymentMethod", rc.getPayment() != null ? rc.getPayment().getMethod().name() : null);
        m.put("customerId", rc.getCustomer() != null ? rc.getCustomer().getId().toString() : null);
        m.put("status", rc.getStatus().name());
        m.put("attemptCount", rc.getAttemptCount());
        m.put("recoveredAmount", rc.getRecoveredAmount());
        m.put("approvedAction", rc.getApprovedAction());
        m.put("pendingAction", rc.getPendingAction());
        m.put("createdAt", rc.getCreatedAt());
        m.put("updatedAt", rc.getUpdatedAt());
        // Updated time as elapsed/updated time for table
        m.put("elapsedHours", rc.getCreatedAt() != null ? java.time.Duration.between(rc.getCreatedAt(), java.time.Instant.now()).toHours() : 0);
        return m;
    }
}
