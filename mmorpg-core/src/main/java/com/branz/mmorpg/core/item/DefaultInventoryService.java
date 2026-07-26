package com.branz.mmorpg.core.item;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.item.InventoryMutationCommit;
import com.branz.mmorpg.api.item.InventoryRepository;
import com.branz.mmorpg.api.item.InventoryService;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.item.ItemInstance;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.runtime.GameClock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Consumer;

public final class DefaultInventoryService implements InventoryService {
    private final InventoryRepository repository;
    private final Supplier<ContentSnapshot> content;
    private final GameClock clock;
    private final InventoryEngine engine = new InventoryEngine();
    private volatile Consumer<InventoryChanged> listener = ignored -> {};

    public record InventoryChanged(
            UUID playerId, ContentId itemId, long quantity,
            OperationId operationId, boolean acquisition) {
    }

    public void mutationListener(Consumer<InventoryChanged> listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public DefaultInventoryService(InventoryRepository repository,
                                   Supplier<ContentSnapshot> content, GameClock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.content = Objects.requireNonNull(content, "content");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override public InventorySnapshot inventory(UUID playerId) {
        return repository.load(playerId);
    }

    @Override
    public InventoryMutationCommit grantMaterial(
            UUID playerId, ContentId materialId, long quantity, OperationId operationId) {
        var definition = content.get().materials().get(materialId);
        if (definition == null) {
            throw new IllegalArgumentException("unknown material " + materialId);
        }
        InventoryEngine.Mutation[] result = new InventoryEngine.Mutation[1];
        InventoryMutationCommit commit = repository.mutate(playerId, operationId, 0, 0, before -> {
            result[0] = engine.grantMaterial(before, definition, quantity,
                    this::stackSize, clock.now());
            return result[0].snapshot();
        });
        InventoryMutationCommit normalized = normalize(commit, result[0]);
        notify(normalized, new InventoryChanged(playerId, materialId,
                normalized.delivered(), operationId, true));
        return normalized;
    }

    @Override
    public InventoryMutationCommit grantUnique(
            UUID playerId, ItemInstance item, OperationId operationId) {
        if (!playerId.equals(item.boundOwner().orElse(playerId))) {
            throw new IllegalArgumentException("bound item belongs to another player");
        }
        if (content.get().find(item.definitionId()).isEmpty()) {
            throw new IllegalArgumentException("unknown item definition " + item.definitionId());
        }
        InventoryEngine.Mutation[] result = new InventoryEngine.Mutation[1];
        InventoryMutationCommit commit = repository.mutate(playerId, operationId, 0, 0, before -> {
            result[0] = engine.grantUnique(before, item, this::stackSize, clock.now());
            return result[0].snapshot();
        });
        InventoryMutationCommit normalized = normalize(commit, result[0]);
        notify(normalized, new InventoryChanged(playerId, item.definitionId(),
                normalized.delivered(), operationId, true));
        return normalized;
    }

    @Override
    public InventoryMutationCommit claimMaterial(
            UUID playerId, ContentId materialId, long quantity, OperationId operationId) {
        var definition = content.get().materials().get(materialId);
        if (definition == null) throw new IllegalArgumentException("unknown material " + materialId);
        InventoryEngine.Mutation[] result = new InventoryEngine.Mutation[1];
        InventoryMutationCommit commit = repository.mutate(playerId, operationId, 0, 0, before -> {
            result[0] = engine.claimMaterial(
                    before, definition, quantity, this::stackSize, clock.now());
            return result[0].snapshot();
        });
        InventoryMutationCommit normalized = normalize(commit, result[0]);
        notify(normalized, new InventoryChanged(playerId, materialId,
                normalized.delivered(), operationId, true));
        return normalized;
    }

    @Override
    public InventoryMutationCommit claimUnique(
            UUID playerId, UUID itemInstanceId, OperationId operationId) {
        InventoryEngine.Mutation[] result = new InventoryEngine.Mutation[1];
        InventoryMutationCommit commit = repository.mutate(playerId, operationId, 0, 0, before -> {
            result[0] = engine.claimUnique(
                    before, itemInstanceId, this::stackSize, clock.now());
            return result[0].snapshot();
        });
        InventoryMutationCommit normalized = normalize(commit, result[0]);
        ItemInstance claimed = normalized.after().items().get(itemInstanceId);
        if (claimed != null) notify(normalized, new InventoryChanged(playerId,
                claimed.definitionId(), normalized.delivered(), operationId, true));
        return normalized;
    }

    @Override public InventoryMutationCommit revokeMaterial(
            UUID playerId, ContentId materialId, long quantity, OperationId operationId) {
        if (!content.get().materials().containsKey(materialId)) {
            throw new IllegalArgumentException("unknown material " + materialId);
        }
        InventoryEngine.Mutation[] result = new InventoryEngine.Mutation[1];
        InventoryMutationCommit commit = repository.mutate(playerId, operationId, 0, 0, before -> {
            result[0] = engine.revokeMaterial(before, materialId, quantity, clock.now());
            return result[0].snapshot();
        });
        InventoryMutationCommit normalized = normalize(commit, result[0]);
        notify(normalized, new InventoryChanged(playerId, materialId, quantity,
                operationId, false));
        return normalized;
    }

    @Override public InventoryMutationCommit revokeUnique(
            UUID playerId, UUID itemInstanceId, OperationId operationId) {
        InventoryEngine.Mutation[] result = new InventoryEngine.Mutation[1];
        InventoryMutationCommit commit = repository.mutate(playerId, operationId, 0, 0, before -> {
            result[0] = engine.revokeUnique(before, itemInstanceId, clock.now());
            return result[0].snapshot();
        });
        InventoryMutationCommit normalized = normalize(commit, result[0]);
        ItemInstance removed = normalized.before().items().get(itemInstanceId);
        if (removed != null) notify(normalized, new InventoryChanged(playerId,
                removed.definitionId(), 1, operationId, false));
        return normalized;
    }

    private void notify(InventoryMutationCommit commit, InventoryChanged event) {
        if (!commit.applied() || event.quantity() <= 0) return;
        try {
            listener.accept(event);
        } catch (RuntimeException ignored) {
            // The inventory transaction is already authoritative and must not be
            // reported as failed merely because an observer is unavailable.
        }
    }

    private static InventoryMutationCommit normalize(
            InventoryMutationCommit commit, InventoryEngine.Mutation result) {
        if (!commit.applied()) {
            return commit;
        }
        return new InventoryMutationCommit(true, commit.before(), commit.after(),
                result.delivered(), result.overflowed());
    }

    private int stackSize(ContentId id) {
        var definition = content.get().materials().get(id);
        return definition == null ? 0 : definition.maxStackSize();
    }
}
