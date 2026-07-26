package com.branz.mmorpg.core.status;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.api.status.StatusDefinition;
import com.branz.mmorpg.api.status.StatusInstance;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The single ticker for every status on the server.
 *
 * <p>One sweep advances every container, rather than each effect owning a
 * scheduled task. Ten thousand active effects cost one pass over ten thousand
 * records, not ten thousand timers competing for the scheduler — which is the
 * difference between a status system that scales and one that becomes the
 * server's dominant cost.
 *
 * <p>Registration is per target. A target with no statuses is cheap to visit and
 * is dropped when it unregisters, so nothing survives logout.
 */
public final class StatusWheel {

    private final Map<UUID, StatusContainer> containers = new ConcurrentHashMap<>();
    private final Function<ContentId, StatusDefinition> definitions;

    public StatusWheel(Function<ContentId, StatusDefinition> definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    /** Container for {@code targetId}, created on first use. */
    public StatusContainer container(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        return containers.computeIfAbsent(targetId, id -> new StatusContainer());
    }

    public boolean isRegistered(UUID targetId) {
        return containers.containsKey(targetId);
    }

    /** Drops a target entirely. Called on death cleanup and on logout. */
    public List<StatusInstance> unregister(UUID targetId) {
        StatusContainer container = containers.remove(targetId);
        return container == null ? List.of() : container.clear();
    }

    public int trackedTargets() {
        return containers.size();
    }

    public int activeEffects() {
        return containers.values().stream().mapToInt(StatusContainer::size).sum();
    }

    /**
     * Advances every registered target one sweep.
     *
     * @param handler receives each target's ticks and expiries; it is responsible
     *                for turning a tick into damage, healing, or absorption, and
     *                for removing the modifiers of expired statuses
     * @return how many targets produced anything
     */
    public int advance(GameClock clock, TickHandler handler) {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(handler, "handler");
        var now = clock.now();
        int touched = 0;
        for (Map.Entry<UUID, StatusContainer> entry : containers.entrySet()) {
            StatusContainer container = entry.getValue();
            if (container.size() == 0) {
                continue;
            }
            StatusTickResult result = container.advance(now);
            if (result.isEmpty()) {
                continue;
            }
            for (StatusInstance instance : result.ticked()) {
                StatusDefinition definition = definitions.apply(instance.definitionId());
                if (definition == null) {
                    // The definition vanished from the active content snapshot.
                    // Drop the instance rather than tick an effect nobody can
                    // describe any more.
                    container.remove(instance.instanceId());
                    continue;
                }
                handler.onTick(entry.getKey(), instance, definition);
                container.tickDelivered(instance, definition.periodicInterval(), now);
            }
            for (StatusInstance instance : result.expired()) {
                handler.onExpire(entry.getKey(), instance, definitions.apply(instance.definitionId()));
            }
            touched++;
        }
        return touched;
    }

    /** Callback for the effects a sweep produced. */
    public interface TickHandler {

        void onTick(UUID targetId, StatusInstance instance, StatusDefinition definition);

        /** {@code definition} may be null if it left the content snapshot. */
        void onExpire(UUID targetId, StatusInstance instance, StatusDefinition definition);
    }
}
