CREATE TABLE character_leases (
    character_id UUID PRIMARY KEY,
    server_instance TEXT NOT NULL,
    session_id UUID NOT NULL UNIQUE,
    version BIGINT NOT NULL CHECK (version >= 1),
    acquired_at TIMESTAMPTZ NOT NULL,
    heartbeat_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CHECK (expires_at > heartbeat_at)
);

CREATE INDEX character_leases_expiry_idx ON character_leases (expires_at);

CREATE TYPE mmo_transaction_state AS ENUM (
    'PREPARED',
    'COMMITTED',
    'ROLLED_BACK',
    'QUARANTINED'
);

CREATE TABLE transaction_journal (
    transaction_id UUID PRIMARY KEY,
    idempotency_key TEXT NOT NULL UNIQUE,
    character_id UUID,
    session_id UUID,
    operation_type TEXT NOT NULL,
    state mmo_transaction_state NOT NULL,
    reserved_inputs JSONB NOT NULL,
    intended_outputs JSONB NOT NULL,
    content_version TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (updated_at >= created_at)
);

CREATE INDEX transaction_journal_state_updated_idx
    ON transaction_journal (state, updated_at);
