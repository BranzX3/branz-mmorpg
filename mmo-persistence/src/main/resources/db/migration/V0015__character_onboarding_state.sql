CREATE TABLE character_onboarding_state (
    character_id UUID PRIMARY KEY,
    foundation_id TEXT NOT NULL CHECK (
        foundation_id IN ('GREATSWORD', 'SWORD_AND_SHIELD', 'BOW', 'STAFF_EMBER')
    ),
    kit_ready BOOLEAN NOT NULL DEFAULT FALSE,
    content_version TEXT NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    last_transaction_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX character_onboarding_kit_ready_idx
    ON character_onboarding_state (kit_ready, updated_at);
