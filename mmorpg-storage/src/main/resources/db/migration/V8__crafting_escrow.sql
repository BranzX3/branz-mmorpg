CREATE TABLE mmorpg_profession_progress (
    player_uuid BINARY(16) NOT NULL,
    profession_id VARCHAR(128) NOT NULL,
    level INT NOT NULL DEFAULT 1,
    total_xp BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (player_uuid, profession_id),
    KEY idx_mmorpg_profession_leaderboard (profession_id, total_xp),
    CONSTRAINT ck_mmorpg_profession_level CHECK (level >= 1),
    CONSTRAINT ck_mmorpg_profession_xp CHECK (total_xp >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_craft_sequence (
    player_uuid BINARY(16) NOT NULL,
    recipe_id VARCHAR(128) NOT NULL,
    next_sequence BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, recipe_id),
    CONSTRAINT ck_mmorpg_craft_sequence CHECK (next_sequence >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_craft_job (
    operation_id VARCHAR(128) NOT NULL,
    player_uuid BINARY(16) NOT NULL,
    recipe_id VARCHAR(128) NOT NULL,
    content_revision BIGINT NOT NULL,
    job_status VARCHAR(24) NOT NULL,
    coin_fee BIGINT NOT NULL,
    duration_millis BIGINT NOT NULL,
    output_item_id VARCHAR(128) NOT NULL,
    output_quantity BIGINT NOT NULL,
    output_binding VARCHAR(24) NOT NULL,
    quality_policy VARCHAR(64) NOT NULL,
    profession_id VARCHAR(128) NULL,
    profession_xp BIGINT NOT NULL,
    trivial_after_level INT NOT NULL,
    ready_at TIMESTAMP(6) NULL,
    failure_reason VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (operation_id),
    KEY idx_mmorpg_craft_player_status (player_uuid, job_status, updated_at),
    CONSTRAINT ck_mmorpg_craft_fee CHECK (coin_fee >= 0),
    CONSTRAINT ck_mmorpg_craft_duration CHECK (duration_millis >= 0),
    CONSTRAINT ck_mmorpg_craft_output CHECK (output_quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_craft_escrow (
    operation_id VARCHAR(128) NOT NULL,
    material_id VARCHAR(128) NOT NULL,
    quantity BIGINT NOT NULL,
    PRIMARY KEY (operation_id, material_id),
    CONSTRAINT fk_mmorpg_craft_escrow_job FOREIGN KEY (operation_id)
        REFERENCES mmorpg_craft_job (operation_id) ON DELETE CASCADE,
    CONSTRAINT ck_mmorpg_craft_escrow_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
