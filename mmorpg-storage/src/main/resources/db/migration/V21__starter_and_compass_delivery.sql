CREATE TABLE mmorpg_starter_kit_delivery (
    player_uuid BINARY(16) NOT NULL,
    selection_operation_id VARCHAR(128) NOT NULL,
    starter_plan_id VARCHAR(128) NOT NULL,
    starter_plan_revision INT NOT NULL,
    starter_weapon_id VARCHAR(128) NOT NULL,
    starter_additional_items JSON NOT NULL,
    state ENUM('PENDING', 'DELIVERED') NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP(6) NOT NULL,
    delivered_at TIMESTAMP(6) NULL,
    PRIMARY KEY (player_uuid),
    UNIQUE KEY uk_mmorpg_starter_delivery_operation (selection_operation_id),
    CONSTRAINT ck_mmorpg_starter_delivery_revision CHECK (starter_plan_revision >= 1),
    CONSTRAINT fk_mmorpg_starter_delivery_player FOREIGN KEY (player_uuid)
        REFERENCES mmorpg_player_profiles(player_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO mmorpg_starter_kit_delivery
    (player_uuid, selection_operation_id, starter_plan_id, starter_plan_revision,
     starter_weapon_id, starter_additional_items, state, created_at)
SELECT player_uuid, operation_id, starter_plan_id, starter_plan_revision,
       starter_weapon_id, starter_additional_items, 'PENDING', selected_at
FROM mmorpg_character_class_selection;

CREATE TABLE mmorpg_reserved_slot_pending_item (
    player_uuid BINARY(16) NOT NULL,
    delivery_uuid BINARY(16) NOT NULL,
    payload LONGBLOB NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (player_uuid),
    UNIQUE KEY uk_mmorpg_reserved_slot_delivery (delivery_uuid),
    CONSTRAINT fk_mmorpg_reserved_slot_player FOREIGN KEY (player_uuid)
        REFERENCES mmorpg_player_profiles(player_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
