CREATE TABLE mmorpg_trade (
    trade_uuid BINARY(16) NOT NULL,
    requester_uuid BINARY(16) NOT NULL,
    recipient_uuid BINARY(16) NOT NULL,
    trade_state VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    trade_revision BIGINT NOT NULL,
    PRIMARY KEY (trade_uuid),
    KEY idx_mmorpg_trade_recovery (trade_state, expires_at),
    KEY idx_mmorpg_trade_requester (requester_uuid, trade_state),
    KEY idx_mmorpg_trade_recipient (recipient_uuid, trade_state),
    CONSTRAINT ck_mmorpg_trade_participants CHECK (requester_uuid <> recipient_uuid),
    CONSTRAINT ck_mmorpg_trade_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_mmorpg_trade_revision CHECK (trade_revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_trade_confirmation (
    trade_uuid BINARY(16) NOT NULL,
    player_uuid BINARY(16) NOT NULL,
    PRIMARY KEY (trade_uuid, player_uuid),
    CONSTRAINT fk_mmorpg_trade_confirmation FOREIGN KEY (trade_uuid)
        REFERENCES mmorpg_trade(trade_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_trade_participant_lock (
    player_uuid BINARY(16) NOT NULL,
    trade_uuid BINARY(16) NOT NULL,
    PRIMARY KEY (player_uuid),
    KEY idx_mmorpg_trade_lock_trade (trade_uuid),
    CONSTRAINT fk_mmorpg_trade_participant_lock FOREIGN KEY (trade_uuid)
        REFERENCES mmorpg_trade(trade_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_trade_escrow_material (
    trade_uuid BINARY(16) NOT NULL,
    owner_uuid BINARY(16) NOT NULL,
    definition_id VARCHAR(128) NOT NULL,
    quantity BIGINT NOT NULL,
    PRIMARY KEY (trade_uuid, owner_uuid, definition_id),
    CONSTRAINT fk_mmorpg_trade_material FOREIGN KEY (trade_uuid)
        REFERENCES mmorpg_trade(trade_uuid) ON DELETE CASCADE,
    CONSTRAINT ck_mmorpg_trade_material_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_trade_escrow_item (
    trade_uuid BINARY(16) NOT NULL,
    original_owner_uuid BINARY(16) NOT NULL,
    item_uuid BINARY(16) NOT NULL,
    definition_id VARCHAR(128) NOT NULL,
    category VARCHAR(32) NOT NULL,
    quality_seed BIGINT NOT NULL,
    bound_owner_uuid BINARY(16) NULL,
    durability INT NOT NULL,
    created_source VARCHAR(128) NOT NULL,
    schema_version INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (item_uuid),
    KEY idx_mmorpg_trade_item_trade_owner (trade_uuid, original_owner_uuid),
    CONSTRAINT fk_mmorpg_trade_item FOREIGN KEY (trade_uuid)
        REFERENCES mmorpg_trade(trade_uuid) ON DELETE CASCADE,
    CONSTRAINT ck_mmorpg_trade_item_durability CHECK (durability >= 0),
    CONSTRAINT ck_mmorpg_trade_item_schema CHECK (schema_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
