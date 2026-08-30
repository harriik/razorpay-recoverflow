package com.recoverflow.recovery;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Pure deterministic state machine for RecoveryCase.
 * All valid transitions are explicitly listed; any other pair is rejected.
 * Terminal states have no outgoing transitions.
 */
@Component
public class RecoveryCaseStateMachine {

    private static final Set<RecoveryCaseStatus> TERMINAL_STATES = EnumSet.of(
            RecoveryCaseStatus.RECOVERED,
            RecoveryCaseStatus.FAILED_TERMINAL,
            RecoveryCaseStatus.ESCALATED,
            RecoveryCaseStatus.STOPPED
    );

    private static final Map<RecoveryCaseStatus, Set<RecoveryCaseStatus>> ALLOWED;

    static {
        Map<RecoveryCaseStatus, Set<RecoveryCaseStatus>> m = new EnumMap<>(RecoveryCaseStatus.class);
        m.put(RecoveryCaseStatus.DETECTED, EnumSet.of(RecoveryCaseStatus.CLASSIFYING));
        m.put(RecoveryCaseStatus.CLASSIFYING, EnumSet.of(RecoveryCaseStatus.ELIGIBILITY_CHECK));
        m.put(RecoveryCaseStatus.ELIGIBILITY_CHECK, EnumSet.of(
                RecoveryCaseStatus.AI_ANALYSIS,
                RecoveryCaseStatus.STOPPED,
                RecoveryCaseStatus.ESCALATED));
        m.put(RecoveryCaseStatus.AI_ANALYSIS, EnumSet.of(
                RecoveryCaseStatus.ACTION_EVALUATION,
                RecoveryCaseStatus.ESCALATED));
        m.put(RecoveryCaseStatus.ACTION_EVALUATION, EnumSet.of(RecoveryCaseStatus.POLICY_EVALUATION));
        m.put(RecoveryCaseStatus.POLICY_EVALUATION, EnumSet.of(
                RecoveryCaseStatus.ACTION_APPROVED,
                RecoveryCaseStatus.ESCALATED,
                RecoveryCaseStatus.STOPPED,
                RecoveryCaseStatus.FAILED_TERMINAL));
        m.put(RecoveryCaseStatus.ACTION_APPROVED, EnumSet.of(RecoveryCaseStatus.EXECUTING));
        m.put(RecoveryCaseStatus.EXECUTING, EnumSet.of(
                RecoveryCaseStatus.RECOVERED,
                RecoveryCaseStatus.ACTION_FAILED,
                RecoveryCaseStatus.FAILED_TERMINAL,
                RecoveryCaseStatus.RETRY_PENDING,
                RecoveryCaseStatus.UNKNOWN));
        m.put(RecoveryCaseStatus.RETRY_PENDING, EnumSet.of(
                RecoveryCaseStatus.EXECUTING,
                RecoveryCaseStatus.STOPPED,
                RecoveryCaseStatus.ESCALATED,
                RecoveryCaseStatus.FAILED_TERMINAL));
        m.put(RecoveryCaseStatus.ACTION_FAILED, EnumSet.of(
                RecoveryCaseStatus.ACTION_EVALUATION,
                RecoveryCaseStatus.ESCALATED,
                RecoveryCaseStatus.STOPPED,
                RecoveryCaseStatus.FAILED_TERMINAL));
        m.put(RecoveryCaseStatus.UNKNOWN, EnumSet.of(
                RecoveryCaseStatus.RECOVERED,
                RecoveryCaseStatus.ACTION_FAILED,
                RecoveryCaseStatus.FAILED_TERMINAL,
                RecoveryCaseStatus.ESCALATED,
                RecoveryCaseStatus.EXECUTING));
        // Terminal states have no outgoing - not put in map
        ALLOWED = Collections.unmodifiableMap(m);
    }

    public boolean isTerminal(RecoveryCaseStatus status) {
        return TERMINAL_STATES.contains(status);
    }

    public Set<RecoveryCaseStatus> allowedFrom(RecoveryCaseStatus from) {
        return ALLOWED.getOrDefault(from, Collections.emptySet());
    }

    public boolean isAllowed(RecoveryCaseStatus from, RecoveryCaseStatus to) {
        return allowedFrom(from).contains(to);
    }

    public void validate(RecoveryCaseStatus from, RecoveryCaseStatus to) {
        if (!isAllowed(from, to)) {
            throw new InvalidTransitionException(from, to);
        }
    }

    public Set<RecoveryCaseStatus> terminalStates() {
        return Collections.unmodifiableSet(TERMINAL_STATES);
    }

    public Map<RecoveryCaseStatus, Set<RecoveryCaseStatus>> allAllowed() {
        return ALLOWED;
    }
}
