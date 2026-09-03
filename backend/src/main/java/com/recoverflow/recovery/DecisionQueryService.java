package com.recoverflow.recovery;

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
    private final InterventionLikelihoodEstimator estimator;
    private final ExpectedNetRecoveryValueEngine evEngine;
    private final RecoveryDecisionService decisionService;
    private final PolicyConfig policyConfig;

    public DecisionQueryService(RecoveryCaseRepository caseRepo,
                                RecoveryActionRepository actionRepo,
                                AuditEventRepository auditRepo,
                                InterventionLikelihoodEstimator estimator,
                                ExpectedNetRecoveryValueEngine evEngine,
                                RecoveryDecisionService decisionService,
                                PolicyConfig policyConfig) {
        this.caseRepo = caseRepo;
        this.actionRepo = actionRepo;
        this.auditRepo = auditRepo;
        this.estimator = estimator;
        this.evEngine = evEngine;
        this.decisionService = decisionService;
        this.policyConfig = policyConfig;
    }

    public Map<String, Object> buildDecisionResponse(UUID caseId) {
        RecoveryCase rc = caseRepo.findById(caseId).orElseThrow();
        // Observable evidence – only approved observable fields, never HiddenTruth/P_true
        Map<String, Object> observable = new LinkedHashMap<>();
        observable.put("amount", rc.getAmount());
        observable.put("currency", rc.getCurrency());
        observable.put("paymentMethod", rc.getPayment() != null ? rc.getPayment().getMethod().name() : null);
        observable.put("gatewayCode", rc.getFailureCode());
        observable.put("failedAt", rc.getPayment() != null ? rc.getPayment().getFailedAt() : null);
        observable.put("elapsedHours", rc.getCreatedAt() != null ? java.time.Duration.between(rc.getCreatedAt(), Instant.now()).toHours() : 0);
        observable.put("attemptCount", rc.getAttemptCount());
        // Prior counts from customer
        observable.put("priorSuccessCount", rc.getCustomer() != null ? rc.getCustomer().getSuccessCount() : 0);
        observable.put("priorFailureCount", rc.getCustomer() != null ? rc.getCustomer().getFailureCount() : 0);
        observable.put("linkAlreadySent", isLinkAlreadySent(rc));

        // AI assessment – not yet persisted, return NOT_PERSISTED
        Map<String, Object> aiAssessment = new LinkedHashMap<>();
        aiAssessment.put("status", "NOT_PERSISTED");
        aiAssessment.put("description", "Historical AI assessment not yet persisted – persistence is the missing capability. Current decision is recomputed live, not reconstructed from history.");
        aiAssessment.put("note", "Do not fabricate AI assessment from gatewayCode. Actual assessment must be recorded during decision flow.");
        // Provide empty structure for frontend to handle
        aiAssessment.put("failureCategory", null);
        aiAssessment.put("recoverability", null);
        aiAssessment.put("candidateAssessments", null);
        aiAssessment.put("recommendedAction", null);
        aiAssessment.put("evidenceQuality", null);
        aiAssessment.put("riskLevel", null);
        aiAssessment.put("reasoningSummary", null);

        // Candidates – current recomputation via authoritative DecisionService (not historical fabrication)
        // We compute candidates on-the-fly for the current case state; this is live, not historical.
        ObservableContext obs = toObservableContext(rc);
        // Use null AI for now (since AI not persisted, we recompute with null to show policy-only baseline)
        // Alternatively, we could show with synthetic AI, but we keep it policy-only for now
        var decision = decisionService.decide(obs, null, rc.getAmount(), toPolicyContext(rc, RecoveryActionType.RETRY_NOW), Set.of());
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (int i = 0; i < decision.rankedCandidates().size(); i++) {
            var ranked = decision.rankedCandidates().get(i);
            var policy = decision.policyDecisions().get(i);
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("action", ranked.action().name());
            c.put("pEstimated", ranked.likelihood());
            c.put("expectedNetValue", ranked.expectedNet());
            c.put("operationalCost", ranked.cost());
            c.put("syntheticCustomerFrictionProxy", ranked.syntheticFrictionProxy());
            c.put("riskPenalty", ranked.riskPenalty());
            c.put("policyResult", policy.result().name());
            c.put("policyRuleId", policy.blockingRule() != null ? policy.blockingRule().name() : null);
            c.put("policyReason", policy.reason());
            // Estimator breakdown not persisted – do not fabricate
            c.put("baseContribution", null);
            c.put("aiContribution", null);
            c.put("evidenceModifier", null);
            c.put("recoverabilityModifier", null);
            c.put("historyModifier", null);
            c.put("elapsedModifier", null);
            c.put("finalP", ranked.likelihood());
            candidates.add(c);
        }

        // Selected action – from actual persisted decision if available, otherwise from current decision
        String selectedAction = rc.getApprovedAction();
        String selectionReason = decision.reason();
        BigDecimal selectedEV = decision.hasSelection() ? decision.selected().expectedNet() : null;
        Instant selectionTimestamp = rc.getApprovedAt();

        // Policy summary
        Map<String, Object> policySummary = new LinkedHashMap<>();
        policySummary.put("selectedPolicyDecision", decision.selectedPolicyDecision() != null ? decision.selectedPolicyDecision().result().name() : null);
        policySummary.put("selectedRuleId", decision.selectedPolicyDecision() != null && decision.selectedPolicyDecision().blockingRule() != null ? decision.selectedPolicyDecision().blockingRule().name() : null);
        policySummary.put("selectedReason", decision.selectedPolicyDecision() != null ? decision.selectedPolicyDecision().reason() : null);
        policySummary.put("allDecisions", decision.policyDecisions().stream().map(d -> Map.of(
                "action", d.action() != null ? d.action().name() : "NONE",
                "result", d.result().name(),
                "ruleId", d.blockingRule() != null ? d.blockingRule().name() : null,
                "reason", d.reason()
        )).collect(Collectors.toList()));

        // Versions – essential for auditability
        Map<String, Object> versions = new LinkedHashMap<>();
        versions.put("estimatorVersion", "v1");
        versions.put("policyVersion", policyConfig.getVersion());
        versions.put("aiProvider", "SYNTHETIC_AI_PROXY");
        versions.put("aiModel", "synthetic-ai-v1");
        versions.put("decisionVersion", "decision-v1");
        versions.put("evVersion", "ev-v1");

        // Audit reference
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
        response.put("selectedAction", selectedAction);
        response.put("selectionReason", selectionReason);
        response.put("selectedExpectedNetValue", selectedEV);
        response.put("selectionTimestamp", selectionTimestamp);
        response.put("policySummary", policySummary);
        response.put("versions", versions);
        response.put("auditReference", auditRef);
        // Also include top-level for frontend convenience
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
