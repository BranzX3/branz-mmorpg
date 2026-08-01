ALTER TABLE character_knowledge
    DROP CONSTRAINT character_knowledge_source_type_check;

ALTER TABLE character_knowledge
    ADD CONSTRAINT character_knowledge_source_type_check CHECK (
        source_type IN ('PLAYER_TEACHING', 'LEGACY_BUILD_BACKFILL', 'CONTENT_ACQUISITION')
    );

CREATE TABLE knowledge_acquisition_journal (
    acquisition_id UUID PRIMARY KEY,
    character_id UUID NOT NULL,
    knowledge_type TEXT NOT NULL CHECK (knowledge_type IN ('FORM', 'SPELL')),
    definition_id TEXT NOT NULL,
    source_type TEXT NOT NULL CHECK (
        source_type IN ('MENTOR', 'WORLD_DISCOVERY', 'BOSS_KNOWLEDGE', 'FACTION_QUEST')
    ),
    source_definition_id TEXT NOT NULL,
    content_version TEXT NOT NULL,
    acquired_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (character_id, knowledge_type, definition_id)
        REFERENCES character_knowledge(character_id, knowledge_type, definition_id)
);

CREATE INDEX knowledge_acquisition_character_time_idx
    ON knowledge_acquisition_journal(character_id, acquired_at DESC);
