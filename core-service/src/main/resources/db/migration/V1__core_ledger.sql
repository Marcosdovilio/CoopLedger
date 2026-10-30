CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED')),
    balance NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_accounts_owner ON accounts(owner_id);

CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    source_account_id UUID NOT NULL REFERENCES accounts(id),
    destination_account_id UUID NOT NULL REFERENCES accounts(id),
    actor_id VARCHAR(100) NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    description VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status = 'COMPLETED'),
    completed_at TIMESTAMPTZ NOT NULL,
    CHECK (source_account_id <> destination_account_id)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    transfer_id UUID REFERENCES transfers(id),
    entry_type VARCHAR(20) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT', 'OPENING_CREDIT')),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (transfer_id, entry_type),
    CHECK ((transfer_id IS NULL AND entry_type = 'OPENING_CREDIT')
        OR (transfer_id IS NOT NULL AND entry_type IN ('DEBIT', 'CREDIT')))
);
CREATE INDEX ix_ledger_statement ON ledger_entries(account_id, created_at DESC, id DESC);

CREATE TABLE idempotency_keys (
    actor_id VARCHAR(100) NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    transfer_id UUID REFERENCES transfers(id),
    PRIMARY KEY (actor_id, request_key)
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL UNIQUE REFERENCES transfers(id),
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(500)
);
CREATE INDEX ix_outbox_pending ON outbox_events(next_attempt_at, created_at, id) WHERE published_at IS NULL;

CREATE TABLE reconciliation_runs (
    id UUID PRIMARY KEY,
    initiated_by VARCHAR(100) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    accounts_checked BIGINT NOT NULL DEFAULT 0,
    divergence_count BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE reconciliation_discrepancies (
    run_id UUID NOT NULL REFERENCES reconciliation_runs(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    stored_balance NUMERIC(19,2) NOT NULL,
    ledger_balance NUMERIC(38,2) NOT NULL,
    difference NUMERIC(38,2) NOT NULL,
    PRIMARY KEY (run_id, account_id)
);
