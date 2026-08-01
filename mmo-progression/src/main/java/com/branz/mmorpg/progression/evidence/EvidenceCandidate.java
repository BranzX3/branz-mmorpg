package com.branz.mmorpg.progression.evidence;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import java.util.Objects;
import java.util.UUID;

/** Immutable server-authored summary emitted at an action or encounter boundary. */
public record EvidenceCandidate(
        UUID evidenceId,
        CharacterId characterId,
        EncounterId encounterId,
        ProgressionTrack track,
        String noveltyFingerprint,
        String contentVersion,
        EvidenceTargetKind targetKind,
        EncounterOutcome outcome,
        double baseEvidence,
        double challengeRating,
        double demonstratedCapability,
        double moveDiversityRatio,
        double executionQuality,
        double stressRatio) {

    public EvidenceCandidate {
        Objects.requireNonNull(evidenceId, "evidenceId");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(track, "track");
        noveltyFingerprint = requireText(noveltyFingerprint, "noveltyFingerprint");
        contentVersion = requireText(contentVersion, "contentVersion");
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(outcome, "outcome");
        requireRange(baseEvidence, 0.0, 100.0, "baseEvidence");
        requireRange(challengeRating, 0.0, Double.MAX_VALUE, "challengeRating");
        requireRange(
                demonstratedCapability,
                Double.MIN_NORMAL,
                Double.MAX_VALUE,
                "demonstratedCapability");
        requireRange(moveDiversityRatio, 0.0, 1.0, "moveDiversityRatio");
        requireRange(executionQuality, 0.0, 1.0, "executionQuality");
        requireRange(stressRatio, 0.0, 1.5, "stressRatio");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static void requireRange(double value, double minimum, double maximum, String name) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be finite and between " + minimum + " and " + maximum);
        }
    }
}
