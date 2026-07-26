CREATE TABLE mmorpg_encounter (
    encounter_uuid BINARY(16) NOT NULL,
    definition_id VARCHAR(128) NOT NULL,
    encounter_state VARCHAR(16) NOT NULL,
    phase_index INT NOT NULL,
    attempt INT NOT NULL,
    completion_id VARCHAR(160) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    state_since TIMESTAMP(6) NOT NULL,
    encounter_revision BIGINT NOT NULL,
    PRIMARY KEY (encounter_uuid),
    UNIQUE KEY uq_mmorpg_encounter_completion (completion_id),
    KEY idx_mmorpg_encounter_recovery (encounter_state, state_since),
    CONSTRAINT ck_mmorpg_encounter_phase CHECK (phase_index >= 0),
    CONSTRAINT ck_mmorpg_encounter_attempt CHECK (attempt >= 1),
    CONSTRAINT ck_mmorpg_encounter_revision CHECK (encounter_revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_encounter_participant (
    encounter_uuid BINARY(16) NOT NULL,
    player_uuid BINARY(16) NOT NULL,
    connected BOOLEAN NOT NULL,
    rewarded BOOLEAN NOT NULL,
    PRIMARY KEY (encounter_uuid, player_uuid),
    CONSTRAINT fk_mmorpg_encounter_participant FOREIGN KEY (encounter_uuid)
        REFERENCES mmorpg_encounter(encounter_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_encounter_contribution (
    encounter_uuid BINARY(16) NOT NULL,
    player_uuid BINARY(16) NOT NULL,
    contribution_type VARCHAR(32) NOT NULL,
    amount DOUBLE NOT NULL,
    PRIMARY KEY (encounter_uuid, player_uuid, contribution_type),
    CONSTRAINT fk_mmorpg_encounter_contribution FOREIGN KEY (encounter_uuid)
        REFERENCES mmorpg_encounter(encounter_uuid) ON DELETE CASCADE,
    CONSTRAINT ck_mmorpg_encounter_contribution CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_encounter_actor (
    encounter_uuid BINARY(16) NOT NULL,
    actor_uuid BINARY(16) NOT NULL,
    PRIMARY KEY (encounter_uuid, actor_uuid),
    CONSTRAINT fk_mmorpg_encounter_actor FOREIGN KEY (encounter_uuid)
        REFERENCES mmorpg_encounter(encounter_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_encounter_forced_chunk (
    encounter_uuid BINARY(16) NOT NULL,
    chunk_key VARCHAR(160) NOT NULL,
    PRIMARY KEY (encounter_uuid, chunk_key),
    CONSTRAINT fk_mmorpg_encounter_chunk FOREIGN KEY (encounter_uuid)
        REFERENCES mmorpg_encounter(encounter_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
