CREATE TABLE quest_progress (
    player_uuid BINARY(16) NOT NULL,
    quest_id VARCHAR(128) NOT NULL,
    definition_version INT NOT NULL,
    progress_revision BIGINT NOT NULL,
    quest_state VARCHAR(32) NOT NULL,
    stage_id VARCHAR(128) NOT NULL,
    occurrence_uuid BINARY(16) NOT NULL,
    objective_state_json JSON NOT NULL,
    flags_json JSON NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (player_uuid, quest_id),
    KEY idx_quest_progress_active (player_uuid, quest_state, updated_at),
    CONSTRAINT ck_quest_definition_version CHECK (definition_version >= 1),
    CONSTRAINT ck_quest_progress_revision CHECK (progress_revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quest_processed_events (
    player_uuid BINARY(16) NOT NULL,
    event_uuid BINARY(16) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL,
    expiry_bucket INT NOT NULL,
    PRIMARY KEY (player_uuid, event_uuid),
    KEY idx_quest_processed_expiry (expiry_bucket)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quest_pending_operations (
    operation_id VARCHAR(220) NOT NULL,
    player_uuid BINARY(16) NOT NULL,
    quest_id VARCHAR(128) NOT NULL,
    operation_type VARCHAR(40) NOT NULL,
    payload_json JSON NOT NULL,
    operation_state VARCHAR(16) NOT NULL,
    attempts INT NOT NULL,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    last_error VARCHAR(512) NOT NULL,
    PRIMARY KEY (operation_id),
    KEY idx_quest_pending_due (operation_state, next_attempt_at),
    KEY idx_quest_pending_player_quest (player_uuid, quest_id, operation_state),
    CONSTRAINT ck_quest_pending_attempts CHECK (attempts >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quest_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    player_uuid BINARY(16) NOT NULL,
    quest_id VARCHAR(128) NOT NULL,
    occurrence_uuid BINARY(16) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (history_id),
    UNIQUE KEY uq_quest_history_occurrence (player_uuid, quest_id, occurrence_uuid),
    KEY idx_quest_history_player_completed (player_uuid, completed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
