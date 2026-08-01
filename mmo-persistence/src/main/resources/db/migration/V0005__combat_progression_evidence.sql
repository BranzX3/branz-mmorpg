CREATE TABLE character_progression_track (
    character_id UUID NOT NULL,
    track_id TEXT NOT NULL,
    track_type TEXT NOT NULL CHECK (
        track_type IN ('DISCIPLINE_MASTERY', 'BODY_CONDITIONING')
    ),
    evidence DOUBLE PRECISION NOT NULL CHECK (evidence >= 0 AND evidence <= 1000),
    version BIGINT NOT NULL CHECK (version >= 1),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (character_id, track_id)
);

CREATE TABLE combat_progression_evidence (
    evidence_id UUID PRIMARY KEY,
    batch_id UUID NOT NULL,
    character_id UUID NOT NULL,
    encounter_id UUID NOT NULL,
    track_id TEXT NOT NULL,
    track_type TEXT NOT NULL CHECK (
        track_type IN ('DISCIPLINE_MASTERY', 'BODY_CONDITIONING')
    ),
    novelty_fingerprint TEXT NOT NULL,
    content_version TEXT NOT NULL,
    target_kind TEXT NOT NULL,
    outcome TEXT NOT NULL,
    base_evidence DOUBLE PRECISION NOT NULL CHECK (base_evidence >= 0 AND base_evidence <= 100),
    challenge_rating DOUBLE PRECISION NOT NULL CHECK (challenge_rating >= 0),
    demonstrated_capability DOUBLE PRECISION NOT NULL CHECK (demonstrated_capability > 0),
    move_diversity_ratio DOUBLE PRECISION NOT NULL CHECK (
        move_diversity_ratio >= 0 AND move_diversity_ratio <= 1
    ),
    execution_quality DOUBLE PRECISION NOT NULL CHECK (
        execution_quality >= 0 AND execution_quality <= 1
    ),
    stress_ratio DOUBLE PRECISION NOT NULL CHECK (stress_ratio >= 0 AND stress_ratio <= 1.5),
    accepted BOOLEAN NOT NULL,
    awarded_evidence DOUBLE PRECISION NOT NULL CHECK (
        awarded_evidence >= 0 AND awarded_evidence <= 100
    ),
    resulting_evidence DOUBLE PRECISION NOT NULL CHECK (
        resulting_evidence >= 0 AND resulting_evidence <= 1000
    ),
    previous_band TEXT NOT NULL,
    resulting_band TEXT NOT NULL,
    suppression_reason TEXT NOT NULL,
    factor_challenge DOUBLE PRECISION NOT NULL CHECK (factor_challenge >= 0),
    factor_outcome DOUBLE PRECISION NOT NULL CHECK (factor_outcome >= 0),
    factor_diversity DOUBLE PRECISION NOT NULL CHECK (factor_diversity >= 0),
    factor_execution DOUBLE PRECISION NOT NULL CHECK (factor_execution >= 0),
    factor_novelty DOUBLE PRECISION NOT NULL CHECK (factor_novelty >= 0),
    factor_repetition DOUBLE PRECISION NOT NULL CHECK (factor_repetition >= 0),
    factor_risk DOUBLE PRECISION NOT NULL CHECK (factor_risk >= 0),
    factor_daily_curve DOUBLE PRECISION NOT NULL CHECK (factor_daily_curve >= 0),
    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX combat_progression_character_time_idx
    ON combat_progression_evidence(character_id, recorded_at DESC);

CREATE INDEX combat_progression_novelty_window_idx
    ON combat_progression_evidence(
        character_id,
        track_id,
        novelty_fingerprint,
        recorded_at DESC
    ) WHERE accepted;
