CREATE TABLE character_knowledge (
    character_id UUID NOT NULL,
    knowledge_type TEXT NOT NULL CHECK (
        knowledge_type IN ('FOUNDATION', 'TECHNIQUE', 'FORM', 'SPELL', 'RECIPE', 'LORE')
    ),
    definition_id TEXT NOT NULL,
    source_type TEXT NOT NULL CHECK (
        source_type IN ('PLAYER_TEACHING', 'LEGACY_BUILD_BACKFILL')
    ),
    source_id UUID NOT NULL,
    content_version TEXT NOT NULL,
    learned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (character_id, knowledge_type, definition_id)
);

INSERT INTO character_knowledge(
    character_id, knowledge_type, definition_id, source_type, source_id,
    content_version, learned_at
)
SELECT
    build.character_id,
    'TECHNIQUE',
    technique.value,
    'LEGACY_BUILD_BACKFILL',
    build.last_transaction_id,
    build.content_version,
    CURRENT_TIMESTAMP
FROM character_build_state AS build
CROSS JOIN LATERAL jsonb_each_text(build.build_payload -> 'techniques') AS technique;

INSERT INTO character_knowledge(
    character_id, knowledge_type, definition_id, source_type, source_id,
    content_version, learned_at
)
SELECT
    build.character_id,
    'FORM',
    build.build_payload ->> 'form',
    'LEGACY_BUILD_BACKFILL',
    build.last_transaction_id,
    build.content_version,
    CURRENT_TIMESTAMP
FROM character_build_state AS build
WHERE build.build_payload ->> 'form' IS NOT NULL;

INSERT INTO character_knowledge(
    character_id, knowledge_type, definition_id, source_type, source_id,
    content_version, learned_at
)
SELECT
    build.character_id,
    'SPELL',
    effect.value,
    'LEGACY_BUILD_BACKFILL',
    build.last_transaction_id,
    build.content_version,
    CURRENT_TIMESTAMP
FROM character_build_state AS build
CROSS JOIN LATERAL jsonb_array_elements_text(
    build.build_payload -> 'attunedEffects'
) AS effect(value);

CREATE TABLE character_renown (
    character_id UUID PRIMARY KEY,
    renown BIGINT NOT NULL CHECK (renown >= 0),
    version BIGINT NOT NULL CHECK (version >= 1),
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE renown_deed_journal (
    deed_id UUID PRIMARY KEY,
    character_id UUID NOT NULL,
    deed_type TEXT NOT NULL,
    novelty_fingerprint TEXT NOT NULL,
    base_renown INTEGER NOT NULL CHECK (base_renown BETWEEN 1 AND 100),
    content_version TEXT NOT NULL,
    accepted BOOLEAN NOT NULL,
    awarded_renown INTEGER NOT NULL CHECK (awarded_renown BETWEEN 0 AND 100),
    resulting_renown BIGINT NOT NULL CHECK (resulting_renown >= 0),
    repetition_factor DOUBLE PRECISION NOT NULL CHECK (
        repetition_factor >= 0 AND repetition_factor <= 1
    ),
    suppression_reason TEXT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX renown_deed_character_time_idx
    ON renown_deed_journal(character_id, recorded_at DESC);

CREATE INDEX renown_deed_novelty_day_idx
    ON renown_deed_journal(character_id, novelty_fingerprint, recorded_at DESC)
    WHERE accepted;

CREATE TABLE teaching_completion_journal (
    teaching_session_id UUID PRIMARY KEY,
    teacher_id UUID NOT NULL,
    student_id UUID NOT NULL,
    knowledge_type TEXT NOT NULL,
    definition_id TEXT NOT NULL,
    deed_id UUID NOT NULL UNIQUE REFERENCES renown_deed_journal(deed_id),
    content_version TEXT NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    CHECK (teacher_id <> student_id),
    FOREIGN KEY (student_id, knowledge_type, definition_id)
        REFERENCES character_knowledge(character_id, knowledge_type, definition_id)
);

CREATE INDEX teaching_completion_participants_idx
    ON teaching_completion_journal(teacher_id, student_id, completed_at DESC);
