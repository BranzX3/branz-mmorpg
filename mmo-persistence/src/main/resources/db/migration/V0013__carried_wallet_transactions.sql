CREATE TYPE carried_wallet_operation_kind AS ENUM ('CREDIT', 'DEBIT');

CREATE TABLE carried_wallet_account (
    character_id UUID PRIMARY KEY,
    balance BIGINT NOT NULL CHECK (balance >= 0),
    version BIGINT NOT NULL CHECK (version > 0),
    last_transaction_id UUID NOT NULL REFERENCES transaction_journal(transaction_id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE carried_wallet_operation (
    operation_id UUID PRIMARY KEY,
    character_id UUID NOT NULL REFERENCES carried_wallet_account(character_id),
    kind carried_wallet_operation_kind NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    resulting_balance BIGINT NOT NULL CHECK (resulting_balance >= 0),
    transaction_id UUID NOT NULL UNIQUE REFERENCES transaction_journal(transaction_id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX carried_wallet_operation_character_idx
    ON carried_wallet_operation(character_id, created_at, operation_id);
