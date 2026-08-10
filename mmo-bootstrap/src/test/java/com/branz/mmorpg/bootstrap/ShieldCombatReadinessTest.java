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
    void freshShieldIsGuardReadyAtDefinitionMaximum() {
        assertTrue(ShieldCombatReadiness.durabilityFailure(shield(3), "{}").isEmpty());
    }

    @Test
    void wornShieldAboveZeroRemainsGuardReady() {
        String payload = ItemDurabilityPayloadCodec.encode("{}", new ItemDurability(1, 3));

        assertTrue(ShieldCombatReadiness.durabilityFailure(shield(3), payload).isEmpty());
    }

    @Test
    void brokenShieldFailsClosed() {
        String payload = ItemDurabilityPayloadCodec.encode("{}", new ItemDurability(0, 3));

        assertEquals(
                Optional.of(ShieldCombatReadiness.BROKEN),
                ShieldCombatReadiness.durabilityFailure(shield(3), payload));
    }

    @Test
    void malformedOrMismatchedDurabilityFailsClosed() {
        assertEquals(
                Optional.of(ShieldCombatReadiness.INVALID),
                ShieldCombatReadiness.durabilityFailure(
                        shield(3),
                        "{\"durability\":{\"currentDurability\":1,\"maximumDurability\":4}}"));
    }

    private static ItemDefinition shield(int maximumDurability) {
        return new ItemDefinition(
                DefinitionId.of("shield.test"),
                DefinitionId.of("asset.shield.test"),
                ItemClass.UNIQUE_DURABLE,
                OptionalInt.of(maximumDurability),
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(
                        new ShieldProfile(
                                new GuardCombatProfile(
                                        120,
                                        0.8,
                                        3,
                                        100,
                                        20,
                                        10,
                                        5,
                                        30,
                                        25))));
    }
}
