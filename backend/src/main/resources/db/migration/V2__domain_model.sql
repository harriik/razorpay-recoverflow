-- V2: Domain model for Phase 2 — merchants, customers, payments, recovery_cases, recovery_actions, audit_events
-- Approved Phase 0 schema, NUMERIC(19,4) for money, UUID PKs, FKs, UCs, indexes, optimistic locking

-- merchants
CREATE TABLE IF NOT EXISTS merchants (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    auto_action_limit NUMERIC(19,4) NOT NULL DEFAULT 10000.00,
    max_retries INT NOT NULL DEFAULT 3,
    recovery_window_hours INT NOT NULL DEFAULT 48,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- customers
CREATE TABLE IF NOT EXISTS customers (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    email TEXT,
    phone TEXT,
    opted_out BOOLEAN NOT NULL DEFAULT FALSE,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,
    last_success_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_customers_merchant ON customers(merchant_id);

-- payments
CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    customer_id UUID NOT NULL REFERENCES customers(id),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    method TEXT NOT NULL CHECK (method IN ('CARD','UPI','NB','WALLET')),
    status TEXT NOT NULL CHECK (status IN ('FAILED','SUCCESS')),
    gateway_code TEXT,
    gateway_ref TEXT UNIQUE,
    failed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_payments_merchant_failed ON payments(merchant_id, failed_at);

-- recovery_cases
CREATE TABLE IF NOT EXISTS recovery_cases (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL UNIQUE REFERENCES payments(id),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    customer_id UUID NOT NULL REFERENCES customers(id),
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    failure_code TEXT,
    failure_category TEXT,
    status TEXT NOT NULL CHECK (status IN (
        'DETECTED','CLASSIFYING','ELIGIBILITY_CHECK','AI_ANALYSIS',
        'ACTION_EVALUATION','POLICY_EVALUATION','ACTION_APPROVED',
        'EXECUTING','RETRY_PENDING','ACTION_FAILED','RECOVERED','FAILED_TERMINAL','ESCALATED','STOPPED','UNKNOWN'
    )),
    attempt_count INT NOT NULL DEFAULT 0,
    recovered_amount NUMERIC(19,4),
    escalated_reason TEXT,
    stopped_reason TEXT,
    unknown_since TIMESTAMPTZ,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cases_status ON recovery_cases(status);
CREATE INDEX IF NOT EXISTS idx_cases_merchant_created ON recovery_cases(merchant_id, created_at);

-- recovery_actions
CREATE TABLE IF NOT EXISTS recovery_actions (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES recovery_cases(id),
    action_type TEXT NOT NULL CHECK (action_type IN ('RETRY_NOW','SCHEDULE_RETRY','SEND_PAYMENT_LINK','SEND_REMINDER','ESCALATE','STOP')),
    status TEXT NOT NULL CHECK (status IN ('PENDING','SUCCESS','FAILED','UNKNOWN')),
    idempotency_key TEXT NOT NULL UNIQUE,
    gateway_ref TEXT,
    estimated_likelihood NUMERIC(4,3),
    expected_value NUMERIC(19,4),
    cost_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    synthetic_friction_proxy NUMERIC(19,4) NOT NULL DEFAULT 0,
    risk_penalty NUMERIC(19,4) NOT NULL DEFAULT 0,
    executed_at TIMESTAMPTZ,
    observed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_actions_case ON recovery_actions(case_id);
CREATE INDEX IF NOT EXISTS idx_actions_idem ON recovery_actions(idempotency_key);

-- audit_events (append-only)
CREATE TABLE IF NOT EXISTS audit_events (
    id UUID PRIMARY KEY,
    correlation_id UUID NOT NULL,
    case_id UUID NOT NULL REFERENCES recovery_cases(id),
    payment_id UUID,
    merchant_id UUID,
    event_type TEXT NOT NULL,
    from_state TEXT,
    to_state TEXT,
    actor TEXT NOT NULL CHECK (actor IN ('SYSTEM','AI','POLICY','GATEWAY','USER')),
    payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audit_case ON audit_events(case_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_corr ON audit_events(correlation_id);
