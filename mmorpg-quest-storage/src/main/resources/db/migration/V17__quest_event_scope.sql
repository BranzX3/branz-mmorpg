ALTER TABLE quest_processed_events
    ADD COLUMN quest_id VARCHAR(128) NOT NULL DEFAULT 'legacy:processed' AFTER player_uuid,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (player_uuid, quest_id, event_uuid);

ALTER TABLE quest_processed_events
    ALTER COLUMN quest_id DROP DEFAULT;
