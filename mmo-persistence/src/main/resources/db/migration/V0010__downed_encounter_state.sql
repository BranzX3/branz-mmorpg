CREATE TABLE downed_encounter_state (
    encounter_id UUID PRIMARY KEY REFERENCES boss_encounter_state(encounter_id) ON DELETE CASCADE,
    attempt INTEGER NOT NULL CHECK (attempt > 0),
    recoverable BOOLEAN NOT NULL,
    state_payload JSONB NOT NULL CHECK (jsonb_typeof(state_payload) = 'object'),
    content_version TEXT NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    last_transaction_id UUID NOT NULL REFERENCES transaction_journal(transaction_id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX downed_encounter_recovery_idx
    ON downed_encounter_state(updated_at, encounter_id)
    WHERE recoverable;
