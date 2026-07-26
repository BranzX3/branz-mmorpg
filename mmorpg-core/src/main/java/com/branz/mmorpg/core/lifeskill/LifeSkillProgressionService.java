package com.branz.mmorpg.core.lifeskill;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.event.EventBus;
import com.branz.mmorpg.api.lifeskill.LifeSkillMutationCommit;
import com.branz.mmorpg.api.lifeskill.LifeSkillMutationService;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.lifeskill.SurvivalSkillLevelChanged;
import com.branz.mmorpg.api.lifeskill.SurvivalSkillNodeUnlocked;
import com.branz.mmorpg.api.lifeskill.SurvivalXpGranted;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.player.PlayerProfileRepository;
import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.core.player.PlayerSessionService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Transactional facade for XP, mastery-node purchases, and respecs. */
public final class LifeSkillProgressionService implements LifeSkillMutationService {

    private final PlayerProfileRepository repository;
    private final PlayerSessionService sessions;
    private final GameClock clock;
    private final Supplier<ContentSnapshot> content;
    private final EventBus events;

    public LifeSkillProgressionService(PlayerProfileRepository repository,
                                       PlayerSessionService sessions,
                                       GameClock clock,
                                       Supplier<ContentSnapshot> content) {
        this(repository, sessions, clock, content,
                new com.branz.mmorpg.core.event.SimpleEventBus());
    }

    public LifeSkillProgressionService(PlayerProfileRepository repository,
                                       PlayerSessionService sessions,
                                       GameClock clock,
                                       Supplier<ContentSnapshot> content,
                                       EventBus events) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.content = Objects.requireNonNull(content, "content");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override
    public LifeSkillMutationCommit grantXp(UUID playerId, ContentId skillId, long amount,
                                           OperationId operationId) {
        sessions.requirePlayable(playerId);
        ContentSnapshot snapshot = content.get();
        LifeSkillProgressionEngine engine = engine(snapshot, skillId);
        Instant now = clock.now();
        LifeSkillMutationCommit committed = repository.mutateLifeSkill(
                playerId, skillId, operationId,
                current -> engine.award(current, amount, snapshot.revision(), now).after());
        sessions.requirePlayable(playerId).acceptPersistedLifeSkill(committed.after());
        if (committed.applied()) {
            long awarded = committed.after().totalXp() - committed.before().totalXp();
            events.publish(new SurvivalXpGranted(
                    eventId(operationId, "xp"), now, operationId, playerId, skillId,
                    "api", amount, awarded, committed.after().totalXp(), snapshot.revision()));
            publishLevelChanges(playerId, skillId, operationId, committed, snapshot, now);
        }
        return committed;
    }

    @Override
    public LifeSkillMutationCommit purchase(UUID playerId, ContentId skillId, ContentId nodeId,
                                            OperationId operationId) {
        sessions.requirePlayable(playerId);
        ContentSnapshot snapshot = content.get();
        LifeSkillProgressionEngine engine = engine(snapshot, skillId);
        Instant now = clock.now();
        LifeSkillMutationCommit committed = repository.mutateLifeSkill(
                playerId, skillId, operationId,
                current -> engine.purchase(current, nodeId, now).after());
        sessions.requirePlayable(playerId).acceptPersistedLifeSkill(committed.after());
        if (committed.applied()) {
            int oldRank = committed.before().rankOf(nodeId);
            int newRank = committed.after().rankOf(nodeId);
            events.publish(new SurvivalSkillNodeUnlocked(
                    eventId(operationId, "node-" + newRank), now, operationId,
                    playerId, skillId, nodeId, oldRank, newRank,
                    committed.before().unspentPoints() - committed.after().unspentPoints(),
                    committed.after().unspentPoints(), snapshot.revision()));
        }
        return committed;
    }

    @Override
    public LifeSkillMutationCommit respec(UUID playerId, ContentId skillId,
                                          OperationId operationId) {
        sessions.requirePlayable(playerId);
        LifeSkillProgressionEngine engine = engine(content.get(), skillId);
        LifeSkillMutationCommit committed = repository.mutateLifeSkill(
                playerId, skillId, operationId,
                current -> engine.respec(current, clock.now()).after());
        sessions.requirePlayable(playerId).acceptPersistedLifeSkill(committed.after());
        return committed;
    }

    private static LifeSkillProgressionEngine engine(ContentSnapshot snapshot, ContentId skillId) {
        var skill = snapshot.lifeSkills().get(skillId);
        if (skill == null) {
            throw new IllegalArgumentException("unknown Life Skill " + skillId);
        }
        Map<ContentId, LifeSkillNodeDefinition> nodes = snapshot.lifeSkillNodes().entrySet().stream()
                .filter(entry -> entry.getValue().skillId().equals(skillId))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return new LifeSkillProgressionEngine(skill, nodes);
    }

    private void publishLevelChanges(
            UUID playerId, ContentId skillId, OperationId operationId,
            LifeSkillMutationCommit commit, ContentSnapshot snapshot, Instant now) {
        var definition = snapshot.lifeSkills().get(skillId);
        for (int level = commit.before().level() + 1; level <= commit.after().level(); level++) {
            int points = definition.pointMilestones().contains(level) ? 1 : 0;
            events.publish(new SurvivalSkillLevelChanged(
                    eventId(operationId, "level-" + level), now, operationId,
                    playerId, skillId, level - 1, level, commit.after().totalXp(),
                    points, snapshot.revision()));
        }
    }

    private static UUID eventId(OperationId operationId, String kind) {
        return UUID.nameUUIDFromBytes(
                (operationId.value() + ':' + kind).getBytes(StandardCharsets.UTF_8));
    }
}
