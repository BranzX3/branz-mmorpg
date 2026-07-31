package com.branz.mmorpg.combat.state;

import com.branz.mmorpg.api.result.Result;
import java.util.Objects;

/** Immutable orthogonal combat-state transitions. */
public final class CombatStateMachine {
    private CombatStateMachine() {}

    public static Result<CombatStateSnapshot, CombatStateErrorCode> openUi(
            CombatStateSnapshot current, UiState requested) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(requested, "requested");
        if (current.engagement() == EngagementState.ENGAGED && !requested.allowedWhileEngaged()) {
            return Result.failure(
                    CombatStateErrorCode.UI_BLOCKED_WHILE_ENGAGED,
                    requested + " cannot open while combat is ENGAGED.");
        }
        return Result.success(
                new CombatStateSnapshot(
                        current.engagement(),
                        current.action(),
                        requested,
                        current.encounter(),
                        current.revision() + 1));
    }

    public static CombatStateSnapshot hostileCommit(CombatStateSnapshot current) {
        Objects.requireNonNull(current, "current");
        UiState ui = current.ui().closesOnDanger() ? UiState.NONE : current.ui();
        return new CombatStateSnapshot(
                EngagementState.ENGAGED,
                current.action(),
                ui,
                current.encounter(),
                current.revision() + 1);
    }

    public static Result<CombatStateSnapshot, CombatStateErrorCode> startAction(
            CombatStateSnapshot current, ActionState requested) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(requested, "requested");
        if (current.action() != ActionState.IDLE) {
            return Result.failure(
                    CombatStateErrorCode.ACTION_LOCKED,
                    "Primary action is already " + current.action() + ".");
        }
        if (requested == ActionState.IDLE || requested == ActionState.DEAD) {
            return Result.failure(
                    CombatStateErrorCode.INVALID_TRANSITION,
                    "Action start requires a live non-idle action state.");
        }
        return Result.success(
                new CombatStateSnapshot(
                        current.engagement(),
                        requested,
                        current.ui(),
                        current.encounter(),
                        current.revision() + 1));
    }

    public static CombatStateSnapshot forceAction(CombatStateSnapshot current, ActionState forced) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(forced, "forced");
        UiState ui =
                forced.hardControl() && current.ui().closesOnDanger() ? UiState.NONE : current.ui();
        return new CombatStateSnapshot(
                current.engagement(), forced, ui, current.encounter(), current.revision() + 1);
    }

    public static CombatStateSnapshot resetTransient() {
        return CombatStateSnapshot.initial();
    }
}
