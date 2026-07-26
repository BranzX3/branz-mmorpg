package com.branz.mmorpg.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.character.CharacterClassId;
import com.branz.mmorpg.api.character.CharacterClassRole;
import com.branz.mmorpg.api.character.StarterGrantPlan;
import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentReloadResult;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.item.EquipmentService;
import com.branz.mmorpg.api.item.EquipmentSlot;
import com.branz.mmorpg.api.item.InventoryMutationCommit;
import com.branz.mmorpg.api.item.InventoryService;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.item.ItemInstance;
import com.branz.mmorpg.api.item.LoadoutService;
import com.branz.mmorpg.api.item.StarterKitDelivery;
import com.branz.mmorpg.api.item.StarterKitDeliveryRepository;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.player.DuplicateLoginPolicy;
import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.api.skill.ResourceType;
import com.branz.mmorpg.api.skill.SkillDefinition;
import com.branz.mmorpg.core.fixture.DirectScheduler;
import com.branz.mmorpg.core.fixture.FakePlayerProfileRepository;
import com.branz.mmorpg.core.fixture.FixedGameClock;
import com.branz.mmorpg.core.player.PlayerSessionService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StarterKitDeliveryServiceTest {
    private static final UUID PLAYER = UUID.fromString("34bf73b0-389e-4b5b-9ba5-9c73ce807f15");
    private static final ContentId WEAPON = ContentId.parse("branz:broadsword");
    private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");

    @Test
    void retryGrantsOneAuthoritativeStarterAndKeepsItEquipped() throws Exception {
        FixedGameClock clock = FixedGameClock.at(NOW.toString());
        FakePlayerProfileRepository profiles = new FakePlayerProfileRepository();
        profiles.preload(PlayerProfile.create(PLAYER, "Starter", NOW)
                .withPermanentClass(CharacterClassId.WARRIOR.value()));
        PlayerSessionService sessions = new PlayerSessionService(profiles,
                new DirectScheduler(), clock, () -> 1L, DuplicateLoginPolicy.CLOSE_PREVIOUS);
        sessions.start();
        try {
            sessions.login(PLAYER, "Starter").get();
            DeliveryRepository deliveries = new DeliveryRepository(new StarterKitDelivery(
                    PLAYER, OperationId.of("class", "selection", PLAYER, "starter-test"),
                    ContentId.parse("branz:warrior_starter"), 1, WEAPON, Map.of(),
                    StarterKitDelivery.State.PENDING, NOW, null));
            RecordingInventory inventory = new RecordingInventory();
            RecordingEquipment equipment = new RecordingEquipment(inventory);
            RecordingLoadout loadout = new RecordingLoadout();
            StarterKitDeliveryService service = new StarterKitDeliveryService(
                    deliveries, inventory, equipment, loadout, sessions,
                    content(), clock);

            var first = service.deliver(PLAYER);
            var retry = service.deliver(PLAYER);

            assertEquals(StarterKitDeliveryService.Status.DELIVERED_AND_EQUIPPED,
                    first.status());
            assertEquals(first.weaponItemId(), retry.weaponItemId());
            assertEquals(1, inventory.grants);
            assertEquals(1, inventory.snapshot.items().size());
            assertEquals(1, deliveries.completions);
            assertEquals(first.weaponItemId(),
                    inventory.snapshot.equipped().get(EquipmentSlot.MAIN_HAND));
            assertEquals(WEAPON, loadout.current(PLAYER).orElseThrow().id());
        } finally {
            sessions.stop();
        }
    }

    private static ContentService content() {
        WeaponDefinition weapon = new WeaponDefinition(WEAPON, "Broadsword",
                ContentId.parse("branz:sword_mastery"),
                ContentId.parse("branz:broadsword_mastery"),
                ContentId.parse("branz:basic_strike"), List.of(), Set.of("sword"), false);
        CharacterClassDefinition warrior = new CharacterClassDefinition(
                CharacterClassId.WARRIOR.value(), "Warrior", 1,
                Set.of(CharacterClassRole.DAMAGE), Map.of("max_health", 100.0),
                ResourceType.STAMINA, Set.of(), Set.of("sword"), Set.of("heavy"),
                List.of(ContentId.parse("branz:heavy_slash")),
                ContentId.parse("branz:warbreaker"), ContentId.parse("branz:warrior_root"),
                new StarterGrantPlan(ContentId.parse("branz:warrior_starter"), 1,
                        WEAPON, List.of(ContentId.parse("branz:basic_strike")), Map.of()),
                Set.of("physical"));
        ContentSnapshot snapshot = new ContentSnapshot() {
            @Override public long revision() { return 1; }
            @Override public Instant loadedAt() { return NOW; }
            @Override public Collection<ContentDefinition> definitions() {
                return List.of(warrior, weapon);
            }
            @Override public Optional<ContentDefinition> find(ContentId id) {
                return definitions().stream().filter(value -> value.id().equals(id)).findFirst();
            }
            @Override public <T extends ContentDefinition> Optional<T> find(ContentId id,
                                                                            Class<T> type) {
                return find(id).filter(type::isInstance).map(type::cast);
            }
            @Override public Map<ContentId, MaterialDefinition> materials() { return Map.of(); }
            @Override public Map<ContentId, SkillDefinition> skills() { return Map.of(); }
            @Override public Map<ContentId, LifeSkillDefinition> lifeSkills() { return Map.of(); }
            @Override public Map<ContentId, LifeSkillNodeDefinition> lifeSkillNodes() {
                return Map.of();
            }
            @Override public Map<ContentId, MasteryDefinition> masteries() { return Map.of(); }
            @Override public Map<ContentId, WeaponDefinition> weapons() {
                return Map.of(weapon.id(), weapon);
            }
            @Override public Map<ContentId, CharacterClassDefinition> characterClasses() {
                return Map.of(warrior.id(), warrior);
            }
        };
        return new ContentService() {
            @Override public ContentSnapshot snapshot() { return snapshot; }
            @Override public ContentReloadResult reload(Path root) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class DeliveryRepository implements StarterKitDeliveryRepository {
        private StarterKitDelivery delivery;
        private int completions;
        private DeliveryRepository(StarterKitDelivery delivery) { this.delivery = delivery; }
        @Override public Optional<StarterKitDelivery> find(UUID playerId) {
            return Optional.of(delivery);
        }
        @Override public boolean markDelivered(UUID playerId, Instant deliveredAt) {
            if (delivery.state() == StarterKitDelivery.State.DELIVERED) return false;
            delivery = new StarterKitDelivery(delivery.playerId(),
                    delivery.selectionOperationId(), delivery.planId(), delivery.planRevision(),
                    delivery.weaponId(), delivery.additionalItems(),
                    StarterKitDelivery.State.DELIVERED, delivery.createdAt(), deliveredAt);
            completions++;
            return true;
        }
    }

    private static final class RecordingInventory implements InventoryService {
        private InventorySnapshot snapshot = InventorySnapshot.empty(PLAYER, 36, NOW);
        private int grants;
        @Override public InventorySnapshot inventory(UUID playerId) { return snapshot; }
        @Override public InventoryMutationCommit grantUnique(UUID playerId, ItemInstance item,
                                                              OperationId operationId) {
            InventorySnapshot before = snapshot;
            Map<UUID, ItemInstance> items = new LinkedHashMap<>(before.items());
            if (items.putIfAbsent(item.instanceId(), item) == null) grants++;
            snapshot = new InventorySnapshot(playerId, before.slotCapacity(), before.materials(),
                    items, before.equipped(), before.pendingMaterials(), before.pendingItems(), NOW);
            return new InventoryMutationCommit(true, before, snapshot, 1, 0);
        }
        @Override public InventoryMutationCommit grantMaterial(UUID p, ContentId i, long q,
                                                                OperationId o) {
            throw new UnsupportedOperationException();
        }
        @Override public InventoryMutationCommit claimMaterial(UUID p, ContentId i, long q,
                                                                OperationId o) {
            throw new UnsupportedOperationException();
        }
        @Override public InventoryMutationCommit claimUnique(UUID p, UUID i, OperationId o) {
            throw new UnsupportedOperationException();
        }
        @Override public InventoryMutationCommit revokeMaterial(UUID p, ContentId i, long q,
                                                                 OperationId o) {
            throw new UnsupportedOperationException();
        }
        @Override public InventoryMutationCommit revokeUnique(UUID p, UUID i, OperationId o) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingEquipment implements EquipmentService {
        private final RecordingInventory inventory;
        private RecordingEquipment(RecordingInventory inventory) { this.inventory = inventory; }
        @Override public InventoryMutationCommit equip(UUID playerId, UUID itemId,
                                                        EquipmentSlot slot, OperationId operationId) {
            InventorySnapshot before = inventory.snapshot;
            inventory.snapshot = new InventorySnapshot(playerId, before.slotCapacity(),
                    before.materials(), before.items(), Map.of(slot, itemId),
                    before.pendingMaterials(), before.pendingItems(), NOW);
            return new InventoryMutationCommit(true, before, inventory.snapshot, 0, 0);
        }
        @Override public InventoryMutationCommit unequip(UUID playerId, EquipmentSlot slot,
                                                          OperationId operationId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingLoadout implements LoadoutService {
        private WeaponDefinition current;
        @Override public Optional<WeaponDefinition> current(UUID playerId) {
            return Optional.ofNullable(current);
        }
        @Override public EquipResult equip(UUID playerId, ContentId weaponId) {
            current = content().snapshot().weapons().get(weaponId);
            assertTrue(current != null);
            return new EquipResult(true, "", current);
        }
    }
}
