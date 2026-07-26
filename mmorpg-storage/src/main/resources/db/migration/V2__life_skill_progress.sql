-- Life Skill Mastery persistence model (S0).
--
-- Progress is keyed by player UUID and skill ID; mastery ranks by player, skill,
-- and node. Both are written only by the progression engine, inside the same
-- transaction as the harvest or purchase that caused them.
--
-- player_uuid is BINARY(16) to match mmorpg_audit_log and to keep the primary
-- key narrow: these tables are read on every login and joined per skill.

CREATE TABLE mmorpg_life_skill_progress (
    player_uuid BINARY(16) NOT NULL,
    skill_id VARCHAR(128) NOT NULL,
    level INT NOT NULL DEFAULT 1,
    total_xp BIGINT NOT NULL DEFAULT 0,
    unspent_points INT NOT NULL DEFAULT 0,
    tree_revision BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (player_uuid, skill_id),
    KEY idx_mmorpg_life_skill_leaderboard (skill_id, total_xp),
    CONSTRAINT ck_mmorpg_life_skill_level CHECK (level >= 1),
    CONSTRAINT ck_mmorpg_life_skill_xp CHECK (total_xp >= 0),
    CONSTRAINT ck_mmorpg_life_skill_points CHECK (unspent_points >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_life_skill_node_rank (
    player_uuid BINARY(16) NOT NULL,
    skill_id VARCHAR(128) NOT NULL,
    node_id VARCHAR(128) NOT NULL,
    rank_value INT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (player_uuid, skill_id, node_id),
    KEY idx_mmorpg_life_skill_node_player (player_uuid, skill_id),
    CONSTRAINT ck_mmorpg_life_skill_rank CHECK (rank_value >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
