package com.branz.mmorpg.core.stat;

import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.event.EventBus;
import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.api.skill.ResourceType;
import com.branz.mmorpg.api.stat.AttributeChanged;
import com.branz.mmorpg.api.stat.AttributeModifier;
import com.branz.mmorpg.api.stat.AttributeSnapshot;
import com.branz.mmorpg.api.stat.AttributeType;
import com.branz.mmorpg.api.stat.ModifierAdded;
import com.branz.mmorpg.api.stat.ModifierRemoved;
import com.branz.mmorpg.api.stat.ModifierSource;
import com.branz.mmorpg.api.stat.ResourceChanged;
import com.branz.mmorpg.api.stat.ResourceDepleted;
import com.branz.mmorpg.api.stat.ResourceSnapshot;
import com.branz.mmorpg.core.player.PlayerSessionService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Session-scoped C2 runtime; no Bukkit object is retained. */
public final class PlayerAttributeService {
    private final PlayerSessionService sessions;
    private final ContentService content;
    private final EventBus events;
    private final GameClock clock;
    private final Map<UUID, PlayerStatBlock> blocks = new ConcurrentHashMap<>();

    public PlayerAttributeService(PlayerSessionService sessions, ContentService content,
                                  EventBus events, GameClock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.content = Objects.requireNonNull(content, "content");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PlayerStatBlock activate(UUID playerId) {
        var profile = sessions.requirePlayable(playerId).profile();
        var classId = profile.classId().orElseThrow(() -> new MMOException(
                ErrorCode.INVALID_ARGUMENT, "player must select a permanent class first"));
        var snapshot = content.snapshot();
        CharacterClassDefinition definition = snapshot.find(classId, CharacterClassDefinition.class)
                .orElseThrow(() -> new MMOException(ErrorCode.CONTENT_INVALID,
                        "selected class is absent from active content: " + classId));
        return blocks.compute(playerId, (ignored, current) ->
                current != null && current.classId().value().equals(classId)
                        && current.contentRevision() == snapshot.revision()
                        ? current : new PlayerStatBlock(playerId, definition, snapshot.revision()));
    }

    public Optional<PlayerStatBlock> find(UUID playerId) {
        return Optional.ofNullable(blocks.get(playerId));
    }

    public PlayerStatBlock require(UUID playerId) {
        PlayerStatBlock block = blocks.get(playerId);
        return block == null ? activate(playerId) : block;
    }

    public void forget(UUID playerId) { blocks.remove(playerId); }

    public AttributeSnapshot attributes(UUID playerId) { return require(playerId).attributes(clock); }
    public ResourceSnapshot resource(UUID playerId, ResourceType resource) {
        return require(playerId).resource(resource);
    }
    public Map<ResourceType, ResourceSnapshot> resources(UUID playerId) {
        return require(playerId).resources();
    }

    public boolean addModifier(UUID playerId, AttributeModifier modifier) {
        PlayerStatBlock block = require(playerId);
        AttributeSnapshot before = block.attributes(clock);
        Map<ResourceType, ResourceSnapshot> resourcesBefore = block.resources();
        Optional<AttributeModifier> replaced = block.modifier(modifier.id());
        if (!block.addModifier(modifier)) return false;
        replaced.ifPresent(value -> events.publish(new ModifierRemoved(
                UUID.randomUUID(), clock.now(), playerId, value)));
        events.publish(new ModifierAdded(UUID.randomUUID(), clock.now(), playerId, modifier));
        publishAttributeChanges(playerId, before, block.attributes(clock));
        publishResourceChanges(playerId, resourcesBefore, block.resources(), "modifier_added");
        return true;
    }

    public boolean removeModifier(UUID playerId, String modifierId) {
        PlayerStatBlock block = require(playerId);
        Optional<AttributeModifier> removed = block.modifier(modifierId);
        if (removed.isEmpty()) return false;
        AttributeSnapshot before = block.attributes(clock);
        Map<ResourceType, ResourceSnapshot> resourcesBefore = block.resources();
        block.removeModifier(modifierId);
        events.publish(new ModifierRemoved(UUID.randomUUID(), clock.now(), playerId, removed.get()));
        publishAttributeChanges(playerId, before, block.attributes(clock));
        publishResourceChanges(playerId, resourcesBefore, block.resources(), "modifier_removed");
        return true;
    }

    /** Removes exactly the modifiers granted by one equipment/status/mastery source. */
    public int removeSource(UUID playerId, ModifierSource source) {
        PlayerStatBlock block = require(playerId);
        List<AttributeModifier> removed = block.modifiers().stream()
                .filter(modifier -> modifier.source().equals(source)).toList();
        if (removed.isEmpty()) return 0;
        AttributeSnapshot before = block.attributes(clock);
        Map<ResourceType, ResourceSnapshot> resourcesBefore = block.resources();
        block.removeSource(source);
        removed.forEach(modifier -> events.publish(new ModifierRemoved(
                UUID.randomUUID(), clock.now(), playerId, modifier)));
        publishAttributeChanges(playerId, before, block.attributes(clock));
        publishResourceChanges(playerId, resourcesBefore, block.resources(), "source_removed");
        return removed.size();
    }

    public boolean spend(UUID playerId, Map<ResourceType, Double> costs, String reason) {
        PlayerStatBlock block = require(playerId);
        Map<ResourceType, ResourceSnapshot> before = block.resources();
        if (!block.spend(costs)) return false;
        publishResourceChanges(playerId, before, block.resources(), reason);
        return true;
    }

    public ResourceSnapshot add(UUID playerId, ResourceType resource, double amount, String reason) {
        PlayerStatBlock block = require(playerId);
        Map<ResourceType, ResourceSnapshot> before = block.resources();
        ResourceSnapshot result = block.add(resource, amount);
        publishResourceChanges(playerId, before, block.resources(), reason);
        return result;
    }

    public void tick(UUID playerId, long elapsedTicks, boolean inCombat) {
        PlayerStatBlock block = require(playerId);
        AttributeSnapshot attributesBefore = block.attributesWithoutExpirySweep();
        Map<ResourceType, ResourceSnapshot> resourcesBeforeExpiry = block.resources();
        List<AttributeModifier> expired = block.modifiers().stream()
                .filter(modifier -> modifier.expiredAt(clock.now())).toList();
        block.purgeExpired(clock.now());
        expired.forEach(modifier -> events.publish(new ModifierRemoved(
                UUID.randomUUID(), clock.now(), playerId, modifier)));
        publishAttributeChanges(playerId, attributesBefore, block.attributes(clock));
        publishResourceChanges(playerId, resourcesBeforeExpiry, block.resources(), "modifier_expired");
        Map<ResourceType, ResourceSnapshot> before = block.resources();
        block.regenerate(elapsedTicks, inCombat);
        publishResourceChanges(playerId, before, block.resources(), "regeneration");
    }

    private void publishAttributeChanges(UUID playerId, AttributeSnapshot before,
                                         AttributeSnapshot after) {
        after.differenceFrom(before).forEach((attribute, value) -> events.publish(
                new AttributeChanged(UUID.randomUUID(), clock.now(), playerId,
                        attribute, before.get(attribute), value)));
    }

    private void publishResourceChanges(UUID playerId,
                                        Map<ResourceType, ResourceSnapshot> before,
                                        Map<ResourceType, ResourceSnapshot> after,
                                        String reason) {
        after.forEach((type, value) -> {
            ResourceSnapshot old = before.get(type);
            if (old != null && (Double.compare(old.current(), value.current()) != 0
                    || Double.compare(old.maximum(), value.maximum()) != 0)) {
                events.publish(new ResourceChanged(UUID.randomUUID(), clock.now(), playerId,
                        type, old.current(), value.current(), value.maximum(), reason));
                if (!old.depleted() && value.depleted()) {
                    events.publish(new ResourceDepleted(UUID.randomUUID(), clock.now(), playerId,
                            type, reason));
                }
            }
        });
    }
}
