package com.branz.mmorpg.combat.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import org.junit.jupiter.api.Test;

class CombatStateMachineTest {
    @Test
    void vanillaInventoryRemainsLegalWhileEngagedButExclusiveUiDoesNot() {
        CombatStateSnapshot engaged =
                CombatStateMachine.hostileCommit(CombatStateSnapshot.initial());

        Result<CombatStateSnapshot, CombatStateErrorCode> inventory =
                CombatStateMachine.openUi(engaged, UiState.VANILLA_INVENTORY);
        assertTrue(inventory.isSuccess());
        assertEquals(
                UiState.VANILLA_INVENTORY,
                ((Result.Success<CombatStateSnapshot, CombatStateErrorCode>) inventory)
                        .value()
                        .ui());

        Result<CombatStateSnapshot, CombatStateErrorCode> scene =
                CombatStateMachine.openUi(engaged, UiState.SCENE);
        assertTrue(scene instanceof Result.Failure<?, ?>);
        assertEquals(
                CombatStateErrorCode.UI_BLOCKED_WHILE_ENGAGED,
                ((Result.Failure<CombatStateSnapshot, CombatStateErrorCode>) scene).error());
    }

    @Test
    void hostileCommitAndHardControlCloseDangerSensitiveUi() {
        CombatStateSnapshot scene =
                success(CombatStateMachine.openUi(CombatStateSnapshot.initial(), UiState.SCENE));
        assertEquals(UiState.NONE, CombatStateMachine.hostileCommit(scene).ui());

        CombatStateSnapshot dialogue =
                success(CombatStateMachine.openUi(CombatStateSnapshot.initial(), UiState.DIALOGUE));
        assertEquals(
                UiState.NONE,
                CombatStateMachine.forceAction(dialogue, ActionState.KNOCKED_DOWN).ui());
    }

    @Test
    void loginResetNeverResumesTransientCombatState() {
        CombatStateSnapshot active =
                CombatStateMachine.hostileCommit(
                        success(
                                CombatStateMachine.startAction(
                                        CombatStateSnapshot.initial(), ActionState.WINDUP)));

        assertEquals(CombatStateSnapshot.initial(), CombatStateMachine.resetTransient());
        assertEquals(EngagementState.ENGAGED, active.engagement());
        assertEquals(ActionState.WINDUP, active.action());
    }

    private static CombatStateSnapshot success(
            Result<CombatStateSnapshot, CombatStateErrorCode> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<CombatStateSnapshot, CombatStateErrorCode>) result).value();
    }
}
