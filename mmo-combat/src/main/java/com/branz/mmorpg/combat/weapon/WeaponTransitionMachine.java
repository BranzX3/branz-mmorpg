package com.branz.mmorpg.combat.weapon;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.state.ActionState;
import com.branz.mmorpg.combat.state.WeaponState;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Deterministic draw/sheathe state. Slot spam replaces only the desired destination and never skips
 * a required transition.
 */
public final class WeaponTransitionMachine {
    private final int drawTicks;
    private final int sheatheTicks;

    public WeaponTransitionMachine(int drawTicks, int sheatheTicks) {
        if (drawTicks < 1 || sheatheTicks < 1) {
            throw new IllegalArgumentException("draw and sheathe durations must be positive");
        }
        this.drawTicks = drawTicks;
        this.sheatheTicks = sheatheTicks;
    }

    public Result<WeaponTransitionSnapshot, WeaponTransitionErrorCode> select(
            WeaponTransitionSnapshot current, SelectedHotbarSlot selected) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(selected, "selected");
        if (current.state() == WeaponState.DISABLED) {
            return Result.failure(
                    WeaponTransitionErrorCode.WEAPON_DISABLED, "Weapon transitions are disabled.");
        }
        return selected.kind() == SelectedSlotKind.COMBAT_WEAPON
                ? Result.success(selectWeapon(current, selected.slot()))
                : Result.success(selectNonWeapon(current));
    }

    public WeaponTransitionSnapshot tick(WeaponTransitionSnapshot current) {
        Objects.requireNonNull(current, "current");
        if (current.state() != WeaponState.DRAWING && current.state() != WeaponState.SHEATHING) {
            return current;
        }
        if (current.ticksRemaining() > 1) {
            return new WeaponTransitionSnapshot(
                    current.state(),
                    current.activeWeaponSlot(),
                    current.desiredWeaponSlot(),
                    current.ticksRemaining() - 1,
                    current.revision() + 1);
        }
        if (current.state() == WeaponState.SHEATHING) {
            if (current.desiredWeaponSlot().isPresent()) {
                return new WeaponTransitionSnapshot(
                        WeaponState.DRAWING,
                        OptionalInt.empty(),
                        current.desiredWeaponSlot(),
                        drawTicks,
                        current.revision() + 1);
            }
            return new WeaponTransitionSnapshot(
                    WeaponState.SHEATHED,
                    OptionalInt.empty(),
                    OptionalInt.empty(),
                    0,
                    current.revision() + 1);
        }
        return new WeaponTransitionSnapshot(
                WeaponState.READY,
                current.desiredWeaponSlot(),
                current.desiredWeaponSlot(),
                0,
                current.revision() + 1);
    }

    public WeaponTransitionSnapshot interrupt(
            WeaponTransitionSnapshot current, ActionState forcedAction) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(forcedAction, "forcedAction");
        if (current.state() != WeaponState.DRAWING || !cancelsDraw(forcedAction)) {
            return current;
        }
        return new WeaponTransitionSnapshot(
                WeaponState.SHEATHED,
                OptionalInt.empty(),
                OptionalInt.empty(),
                0,
                current.revision() + 1);
    }

    public WeaponTransitionSnapshot disable(WeaponTransitionSnapshot current) {
        Objects.requireNonNull(current, "current");
        return new WeaponTransitionSnapshot(
                WeaponState.DISABLED,
                OptionalInt.empty(),
                OptionalInt.empty(),
                0,
                current.revision() + 1);
    }

    public WeaponTransitionSnapshot resetTransient() {
        return WeaponTransitionSnapshot.initial();
    }

    private WeaponTransitionSnapshot selectWeapon(
            WeaponTransitionSnapshot current, int desiredSlot) {
        OptionalInt desired = OptionalInt.of(desiredSlot);
        return switch (current.state()) {
            case SHEATHED ->
                    new WeaponTransitionSnapshot(
                            WeaponState.DRAWING,
                            OptionalInt.empty(),
                            desired,
                            drawTicks,
                            current.revision() + 1);
            case DRAWING ->
                    current.desiredWeaponSlot().orElseThrow() == desiredSlot
                            ? current
                            : new WeaponTransitionSnapshot(
                                    WeaponState.DRAWING,
                                    OptionalInt.empty(),
                                    desired,
                                    drawTicks,
                                    current.revision() + 1);
            case READY ->
                    current.activeWeaponSlot().orElseThrow() == desiredSlot
                            ? current
                            : new WeaponTransitionSnapshot(
                                    WeaponState.SHEATHING,
                                    current.activeWeaponSlot(),
                                    desired,
                                    sheatheTicks,
                                    current.revision() + 1);
            case SHEATHING ->
                    new WeaponTransitionSnapshot(
                            WeaponState.SHEATHING,
                            current.activeWeaponSlot(),
                            desired,
                            current.ticksRemaining(),
                            current.revision() + 1);
            case DISABLED -> throw new IllegalStateException("disabled state was rejected");
        };
    }

    private WeaponTransitionSnapshot selectNonWeapon(WeaponTransitionSnapshot current) {
        return switch (current.state()) {
            case SHEATHED -> current;
            case DRAWING ->
                    new WeaponTransitionSnapshot(
                            WeaponState.SHEATHED,
                            OptionalInt.empty(),
                            OptionalInt.empty(),
                            0,
                            current.revision() + 1);
            case READY, SHEATHING ->
                    new WeaponTransitionSnapshot(
                            WeaponState.SHEATHING,
                            current.activeWeaponSlot(),
                            OptionalInt.empty(),
                            current.state() == WeaponState.SHEATHING
                                    ? current.ticksRemaining()
                                    : sheatheTicks,
                            current.revision() + 1);
            case DISABLED -> throw new IllegalStateException("disabled state was rejected");
        };
    }

    private static boolean cancelsDraw(ActionState action) {
        return action == ActionState.STAGGERED
                || action == ActionState.KNOCKED_DOWN
                || action == ActionState.GRABBED
                || action == ActionState.DEAD;
    }
}
