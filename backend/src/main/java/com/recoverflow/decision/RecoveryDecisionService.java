package com.recoverflow.decision;

import com.recoverflow.ai.AiAssessment;
import com.recoverflow.decision.ExpectedNetRecoveryValueEngine.RankedCandidate;
import com.recoverflow.likelihood.InterventionLikelihoodEstimator;
import com.recoverflow.likelihood.ObservableContext;
import com.recoverflow.policy.PolicyContext;
import com.recoverflow.policy.PolicyDecision;
import com.recoverflow.policy.PolicyDecisionType;
import com.recoverflow.policy.PolicyEngine;
import com.recoverflow.recovery.RecoveryActionType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Orchestrates fresh decisioning after ACTION_FAILED or for initial selection.
 * Flow: refresh observable -> recalculate P_estimated -> recalculate EV -> re-run Policy -> select best permissible.
 * Previous failed actions are excluded unless explicitly allowed (none in Phase 5).
 */
@Service
public class RecoveryDecisionService {

    private final InterventionLikelihoodEstimator estimator;
    private final ExpectedNetRecoveryValueEngine evEngine;
    private final PolicyEngine policyEngine;

    public RecoveryDecisionService(InterventionLikelihoodEstimator estimator,
                                   ExpectedNetRecoveryValueEngine evEngine,
                                   PolicyEngine policyEngine) {
        this.estimator = estimator;
        this.evEngine = evEngine;
        this.policyEngine = policyEngine;
    }

    public DecisionResult decide(ObservableContext obs, AiAssessment ai,
                                 BigDecimal amount, PolicyContext basePolicyCtx,
                                 Set<RecoveryActionType> alreadyFailed) {

        // 1. Recalculate P_estimated with fresh observable + AI
        Map<RecoveryActionType, BigDecimal> pEstimated = (ai != null)
                ? estimator.estimate(obs, ai)
                : estimator.estimateObservableOnly(obs);

        // 2. Exclude already failed actions (must not be selected again without explicit reason)
        Map<RecoveryActionType, BigDecimal> filteredP = pEstimated.entrySet().stream()
                .filter(e -> !alreadyFailed.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (filteredP.isEmpty()) {
            // No candidates left
            return new DecisionResult(List.of(), List.of(), null, null, "no_candidates_after_excluding_failed");
        }

        // 3. Recalculate EV ranking (policy-independent)
        // Need risk level for EV: use AI riskLevel if available, else LOW
        var risk = ai != null ? ai.riskLevel() : com.recoverflow.ai.RiskLevel.LOW;
        List<RankedCandidate> ranked = evEngine.rank(amount, filteredP, risk);

        // 4. Re-run Policy for each ranked candidate with fresh policy context
        // Build fresh PolicyContext per candidate using current basePolicyCtx (which reflects updated attemptCount/elapsed etc.)
        List<PolicyDecision> decisions = new ArrayList<>();
        for (RankedCandidate rc : ranked) {
            PolicyContext ctx = new PolicyContext(
                    basePolicyCtx.amount(), basePolicyCtx.gatewayCode(), basePolicyCtx.attemptCount(),
                    basePolicyCtx.elapsedHours(), basePolicyCtx.optedOut(), basePolicyCtx.linkAlreadySent(),
                    rc.action(), basePolicyCtx.autoActionLimit(), basePolicyCtx.maxRetries(), basePolicyCtx.recoveryWindowHours());
            PolicyDecision d = policyEngine.evaluate(ctx);
            decisions.add(d);
        }

        // 5. Select highest-value permissible
        for (int i = 0; i < ranked.size(); i++) {
            if (decisions.get(i).result() == PolicyDecisionType.ALLOWED) {
                return new DecisionResult(ranked, decisions, ranked.get(i), decisions.get(i), "selected_highest_allowed");
            }
        }

        // No allowed: check escalate vs stop
        boolean hasEscalate = decisions.stream().anyMatch(d -> d.result() == PolicyDecisionType.ESCALATE);
        PolicyDecision overall = new PolicyDecision(null, hasEscalate ? PolicyDecisionType.ESCALATE : PolicyDecisionType.STOP,
                null, hasEscalate ? "all_blocked_escalate" : "all_blocked_stop", policyEngine.getVersion(), Map.of());
        return new DecisionResult(ranked, decisions, null, overall, "no_allowed_candidate");
    }

    public record DecisionResult(
            List<RankedCandidate> rankedCandidates,
            List<PolicyDecision> policyDecisions,
            RankedCandidate selected,
            PolicyDecision selectedPolicyDecision,
            String reason
    ) {
        public boolean hasSelection() { return selected != null; }
    }
}
