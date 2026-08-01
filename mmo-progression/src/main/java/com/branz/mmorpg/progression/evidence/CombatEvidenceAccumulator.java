package com.branz.mmorpg.progression.evidence;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.EncounterId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Session-scoped accumulator that emits evidence only when a server encounter outcome completes.
 */
public final class CombatEvidenceAccumulator {
    public static final int MAXIMUM_ACTIVE_TARGETS = 64;

    private final CombatEvidenceCandidateFactory candidates;
    private final Map<UUID, TargetEvidence> targets = new HashMap<>();

    public CombatEvidenceAccumulator() {
        this(new CombatEvidenceCandidateFactory());
    }

    CombatEvidenceAccumulator(CombatEvidenceCandidateFactory candidates) {
        this.candidates = Objects.requireNonNull(candidates, "candidates");
    }

    /** Adds a committed action to already-observed targets for the same discipline. */
    public void observeCommittedAction(
            String discipline, UUID actionId, DefinitionId moveOrSpellId) {
        String normalized = normalizeDiscipline(discipline);
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(moveOrSpellId, "moveOrSpellId");
        targets.values()
                .forEach(
                        target -> {
                            DisciplineEvidence evidence = target.disciplines.get(normalized);
                            if (evidence != null) {
                                evidence.committedActions.add(actionId);
                            }
                        });
    }

    /** Records a successful server-resolved action without granting progression immediately. */
    public boolean observeSuccessfulAction(
            UUID targetId,
            EvidenceTargetKind targetKind,
            String targetFingerprint,
            double challengeRating,
            String discipline,
            BodyConditioningAxis conditioningAxis,
            UUID actionId,
            DefinitionId moveOrSpellId,
            double demonstratedCapability,
            double stressRatio) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(targetKind, "targetKind");
        targetFingerprint = requireText(targetFingerprint, "targetFingerprint");
        requirePositive(challengeRating, "challengeRating");
        String normalized = normalizeDiscipline(discipline);
        Objects.requireNonNull(conditioningAxis, "conditioningAxis");
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(moveOrSpellId, "moveOrSpellId");
        requirePositive(demonstratedCapability, "demonstratedCapability");
        if (!Double.isFinite(stressRatio) || stressRatio < 0 || stressRatio > 1.5) {
            throw new IllegalArgumentException("stressRatio must be between 0 and 1.5");
        }
        TargetEvidence target = targets.get(targetId);
        if (target == null) {
            if (targets.size() >= MAXIMUM_ACTIVE_TARGETS) {
                return false;
            }
            target =
                    new TargetEvidence(
                            UUID.randomUUID(),
                            targetKind,
                            targetFingerprint,
                            challengeRating,
                            new HashMap<>());
            targets.put(targetId, target);
        }
        DisciplineEvidence evidence =
                target.disciplines.computeIfAbsent(
                        normalized, ignored -> new DisciplineEvidence(conditioningAxis));
        evidence.committedActions.add(actionId);
        evidence.successfulActions.add(actionId);
        evidence.successfulMoves.add(moveOrSpellId);
        evidence.demonstratedCapability =
                Math.max(evidence.demonstratedCapability, demonstratedCapability);
        evidence.peakStressRatio = Math.max(evidence.peakStressRatio, stressRatio);
        return true;
    }

    public List<EvidenceCandidate> completeTarget(
            CharacterId characterId,
            UUID targetId,
            String contentVersion,
            EncounterOutcome outcome) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(targetId, "targetId");
        contentVersion = requireText(contentVersion, "contentVersion");
        Objects.requireNonNull(outcome, "outcome");
        TargetEvidence target = targets.remove(targetId);
        return target == null ? List.of() : complete(characterId, contentVersion, outcome, target);
    }

    public List<EvidenceCandidate> completeAll(
            CharacterId characterId, String contentVersion, EncounterOutcome outcome) {
        Objects.requireNonNull(characterId, "characterId");
        String validatedContentVersion = requireText(contentVersion, "contentVersion");
        Objects.requireNonNull(outcome, "outcome");
        List<EvidenceCandidate> completed = new ArrayList<>();
        targets.keySet().stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList()
                .forEach(
                        targetId ->
                                completed.addAll(
                                        complete(
                                                characterId,
                                                validatedContentVersion,
                                                outcome,
                                                targets.get(targetId))));
        targets.clear();
        return List.copyOf(completed);
    }

    public int activeTargetCount() {
        return targets.size();
    }

    /** Raises productive-stress context for every active observed discipline. */
    public void observeStress(double stressRatio) {
        if (!Double.isFinite(stressRatio) || stressRatio < 0 || stressRatio > 1.5) {
            throw new IllegalArgumentException("stressRatio must be between 0 and 1.5");
        }
        targets.values()
                .forEach(
                        target ->
                                target.disciplines
                                        .values()
                                        .forEach(
                                                evidence ->
                                                        evidence.peakStressRatio =
                                                                Math.max(
                                                                        evidence.peakStressRatio,
                                                                        stressRatio)));
    }

    public void clear() {
        targets.clear();
    }

    private List<EvidenceCandidate> complete(
            CharacterId characterId,
            String contentVersion,
            EncounterOutcome outcome,
            TargetEvidence target) {
        List<EvidenceCandidate> completed = new ArrayList<>();
        target.disciplines.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        entry -> {
                            DisciplineEvidence evidence = entry.getValue();
                            int committed =
                                    Math.min(
                                            CombatEncounterSummary.MAXIMUM_ACTIONS,
                                            evidence.committedActions.size());
                            int successful = Math.min(committed, evidence.successfulActions.size());
                            int distinct = Math.min(successful, evidence.successfulMoves.size());
                            if (successful == 0 || distinct == 0) {
                                return;
                            }
                            CombatEncounterSummary summary =
                                    new CombatEncounterSummary(
                                            characterId,
                                            new EncounterId(target.encounterId),
                                            ProgressionTrack.mastery(entry.getKey()),
                                            ProgressionTrack.conditioning(
                                                    evidence.conditioningAxis),
                                            target.fingerprint + ":" + entry.getKey(),
                                            contentVersion,
                                            target.targetKind,
                                            outcome,
                                            target.challengeRating,
                                            evidence.demonstratedCapability,
                                            committed,
                                            successful,
                                            distinct,
                                            evidence.peakStressRatio);
                            completed.addAll(candidates.create(summary));
                        });
        return completed;
    }

    private static String normalizeDiscipline(String value) {
        String normalized = requireText(value, "discipline").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException(
                    "discipline must contain only lower-case letters, digits or underscore");
        }
        return normalized;
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

    private static final class TargetEvidence {
        private final UUID encounterId;
        private final EvidenceTargetKind targetKind;
        private final String fingerprint;
        private final double challengeRating;
        private final Map<String, DisciplineEvidence> disciplines;

        private TargetEvidence(
                UUID encounterId,
                EvidenceTargetKind targetKind,
                String fingerprint,
                double challengeRating,
                Map<String, DisciplineEvidence> disciplines) {
            this.encounterId = encounterId;
            this.targetKind = targetKind;
            this.fingerprint = fingerprint;
            this.challengeRating = challengeRating;
            this.disciplines = disciplines;
        }
    }

    private static final class DisciplineEvidence {
        private final BodyConditioningAxis conditioningAxis;
        private final Set<UUID> committedActions = new HashSet<>();
        private final Set<UUID> successfulActions = new HashSet<>();
        private final Set<DefinitionId> successfulMoves = new HashSet<>();
        private double demonstratedCapability;
        private double peakStressRatio;

        private DisciplineEvidence(BodyConditioningAxis conditioningAxis) {
            this.conditioningAxis = conditioningAxis;
        }
    }
}
