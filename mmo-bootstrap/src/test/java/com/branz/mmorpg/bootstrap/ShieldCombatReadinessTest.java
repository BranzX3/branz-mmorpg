package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.items.definition.GuardCombatProfile;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ShieldProfile;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ShieldCombatReadinessTest {
    @Test
    void freshAndWornShieldAboveZeroRemainGuardReady() {
        assertTrue(ShieldCombatReadiness.durabilityFailure(shield(), "{}").isEmpty());
        String worn = ItemDurabilityPayloadCodec.encode("{}", new ItemDurability(1, 3));
        assertTrue(ShieldCombatReadiness.durabilityFailure(shield(), worn).isEmpty());
    }

    @Test
    void brokenShieldDisablesGuard() {
        String broken = ItemDurabilityPayloadCodec.encode("{}", new ItemDurability(0, 3));

        assertEquals(
                Optional.of(ShieldCombatReadiness.BROKEN),
                ShieldCombatReadiness.durabilityFailure(shield(), broken));
    }

    @Test
    void malformedShieldDurabilityFailsClosed() {
        assertEquals(
                Optional.of(ShieldCombatReadiness.INVALID),
                ShieldCombatReadiness.durabilityFailure(
                        shield(),
                        "{\"durability\":{\"currentDurability\":1,\"maximumDurability\":4}}"));
    }

    private static ItemDefinition shield() {
        return new ItemDefinition(
                DefinitionId.of("equipment.test_shield"),
                DefinitionId.of("asset.equipment.test_shield"),
                ItemClass.UNIQUE_DURABLE,
                OptionalInt.of(3),
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(
                        new ShieldProfile(
                                new GuardCombatProfile(
                                        145, 0.9, 4, 130, 24, 24, 10, 22, 50))));
    }
}
