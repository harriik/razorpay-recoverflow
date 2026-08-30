-- Phase 1 baseline schema - health check only, no business tables yet
-- Business tables (merchants, customers, payments, recovery_cases etc.) will be added in Phase 2
-- This migration validates Flyway + PostgreSQL connectivity

CREATE TABLE IF NOT EXISTS schema_version_check (
    id SERIAL PRIMARY KEY,
    version VARCHAR(50) NOT NULL,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO schema_version_check (version) VALUES ('V1 Phase 1 bootstrap');
