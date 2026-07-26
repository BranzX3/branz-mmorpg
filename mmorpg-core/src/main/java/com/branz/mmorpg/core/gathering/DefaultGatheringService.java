package com.branz.mmorpg.core.gathering;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.gathering.GatheringNodeDefinition;
import com.branz.mmorpg.api.gathering.GatheringNodeInstance;
import com.branz.mmorpg.api.gathering.GatheringNodeRepository;
import com.branz.mmorpg.api.gathering.GatheringNodeState;
import com.branz.mmorpg.api.gathering.GatheringReservation;
import com.branz.mmorpg.api.gathering.GatheringResult;
import com.branz.mmorpg.api.gathering.GatheringService;
import com.branz.mmorpg.api.gathering.WorldBlockPosition;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.core.item.InventoryEngine;
import com.branz.mmorpg.core.lifeskill.LifeSkillProgressionEngine;
import com.branz.mmorpg.core.player.PlayerSessionService;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Registered-node gathering orchestration; ordinary blocks never enter this service. */
public final class DefaultGatheringService implements GatheringService {
    private static final Duration RESERVATION_GRACE = Duration.ofSeconds(2);
    private final GatheringNodeRepository repository;
    private final PlayerSessionService sessions;
    private final Supplier<ContentSnapshot> content;
    private final GameClock clock;
    private final InventoryEngine inventoryEngine = new InventoryEngine();

    public DefaultGatheringService(GatheringNodeRepository repository,
                                   PlayerSessionService sessions,
                                   Supplier<ContentSnapshot> content, GameClock clock) {
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.sessions = java.util.Objects.requireNonNull(sessions, "sessions");
        this.content = java.util.Objects.requireNonNull(content, "content");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    @Override public Optional<GatheringNodeInstance> findAt(WorldBlockPosition position) {
        return repository.findAt(position);
    }

    @Override public Collection<GatheringNodeInstance> nodes() { return repository.list(); }

    @Override
    public GatheringNodeInstance place(
            ContentId definitionId, WorldBlockPosition position, UUID createdBy) {
        requireDefinition(definitionId);
        return repository.place(GatheringNodeInstance.placed(
                UUID.randomUUID(), definitionId, position, createdBy, clock.now()));
    }

    @Override public boolean remove(UUID nodeInstanceId) {
        return repository.remove(nodeInstanceId);
    }

    @Override
    public GatheringReservation begin(
            UUID playerId, WorldBlockPosition position, Set<String> heldToolTags,
            boolean regionAllowed, boolean presentationMatches) {
        var session = sessions.requirePlayable(playerId);
        GatheringNodeInstance node = repository.findAt(position)
                .orElseThrow(() -> new IllegalArgumentException("NOT_A_REGISTERED_NODE"));
        GatheringNodeDefinition definition = content.get().gatheringNodes()
                .get(node.definitionId());
        if (definition == null || !presentationMatches) {
            repository.setState(node.instanceId(), GatheringNodeState.BROKEN, clock.now());
            throw new IllegalStateException("NODE_BROKEN");
        }
        if (!regionAllowed) throw new IllegalStateException("REGION_DENIED");
        if (!heldToolTags.contains(definition.requiredToolTag())) {
            throw new IllegalStateException("INVALID_TOOL");
        }
        if (session.lifeSkills().skill(definition.skillId()).level()
                < definition.requiredLevel()) {
            throw new IllegalStateException("LEVEL_TOO_LOW");
        }
        Instant now = clock.now();
        long durationMillis = effectiveHarvestMillis(definition, session.lifeSkills()
                .skill(definition.skillId()).nodeRanks(), content.get());
        GatheringNodeInstance reserved = repository.reserve(
                node.instanceId(), playerId, now,
                Duration.ofMillis(durationMillis), RESERVATION_GRACE);
        OperationId operation = OperationId.of("gathering", definition.id().toString(), playerId,
                compactNode(reserved.instanceId()) + '-' + reserved.reservationSequence());
        return new GatheringReservation(reserved.instanceId(), definition.id(), playerId,
                reserved.reservationSequence(), now, now.plusMillis(durationMillis), operation);
    }

    @Override
    public GatheringResult complete(GatheringReservation reservation) {
        sessions.requirePlayable(reservation.playerId());
        Instant now = clock.now();
        if (now.isBefore(reservation.completesAt())) {
            throw new IllegalStateException("HARVEST_NOT_COMPLETE");
        }
        ContentSnapshot snapshot = content.get();
        GatheringNodeDefinition definition = snapshot.gatheringNodes()
                .get(reservation.definitionId());
        if (definition == null) throw new IllegalStateException("NODE_BROKEN");
        Map<ContentId, Long> yields = resolveYields(
                definition, reservation.nodeInstanceId(), reservation.reservationSequence());
        long xp = definition.baseXp();
        long jitter = respawnJitter(definition, reservation.nodeInstanceId(),
                reservation.reservationSequence());
        Instant respawnAt = now.plusMillis(definition.respawnMillis() + jitter);
        LifeSkillProgressionEngine progression = progression(snapshot, definition.skillId());
        var commit = repository.commitHarvest(
                reservation.nodeInstanceId(), reservation.playerId(),
                reservation.reservationSequence(), definition.skillId(),
                reservation.operationId(), now, respawnAt,
                before -> progression.award(
                        before, xp, snapshot.revision(), now).after(),
                before -> grantYields(before, yields, snapshot, now));
        sessions.requirePlayable(reservation.playerId())
                .acceptPersistedLifeSkill(commit.skillAfter());
        Instant committedRespawn = commit.nodeAfter().respawnAt().orElse(respawnAt);
        return new GatheringResult(commit.applied(), commit.nodeAfter(),
                commit.applied() ? xp : 0, yields, committedRespawn);
    }

    @Override
    public void interrupt(GatheringReservation reservation, String reason) {
        repository.release(reservation.nodeInstanceId(), reservation.playerId(),
                reservation.reservationSequence(), clock.now());
    }

    @Override
    public GatheringNodeInstance setState(
            UUID nodeInstanceId, GatheringNodeState state, Instant now) {
        return repository.setState(nodeInstanceId, state, now);
    }

    private InventorySnapshot grantYields(
            InventorySnapshot before, Map<ContentId, Long> yields,
            ContentSnapshot snapshot, Instant now) {
        InventorySnapshot current = before;
        for (var entry : yields.entrySet()) {
            var material = snapshot.materials().get(entry.getKey());
            if (material == null) throw new IllegalStateException(
                    "unknown gathering material " + entry.getKey());
            current = inventoryEngine.grantMaterial(current, material, entry.getValue(),
                    id -> {
                        var definition = snapshot.materials().get(id);
                        return definition == null ? 0 : definition.maxStackSize();
                    }, now).snapshot();
        }
        return current;
    }

    private GatheringNodeDefinition requireDefinition(ContentId id) {
        GatheringNodeDefinition definition = content.get().gatheringNodes().get(id);
        if (definition == null) throw new IllegalArgumentException("unknown gathering node " + id);
        return definition;
    }

    private static LifeSkillProgressionEngine progression(
            ContentSnapshot snapshot, ContentId skillId) {
        var skill = snapshot.lifeSkills().get(skillId);
        if (skill == null) throw new IllegalArgumentException("unknown Life Skill " + skillId);
        var nodes = snapshot.lifeSkillNodes().entrySet().stream()
                .filter(entry -> entry.getValue().skillId().equals(skillId))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return new LifeSkillProgressionEngine(skill, nodes);
    }

    private static long effectiveHarvestMillis(
            GatheringNodeDefinition definition, Map<ContentId, Integer> ranks,
            ContentSnapshot snapshot) {
        double reduction = 0;
        for (var rank : ranks.entrySet()) {
            var node = snapshot.lifeSkillNodes().get(rank.getKey());
            if (node == null || !"harvest_time_reduction".equals(node.effect().type())
                    || java.util.Collections.disjoint(
                            node.effect().targetTags(), definition.tags())) continue;
            reduction += Math.min(node.effect().capPercent(),
                    node.effect().percentPerRank() * rank.getValue());
        }
        reduction = Math.min(80.0, Math.max(0.0, reduction));
        return Math.max(250, Math.round(definition.harvestTimeMillis() * (1 - reduction / 100)));
    }

    private static Map<ContentId, Long> resolveYields(
            GatheringNodeDefinition definition, UUID nodeId, long sequence) {
        SplittableRandom random = random(nodeId, sequence, 1);
        Map<ContentId, Long> result = new LinkedHashMap<>();
        for (var yield : definition.yields()) {
            if (random.nextDouble() > yield.chance()) continue;
            long range = Math.addExact(yield.maximumAmount() - yield.minimumAmount(), 1);
            long amount = yield.minimumAmount() + random.nextLong(range);
            result.merge(yield.itemId(), amount, Math::addExact);
        }
        return Map.copyOf(result);
    }

    private static long respawnJitter(
            GatheringNodeDefinition definition, UUID nodeId, long sequence) {
        if (definition.respawnJitterMillis() == 0) return 0;
        return random(nodeId, sequence, 2)
                .nextLong(definition.respawnJitterMillis() + 1);
    }

    private static SplittableRandom random(UUID nodeId, long sequence, int stream) {
        ByteBuffer buffer = ByteBuffer.allocate(28)
                .putLong(nodeId.getMostSignificantBits()).putLong(nodeId.getLeastSignificantBits())
                .putLong(sequence).putInt(stream);
        return new SplittableRandom(java.util.Arrays.hashCode(buffer.array()));
    }

    private static String compactNode(UUID nodeId) {
        return nodeId.toString().replace("-", "");
    }
}
