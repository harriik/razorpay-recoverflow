-- V3: Execution boundary fields — approved action and link tracking
ALTER TABLE recovery_cases ADD COLUMN IF NOT EXISTS approved_action TEXT
    CHECK (approved_action IN ('RETRY_NOW','SCHEDULE_RETRY','SEND_PAYMENT_LINK','SEND_REMINDER','ESCALATE','STOP'));

ALTER TABLE recovery_cases ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ;
ALTER TABLE recovery_cases ADD COLUMN IF NOT EXISTS approved_policy_version TEXT;
ALTER TABLE recovery_cases ADD COLUMN IF NOT EXISTS approved_policy_decision_id UUID;
ALTER TABLE recovery_cases ADD COLUMN IF NOT EXISTS approved_threshold_snapshot TEXT;

ALTER TABLE recovery_actions ADD COLUMN IF NOT EXISTS approved_policy_version TEXT;
