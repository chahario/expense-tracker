-- ─────────────────────────────────────────────────────────────────────────
-- V1: Initial schema
--
-- Design notes:
--   • amount is NUMERIC(19,2) — exact decimal, safe for money.
--     NEVER use FLOAT or DOUBLE for currency.
--   • id uses gen_random_uuid() — available natively in PostgreSQL 13+.
--   • created_at is TIMESTAMPTZ (time-zone-aware); stores UTC.
--   • date is DATE — the user-supplied transaction date, not server time.
--   • idempotency_records.expires_at: keys expire after 48h.
--     A background job (IdempotencyCleanupScheduler) prunes these.
-- ─────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS expenses (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    amount      NUMERIC(19, 2)  NOT NULL CHECK (amount > 0),
    category    VARCHAR(100)    NOT NULL,
    description VARCHAR(500)    NOT NULL,
    date        DATE            NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Primary access pattern: list all expenses newest-first
CREATE INDEX IF NOT EXISTS idx_expenses_date_desc
    ON expenses (date DESC, created_at DESC);

-- Filter by category (case-insensitive via LOWER())
CREATE INDEX IF NOT EXISTS idx_expenses_category_lower
    ON expenses (LOWER(category));

-- Composite: category filter + date sort in one index scan
CREATE INDEX IF NOT EXISTS idx_expenses_category_date
    ON expenses (LOWER(category), date DESC, created_at DESC);

-- ─────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS idempotency_records (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    expense_id      UUID         NOT NULL REFERENCES expenses (id) ON DELETE CASCADE,
    processed_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ  NOT NULL DEFAULT (NOW() + INTERVAL '48 hours')
);

-- Scheduler uses this index to efficiently delete expired keys
CREATE INDEX IF NOT EXISTS idx_idempotency_expires
    ON idempotency_records (expires_at);
