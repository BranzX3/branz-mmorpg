package com.branz.mmorpg.core.item;

import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.item.EquipmentService;
import com.branz.mmorpg.api.item.EquipmentSlot;
import com.branz.mmorpg.api.item.InventoryMutationCommit;
import com.branz.mmorpg.api.item.InventoryRepository;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.runtime.GameClock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

public final class DefaultEquipmentService implements EquipmentService {
    private final InventoryRepository repository;
    private final Supplier<ContentSnapshot> content;
    private final BiPredicate<UUID, java.time.Instant> inCombat;
    private final GameClock clock;
    private final EquipmentEngine engine = new EquipmentEngine();

    public DefaultEquipmentService(InventoryRepository repository,
                                   Supplier<ContentSnapshot> content,
                                   BiPredicate<UUID, java.time.Instant> inCombat,
                                   GameClock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.content = Objects.requireNonNull(content, "content");
        this.inCombat = Objects.requireNonNull(inCombat, "inCombat");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public InventoryMutationCommit equip(
            UUID playerId, UUID itemInstanceId, EquipmentSlot slot, OperationId operationId) {
        rejectCombat(playerId);
        return repository.mutate(playerId, operationId, 0, 0,
                before -> engine.equip(before, itemInstanceId, slot, content.get(), clock.now()));
    }

    @Override
    public InventoryMutationCommit unequip(
            UUID playerId, EquipmentSlot slot, OperationId operationId) {
        rejectCombat(playerId);
        return repository.mutate(playerId, operationId, 0, 0,
                before -> engine.unequip(before, slot, clock.now()));
    }

    private void rejectCombat(UUID playerId) {
        if (inCombat.test(playerId, clock.now())) {
            throw new IllegalStateException("equipment changes are blocked in combat");
        }
    }
}
