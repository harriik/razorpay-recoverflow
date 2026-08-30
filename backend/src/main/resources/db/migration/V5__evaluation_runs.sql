-- V5: Evaluation runs for Phase 6 — stores evaluation results, not hidden ground truth
CREATE TABLE IF NOT EXISTS evaluation_runs (
    id UUID PRIMARY KEY,
    seed BIGINT NOT NULL,
    dataset_size INT NOT NULL,
    estimator_version TEXT NOT NULL,
    policy_version TEXT NOT NULL,
    synthetic_registry_version TEXT NOT NULL,
    config_snapshot TEXT, -- JSON
    baseline_a_result TEXT, -- JSON
    baseline_b_result TEXT, -- JSON
    recoverflow_result TEXT, -- JSON
    ablation_summary TEXT, -- JSON
    incremental_revenue_a NUMERIC(19,4),
    incremental_revenue_b NUMERIC(19,4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_eval_seed ON evaluation_runs(seed);
CREATE INDEX IF NOT EXISTS idx_eval_created ON evaluation_runs(created_at);
