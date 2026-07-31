package com.branz.mmorpg.combat.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.state.ActionState;
import com.branz.mmorpg.combat.state.WeaponState;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WeaponTransitionMachineTest {
    private final WeaponTransitionMachine machine = new WeaponTransitionMachine(4, 3);

    @Test
    void scrollSpamKeepsOnlyLatestTargetAndCannotBypassSheatheAndDraw() {
        WeaponTransitionSnapshot state =
                selected(WeaponTransitionSnapshot.initial(), SelectedHotbarSlot.combatWeapon(0));
        state = ticks(state, 4);
        assertReady(state, 0);

        state = selected(state, SelectedHotbarSlot.combatWeapon(1));
        state = selected(state, SelectedHotbarSlot.combatWeapon(2));
        state = selected(state, SelectedHotbarSlot.combatWeapon(3));
        assertEquals(WeaponState.SHEATHING, state.state());
        assertEquals(3, state.desiredWeaponSlot().orElseThrow());

        state = ticks(state, 3);
        assertEquals(WeaponState.DRAWING, state.state());
        assertEquals(4, state.ticksRemaining());
        assertTrue(state.activeWeaponSlot().isEmpty());

        state = ticks(state, 3);
        assertEquals(WeaponState.DRAWING, state.state());
        state = machine.tick(state);
        assertReady(state, 3);
    }

    @Test
    void changingTargetDuringDrawRestartsFullDrawAndHardControlCancelsIt() {
        WeaponTransitionSnapshot state =
                selected(WeaponTransitionSnapshot.initial(), SelectedHotbarSlot.combatWeapon(0));
        state = machine.tick(state);
        state = machine.tick(state);
        assertEquals(2, state.ticksRemaining());

        state = selected(state, SelectedHotbarSlot.combatWeapon(7));
        assertEquals(4, state.ticksRemaining());
        assertEquals(7, state.desiredWeaponSlot().orElseThrow());

        state = machine.interrupt(state, ActionState.KNOCKED_DOWN);
        assertEquals(WeaponState.SHEATHED, state.state());
        assertTrue(state.activeWeaponSlot().isEmpty());
        assertTrue(state.desiredWeaponSlot().isEmpty());
        assertEquals(0, state.ticksRemaining());
    }

    @Test
    void randomizedSlotAndTickSequenceNeverCreatesIllegalSnapshot() {
        Random random = new Random(0xB12A2L);
        WeaponTransitionSnapshot state = WeaponTransitionSnapshot.initial();
        for (int index = 0; index < 20_000; index++) {
            if (random.nextBoolean()) {
                int slot = random.nextInt(9);
                SelectedHotbarSlot selected =
                        slot == 8
                                ? new SelectedHotbarSlot(8, SelectedSlotKind.CHRONICLE)
                                : new SelectedHotbarSlot(
                                        slot,
                                        random.nextBoolean()
                                                ? SelectedSlotKind.COMBAT_WEAPON
                                                : SelectedSlotKind.TOOL_OR_BLOCK);
                state = selected(state, selected);
            } else {
                state = machine.tick(state);
            }
            if (state.state() == WeaponState.READY) {
                assertTrue(state.activeWeaponSlot().isPresent());
            }
            if (state.activeWeaponSlot().isPresent()) {
                assertTrue(state.activeWeaponSlot().getAsInt() < 8);
            }
        }
    }

    private WeaponTransitionSnapshot selected(
            WeaponTransitionSnapshot state, SelectedHotbarSlot slot) {
        Result<WeaponTransitionSnapshot, WeaponTransitionErrorCode> result =
                machine.select(state, slot);
        assertTrue(result.isSuccess());
        return ((Result.Success<WeaponTransitionSnapshot, WeaponTransitionErrorCode>) result)
                .value();
    }

    private WeaponTransitionSnapshot ticks(WeaponTransitionSnapshot state, int count) {
        WeaponTransitionSnapshot current = state;
        for (int index = 0; index < count; index++) {
            current = machine.tick(current);
        }
        return current;
    }

    private static void assertReady(WeaponTransitionSnapshot state, int slot) {
        assertEquals(WeaponState.READY, state.state());
        assertEquals(slot, state.activeWeaponSlot().orElseThrow());
    }
}
