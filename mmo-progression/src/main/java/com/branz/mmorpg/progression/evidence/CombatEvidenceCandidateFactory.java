package com.branz.mmorpg.progression.evidence;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Composes one bounded Mastery candidate and one Conditioning candidate per combat summary. */
public final class CombatEvidenceCandidateFactory {
    private static final double EVIDENCE_PER_SUCCESSFUL_ACTION = 2.5;
    private static final double CONDITIONING_SHARE = 0.5;

    public List<EvidenceCandidate> create(CombatEncounterSummary summary) {
        Objects.requireNonNull(summary, "summary");
        double masteryBase =
                Math.min(100.0, summary.successfulActions() * EVIDENCE_PER_SUCCESSFUL_ACTION);
        double diversity =
                Math.min(
                        1.0,
                        summary.distinctMoves() / (double) Math.min(4, summary.committedActions()));
        double execution = summary.successfulActions() / (double) summary.committedActions();
        return List.of(
                candidate(summary, summary.masteryTrack(), masteryBase, diversity, execution),
                candidate(
                        summary,
                        summary.conditioningTrack(),
                        masteryBase * CONDITIONING_SHARE,
                        diversity,
                        execution));
    }

    private static EvidenceCandidate candidate(
            CombatEncounterSummary summary,
            ProgressionTrack track,
            double baseEvidence,
            double diversity,
            double execution) {
        return new EvidenceCandidate(
                evidenceId(summary, track),
                summary.characterId(),
                summary.encounterId(),
                track,
                summary.noveltyFingerprint(),
                summary.contentVersion(),
                summary.targetKind(),
                summary.outcome(),
                baseEvidence,
                summary.challengeRating(),
                summary.demonstratedCapability(),
                diversity,
                execution,
                summary.peakStressRatio());
    }

    private static UUID evidenceId(CombatEncounterSummary summary, ProgressionTrack outputTrack) {
        String identity =
                "combat-evidence-v1|"
                        + summary.characterId().value()
                        + "|"
                        + summary.encounterId().value()
                        + "|"
                        + summary.masteryTrack().id().value()
                        + "|"
                        + outputTrack.id().value();
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }
}
