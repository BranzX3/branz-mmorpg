CREATE TABLE mmorpg_processed_operation (
    operation_id VARCHAR(128) NOT NULL,
    player_uuid BINARY(16) NOT NULL,
    subsystem VARCHAR(32) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (operation_id),
    KEY idx_mmorpg_operation_player_time (player_uuid, processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
