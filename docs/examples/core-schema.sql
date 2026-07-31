CREATE TABLE character_session_lease (
    character_id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    server_instance TEXT NOT NULL,
    lease_version BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE item_instance (
    item_uuid UUID PRIMARY KEY,
    definition_id TEXT NOT NULL,
    owner_character_id UUID,
    location_type TEXT NOT NULL,
    location_ref TEXT,
    payload JSONB NOT NULL,
    content_version TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX item_owner_location_idx ON item_instance(owner_character_id, location_type);

CREATE TABLE commodity_lot (
    lot_uuid UUID PRIMARY KEY,
    definition_id TEXT NOT NULL,
    variant TEXT NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity >= 0),
    owner_character_id UUID,
    location_type TEXT NOT NULL,
    location_ref TEXT,
    lineage JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE transaction_journal (
    transaction_id UUID PRIMARY KEY,
    idempotency_key TEXT NOT NULL UNIQUE,
    operation_type TEXT NOT NULL,
    state TEXT NOT NULL,
    actor_character_id UUID,
    payload JSONB NOT NULL,
    content_version TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE market_order (
    order_id UUID PRIMARY KEY,
    character_id UUID NOT NULL,
    side TEXT NOT NULL,
    definition_id TEXT NOT NULL,
    variant TEXT NOT NULL,
    limit_price BIGINT NOT NULL CHECK (limit_price > 0),
    original_quantity BIGINT NOT NULL CHECK (original_quantity > 0),
    remaining_quantity BIGINT NOT NULL CHECK (remaining_quantity >= 0),
    state TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX market_match_idx ON market_order(definition_id, variant, side, state, limit_price, created_at);

CREATE TABLE worker_job (
    job_id UUID PRIMARY KEY,
    worker_uuid UUID NOT NULL,
    owner_character_id UUID NOT NULL,
    definition_id TEXT NOT NULL,
    state TEXT NOT NULL,
    started_at TIMESTAMPTZ,
    completes_at TIMESTAMPTZ,
    reservation_transaction_id UUID NOT NULL,
    completion_transaction_id UUID,
    payload JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
