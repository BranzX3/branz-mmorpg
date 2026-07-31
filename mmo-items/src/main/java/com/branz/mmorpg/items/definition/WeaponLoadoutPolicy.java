package com.branz.mmorpg.items.definition;

import com.branz.mmorpg.api.result.Result;
import java.util.Objects;
import java.util.Optional;

/** One shared compatibility authority for Scene commits and live combat readiness. */
public final class WeaponLoadoutPolicy {
    private WeaponLoadoutPolicy() {}

    public static Result<WeaponLoadoutResolution, WeaponLoadoutErrorCode> resolve(
            ItemDefinition mainHand, Optional<ItemDefinition> offHand) {
        Objects.requireNonNull(mainHand, "mainHand");
        Objects.requireNonNull(offHand, "offHand");
        WeaponCombatProfile weapon = mainHand.weaponProfile().orElse(null);
        if (weapon == null) {
            return Result.failure(
                    WeaponLoadoutErrorCode.MAIN_HAND_NOT_WEAPON,
                    "Main hand does not contain an authored combat weapon.");
        }
        boolean shield = offHand.flatMap(ItemDefinition::shieldProfile).isPresent();
        if (weapon.offhandPolicy() == OffhandPolicy.EMPTY && offHand.isPresent()) {
            return Result.failure(
                    WeaponLoadoutErrorCode.OFF_HAND_MUST_BE_EMPTY,
                    "This weapon requires an empty off hand.");
        }
        if (weapon.offhandPolicy() == OffhandPolicy.SHIELD && !shield) {
            return Result.failure(
                    WeaponLoadoutErrorCode.SHIELD_REQUIRED,
                    "Sword & Shield requires an authored Shield in the off hand.");
        }
        if (weapon.offhandPolicy() != OffhandPolicy.SHIELD && shield) {
            return Result.failure(
                    WeaponLoadoutErrorCode.SHIELD_NOT_COMPATIBLE,
                    "The selected weapon is not compatible with a Shield.");
        }
        Optional<GuardCombatProfile> guard =
                weapon.offhandPolicy() == OffhandPolicy.SHIELD
                        ? offHand.flatMap(ItemDefinition::shieldProfile)
                                .map(ShieldProfile::guardProfile)
                        : weapon.guardProfile();
        return Result.success(new WeaponLoadoutResolution(weapon, guard));
    }
}
