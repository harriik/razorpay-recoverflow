-- V4: Clarify RETRY_PENDING semantics with pendingAction/pendingReason
-- RETRY_PENDING + SCHEDULE_RETRY vs RETRY_PENDING + PAYMENT_LINK are semantically different

ALTER TABLE recovery_cases ADD COLUMN IF NOT EXISTS pending_action TEXT
    CHECK (pending_action IN ('RETRY_NOW','SCHEDULE_RETRY','SEND_PAYMENT_LINK','SEND_REMINDER'));
ALTER TABLE recovery_cases ADD COLUMN IF NOT EXISTS pending_reason TEXT;
