package com.branz.mmorpg.core.character;

import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.character.CharacterClassLevelChanged;
import com.branz.mmorpg.api.character.CharacterClassProgress;
import com.branz.mmorpg.api.character.CharacterClassProgressionRepository;
import com.branz.mmorpg.api.character.ClassProgressionMutationCommit;
import com.branz.mmorpg.api.character.ClassSkillNodeUnlocked;
import com.branz.mmorpg.api.character.ClassSkillPointsGranted;
import com.branz.mmorpg.api.character.ClassSkillTreeRespecced;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.event.EventBus;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.api.stat.AttributeModifier;
import com.branz.mmorpg.api.stat.ModifierSource;
import com.branz.mmorpg.core.player.PlayerSessionService;
import com.branz.mmorpg.core.stat.PlayerAttributeService;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Transactional K2 service for class XP, Skill Points, tree ranks, and effects. */
public final class CharacterClassProgressionService {
    private final PlayerSessionService sessions;
    private final ContentService content;
    private final CharacterClassProgressionRepository repository;
    private final PlayerAttributeService attributes;
    private final EventBus events;
    private final GameClock clock;
    private final ConcurrentHashMap<UUID, CharacterClassProgress> active = new ConcurrentHashMap<>();
    private final Object[] mutationLocks = locks();

    public CharacterClassProgressionService(PlayerSessionService sessions, ContentService content,
                                            CharacterClassProgressionRepository repository,
                                            PlayerAttributeService attributes, EventBus events,
                                            GameClock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.content = Objects.requireNonNull(content, "content");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.attributes = Objects.requireNonNull(attributes, "attributes");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CharacterClassProgress progress(UUID playerId) {
        CharacterClassProgress cached = active.get(playerId);
        if (cached != null) return cached;
        CharacterClassDefinition definition = definition(playerId);
        CharacterClassProgress loaded = repository.load(
                playerId, definition.id(), definition.treeRevision(), clock.now());
        CharacterClassProgress raced = active.putIfAbsent(playerId, loaded);
        return raced == null ? loaded : raced;
    }

    public ClassProgressionMutationCommit grantXp(UUID playerId, long amount,
                                                  OperationId operationId) {
        CharacterClassProgressionEngine engine = engine(playerId);
        CharacterClassDefinition definition = definition(playerId);
        ClassProgressionMutationCommit commit;
        synchronized (lock(playerId)) {
            commit = repository.mutate(playerId, definition.id(),
                    definition.treeRevision(), operationId, "class_xp_grant",
                    current -> engine.grantXp(current, amount, clock.now()));
            active.put(playerId, commit.after());
        }
        if (commit.applied()) publishLevelEvents(commit);
        return commit;
    }

    public ClassProgressionMutationCommit purchase(UUID playerId, ContentId nodeId,
                                                   OperationId operationId) {
        CharacterClassProgressionEngine engine = engine(playerId);
        CharacterClassDefinition definition = definition(playerId);
        ClassProgressionMutationCommit commit;
        synchronized (lock(playerId)) {
            commit = repository.mutate(playerId, definition.id(),
                    definition.treeRevision(), operationId, "class_skill_node_purchase",
                    current -> engine.purchase(current, nodeId, clock.now()));
            active.put(playerId, commit.after());
        }
        if (commit.applied()) {
            int oldRank = commit.before().rank(nodeId);
            events.publish(new ClassSkillNodeUnlocked(UUID.randomUUID(), clock.now(), playerId,
                    definition.id(), nodeId, oldRank, commit.after().rank(nodeId),
                    commit.after().unspentSkillPoints()));
            reconcileModifiers(playerId, commit.after());
        }
        return commit;
    }

    public ClassProgressionMutationCommit respec(UUID playerId, OperationId operationId) {
        CharacterClassProgressionEngine engine = engine(playerId);
        CharacterClassDefinition definition = definition(playerId);
        ClassProgressionMutationCommit commit;
        synchronized (lock(playerId)) {
            commit = repository.mutate(playerId, definition.id(),
                    definition.treeRevision(), operationId, "class_skill_tree_respec",
                    current -> engine.respec(current, clock.now()));
            active.put(playerId, commit.after());
        }
        if (commit.applied()) {
            int refunded = commit.after().unspentSkillPoints()
                    - commit.before().unspentSkillPoints();
            events.publish(new ClassSkillTreeRespecced(UUID.randomUUID(), clock.now(), playerId,
                    definition.id(), refunded, commit.after().unspentSkillPoints(),
                    commit.after().treeRevision()));
            reconcileModifiers(playerId, commit.after());
        }
        return commit;
    }

    public boolean skillUnlocked(UUID playerId, ContentId skillId) {
        CharacterClassProgressionEngine engine = engine(playerId);
        return engine.unlockedSkills(progress(playerId)).contains(skillId);
    }

    public Set<ContentId> unlockedSkills(UUID playerId) {
        CharacterClassProgressionEngine engine = engine(playerId);
        return engine.unlockedSkills(progress(playerId));
    }

    /** Idempotently rebuilds all class-tree modifiers from authoritative ranks. */
    public void reconcileModifiers(UUID playerId, CharacterClassProgress progress) {
        CharacterClassProgressionEngine engine = engine(playerId);
        for (var node : engine.nodes().values()) {
            attributes.removeSource(playerId, ModifierSource.of(
                    ModifierSource.SourceType.CLASS_TREE, node.id().toString()));
        }
        progress.nodeRanks().forEach((nodeId, rank) -> {
            var node = engine.nodes().get(nodeId);
            if (node == null || rank <= 0) return;
            ModifierSource source = ModifierSource.of(
                    ModifierSource.SourceType.CLASS_TREE, node.id().toString());
            for (AttributeModifier template : node.modifiers()) {
                attributes.addModifier(playerId, new AttributeModifier(
                        "class-tree:" + node.id() + ":" + template.id(),
                        template.attribute(), template.operation(), template.value() * rank,
                        source, template.stackingGroup(), template.priority(), template.expiresAt()));
            }
        });
    }

    public void activate(UUID playerId) {
        CharacterClassDefinition definition = definition(playerId);
        CharacterClassProgress loaded = repository.load(
                playerId, definition.id(), definition.treeRevision(), clock.now());
        active.put(playerId, loaded);
        if (attributes.find(playerId).isPresent()) reconcileModifiers(playerId, loaded);
    }

    public void reconcileCached(UUID playerId) {
        CharacterClassProgress progress = active.get(playerId);
        if (progress == null) {
            throw new IllegalStateException("class progression is not active");
        }
        reconcileModifiers(playerId, progress);
    }

    public void forget(UUID playerId) {
        active.remove(playerId);
    }

    private Object lock(UUID playerId) {
        return mutationLocks[(playerId.hashCode() & Integer.MAX_VALUE) % mutationLocks.length];
    }

    private static Object[] locks() {
        Object[] result = new Object[64];
        java.util.Arrays.setAll(result, ignored -> new Object());
        return result;
    }

    private CharacterClassProgressionEngine engine(UUID playerId) {
        return new CharacterClassProgressionEngine(definition(playerId),
                content.snapshot().classSkillNodes());
    }

    private CharacterClassDefinition definition(UUID playerId) {
        ContentId classId = sessions.requirePlayable(playerId).profile().classId()
                .orElseThrow(() -> new IllegalStateException("permanent class must be selected"));
        CharacterClassDefinition definition = content.snapshot().characterClasses().get(classId);
        if (definition == null) throw new IllegalStateException("selected class content is unavailable");
        return definition;
    }

    private void publishLevelEvents(ClassProgressionMutationCommit commit) {
        if (commit.after().level() != commit.before().level()) {
            events.publish(new CharacterClassLevelChanged(UUID.randomUUID(), clock.now(),
                    commit.after().playerId(), commit.after().classId(), commit.before().level(),
                    commit.after().level(), commit.after().totalXp()));
        }
        int granted = commit.after().unspentSkillPoints()
                - commit.before().unspentSkillPoints();
        if (granted > 0) {
            events.publish(new ClassSkillPointsGranted(UUID.randomUUID(), clock.now(),
                    commit.after().playerId(), commit.after().classId(), granted,
                    commit.after().unspentSkillPoints()));
        }
    }
}
