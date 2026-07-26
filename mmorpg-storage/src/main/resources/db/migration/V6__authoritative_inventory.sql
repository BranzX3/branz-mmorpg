CREATE TABLE mmorpg_inventory (
    player_uuid BINARY(16) NOT NULL,
    slot_capacity INT NOT NULL DEFAULT 36,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (player_uuid),
    CONSTRAINT ck_mmorpg_inventory_capacity CHECK (slot_capacity >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_inventory_material (
    player_uuid BINARY(16) NOT NULL,
    definition_id VARCHAR(128) NOT NULL,
    location ENUM('INVENTORY', 'PENDING') NOT NULL,
    quantity BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, definition_id, location),
    CONSTRAINT ck_mmorpg_inventory_material_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_item_instance (
    item_uuid BINARY(16) NOT NULL,
    owner_uuid BINARY(16) NOT NULL,
    definition_id VARCHAR(128) NOT NULL,
    category VARCHAR(32) NOT NULL,
    quality_seed BIGINT NOT NULL,
    bound_owner_uuid BINARY(16) NULL,
    durability INT NOT NULL,
    created_source VARCHAR(128) NOT NULL,
    schema_version INT NOT NULL,
    location ENUM('INVENTORY', 'PENDING') NOT NULL,
    equipped_slot VARCHAR(32) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (item_uuid),
    KEY idx_mmorpg_item_owner_location (owner_uuid, location),
    CONSTRAINT ck_mmorpg_item_durability CHECK (durability >= 0),
    CONSTRAINT ck_mmorpg_item_schema CHECK (schema_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
