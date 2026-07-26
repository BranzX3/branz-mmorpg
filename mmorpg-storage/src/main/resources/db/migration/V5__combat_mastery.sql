CREATE TABLE mmorpg_combat_mastery (
    player_uuid BINARY(16) NOT NULL,
    mastery_id VARCHAR(128) NOT NULL,
    level INT NOT NULL DEFAULT 1,
    total_xp BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (player_uuid, mastery_id),
    KEY idx_mmorpg_mastery_leaderboard (mastery_id, total_xp),
    CONSTRAINT ck_mmorpg_mastery_level CHECK (level >= 1),
    CONSTRAINT ck_mmorpg_mastery_xp CHECK (total_xp >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
