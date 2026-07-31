CREATE TABLE item_instance (
    item_uuid UUID PRIMARY KEY,
    definition_id TEXT NOT NULL,
    owner_character_id UUID,
    location_type TEXT NOT NULL,
    location_ref TEXT,
    payload JSONB NOT NULL,
    content_version TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    last_transaction_id UUID NOT NULL REFERENCES transaction_journal(transaction_id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (updated_at >= created_at)
);

CREATE INDEX item_owner_location_idx
    ON item_instance(owner_character_id, location_type);

CREATE TABLE commodity_lot (
    lot_uuid UUID PRIMARY KEY,
    definition_id TEXT NOT NULL,
    variant TEXT NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity >= 0),
    owner_character_id UUID,
    location_type TEXT NOT NULL,
    location_ref TEXT,
    lineage JSONB NOT NULL,
    content_version TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    last_transaction_id UUID NOT NULL REFERENCES transaction_journal(transaction_id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (updated_at >= created_at)
);

CREATE INDEX lot_owner_location_idx
    ON commodity_lot(owner_character_id, location_type);
