package com.branz.mmorpg.core.item;

import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.item.EquipmentService;
import com.branz.mmorpg.api.item.EquipmentSlot;
import com.branz.mmorpg.api.item.InventoryService;
import com.branz.mmorpg.api.item.ItemCategory;
import com.branz.mmorpg.api.item.ItemInstance;
import com.branz.mmorpg.api.item.LoadoutService;
import com.branz.mmorpg.api.item.StarterKitDelivery;
import com.branz.mmorpg.api.item.StarterKitDeliveryRepository;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.core.player.PlayerSessionService;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Drains the durable class-selection outbox into authoritative inventory once. */
public final class StarterKitDeliveryService {
    private final StarterKitDeliveryRepository repository;
    private final InventoryService inventory;
    private final EquipmentService equipment;
    private final LoadoutService loadouts;
    private final PlayerSessionService sessions;
    private final ContentService content;
    private final GameClock clock;

    public StarterKitDeliveryService(StarterKitDeliveryRepository repository,
                                     InventoryService inventory,
                                     EquipmentService equipment,
                                     LoadoutService loadouts,
                                     PlayerSessionService sessions,
                                     ContentService content, GameClock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.equipment = Objects.requireNonNull(equipment, "equipment");
        this.loadouts = Objects.requireNonNull(loadouts, "loadouts");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.content = Objects.requireNonNull(content, "content");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Result deliver(UUID playerId) {
        Optional<StarterKitDelivery> found = repository.find(playerId);
        if (found.isEmpty()) return new Result(Status.NONE, null, null, false);
        StarterKitDelivery delivery = found.get();
        var session = sessions.requirePlayable(playerId);
        ContentId classId = session.profile().classId()
                .orElseThrow(() -> new IllegalStateException("class is not selected"));
        CharacterClassDefinition characterClass = content.snapshot()
                .characterClasses().get(classId);
        var weapon = content.snapshot().weapons().get(delivery.weaponId());
        if (characterClass == null || weapon == null || weapon.tags().stream()
                .noneMatch(characterClass.allowedWeaponTags()::contains)) {
            throw new IllegalStateException("starter weapon is incompatible with selected class");
        }
        UUID itemId = deterministicItemId(delivery);
        if (delivery.state() == StarterKitDelivery.State.PENDING) {
            ItemInstance item = new ItemInstance(itemId, delivery.weaponId(), ItemCategory.WEAPON,
                    qualitySeed(delivery), Optional.of(playerId), 100,
                    "starter:" + delivery.planId(), 1, delivery.createdAt());
            inventory.grantUnique(playerId, item, OperationId.of(
                    "starter_item", delivery.weaponId().value(), playerId,
                    "r" + delivery.planRevision()));
            delivery.additionalItems().forEach((itemIdDefinition, quantity) ->
                    inventory.grantMaterial(playerId, itemIdDefinition, quantity,
                            OperationId.of("starter_extra", itemIdDefinition.value(), playerId,
                                    "r" + delivery.planRevision())));
            repository.markDelivered(playerId, clock.now());
        }
        var authoritative = inventory.inventory(playerId);
        boolean equipped = authoritative.items().containsKey(itemId);
        if (equipped) {
            equipment.equip(playerId, itemId, EquipmentSlot.MAIN_HAND,
                    OperationId.of("starter_equip", delivery.weaponId().value(), playerId,
                            "r" + delivery.planRevision()));
            LoadoutService.EquipResult loadout = loadouts.equip(playerId, delivery.weaponId());
            if (!loadout.equipped()) {
                throw new IllegalStateException("starter loadout rejected: " + loadout.rejection());
            }
        }
        return new Result(equipped ? Status.DELIVERED_AND_EQUIPPED : Status.PENDING_INVENTORY,
                delivery, itemId, equipped);
    }

    public Optional<StarterKitDelivery> pending(UUID playerId) {
        return repository.find(playerId).filter(
                delivery -> delivery.state() == StarterKitDelivery.State.PENDING);
    }

    private static UUID deterministicItemId(StarterKitDelivery delivery) {
        return UUID.nameUUIDFromBytes((delivery.selectionOperationId().value() + "|weapon")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static long qualitySeed(StarterKitDelivery delivery) {
        return deterministicItemId(delivery).getMostSignificantBits();
    }

    public enum Status { NONE, PENDING_INVENTORY, DELIVERED_AND_EQUIPPED }
    public record Result(Status status, StarterKitDelivery delivery,
                         UUID weaponItemId, boolean equipped) {}
}
