ALTER TABLE mmorpg_combat_mastery
    ADD COLUMN unspent_points INT NOT NULL DEFAULT 0 AFTER total_xp,
    ADD COLUMN tree_revision INT NOT NULL DEFAULT 1 AFTER unspent_points;

UPDATE mmorpg_combat_mastery
SET unspent_points = GREATEST(level - 1, 0)
WHERE unspent_points = 0;

ALTER TABLE mmorpg_combat_mastery
    ADD CONSTRAINT ck_mmorpg_mastery_points CHECK (unspent_points >= 0),
    ADD CONSTRAINT ck_mmorpg_mastery_tree_revision CHECK (tree_revision >= 1);

CREATE TABLE mmorpg_combat_mastery_node_rank (
    player_uuid BINARY(16) NOT NULL,
    mastery_id VARCHAR(128) NOT NULL,
    node_id VARCHAR(128) NOT NULL,
    node_rank INT NOT NULL,
    PRIMARY KEY (player_uuid, mastery_id, node_id),
    CONSTRAINT fk_mmorpg_mastery_node_progress
        FOREIGN KEY (player_uuid, mastery_id)
        REFERENCES mmorpg_combat_mastery (player_uuid, mastery_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_mmorpg_mastery_node_rank CHECK (node_rank >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
