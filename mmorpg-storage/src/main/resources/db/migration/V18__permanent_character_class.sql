ALTER TABLE mmorpg_player_profiles
    ADD COLUMN class_selected_at TIMESTAMP(6) NULL AFTER class_id,
    ADD COLUMN class_selection_operation_id VARCHAR(128) NULL AFTER class_selected_at,
    ADD COLUMN class_schema_version INT NULL AFTER class_selection_operation_id;

-- Preserve selections made by builds before I3. They remain permanent, but do
-- not synthesize a starter grant because older builds may already have granted it.
UPDATE mmorpg_player_profiles
SET class_selected_at = COALESCE(last_seen_at, created_at),
    class_selection_operation_id = CONCAT(
        'mmo:class:selection:', LOWER(BIN_TO_UUID(player_uuid)), ':legacy'),
    class_schema_version = 1
WHERE class_id IS NOT NULL AND class_selection_operation_id IS NULL;

ALTER TABLE mmorpg_player_profiles
    ADD UNIQUE KEY uq_mmorpg_player_class_operation (class_selection_operation_id),
    ADD CONSTRAINT ck_mmorpg_player_class_metadata CHECK (
        (class_id IS NULL AND class_selected_at IS NULL
            AND class_selection_operation_id IS NULL AND class_schema_version IS NULL)
        OR
        (class_id IS NOT NULL AND class_selected_at IS NOT NULL
            AND class_selection_operation_id IS NOT NULL AND class_schema_version >= 1)
    );

CREATE TABLE mmorpg_character_class_selection (
    player_uuid BINARY(16) NOT NULL,
    operation_id VARCHAR(128) NOT NULL,
    class_id VARCHAR(128) NOT NULL,
    class_schema_version INT NOT NULL,
    selected_at TIMESTAMP(6) NOT NULL,
    content_revision BIGINT NOT NULL,
    profile_revision BIGINT NOT NULL,
    starter_plan_id VARCHAR(128) NOT NULL,
    starter_plan_revision INT NOT NULL,
    starter_weapon_id VARCHAR(128) NOT NULL,
    starter_skill_ids JSON NOT NULL,
    starter_additional_items JSON NOT NULL,
    PRIMARY KEY (player_uuid),
    UNIQUE KEY uq_mmorpg_class_selection_operation (operation_id),
    CONSTRAINT fk_mmorpg_class_selection_profile
        FOREIGN KEY (player_uuid) REFERENCES mmorpg_player_profiles (player_uuid)
        ON DELETE CASCADE,
    CONSTRAINT ck_mmorpg_class_selection_revisions CHECK (
        class_schema_version >= 1 AND content_revision >= 1
        AND profile_revision >= 1 AND starter_plan_revision >= 1
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
