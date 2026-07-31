package com.branz.mmorpg.combat.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.state.ActionState;
import com.branz.mmorpg.combat.state.EngagementState;
import com.branz.mmorpg.combat.state.UiState;
import com.branz.mmorpg.combat.state.WeaponState;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatInputPolicyTest {
    private final CombatInputPolicy policy = new CombatInputPolicy();

    @Test
    void rmbWorldInteractionYieldsToCombatOnlyWhenEngaged() {
        assertEquals(
                SemanticInput.WORLD_INTERACTION,
                resolved(
                        ClientAction.USE,
                        context(
                                EngagementState.EXPLORATION,
                                WeaponState.READY,
                                UiState.NONE,
                                true,
                                DirectionSnapshot.NEUTRAL)));
        assertEquals(
                SemanticInput.SECONDARY,
                resolved(
                        ClientAction.USE,
                        context(
                                EngagementState.ENGAGED,
                                WeaponState.READY,
                                UiState.NONE,
                                true,
                                DirectionSnapshot.NEUTRAL)));
    }

    @Test
    void readyOwnsDropAndSwapWhileExplorationFallsBackToVanilla() {
        InputPolicyContext ready =
                context(
                        EngagementState.EXPLORATION,
                        WeaponState.READY,
                        UiState.NONE,
                        false,
                        DirectionSnapshot.NEUTRAL);
        InputPolicyContext sheathed =
                context(
                        EngagementState.EXPLORATION,
                        WeaponState.SHEATHED,
                        UiState.NONE,
                        false,
                        DirectionSnapshot.NEUTRAL);

        assertEquals(SemanticInput.SIGNATURE, resolved(ClientAction.SWAP_HAND, ready));
        assertEquals(SemanticInput.AUXILIARY, resolved(ClientAction.DROP, ready));
        assertEquals(SemanticInput.VANILLA_FALLBACK, resolved(ClientAction.SWAP_HAND, sheathed));
        assertEquals(SemanticInput.VANILLA_FALLBACK, resolved(ClientAction.DROP, sheathed));
    }

    @Test
    void drawingBuffersOneOpenerButStillAllowsDodge() {
        InputPolicyContext drawing =
                context(
                        EngagementState.EXPLORATION,
                        WeaponState.DRAWING,
                        UiState.NONE,
                        false,
                        DirectionSnapshot.FORWARD);
        SemanticInput intent = resolved(ClientAction.ATTACK, drawing);
        InputRoutingContext routing = policy.routingContext(drawing, false);
        InputRouter router = new InputRouter();

        InputRouteOutcome buffered =
                success(
                        router.routeFrame(
                                List.of(
                                        new CombatInputRequest(
                                                1,
                                                0,
                                                intent,
                                                DirectionSnapshot.FORWARD,
                                                "PRIMARY")),
                                routing));
        assertEquals(InputRouteDecision.BUFFERED, buffered.decision());
        assertTrue(routing.legalNow().contains(SemanticInput.DODGE));
    }

    @Test
    void vanillaInventoryDoesNotBlockEngagedCombatButSceneDoes() {
        InputPolicyContext inventory =
                context(
                        EngagementState.ENGAGED,
                        WeaponState.READY,
                        UiState.VANILLA_INVENTORY,
                        false,
                        DirectionSnapshot.NEUTRAL);
        assertEquals(SemanticInput.PRIMARY, resolved(ClientAction.ATTACK, inventory));

        Result<SemanticInput, InputRejectionCode> scene =
                policy.resolve(
                        ClientAction.ATTACK,
                        context(
                                EngagementState.EXPLORATION,
                                WeaponState.READY,
                                UiState.SCENE,
                                false,
                                DirectionSnapshot.NEUTRAL));
        assertEquals(
                InputRejectionCode.ACTION_LOCKED,
                ((Result.Failure<SemanticInput, InputRejectionCode>) scene).error());
    }

    @Test
    void directionalShiftDodgesInCombatButExplorationKeepsVanillaSneak() {
        InputPolicyContext engaged =
                context(
                        EngagementState.ENGAGED,
                        WeaponState.READY,
                        UiState.NONE,
                        false,
                        DirectionSnapshot.FORWARD);
        InputPolicyContext exploration =
                context(
                        EngagementState.EXPLORATION,
                        WeaponState.READY,
                        UiState.NONE,
                        false,
                        DirectionSnapshot.FORWARD);

        assertEquals(SemanticInput.DODGE, resolved(ClientAction.SNEAK_PRESS, engaged));
        assertEquals(
                SemanticInput.VANILLA_FALLBACK, resolved(ClientAction.SNEAK_PRESS, exploration));

        InputPolicyContext drawing =
                context(
                        EngagementState.ENGAGED,
                        WeaponState.DRAWING,
                        UiState.NONE,
                        false,
                        DirectionSnapshot.LEFT);
        assertEquals(SemanticInput.DODGE, resolved(ClientAction.SNEAK_PRESS, drawing));
    }

    private InputPolicyContext context(
            EngagementState engagement,
            WeaponState weapon,
            UiState ui,
            boolean hardWorldTarget,
            DirectionSnapshot direction) {
        return new InputPolicyContext(
                engagement, weapon, ActionState.IDLE, ui, hardWorldTarget, direction);
    }

    private SemanticInput resolved(ClientAction action, InputPolicyContext context) {
        Result<SemanticInput, InputRejectionCode> result = policy.resolve(action, context);
        assertTrue(result.isSuccess());
        return ((Result.Success<SemanticInput, InputRejectionCode>) result).value();
    }

    private static InputRouteOutcome success(Result<InputRouteOutcome, InputRejectionCode> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<InputRouteOutcome, InputRejectionCode>) result).value();
    }
}
