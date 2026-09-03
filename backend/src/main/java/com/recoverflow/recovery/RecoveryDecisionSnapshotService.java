package com.recoverflow.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverflow.ai.AiAssessment;
import com.recoverflow.decision.ExpectedNetRecoveryValueEngine;
import com.recoverflow.decision.RecoveryDecisionService;
import com.recoverflow.likelihood.InterventionLikelihoodEstimator;
import com.recoverflow.likelihood.ObservableContext;
import com.recoverflow.payment.PaymentMethod;
import com.recoverflow.policy.PolicyConfig;
import com.recoverflow.policy.PolicyContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecoveryDecisionSnapshotService {

    private final RecoveryDecisionSnapshotRepository snapshotRepo;
    private final RecoveryCaseRepository caseRepo;
    private final InterventionLikelihoodEstimator estimator;
    private final ExpectedNetRecoveryValueEngine evEngine;
    private final RecoveryDecisionService decisionService;
    private final PolicyConfig policyConfig;
    private final ObjectMapper mapper = new ObjectMapper();

    public RecoveryDecisionSnapshotService(RecoveryDecisionSnapshotRepository snapshotRepo,
                                           RecoveryCaseRepository caseRepo,
                                           InterventionLikelihoodEstimator estimator,
                                           ExpectedNetRecoveryValueEngine evEngine,
                                           RecoveryDecisionService decisionService,
                                           PolicyConfig policyConfig) {
        this.snapshotRepo = snapshotRepo;
        this.caseRepo = caseRepo;
        this.estimator = estimator;
        this.evEngine = evEngine;
        this.decisionService = decisionService;
        this.policyConfig = policyConfig;
    }

    /**
     * Persist the actual decision snapshot at decision finalization time.
     * Captures observable, AI assessment, candidates with P/EV/policy, selected, versions.
     * Never stores HiddenTruth/P_true.
     */
    @Transactional
    public RecoveryDecisionSnapshot persistSnapshot(UUID caseId, ObservableContext obs, AiAssessment aiAssessment, String aiProvider, String aiModel) {
        RecoveryCase rc = caseRepo.findById(caseId).orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
        // Build observable snapshot (only observable fields)
        Map<String, Object> obsMap = new LinkedHashMap<>();
        obsMap.put("amount", obs.amount().toPlainString());
        obsMap.put("currency", obs.currency());
        obsMap.put("paymentMethod", obs.method().name());
        obsMap.put("gatewayCode", obs.gatewayCode());
        obsMap.put("failedAt", null); // not in ObservableContext, use elapsed
        obsMap.put("elapsedHours", obs.elapsedHours());
        obsMap.put("attemptCount", obs.attemptCount());
        obsMap.put("priorSuccessCount", obs.priorSuccessCount());
        obsMap.put("priorFailureCount", obs.priorFailureCount());
        obsMap.put("linkAlreadySent", obs.linkAlreadySent());
        obsMap.put("amountBucket", obs.amountBucket());
        obsMap.put("historyBucket", obs.historyBucket());
        obsMap.put("elapsedBucket", obs.elapsedBucket());

        // AI assessment snapshot (actual, not fabricated)
        String aiJson = null;
        if (aiAssessment != null) {
            try {
                Map<String, Object> aiMap = new LinkedHashMap<>();
                aiMap.put("failureCategory", aiAssessment.failureCategory().name());
                aiMap.put("recoverability", aiAssessment.recoverability().name());
                aiMap.put("candidateAssessments", aiAssessment.candidateAssessments().stream().map(ca -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("action", ca.action().name());
                    m.put("assessment", ca.assessment() != null ? ca.assessment().name() : null);
                    m.put("applicable", ca.applicable());
                    m.put("reason", ca.inapplicableReason());
                    return m;
                }).toList());
                aiMap.put("recommendedAction", aiAssessment.recommendedAction().name());
                aiMap.put("evidenceQuality", aiAssessment.evidenceQuality().name());
                aiMap.put("riskLevel", aiAssessment.riskLevel().name());
                aiMap.put("reasoningSummary", aiAssessment.reasoningSummary());
                aiMap.put("provider", aiProvider);
                aiMap.put("modelId", aiModel);
                aiMap.put("promptVersion", aiAssessment.promptVersion());
                aiJson = mapper.writeValueAsString(aiMap);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize AI assessment", e);
            }
        }

        // Candidate snapshot: actual P_estimated, EV, policy for each candidate at decision time
        var decision = decisionService.decide(obs, aiAssessment, rc.getAmount(), toPolicyContext(rc, obs), Set.of());
        List<Map<String, Object>> candidateList = new ArrayList<>();
        for (int i = 0; i < decision.rankedCandidates().size(); i++) {
            var ranked = decision.rankedCandidates().get(i);
            var policy = decision.policyDecisions().get(i);
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("action", ranked.action().name());
            c.put("pEstimated", ranked.likelihood().toPlainString());
            c.put("expectedNetValue", ranked.expectedNet().toPlainString());
            c.put("operationalCost", ranked.cost().toPlainString());
            c.put("syntheticCustomerFrictionProxy", ranked.syntheticFrictionProxy().toPlainString());
            c.put("riskPenalty", ranked.riskPenalty().toPlainString());
            c.put("policyResult", policy.result().name());
            c.put("policyRuleId", policy.blockingRule() != null ? policy.blockingRule().name() : null);
            c.put("policyReason", policy.reason());
            // Estimator breakdown not persisted separately, just P and EV
            candidateList.add(c);
        }
        String candidateJson;
        String policyJson;
        try {
            candidateJson = mapper.writeValueAsString(candidateList);
            List<Map<String, Object>> policyList = decision.policyDecisions().stream().map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("action", d.action() != null ? d.action().name() : null);
                m.put("result", d.result().name());
                m.put("ruleId", d.blockingRule() != null ? d.blockingRule().name() : null);
                m.put("reason", d.reason());
                m.put("thresholdSnapshot", d.thresholdSnapshot());
                return m;
            }).toList();
            policyJson = mapper.writeValueAsString(policyList);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize candidates/policy", e);
        }

        String observableJson;
        try {
            observableJson = mapper.writeValueAsString(obsMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize observable", e);
        }

        String selectedAction = decision.hasSelection() ? decision.selected().action().name() : null;
        String selectionReason = decision.reason();
        BigDecimal selectedEV = decision.hasSelection() ? decision.selected().expectedNet() : null;
        Instant now = Instant.now();

        RecoveryDecisionSnapshot snap = new RecoveryDecisionSnapshot(
                UUID.randomUUID(), caseId, now,
                observableJson, aiJson, candidateJson, policyJson,
                selectedAction, selectionReason, selectedEV, now,
                "v1", policyConfig.getVersion(), aiProvider, aiModel,
                "ev-v1", "decision-v1"
        );
        return snapshotRepo.save(snap);
    }

    public Optional<RecoveryDecisionSnapshot> findLatest(UUID caseId) {
        return snapshotRepo.findTopByCaseIdOrderByCreatedAtDesc(caseId);
    }

    private PolicyContext toPolicyContext(RecoveryCase rc, ObservableContext obs) {
        String gatewayCode = rc.getFailureCode() != null ? rc.getFailureCode() : "UNKNOWN";
        int elapsed = obs.elapsedHours();
        boolean optedOut = rc.getCustomer() != null && Boolean.TRUE.equals(rc.getCustomer().getOptedOut());
        boolean linkSent = obs.linkAlreadySent();
        BigDecimal limit = rc.getMerchant() != null ? rc.getMerchant().getAutoActionLimit() : new BigDecimal("10000.0000");
        int maxRetries = rc.getMerchant() != null ? rc.getMerchant().getMaxRetries() : 3;
        int window = rc.getMerchant() != null ? rc.getMerchant().getRecoveryWindowHours() : 48;
        // Use a dummy candidate action for base context; decisionService will re-evaluate per candidate
        return new PolicyContext(
                rc.getAmount(),
                gatewayCode,
                rc.getAttemptCount() != null ? rc.getAttemptCount() : 0,
                elapsed,
                optedOut,
                linkSent,
                RecoveryActionType.RETRY_NOW,
                limit, maxRetries, window
        );
    }
}
