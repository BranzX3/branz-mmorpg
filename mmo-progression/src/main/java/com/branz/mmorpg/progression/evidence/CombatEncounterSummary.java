package com.branz.mmorpg.progression.evidence;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import java.util.Objects;

/** Bounded server-authored combat outcome used to compose Mastery and Conditioning evidence. */
public record CombatEncounterSummary(
        CharacterId characterId,
        EncounterId encounterId,
        ProgressionTrack masteryTrack,
        ProgressionTrack conditioningTrack,
        String noveltyFingerprint,
        String contentVersion,
        EvidenceTargetKind targetKind,
        EncounterOutcome outcome,
        double challengeRating,
        double demonstratedCapability,
        int committedActions,
        int successfulActions,
        int distinctMoves,
        double peakStressRatio) {
    public static final int MAXIMUM_ACTIONS = 64;

    public CombatEncounterSummary {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(masteryTrack, "masteryTrack");
        Objects.requireNonNull(conditioningTrack, "conditioningTrack");
        noveltyFingerprint = requireText(noveltyFingerprint, "noveltyFingerprint");
        contentVersion = requireText(contentVersion, "contentVersion");
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(outcome, "outcome");
        if (masteryTrack.type() != ProgressionTrackType.DISCIPLINE_MASTERY) {
            throw new IllegalArgumentException("masteryTrack must be Discipline Mastery");
        }
        if (conditioningTrack.type() != ProgressionTrackType.BODY_CONDITIONING) {
            throw new IllegalArgumentException("conditioningTrack must be Body Conditioning");
        }
        requirePositive(challengeRating, "challengeRating");
        requirePositive(demonstratedCapability, "demonstratedCapability");
        if (committedActions < 1 || committedActions > MAXIMUM_ACTIONS) {
            throw new IllegalArgumentException("committedActions must be between 1 and 64");
        }
        if (successfulActions < 1 || successfulActions > committedActions) {
            throw new IllegalArgumentException(
                    "successfulActions must be between 1 and committedActions");
        }
        if (distinctMoves < 1 || distinctMoves > successfulActions) {
            throw new IllegalArgumentException(
                    "distinctMoves must be between 1 and successfulActions");
        }
        if (!Double.isFinite(peakStressRatio) || peakStressRatio < 0 || peakStressRatio > 1.5) {
            throw new IllegalArgumentException("peakStressRatio must be between 0 and 1.5");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
