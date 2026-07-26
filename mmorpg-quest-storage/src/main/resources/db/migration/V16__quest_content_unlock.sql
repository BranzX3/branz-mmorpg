CREATE TABLE quest_content_unlock (
    player_uuid BINARY(16) NOT NULL,
    content_id VARCHAR(191) NOT NULL,
    operation_id VARCHAR(220) NOT NULL,
    unlocked_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (player_uuid, content_id),
    UNIQUE KEY uq_quest_content_unlock_operation (operation_id)
) ENGINE=InnoDB;
