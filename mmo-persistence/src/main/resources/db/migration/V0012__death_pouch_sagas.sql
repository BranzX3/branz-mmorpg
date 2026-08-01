CREATE TYPE death_pouch_state AS ENUM (
    'PENDING_DEBIT',
    'ACTIVE',
    'RECOVERING',
    'RECOVERED',
    'EXPIRED'
);

CREATE TABLE death_pouch (
    pouch_id UUID PRIMARY KEY,
    death_id UUID NOT NULL UNIQUE,
    owner_character_id UUID NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    wallet_debit_operation_id UUID NOT NULL UNIQUE,
    wallet_credit_operation_id UUID NOT NULL UNIQUE,
    world_key TEXT NOT NULL CHECK (length(btrim(world_key)) > 0),
    location_x DOUBLE PRECISION NOT NULL,
    location_y DOUBLE PRECISION NOT NULL,
    location_z DOUBLE PRECISION NOT NULL,
    state death_pouch_state NOT NULL,
    state_payload JSONB NOT NULL CHECK (jsonb_typeof(state_payload) = 'object'),
    content_version TEXT NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    last_transaction_id UUID NOT NULL REFERENCES transaction_journal(transaction_id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > created_at),
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX death_pouch_owner_active_idx
    ON death_pouch(owner_character_id, created_at, pouch_id)
    WHERE state = 'ACTIVE';

CREATE INDEX death_pouch_recovery_idx
    ON death_pouch(updated_at, pouch_id)
    WHERE state IN ('PENDING_DEBIT', 'RECOVERING');

CREATE INDEX death_pouch_expiry_idx
    ON death_pouch(expires_at, pouch_id)
    WHERE state IN ('PENDING_DEBIT', 'ACTIVE');
