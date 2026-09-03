package com.recoverflow.recovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverflow.audit.AuditEventRepository;
import com.recoverflow.decision.ExpectedNetRecoveryValueEngine;
import com.recoverflow.decision.RecoveryDecisionService;
import com.recoverflow.likelihood.InterventionLikelihoodEstimator;
import com.recoverflow.likelihood.ObservableContext;
import com.recoverflow.payment.PaymentMethod;
import com.recoverflow.policy.PolicyConfig;
import com.recoverflow.policy.PolicyContext;
import com.recoverflow.policy.PolicyDecision;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DecisionQueryService {

    private final RecoveryCaseRepository caseRepo;
    private final RecoveryActionRepository actionRepo;
    private final AuditEventRepository auditRepo;
    private final RecoveryDecisionSnapshotRepository snapshotRepo;
    private final InterventionLikelihoodEstimator estimator;
    private final ExpectedNetRecoveryValueEngine evEngine;
    private final RecoveryDecisionService decisionService;
    private final PolicyConfig policyConfig;
    private final ObjectMapper mapper = new ObjectMapper();

    public DecisionQueryService(RecoveryCaseRepository caseRepo,
                                RecoveryActionRepository actionRepo,
                                AuditEventRepository auditRepo,
                                RecoveryDecisionSnapshotRepository snapshotRepo,
                                InterventionLikelihoodEstimator estimator,
                                ExpectedNetRecoveryValueEngine evEngine,
                                RecoveryDecisionService decisionService,
                                PolicyConfig policyConfig) {
        this.caseRepo = caseRepo;
        this.actionRepo = actionRepo;
        this.auditRepo = auditRepo;
        this.snapshotRepo = snapshotRepo;
        this.estimator = estimator;
        this.evEngine = evEngine;
        this.decisionService = decisionService;
        this.policyConfig = policyConfig;
    }

    public Map<String, Object> buildDecisionResponse(UUID caseId) {
        RecoveryCase rc = caseRepo.findById(caseId).orElseThrow();
        // Check for historical snapshot – this is the QUERY, not a fresh decision
        Optional<RecoveryDecisionSnapshot> snapOpt = snapshotRepo.findTopByCaseIdOrderByCreatedAtDesc(caseId);
        if (snapOpt.isPresent()) {
            RecoveryDecisionSnapshot snap = snapOpt.get();
            try {
                Map<String, Object> observable = mapper.readValue(snap.getObservableSnapshot(), new TypeReference<Map<String, Object>>() {});
                Map<String, Object> aiAssessment = snap.getAiAssessmentSnapshot() != null ? mapper.readValue(snap.getAiAssessmentSnapshot(), new TypeReference<Map<String, Object>>() {}) : Map.of("status", "NOT_PERSISTED");
                List<Map<String, Object>> candidates = mapper.readValue(snap.getCandidateSnapshot(), new TypeReference<List<Map<String, Object>>>() {});
                List<Map<String, Object>> policyList = mapper.readValue(snap.getPolicySnapshot(), new TypeReference<List<Map<String, Object>>>() {});
                Map<String, Object> policySummary = new LinkedHashMap<>();
                policySummary.put("allDecisions", policyList);
                // Find selected policy decision from candidates
                Map<String, Object> selectedPolicy = null;
                for (Map<String, Object> c : candidates) {
                    if (snap.getSelectedAction() != null && snap.getSelectedAction().equals(c.get("action")) && "ALLOWED".equals(c.get("policyResult"))) {
                        selectedPolicy = c;
                        break;
                    }
                }
                if (selectedPolicy != null) {
                    policySummary.put("selectedPolicyDecision", selectedPolicy.get("policyResult"));
                    policySummary.put("selectedRuleId", selectedPolicy.get("policyRuleId"));
                    policySummary.put("selectedReason", selectedPolicy.get("policyReason"));
                }
                Map<String, Object> versions = new LinkedHashMap<>();
                versions.put("estimatorVersion", snap.getEstimatorVersion());
                versions.put("policyVersion", snap.getPolicyVersion());
                versions.put("aiProvider", snap.getAiProvider());
                versions.put("aiModel", snap.getAiModel());
                versions.put("decisionVersion", snap.getDecisionVersion());
                versions.put("evVersion", snap.getEvVersion());
                var audits = auditRepo.findByCaseIdOrderByCreatedAtAsc(caseId);
                Map<String, Object> auditRef = new LinkedHashMap<>();
                auditRef.put("auditCount", audits.size());
                auditRef.put("lastCorrelationId", audits.isEmpty() ? null : audits.get(audits.size() - 1).getCorrelationId().toString());
                auditRef.put("eventsUrl", "/api/v1/recovery-cases/" + caseId + "/audit");
                Map<String, Object> response = new LinkedHashMap<>();
                Map<String, Object> caseMap = new LinkedHashMap<>();
                caseMap.put("caseId", rc.getId().toString());
                caseMap.put("paymentId", rc.getPayment() != null ? rc.getPayment().getId().toString() : null);
                caseMap.put("amount", rc.getAmount());
                caseMap.put("currency", rc.getCurrency());
                caseMap.put("status", rc.getStatus().name());
                caseMap.put("attemptCount", rc.getAttemptCount());
                caseMap.put("recoveredAmount", rc.getRecoveredAmount());
                caseMap.put("createdAt", rc.getCreatedAt());
                caseMap.put("updatedAt", rc.getUpdatedAt());
                response.put("case", caseMap);
                response.put("observableEvidence", observable);
                // Historical AI assessment – actual persisted, not NOT_PERSISTED
                response.put("aiAssessment", aiAssessment);
                response.put("candidates", candidates);
                response.put("selectedAction", snap.getSelectedAction());
                response.put("selectionReason", snap.getSelectionReason());
                response.put("selectedExpectedNetValue", snap.getSelectedExpectedNetValue());
                response.put("selectionTimestamp", snap.getSelectionTimestamp());
                response.put("policySummary", policySummary);
                response.put("versions", versions);
                response.put("auditReference", auditRef);
                response.put("auditEvents", audits.stream().map(ev -> Map.of(
                        "id", ev.getId().toString(),
                        "correlationId", ev.getCorrelationId().toString(),
                        "eventType", ev.getEventType(),
                        "fromState", ev.getFromState(),
                        "toState", ev.getToState(),
                        "actor", ev.getActor().name(),
                        "createdAt", ev.getCreatedAt().toString(),
                        "payload", ev.getPayload()
                )).collect(Collectors.toList()));
                // Ensure no hidden data leakage in snapshot
                response.put("historical", true);
                return response;
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse snapshot", e);
            }
        }
        // No snapshot – return NOT_PERSISTED (do not fabricate)
        Map<String, Object> observable = new LinkedHashMap<>();
        observable.put("amount", rc.getAmount());
        observable.put("currency", rc.getCurrency());
        observable.put("paymentMethod", rc.getPayment() != null ? rc.getPayment().getMethod().name() : null);
        observable.put("gatewayCode", rc.getFailureCode());
        observable.put("failedAt", rc.getPayment() != null ? rc.getPayment().getFailedAt() : null);
        observable.put("elapsedHours", rc.getCreatedAt() != null ? java.time.Duration.between(rc.getCreatedAt(), Instant.now()).toHours() : 0);
        observable.put("attemptCount", rc.getAttemptCount());
        observable.put("priorSuccessCount", rc.getCustomer() != null ? rc.getCustomer().getSuccessCount() : 0);
        observable.put("priorFailureCount", rc.getCustomer() != null ? rc.getCustomer().getFailureCount() : 0);
        observable.put("linkAlreadySent", isLinkAlreadySent(rc));

        Map<String, Object> aiAssessment = new LinkedHashMap<>();
        aiAssessment.put("status", "NOT_PERSISTED");
        aiAssessment.put("description", "Historical AI assessment not yet persisted – persistence is the missing capability. Decision snapshot will be created at next decision finalization.");
        aiAssessment.put("failureCategory", null);
        aiAssessment.put("recoverability", null);
        aiAssessment.put("candidateAssessments", null);
        aiAssessment.put("recommendedAction", null);
        aiAssessment.put("evidenceQuality", null);
        aiAssessment.put("riskLevel", null);
        aiAssessment.put("reasoningSummary", null);

        // For NOT_PERSISTED, do not recompute candidates via fresh decision – return empty historical
        List<Map<String, Object>> candidates = List.of();
        Map<String, Object> policySummary = new LinkedHashMap<>();
        policySummary.put("selectedPolicyDecision", null);
        policySummary.put("allDecisions", List.of());

        Map<String, Object> versions = new LinkedHashMap<>();
        versions.put("estimatorVersion", "v1");
        versions.put("policyVersion", policyConfig.getVersion());
        versions.put("aiProvider", null);
        versions.put("aiModel", null);
        versions.put("decisionVersion", "decision-v1");
        versions.put("evVersion", "ev-v1");

        var audits = auditRepo.findByCaseIdOrderByCreatedAtAsc(caseId);
        Map<String, Object> auditRef = new LinkedHashMap<>();
        auditRef.put("auditCount", audits.size());
        auditRef.put("lastCorrelationId", audits.isEmpty() ? null : audits.get(audits.size() - 1).getCorrelationId().toString());
        auditRef.put("eventsUrl", "/api/v1/recovery-cases/" + caseId + "/audit");

        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> caseMap = new LinkedHashMap<>();
        caseMap.put("caseId", rc.getId().toString());
        caseMap.put("paymentId", rc.getPayment() != null ? rc.getPayment().getId().toString() : null);
        caseMap.put("amount", rc.getAmount());
        caseMap.put("currency", rc.getCurrency());
        caseMap.put("status", rc.getStatus().name());
        caseMap.put("attemptCount", rc.getAttemptCount());
        caseMap.put("recoveredAmount", rc.getRecoveredAmount());
        caseMap.put("createdAt", rc.getCreatedAt());
        caseMap.put("updatedAt", rc.getUpdatedAt());
        response.put("case", caseMap);
        response.put("observableEvidence", observable);
        response.put("aiAssessment", aiAssessment);
        response.put("candidates", candidates);
        response.put("selectedAction", rc.getApprovedAction());
        response.put("selectionReason", null);
        response.put("selectedExpectedNetValue", null);
        response.put("selectionTimestamp", rc.getApprovedAt());
        response.put("policySummary", policySummary);
        response.put("versions", versions);
        response.put("auditReference", auditRef);
        response.put("auditEvents", audits.stream().map(ev -> Map.of(
                "id", ev.getId().toString(),
                "correlationId", ev.getCorrelationId().toString(),
                "eventType", ev.getEventType(),
                "fromState", ev.getFromState(),
                "toState", ev.getToState(),
                "actor", ev.getActor().name(),
                "createdAt", ev.getCreatedAt().toString(),
                "payload", ev.getPayload()
        )).collect(Collectors.toList()));
        response.put("historical", false);
        return response;
    }

    private boolean isLinkAlreadySent(RecoveryCase rc) {
        return actionRepo.findByRecoveryCaseId(rc.getId()).stream()
                .anyMatch(a -> a.getActionType() == RecoveryActionType.SEND_PAYMENT_LINK && a.getStatus() == com.recoverflow.recovery.RecoveryActionStatus.SUCCESS);
    }

    private ObservableContext toObservableContext(RecoveryCase rc) {
        // Derive observable from current case state – only approved observable fields
        String gatewayCode = rc.getFailureCode() != null ? rc.getFailureCode() : "UNKNOWN";
        int elapsed = rc.getCreatedAt() != null ? (int) java.time.Duration.between(rc.getCreatedAt(), Instant.now()).toHours() : 0;
        int priorSuccess = rc.getCustomer() != null ? rc.getCustomer().getSuccessCount() : 0;
        int priorFailure = rc.getCustomer() != null ? rc.getCustomer().getFailureCount() : 0;
        boolean linkSent = isLinkAlreadySent(rc);
        return new ObservableContext(
                rc.getAmount(),
                rc.getCurrency() != null ? rc.getCurrency() : "INR",
                rc.getPayment() != null ? rc.getPayment().getMethod() : PaymentMethod.CARD,
                gatewayCode,
                elapsed,
                rc.getAttemptCount() != null ? rc.getAttemptCount() : 0,
                priorSuccess,
                priorFailure,
                linkSent
        );
    }

    private PolicyContext toPolicyContext(RecoveryCase rc, RecoveryActionType action) {
        String gatewayCode = rc.getFailureCode() != null ? rc.getFailureCode() : "UNKNOWN";
        int elapsed = rc.getCreatedAt() != null ? (int) java.time.Duration.between(rc.getCreatedAt(), Instant.now()).toHours() : 0;
        boolean optedOut = rc.getCustomer() != null && Boolean.TRUE.equals(rc.getCustomer().getOptedOut());
        boolean linkSent = isLinkAlreadySent(rc);
        BigDecimal limit = rc.getMerchant() != null ? rc.getMerchant().getAutoActionLimit() : new BigDecimal("10000.0000");
        int maxRetries = rc.getMerchant() != null ? rc.getMerchant().getMaxRetries() : 3;
        int window = rc.getMerchant() != null ? rc.getMerchant().getRecoveryWindowHours() : 48;
        return new PolicyContext(
                rc.getAmount(),
                gatewayCode,
                rc.getAttemptCount() != null ? rc.getAttemptCount() : 0,
                elapsed,
                optedOut,
                linkSent,
                action,
                limit,
                maxRetries,
                window
        );
    }
}
