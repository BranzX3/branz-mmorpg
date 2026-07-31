CREATE TABLE character_build_state (
    character_id UUID PRIMARY KEY,
    build_payload JSONB NOT NULL,
    content_version TEXT NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 1),
    last_transaction_id UUID NOT NULL REFERENCES transaction_journal(transaction_id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (updated_at >= created_at)
);

CREATE INDEX character_build_state_updated_idx
    ON character_build_state(updated_at);
