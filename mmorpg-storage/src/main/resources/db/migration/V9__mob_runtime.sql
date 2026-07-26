CREATE TABLE mmorpg_mob_runtime (
    mob_uuid BINARY(16) NOT NULL,
    definition_id VARCHAR(128) NOT NULL,
    mob_level INT NOT NULL,
    world_uuid BINARY(16) NOT NULL,
    home_x DOUBLE NOT NULL,
    home_y DOUBLE NOT NULL,
    home_z DOUBLE NOT NULL,
    position_x DOUBLE NOT NULL,
    position_y DOUBLE NOT NULL,
    position_z DOUBLE NOT NULL,
    ai_state VARCHAR(16) NOT NULL,
    target_uuid BINARY(16) NULL,
    health DOUBLE NOT NULL,
    maximum_health DOUBLE NOT NULL,
    state_since TIMESTAMP(6) NOT NULL,
    next_decision_at TIMESTAMP(6) NOT NULL,
    next_path_request_at TIMESTAMP(6) NOT NULL,
    decision_sequence BIGINT NOT NULL,
    reward_sequence BIGINT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (mob_uuid),
    KEY idx_mmorpg_mob_definition (definition_id),
    KEY idx_mmorpg_mob_world_state (world_uuid, ai_state),
    CONSTRAINT ck_mmorpg_mob_level CHECK (mob_level >= 1),
    CONSTRAINT ck_mmorpg_mob_health CHECK (
        health >= 0 AND maximum_health > 0 AND health <= maximum_health),
    CONSTRAINT ck_mmorpg_mob_sequences CHECK (
        decision_sequence >= 0 AND reward_sequence >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
