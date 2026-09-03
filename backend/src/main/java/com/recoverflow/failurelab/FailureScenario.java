package com.recoverflow.failurelab;

import java.util.List;

/**
 * Contract for deterministic failure scenarios.
 * Each scenario exercises the real production path: setup -> decision/policy -> execution/reconciliation -> audit.
 */
public interface FailureScenario {

    String getScenarioId();

    String getName();

    String getDescription();

    Setup getSetup();

    Trigger getTrigger();

    ExpectedOutcome getExpectedOutcome();

    List<String> getExpectedStateTransitions();

    List<String> getExpectedAuditEvents();

    /**
     * Execute the scenario via real services. Must be deterministic and resettable.
     */
    FailureLabResult run();

    /**
     * Reset scenario fixtures to initial deterministic state.
     */
    void reset();

    record Setup(String description, String fixtureDetails) {}
    record Trigger(String description, String action) {}
    record ExpectedOutcome(String description, String caseStatus, String actionStatus) {}
}
