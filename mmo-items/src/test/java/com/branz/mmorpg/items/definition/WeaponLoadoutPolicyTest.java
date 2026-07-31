package com.branz.mmorpg.items.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class WeaponLoadoutPolicyTest {
    private static final GuardCombatProfile GUARD =
            new GuardCombatProfile(145, 0.9, 4, 130, 24, 24, 10, 22, 50);

    @Test
    void resolvesShieldGuardOnlyForCompatibleSwordAndShieldLoadout() {
        ItemDefinition sword =
                weapon(
                        "weapon.test.sword",
                        new WeaponCombatProfile(
                                "SWORD_SHIELD",
                                100,
                                Optional.empty(),
                                Optional.empty(),
                                OffhandPolicy.SHIELD,
                                Optional.empty()));
        ItemDefinition shield = shield();

        Result<WeaponLoadoutResolution, WeaponLoadoutErrorCode> resolved =
                WeaponLoadoutPolicy.resolve(sword, Optional.of(shield));
        Result<WeaponLoadoutResolution, WeaponLoadoutErrorCode> missing =
                WeaponLoadoutPolicy.resolve(sword, Optional.empty());

        assertTrue(resolved.isSuccess());
        assertEquals(
                GUARD,
                ((Result.Success<WeaponLoadoutResolution, WeaponLoadoutErrorCode>) resolved)
                        .value()
                        .guardProfile()
                        .orElseThrow());
        assertEquals(
                WeaponLoadoutErrorCode.SHIELD_REQUIRED,
                ((Result.Failure<WeaponLoadoutResolution, WeaponLoadoutErrorCode>) missing)
                        .error());
    }

    @Test
    void rejectsShieldForEmptyOffhandGreatsword() {
        ItemDefinition greatsword =
                weapon(
                        "weapon.test.greatsword",
                        new WeaponCombatProfile(
                                "GREATSWORD",
                                135,
                                Optional.empty(),
                                Optional.empty(),
                                OffhandPolicy.EMPTY,
                                Optional.of(GUARD)));

        Result<WeaponLoadoutResolution, WeaponLoadoutErrorCode> invalid =
                WeaponLoadoutPolicy.resolve(greatsword, Optional.of(shield()));

        assertEquals(
                WeaponLoadoutErrorCode.OFF_HAND_MUST_BE_EMPTY,
                ((Result.Failure<WeaponLoadoutResolution, WeaponLoadoutErrorCode>) invalid)
                        .error());
    }

    private static ItemDefinition weapon(String id, WeaponCombatProfile profile) {
        return new ItemDefinition(
                DefinitionId.of(id),
                DefinitionId.of(id),
                ItemClass.UNIQUE_DURABLE,
                OptionalInt.of(100),
                false,
                Optional.of(profile));
    }

    private static ItemDefinition shield() {
        return new ItemDefinition(
                DefinitionId.of("equipment.test.shield"),
                DefinitionId.of("equipment.test.shield"),
                ItemClass.UNIQUE_DURABLE,
                OptionalInt.of(100),
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new ShieldProfile(GUARD)));
    }
}
