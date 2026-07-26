CREATE TABLE dialogue_session (
    session_uuid BINARY(16) NOT NULL,
    player_uuid BINARY(16) NOT NULL,
    dialogue_id VARCHAR(128) NOT NULL,
    session_state VARCHAR(16) NOT NULL,
    payload_json JSON NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (session_uuid),
    UNIQUE KEY uq_dialogue_active_player (player_uuid, session_state),
    KEY idx_dialogue_session_recovery (session_state, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dialogue_history (
    player_uuid BINARY(16) NOT NULL,
    dialogue_id VARCHAR(128) NOT NULL,
    session_uuid BINARY(16) NOT NULL,
    sequence_number BIGINT NOT NULL,
    node_id VARCHAR(128) NOT NULL,
    speaker_key VARCHAR(160) NOT NULL,
    text_key VARCHAR(220) NOT NULL,
    choice_id VARCHAR(128) NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (player_uuid, session_uuid, sequence_number),
    KEY idx_dialogue_history_read (player_uuid, dialogue_id, recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dialogue_player_lock (
    player_uuid BINARY(16) NOT NULL,
    session_uuid BINARY(16) NOT NULL,
    PRIMARY KEY (player_uuid),
    UNIQUE KEY uq_dialogue_lock_session (session_uuid),
    CONSTRAINT fk_dialogue_player_lock FOREIGN KEY (session_uuid)
        REFERENCES dialogue_session(session_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cutscene_session (
    session_uuid BINARY(16) NOT NULL,
    cutscene_id VARCHAR(128) NOT NULL,
    session_state VARCHAR(16) NOT NULL,
    payload_json JSON NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (session_uuid),
    KEY idx_cutscene_session_recovery (session_state, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quest_accessibility (
    player_uuid BINARY(16) NOT NULL,
    dialogue_mode VARCHAR(16) NOT NULL,
    text_speed DOUBLE NOT NULL,
    skip_previously_read BOOLEAN NOT NULL,
    portrait_intensity VARCHAR(16) NOT NULL,
    vfx_intensity VARCHAR(16) NOT NULL,
    sound_alternatives BOOLEAN NOT NULL,
    PRIMARY KEY (player_uuid),
    CONSTRAINT ck_quest_accessibility_speed CHECK (text_speed >= 0.25 AND text_speed <= 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
