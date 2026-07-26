CREATE TABLE mmorpg_character_class_progress (
    player_uuid BINARY(16) NOT NULL,
    class_id VARCHAR(128) NOT NULL,
    level INT NOT NULL DEFAULT 1,
    total_xp BIGINT NOT NULL DEFAULT 0,
    unspent_skill_points INT NOT NULL DEFAULT 0,
    tree_revision INT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (player_uuid),
    CONSTRAINT fk_mmorpg_class_progress_profile
        FOREIGN KEY (player_uuid) REFERENCES mmorpg_player_profiles (player_uuid)
        ON DELETE CASCADE,
    CONSTRAINT ck_mmorpg_class_progress_values CHECK (
        level >= 1 AND total_xp >= 0 AND unspent_skill_points >= 0 AND tree_revision >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_character_class_node_rank (
    player_uuid BINARY(16) NOT NULL,
    class_id VARCHAR(128) NOT NULL,
    node_id VARCHAR(128) NOT NULL,
    node_rank INT NOT NULL,
    PRIMARY KEY (player_uuid, class_id, node_id),
    CONSTRAINT fk_mmorpg_class_node_progress
        FOREIGN KEY (player_uuid) REFERENCES mmorpg_character_class_progress (player_uuid)
        ON DELETE CASCADE,
    CONSTRAINT ck_mmorpg_class_node_rank CHECK (node_rank >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
