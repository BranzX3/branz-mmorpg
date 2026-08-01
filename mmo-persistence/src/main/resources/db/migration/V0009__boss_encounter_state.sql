CREATE TABLE boss_encounter_state (
    encounter_id UUID PRIMARY KEY,
    definition_id TEXT NOT NULL,
    phase TEXT NOT NULL,
    state_payload JSONB NOT NULL CHECK (jsonb_typeof(state_payload) = 'object'),
    content_version TEXT NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    last_transaction_id UUID NOT NULL REFERENCES transaction_journal(transaction_id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX boss_encounter_recovery_idx
    ON boss_encounter_state(phase, updated_at)
    WHERE phase <> 'COMPLETED';
