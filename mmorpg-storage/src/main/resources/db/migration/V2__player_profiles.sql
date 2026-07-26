CREATE TABLE mmorpg_player_profiles (
    player_uuid BINARY(16) NOT NULL,
    last_known_name VARCHAR(16) NOT NULL,
    schema_version INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    class_id VARCHAR(128) NULL,
    selected_loadout_id VARCHAR(128) NULL,
    respawn_point_id VARCHAR(128) NULL,
    settings_json JSON NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid),
    KEY idx_mmorpg_player_last_seen (last_seen_at),
    CONSTRAINT chk_mmorpg_player_schema_version CHECK (schema_version > 0),
    CONSTRAINT chk_mmorpg_player_revision CHECK (revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
