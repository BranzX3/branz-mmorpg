package com.branz.mmorpg.core.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.item.EquipmentSlot;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.item.ItemCategory;
import com.branz.mmorpg.api.item.ItemInstance;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.skill.SkillDefinition;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EquipmentEngineTest {
    private static final UUID PLAYER =
            UUID.fromString("9a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f8");
    private static final Instant NOW = Instant.parse("2026-07-26T00:00:00Z");
    private final EquipmentEngine engine = new EquipmentEngine();

    @Test
    void twoHandedWeaponAtomicallyReservesOffHand() {
        ItemInstance bow = item("8a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f1",
                ItemCategory.WEAPON, "branz:longbow");
        ItemInstance accessory = item("7a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f2",
                ItemCategory.ACCESSORY, "branz:test_accessory");
        InventorySnapshot before = new InventorySnapshot(PLAYER, 10, Map.of(),
                Map.of(bow.instanceId(), bow, accessory.instanceId(), accessory),
                Map.of(EquipmentSlot.OFF_HAND, accessory.instanceId()),
                Map.of(), Map.of(), NOW);

        InventorySnapshot after = engine.equip(
                before, bow.instanceId(), EquipmentSlot.MAIN_HAND, snapshot(), NOW);

        assertFalse(after.equipped().containsKey(EquipmentSlot.OFF_HAND));
        assertThrows(IllegalArgumentException.class, () -> engine.equip(
                after, accessory.instanceId(), EquipmentSlot.OFF_HAND, snapshot(), NOW));
    }

    private static ItemInstance item(String id, ItemCategory category, String definition) {
        return new ItemInstance(UUID.fromString(id), ContentId.parse(definition), category,
                1, Optional.of(PLAYER), 100, "test", 1, NOW);
    }

    private static ContentSnapshot snapshot() {
        WeaponDefinition bow = new WeaponDefinition(ContentId.parse("branz:longbow"), "Longbow",
                ContentId.parse("branz:bow_mastery"),
                ContentId.parse("branz:longbow_mastery"),
                ContentId.parse("branz:basic_strike"), List.of(), Set.of(), true);
        return new ContentSnapshot() {
            @Override public long revision() { return 1; }
            @Override public Instant loadedAt() { return NOW; }
            @Override public Collection<ContentDefinition> definitions() { return List.of(bow); }
            @Override public Optional<ContentDefinition> find(ContentId id) {
                return id.equals(bow.id()) ? Optional.of(bow) : Optional.empty();
            }
            @Override public <T extends ContentDefinition> Optional<T> find(
                    ContentId id, Class<T> type) {
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
                return Map.of(bow.id(), bow);
            }
        };
    }
}
