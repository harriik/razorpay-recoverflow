-- V6: Historical decision snapshot for auditability
-- Persists the actual AI assessment, candidate decisions, policy, selected action at decision finalization time
-- JSON snapshot columns, no HiddenTruth/P_true/oracle

CREATE TABLE IF NOT EXISTS recovery_decision_snapshots (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES recovery_cases(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    observable_snapshot TEXT NOT NULL,
    ai_assessment_snapshot TEXT,
    candidate_snapshot TEXT NOT NULL,
    policy_snapshot TEXT NOT NULL,
    selected_action TEXT,
    selection_reason TEXT,
    selected_expected_net_value NUMERIC(19,4),
    selection_timestamp TIMESTAMPTZ,
    estimator_version TEXT NOT NULL DEFAULT 'v1',
    policy_version TEXT NOT NULL DEFAULT 'v1',
    ai_provider TEXT,
    ai_model TEXT,
    ev_version TEXT NOT NULL DEFAULT 'ev-v1',
    decision_version TEXT NOT NULL DEFAULT 'decision-v1'
);
CREATE INDEX IF NOT EXISTS idx_decision_snapshots_case ON recovery_decision_snapshots(case_id, created_at);
CREATE INDEX IF NOT EXISTS idx_decision_snapshots_case_latest ON recovery_decision_snapshots(case_id, created_at DESC);
