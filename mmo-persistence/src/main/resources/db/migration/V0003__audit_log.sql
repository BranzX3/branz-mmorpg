CREATE TABLE audit_log (
    audit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transaction_journal(transaction_id),
    actor_character_id UUID,
    action_type TEXT NOT NULL,
    subject_type TEXT NOT NULL,
    subject_id UUID NOT NULL,
    details JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (transaction_id, action_type, subject_type, subject_id)
);

CREATE INDEX audit_log_subject_idx
    ON audit_log(subject_type, subject_id, created_at);

CREATE INDEX audit_log_transaction_idx
    ON audit_log(transaction_id, audit_id);
