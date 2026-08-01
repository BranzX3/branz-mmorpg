package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.progression.KnowledgeAcquisitionRequest;
import com.branz.mmorpg.persistence.progression.KnowledgeRecord;
import com.branz.mmorpg.progression.build.BuildEngine;
import com.branz.mmorpg.progression.evidence.ProgressionTrack;
import com.branz.mmorpg.progression.evidence.ReadinessBand;
import com.branz.mmorpg.progression.knowledge.KnowledgeAcquisitionEngine;
import com.branz.mmorpg.progression.knowledge.KnowledgeAcquisitionPolicy;
import com.branz.mmorpg.progression.knowledge.KnowledgeAcquisitionSourceType;
import com.branz.mmorpg.progression.knowledge.KnowledgeKey;
import com.branz.mmorpg.progression.knowledge.KnowledgeProfile;
import com.branz.mmorpg.progression.knowledge.LearningDecision;
import com.branz.mmorpg.progression.knowledge.LearningRejectionReason;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** Validates authored Form/Spell sources and publishes their permanent Knowledge grant. */
final class KnowledgeAcquisitionController {
    private final CharacterSessionController characters;
    private final BuildEngine builds;
    private final String contentVersion;
    private final KnowledgeAcquisitionEngine acquisitions = new KnowledgeAcquisitionEngine();

    KnowledgeAcquisitionController(
            CharacterSessionController characters, BuildEngine builds, String contentVersion) {
        this.characters = Objects.requireNonNull(characters, "characters");
        this.builds = Objects.requireNonNull(builds, "builds");
        this.contentVersion = requireText(contentVersion, "contentVersion");
    }

    void completeAuthoredFixture(Player player, KnowledgeKey target, UUID acquisitionId) {
        KnowledgeAcquisitionPolicy policy = builds.acquisition(target).orElse(null);
        if (policy == null) {
            reject(player, "No authored acquisition policy exists for " + target.id().value());
            return;
        }
        complete(player, target, policy.sourceType(), policy.sourceId(), acquisitionId);
    }

    void complete(
            Player player,
            KnowledgeKey target,
            KnowledgeAcquisitionSourceType observedSourceType,
            DefinitionId observedSourceId,
            UUID acquisitionId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(observedSourceType, "observedSourceType");
        Objects.requireNonNull(observedSourceId, "observedSourceId");
        Objects.requireNonNull(acquisitionId, "acquisitionId");
        LoadedCharacterSession session = characters.active(player).orElse(null);
        if (session == null || !characters.ready(player)) {
            reject(player, "Player Session is not ready.");
            return;
        }
        KnowledgeAcquisitionPolicy policy = builds.acquisition(target).orElse(null);
        if (policy == null) {
            reject(player, "Target is not an authored Form or Spell.");
            return;
        }
        LearningDecision decision =
                acquisitions.evaluate(
                        policy, observedSourceType, observedSourceId, profile(session));
        if (!decision.accepted() && decision.reason() != LearningRejectionReason.ALREADY_KNOWN) {
            reject(
                    player,
                    decision.reason()
                            + decision.missingRequirement()
                                    .map(missing -> ": " + missing)
                                    .orElse(""));
            return;
        }
        KnowledgeAcquisitionRequest request =
                new KnowledgeAcquisitionRequest(
                        acquisitionId,
                        session.characterId(),
                        target,
                        observedSourceType,
                        observedSourceId,
                        contentVersion);
        player.sendActionBar(
                Component.text("Committing permanent Knowledge...", NamedTextColor.YELLOW));
        characters.commitKnowledgeAcquisition(
                player,
                request,
                result -> {
                    if (result
                            instanceof
                            Result.Failure<
                                            KnowledgeAcquisitionCommitResult,
                                            CharacterSessionErrorCode>
                                    failure) {
                        player.sendMessage(
                                Component.text(
                                        "Knowledge acquisition failed: "
                                                + failure.error().code()
                                                + " "
                                                + failure.detail(),
                                        NamedTextColor.RED));
                        return;
                    }
                    KnowledgeAcquisitionCommitResult committed =
                            ((Result.Success<
                                                    KnowledgeAcquisitionCommitResult,
                                                    CharacterSessionErrorCode>)
                                            result)
                                    .value();
                    player.sendMessage(
                            Component.text(
                                    (committed.execution().replayed()
                                                    ? "Knowledge replay confirmed: "
                                                    : "Knowledge learned: ")
                                            + target.id().value(),
                                    NamedTextColor.GREEN));
                });
    }

    private static KnowledgeProfile profile(LoadedCharacterSession session) {
        Set<KnowledgeKey> learned =
                session.snapshot().learnedKnowledge().stream()
                        .map(KnowledgeRecord::knowledge)
                        .collect(Collectors.toUnmodifiableSet());
        Map<ProgressionTrack, ReadinessBand> readiness =
                session.snapshot().progressionTracks().stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        record -> record.track(),
                                        record -> ReadinessBand.fromEvidence(record.evidence()),
                                        KnowledgeAcquisitionController::higherReadiness));
        return new KnowledgeProfile(learned, readiness, Set.of());
    }

    private static ReadinessBand higherReadiness(ReadinessBand left, ReadinessBand right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }

    private static void reject(Player player, String detail) {
        player.sendMessage(
                Component.text("Knowledge acquisition rejected: " + detail, NamedTextColor.RED));
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
