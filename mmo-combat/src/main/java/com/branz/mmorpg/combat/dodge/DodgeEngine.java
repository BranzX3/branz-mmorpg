package com.branz.mmorpg.combat.dodge;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.input.DirectionSnapshot;
import java.util.Objects;
import java.util.Optional;

/** Starts and queries server-authoritative dodge windows without wall-clock or client hit trust. */
public final class DodgeEngine {
    public Result<DodgeRuntime, DodgeErrorCode> start(
            Optional<DodgeRuntime> current,
            DodgeProfile profile,
            DirectionSnapshot direction,
            int availableStamina,
            long tick) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(direction, "direction");
        if (tick < 0 || availableStamina < 0) {
            throw new IllegalArgumentException("tick and availableStamina must not be negative");
        }
        if (direction == DirectionSnapshot.NEUTRAL) {
            return Result.failure(
                    DodgeErrorCode.NEUTRAL_DIRECTION, "Dodge requires a directional snapshot.");
        }
        if (current.isPresent() && current.orElseThrow().phaseAt(tick) != DodgePhase.COMPLETE) {
            return Result.failure(DodgeErrorCode.ALREADY_DODGING, "Dodge recovery is active.");
        }
        if (availableStamina < profile.staminaCost()) {
            return Result.failure(DodgeErrorCode.NO_STAMINA, "Dodge stamina cost cannot be paid.");
        }
        return Result.success(new DodgeRuntime(profile, direction, tick));
    }

    public boolean avoids(DodgeRuntime runtime, long hitTick, boolean dodgeable) {
        Objects.requireNonNull(runtime, "runtime");
        return dodgeable && runtime.phaseAt(hitTick) == DodgePhase.INVULNERABLE;
    }
}
