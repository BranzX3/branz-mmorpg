CREATE TABLE resource_node_state (
    node_id UUID PRIMARY KEY,
    definition_id TEXT NOT NULL,
    phase TEXT NOT NULL,
    state_payload JSONB NOT NULL CHECK (jsonb_typeof(state_payload) = 'object'),
    content_version TEXT NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    last_transaction_id UUID NOT NULL REFERENCES transaction_journal(transaction_id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX resource_node_recovery_idx
    ON resource_node_state(phase, updated_at)
    WHERE phase <> 'AVAILABLE';

CREATE TABLE character_lifeskill_state (
    character_id UUID PRIMARY KEY,
    state_payload JSONB NOT NULL CHECK (jsonb_typeof(state_payload) = 'object'),
    content_version TEXT NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    last_transaction_id UUID NOT NULL REFERENCES transaction_journal(transaction_id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
