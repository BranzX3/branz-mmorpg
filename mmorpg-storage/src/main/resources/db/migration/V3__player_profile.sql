-- Player profile persistence (C1).
--
-- last_known_name is presentation metadata only; every key and lookup uses
-- player_uuid. Settings live in their own table rather than a JSON blob so a
-- single preference can be read, written, and indexed without rewriting the
-- whole document.

CREATE TABLE mmorpg_player_profile (
    player_uuid BINARY(16) NOT NULL,
    last_known_name VARCHAR(32) NOT NULL DEFAULT '',
    schema_version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_seen_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    selected_loadout_id VARCHAR(128) NULL,
    respawn_point_id VARCHAR(128) NULL,
    PRIMARY KEY (player_uuid),
    KEY idx_mmorpg_player_last_seen (last_seen_at),
    CONSTRAINT ck_mmorpg_player_schema CHECK (schema_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_player_setting (
    player_uuid BINARY(16) NOT NULL,
    setting_key VARCHAR(64) NOT NULL,
    setting_value VARCHAR(255) NOT NULL,
    PRIMARY KEY (player_uuid, setting_key),
    CONSTRAINT fk_mmorpg_player_setting_profile
        FOREIGN KEY (player_uuid) REFERENCES mmorpg_player_profile (player_uuid)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
