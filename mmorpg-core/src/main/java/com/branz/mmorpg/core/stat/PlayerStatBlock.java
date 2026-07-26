package com.branz.mmorpg.core.stat;

import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.character.CharacterClassId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.api.skill.ResourceType;
import com.branz.mmorpg.api.stat.AttributeModifier;
import com.branz.mmorpg.api.stat.AttributeSnapshot;
import com.branz.mmorpg.api.stat.AttributeType;
import com.branz.mmorpg.api.stat.ResourcePolicy;
import com.branz.mmorpg.api.stat.ResourceSnapshot;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Runtime-only attributes and bounded resources derived from one permanent class. */
public final class PlayerStatBlock {
    private final UUID playerId;
    private final CharacterClassId classId;
    private final long contentRevision;
    private final ResourceType primaryResource;
    private final AttributeContainer attributes = new AttributeContainer();
    private final EnumMap<ResourceType, ResourcePolicy> policies = new EnumMap<>(ResourceType.class);
    private final EnumMap<ResourceType, ResourcePool> resources = new EnumMap<>(ResourceType.class);

    public PlayerStatBlock(UUID playerId, CharacterClassDefinition definition, long contentRevision) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(definition, "definition");
        if (contentRevision < 1) throw new IllegalArgumentException("content revision must be positive");
        this.classId = definition.classId();
        this.contentRevision = contentRevision;
        this.primaryResource = definition.primaryResource();
        definition.baseAttributes().forEach((key, value) ->
                attributes.base(AttributeType.fromContentKey(key), value));

        Set<ResourceType> enabled = new LinkedHashSet<>();
        enabled.add(ResourceType.HEALTH);
        enabled.add(primaryResource);
        enabled.addAll(definition.secondaryResources());
        for (ResourceType resource : enabled) {
            ResourcePolicy policy = ResourcePolicy.standard(resource);
            double maximum = attributes.value(AttributeType.maximumFor(resource));
            double initial = policy.initialValue() == ResourcePolicy.InitialValue.FULL ? maximum : 0.0;
            policies.put(resource, policy);
            resources.put(resource, new ResourcePool(resource, maximum, initial));
        }
    }

    public UUID playerId() { return playerId; }
    public CharacterClassId classId() { return classId; }
    public long contentRevision() { return contentRevision; }
    public ResourceType primaryResource() { return primaryResource; }

    public synchronized AttributeSnapshot attributes(GameClock clock) {
        if (attributes.purgeExpired(clock.now()) > 0) reconcileMaximums();
        return attributes.snapshot(clock);
    }

    synchronized AttributeSnapshot attributesWithoutExpirySweep() {
        EnumMap<AttributeType, Double> values = new EnumMap<>(AttributeType.class);
        for (AttributeType attribute : AttributeType.values()) {
            values.put(attribute, attributes.value(attribute));
        }
        return new AttributeSnapshot(values);
    }

    public synchronized List<AttributeModifier> modifiers() {
        return attributes.modifiers();
    }

    public synchronized Optional<AttributeModifier> modifier(String modifierId) {
        return attributes.modifier(modifierId);
    }

    public synchronized boolean addModifier(AttributeModifier modifier) {
        boolean changed = attributes.add(modifier);
        if (changed) reconcileMaximums();
        return changed;
    }

    public synchronized boolean removeModifier(String modifierId) {
        boolean changed = attributes.remove(modifierId);
        if (changed) reconcileMaximums();
        return changed;
    }

    public synchronized int purgeExpired(Instant now) {
        int removed = attributes.purgeExpired(now);
        if (removed > 0) reconcileMaximums();
        return removed;
    }

    public synchronized ResourceSnapshot resource(ResourceType resource) {
        return pool(resource).snapshot();
    }

    public synchronized Map<ResourceType, ResourceSnapshot> resources() {
        EnumMap<ResourceType, ResourceSnapshot> copy = new EnumMap<>(ResourceType.class);
        resources.forEach((type, pool) -> copy.put(type, pool.snapshot()));
        return Map.copyOf(copy);
    }

    /** All resource costs are checked before any pool is changed. */
    public synchronized boolean spend(Map<ResourceType, Double> costs) {
        Objects.requireNonNull(costs, "costs");
        for (Map.Entry<ResourceType, Double> cost : costs.entrySet()) {
            double amount = requireAmount(cost.getValue(), "cost");
            ResourcePool pool = resources.get(cost.getKey());
            if (pool == null || pool.current() < amount) return false;
        }
        costs.forEach((resource, amount) -> resources.get(resource).spend(amount));
        return true;
    }

    public synchronized ResourceSnapshot add(ResourceType resource, double amount) {
        pool(resource).add(amount);
        return pool(resource).snapshot();
    }

    public synchronized void regenerate(long elapsedTicks, boolean inCombat) {
        if (elapsedTicks <= 0) return;
        resources.forEach((type, pool) -> {
            ResourcePolicy policy = policies.get(type);
            pool.regenerate(policy.regenerationPerSecond(), elapsedTicks,
                    inCombat, policy.combatRegenerationFactor());
        });
    }

    private void reconcileMaximums() {
        resources.forEach((resource, pool) ->
                pool.maximum(attributes.value(AttributeType.maximumFor(resource))));
    }

    private ResourcePool pool(ResourceType resource) {
        ResourcePool pool = resources.get(Objects.requireNonNull(resource, "resource"));
        if (pool == null) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    resource + " is not enabled for class " + classId);
        }
        return pool;
    }

    private static double requireAmount(Double amount, String label) {
        if (amount == null || !Double.isFinite(amount) || amount < 0) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT, label + " must be finite and non-negative");
        }
        return amount;
    }
}
