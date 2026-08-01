CREATE TYPE personal_reward_grant_state AS ENUM (
    'FROZEN',
    'ROLLED',
    'DELIVERED'
);

CREATE TABLE personal_reward_grant (
    grant_id UUID PRIMARY KEY,
    encounter_id UUID NOT NULL REFERENCES boss_encounter_state(encounter_id) ON DELETE CASCADE,
    attempt INTEGER NOT NULL CHECK (attempt > 0),
    character_id UUID NOT NULL,
    roll_seed BIGINT NOT NULL,
    state personal_reward_grant_state NOT NULL,
    state_payload JSONB NOT NULL CHECK (jsonb_typeof(state_payload) = 'object'),
    content_version TEXT NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    last_transaction_id UUID NOT NULL REFERENCES transaction_journal(transaction_id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (encounter_id, attempt, character_id)
);

CREATE INDEX personal_reward_grant_pending_idx
    ON personal_reward_grant(updated_at, grant_id)
    WHERE state <> 'DELIVERED';
