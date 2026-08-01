package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.state.EngagementState;
import com.branz.mmorpg.persistence.progression.KnowledgeRecord;
import com.branz.mmorpg.persistence.progression.TeachingCommitRequest;
import com.branz.mmorpg.progression.build.BuildEngine;
import com.branz.mmorpg.progression.build.TechniqueDefinition;
import com.branz.mmorpg.progression.evidence.ProgressionTrack;
import com.branz.mmorpg.progression.evidence.ReadinessBand;
import com.branz.mmorpg.progression.knowledge.KnowledgeKey;
import com.branz.mmorpg.progression.knowledge.KnowledgeProfile;
import com.branz.mmorpg.progression.knowledge.KnowledgeType;
import com.branz.mmorpg.progression.knowledge.LearningRequirements;
import com.branz.mmorpg.progression.renown.RenownDeedCandidate;
import com.branz.mmorpg.progression.teaching.TeachingActionEngine;
import com.branz.mmorpg.progression.teaching.TeachingCompletion;
import com.branz.mmorpg.progression.teaching.TeachingErrorCode;
import com.branz.mmorpg.progression.teaching.TeachingPhase;
import com.branz.mmorpg.progression.teaching.TeachingSession;
import com.branz.mmorpg.progression.teaching.TeachingSessionEngine;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Main-thread lifecycle and durable completion adapter for synchronous player teaching. */
final class LiveTeachingSessionController implements Listener {
    private static final double MAXIMUM_PARTICIPANT_DISTANCE_SQUARED = 16.0 * 16.0;
    private static final long SUPERVISION_PERIOD_TICKS = 20L;
    private static final long COMMIT_RETRY_TICKS = 20L;
    private static final DefinitionId MENTORSHIP_DEED = DefinitionId.of("renown.mentorship");
    private static final int MENTORSHIP_BASE_RENOWN = 20;

    private final JavaPlugin plugin;
    private final CharacterSessionController characters;
    private final CombatSessionController combat;
    private final BuildEngine builds;
    private final String contentVersion;
    private final TeachingSessionEngine sessions = new TeachingSessionEngine();
    private final TeachingActionEngine actions = new TeachingActionEngine(sessions);
    private final Map<UUID, ActiveTeaching> activeBySession = new HashMap<>();
    private final Map<UUID, UUID> sessionByParticipant = new HashMap<>();
    private int supervisionTaskId = -1;

    LiveTeachingSessionController(
            JavaPlugin plugin,
            CharacterSessionController characters,
            CombatSessionController combat,
            BuildEngine builds,
            String contentVersion) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.builds = Objects.requireNonNull(builds, "builds");
        this.contentVersion = requireText(contentVersion, "contentVersion");
    }

    void start() {
        if (supervisionTaskId >= 0) {
            return;
        }
        supervisionTaskId =
                plugin.getServer()
                        .getScheduler()
                        .scheduleSyncRepeatingTask(
                                plugin,
                                this::supervise,
                                SUPERVISION_PERIOD_TICKS,
                                SUPERVISION_PERIOD_TICKS);
    }

    void shutdown() {
        if (supervisionTaskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(supervisionTaskId);
            supervisionTaskId = -1;
        }
        activeBySession.clear();
        sessionByParticipant.clear();
    }

    void begin(Player teacher, Player student, DefinitionId techniqueId) {
        Objects.requireNonNull(teacher, "teacher");
        Objects.requireNonNull(student, "student");
        Objects.requireNonNull(techniqueId, "techniqueId");
        if (!characters.ready(teacher) || !characters.ready(student)) {
            reject(teacher, "Both participants need ready Player Sessions.");
            return;
        }
        if (sessionByParticipant.containsKey(teacher.getUniqueId())
                || sessionByParticipant.containsKey(student.getUniqueId())) {
            reject(teacher, "A participant already has an active teaching session.");
            return;
        }
        if (!insideTrainingContext(teacher, student)) {
            reject(teacher, "Teaching requires both players within 16 blocks in the same world.");
            return;
        }
        if (!canBeginTraining(teacher) || !canBeginTraining(student)) {
            reject(teacher, "Both players must leave combat and finish active actions first.");
            return;
        }
        TechniqueDefinition technique = builds.technique(techniqueId).orElse(null);
        if (technique == null) {
            reject(teacher, "Technique is unavailable in the active content snapshot.");
            return;
        }
        LoadedCharacterSession teacherSession = characters.active(teacher).orElseThrow();
        LoadedCharacterSession studentSession = characters.active(student).orElseThrow();
        KnowledgeProfile teacherProfile = profile(teacherSession);
        KnowledgeProfile studentProfile = profile(studentSession);
        ProgressionTrack mastery = technique.masteryTrack();
        boolean teacherReady =
                readiness(teacherProfile, mastery).ordinal()
                        >= technique.teachingReadiness().ordinal();
        LearningRequirements requirements =
                new LearningRequirements(
                        Set.of(), Map.of(mastery, technique.learningReadiness()), Set.of());
        Result<TeachingSession, TeachingErrorCode> started =
                sessions.start(
                        UUID.randomUUID(),
                        teacherSession.characterId(),
                        studentSession.characterId(),
                        new KnowledgeKey(KnowledgeType.TECHNIQUE, technique.id()),
                        requirements,
                        teacherProfile,
                        teacherReady,
                        teacher.isOnline(),
                        student.isOnline(),
                        studentProfile,
                        plugin.getServer().getCurrentTick());
        if (started instanceof Result.Failure<TeachingSession, TeachingErrorCode> failure) {
            reject(teacher, failure.error().code() + ": " + failure.detail());
            return;
        }
        TeachingSession session =
                ((Result.Success<TeachingSession, TeachingErrorCode>) started).value();
        ActiveTeaching active = new ActiveTeaching(session, technique, UUID.randomUUID());
        activeBySession.put(session.sessionId(), active);
        sessionByParticipant.put(teacher.getUniqueId(), session.sessionId());
        sessionByParticipant.put(student.getUniqueId(), session.sessionId());
        teacher.sendMessage(
                Component.text(
                        "Teaching started: land "
                                + technique.moveId().value()
                                + " to demonstrate it for "
                                + student.getName()
                                + ".",
                        NamedTextColor.LIGHT_PURPLE));
        student.sendMessage(
                Component.text(
                        teacher.getName()
                                + " is teaching "
                                + technique.id().value()
                                + ". Watch the successful demonstration.",
                        NamedTextColor.LIGHT_PURPLE));
    }

    void cancel(Player participant) {
        Objects.requireNonNull(participant, "participant");
        ActiveTeaching active = active(participant.getUniqueId()).orElse(null);
        if (active == null) {
            reject(participant, "You have no active teaching session.");
            return;
        }
        if (active.commitInFlight) {
            reject(participant, "Permanent teaching commit is already in progress.");
            return;
        }
        remove(active);
        notifyParticipants(active, "Teaching cancelled by " + participant.getName() + ".", false);
    }

    void showStatus(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        ActiveTeaching active = active(viewer.getUniqueId()).orElse(null);
        if (active == null) {
            viewer.sendMessage(Component.text("Teaching session: NONE", NamedTextColor.GRAY));
            return;
        }
        TeachingSession session = active.session;
        viewer.sendMessage(
                Component.text(
                        "Teaching session "
                                + session.sessionId()
                                + " | technique="
                                + session.technique().id().value()
                                + " | phase="
                                + session.phase()
                                + " | progress="
                                + session.successfulActionIds().size()
                                + "/"
                                + TeachingSessionEngine.REQUIRED_SUCCESSFUL_ACTIONS
                                + (active.commitInFlight ? " | COMMITTING" : ""),
                        NamedTextColor.AQUA));
    }

    void observeSuccessfulAction(
            CharacterId actorId, UUID actionId, DefinitionId moveId, long currentTick) {
        ActiveTeaching active = active(actorId.value()).orElse(null);
        if (active == null || active.commitInFlight) {
            return;
        }
        TeachingSession previous = active.session;
        Result<TeachingSession, TeachingErrorCode> observed =
                actions.observeSuccessfulAction(
                        previous,
                        active.technique.moveId(),
                        actorId,
                        actionId,
                        moveId,
                        currentTick);
        if (observed instanceof Result.Failure<TeachingSession, TeachingErrorCode> failure) {
            remove(active);
            notifyParticipants(
                    active,
                    "Teaching ended: " + failure.error().code() + " " + failure.detail(),
                    false);
            return;
        }
        TeachingSession next =
                ((Result.Success<TeachingSession, TeachingErrorCode>) observed).value();
        active.session = next;
        if (previous.phase() == TeachingPhase.DEMONSTRATION
                && next.phase() == TeachingPhase.STUDENT_CHALLENGE) {
            notifyParticipants(
                    active,
                    "Demonstration accepted. Student must land "
                            + active.technique.moveId().value()
                            + " three times.",
                    true);
        } else if (next.successfulActionIds().size() > previous.successfulActionIds().size()) {
            notifyParticipants(
                    active,
                    "Teaching challenge progress "
                            + next.successfulActionIds().size()
                            + "/"
                            + TeachingSessionEngine.REQUIRED_SUCCESSFUL_ACTIONS
                            + ".",
                    true);
        }
        if (next.phase() == TeachingPhase.READY_TO_COMMIT) {
            commit(active, currentTick);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        ActiveTeaching active = active(event.getPlayer().getUniqueId()).orElse(null);
        if (active == null) {
            return;
        }
        if (active.commitInFlight) {
            notifyParticipants(
                    active,
                    "A participant disconnected during commit; reconnect to verify PostgreSQL truth.",
                    false);
            return;
        }
        remove(active);
        notifyParticipants(
                active,
                "Teaching cancelled because " + event.getPlayer().getName() + " disconnected.",
                false);
    }

    private void supervise() {
        long tick = plugin.getServer().getCurrentTick();
        for (ActiveTeaching active : java.util.List.copyOf(activeBySession.values())) {
            if (active.commitInFlight) {
                continue;
            }
            Player teacher = plugin.getServer().getPlayer(active.session.teacherId().value());
            Player student = plugin.getServer().getPlayer(active.session.studentId().value());
            if (teacher == null || student == null || !teacher.isOnline() || !student.isOnline()) {
                remove(active);
                notifyParticipants(active, "Teaching cancelled because a participant left.", false);
                continue;
            }
            if (!insideTrainingContext(teacher, student)) {
                remove(active);
                notifyParticipants(
                        active, "Teaching cancelled because the training group separated.", false);
                continue;
            }
            if (tick >= active.session.expiresTick()) {
                active.session = sessions.expire(active.session, tick);
                remove(active);
                notifyParticipants(active, "Teaching session expired.", false);
                continue;
            }
            if (active.session.phase() == TeachingPhase.READY_TO_COMMIT
                    && !active.commitInFlight
                    && tick >= active.nextCommitTick) {
                commit(active, tick);
            }
        }
    }

    private void commit(ActiveTeaching active, long currentTick) {
        if (active.commitInFlight || activeBySession.get(active.session.sessionId()) != active) {
            return;
        }
        Result<TeachingCompletion, TeachingErrorCode> completion =
                sessions.completion(active.session, currentTick);
        if (completion instanceof Result.Failure<TeachingCompletion, TeachingErrorCode> failure) {
            remove(active);
            notifyParticipants(
                    active,
                    "Teaching ended: " + failure.error().code() + " " + failure.detail(),
                    false);
            return;
        }
        Player teacher = plugin.getServer().getPlayer(active.session.teacherId().value());
        Player student = plugin.getServer().getPlayer(active.session.studentId().value());
        if (teacher == null || student == null) {
            remove(active);
            return;
        }
        TeachingCommitRequest request =
                new TeachingCommitRequest(
                        ((Result.Success<TeachingCompletion, TeachingErrorCode>) completion)
                                .value(),
                        new RenownDeedCandidate(
                                active.deedId,
                                active.session.teacherId(),
                                MENTORSHIP_DEED,
                                "mentorship:" + active.technique.id().value(),
                                MENTORSHIP_BASE_RENOWN,
                                contentVersion));
        active.commitInFlight = true;
        active.commitAttempts++;
        notifyParticipants(active, "Challenge complete; committing permanent Knowledge…", true);
        characters.commitTeaching(
                teacher,
                student,
                request,
                result -> completeCommit(active.session.sessionId(), result));
    }

    private void completeCommit(
            UUID teachingSessionId,
            Result<TeachingSessionCommitResult, CharacterSessionErrorCode> result) {
        ActiveTeaching active = activeBySession.get(teachingSessionId);
        if (active == null) {
            return;
        }
        active.commitInFlight = false;
        if (result
                instanceof
                Result.Success<TeachingSessionCommitResult, CharacterSessionErrorCode> success) {
            remove(active);
            int award = success.value().execution().teacherDeed().decision().awardedRenown();
            notifyParticipants(
                    active,
                    "Teaching persisted: "
                            + active.technique.id().value()
                            + " learned; mentor Renown +"
                            + award
                            + ".",
                    true);
            return;
        }
        Result.Failure<TeachingSessionCommitResult, CharacterSessionErrorCode> failure =
                (Result.Failure<TeachingSessionCommitResult, CharacterSessionErrorCode>) result;
        if (failure.error() == CharacterSessionErrorCode.CHARACTER_STATE_INVALID) {
            remove(active);
            notifyParticipants(
                    active,
                    "Participant session changed during commit; reconnect to verify PostgreSQL truth.",
                    false);
            return;
        }
        if (retryable(failure)
                && plugin.getServer().getCurrentTick() < active.session.expiresTick()) {
            active.nextCommitTick =
                    plugin.getServer().getCurrentTick() + retryDelay(active.commitAttempts);
            if (!active.commitRetryAnnounced) {
                active.commitRetryAnnounced = true;
                notifyParticipants(
                        active,
                        "Teaching commit delayed; retrying the same session/deed IDs.",
                        false);
            }
            return;
        }
        remove(active);
        notifyParticipants(
                active,
                "Teaching commit failed: " + failure.error().code() + " " + failure.detail(),
                false);
    }

    private Optional<ActiveTeaching> active(UUID participantId) {
        UUID sessionId = sessionByParticipant.get(participantId);
        return sessionId == null
                ? Optional.empty()
                : Optional.ofNullable(activeBySession.get(sessionId));
    }

    private void remove(ActiveTeaching active) {
        UUID sessionId = active.session.sessionId();
        activeBySession.remove(sessionId, active);
        sessionByParticipant.remove(active.session.teacherId().value(), sessionId);
        sessionByParticipant.remove(active.session.studentId().value(), sessionId);
    }

    private void notifyParticipants(ActiveTeaching active, String message, boolean success) {
        NamedTextColor color = success ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.YELLOW;
        Player teacher = plugin.getServer().getPlayer(active.session.teacherId().value());
        Player student = plugin.getServer().getPlayer(active.session.studentId().value());
        if (teacher != null && teacher.isOnline()) {
            teacher.sendMessage(Component.text(message, color));
        }
        if (student != null && student.isOnline()) {
            student.sendMessage(Component.text(message, color));
        }
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
                                        LiveTeachingSessionController::higherReadiness));
        return new KnowledgeProfile(learned, readiness, Set.of());
    }

    private static ReadinessBand readiness(
            KnowledgeProfile profile, ProgressionTrack progressionTrack) {
        return profile.readiness().getOrDefault(progressionTrack, ReadinessBand.UNFAMILIAR);
    }

    private static ReadinessBand higherReadiness(ReadinessBand left, ReadinessBand right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }

    private boolean canBeginTraining(Player player) {
        return combat.status(player)
                .filter(status -> status.engagementState() == EngagementState.EXPLORATION)
                .filter(status -> status.actionPhase().isEmpty())
                .filter(status -> status.bowDrawPhase().isEmpty())
                .filter(status -> status.spellCastPhase().isEmpty())
                .filter(status -> status.dodgePhase().isEmpty())
                .isPresent();
    }

    private static boolean insideTrainingContext(Player teacher, Player student) {
        return teacher.isOnline()
                && student.isOnline()
                && teacher.getWorld().equals(student.getWorld())
                && teacher.getLocation().distanceSquared(student.getLocation())
                        <= MAXIMUM_PARTICIPANT_DISTANCE_SQUARED;
    }

    private static boolean retryable(
            Result.Failure<TeachingSessionCommitResult, CharacterSessionErrorCode> failure) {
        return failure.error() == CharacterSessionErrorCode.CHARACTER_PERSISTENCE_UNAVAILABLE
                || failure.error() == CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED
                        && failure.detail().contains("Another durable participant mutation");
    }

    private static long retryDelay(int attempts) {
        int shift = Math.min(3, Math.max(0, attempts - 1));
        return Math.min(200L, COMMIT_RETRY_TICKS << shift);
    }

    private static void reject(Player player, String detail) {
        player.sendMessage(Component.text("Teaching rejected: " + detail, NamedTextColor.RED));
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static final class ActiveTeaching {
        private TeachingSession session;
        private final TechniqueDefinition technique;
        private final UUID deedId;
        private boolean commitInFlight;
        private boolean commitRetryAnnounced;
        private int commitAttempts;
        private long nextCommitTick;

        private ActiveTeaching(
                TeachingSession session, TechniqueDefinition technique, UUID deedId) {
            this.session = Objects.requireNonNull(session, "session");
            this.technique = Objects.requireNonNull(technique, "technique");
            this.deedId = Objects.requireNonNull(deedId, "deedId");
            nextCommitTick = session.startedTick();
        }
    }
}
