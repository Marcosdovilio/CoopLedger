CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE transfer_views (
    transfer_id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE REFERENCES processed_events(event_id),
    source_account_id UUID NOT NULL,
    destination_account_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    description VARCHAR(255) NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    CHECK (source_account_id <> destination_account_id)
);
CREATE INDEX ix_transfer_views_completed ON transfer_views(completed_at DESC, transfer_id DESC);
