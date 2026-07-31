package com.branz.mmorpg.combat.input;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.state.ActionState;
import com.branz.mmorpg.combat.state.EngagementState;
import com.branz.mmorpg.combat.state.UiState;
import com.branz.mmorpg.combat.state.WeaponState;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Converts a client action into one semantic owner before InputRouter priority resolution. */
public final class CombatInputPolicy {
    public Result<SemanticInput, InputRejectionCode> resolve(
            ClientAction action, InputPolicyContext context) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(context, "context");
        if (exclusiveUiOwnsInput(context.ui())) {
            return Result.failure(
                    InputRejectionCode.ACTION_LOCKED,
                    context.ui() + " currently owns player input.");
        }
        if (context.action().hardControl()) {
            return Result.failure(
                    InputRejectionCode.ACTION_LOCKED,
                    "Primary input is locked by " + context.action() + ".");
        }
        return Result.success(
                switch (action) {
                    case ATTACK -> attack(context);
                    case USE -> use(context);
                    case SWAP_HAND ->
                            ready(context)
                                    ? SemanticInput.SIGNATURE
                                    : SemanticInput.VANILLA_FALLBACK;
                    case DROP ->
                            ready(context)
                                    ? SemanticInput.AUXILIARY
                                    : SemanticInput.VANILLA_FALLBACK;
                    case SNEAK_PRESS ->
                            ready(context) && context.direction() != DirectionSnapshot.NEUTRAL
                                    ? SemanticInput.DODGE
                                    : SemanticInput.VANILLA_FALLBACK;
                });
    }

    public InputRoutingContext routingContext(
            InputPolicyContext context, boolean authoredQueueWindowOpen) {
        Objects.requireNonNull(context, "context");
        EnumSet<SemanticInput> legal =
                EnumSet.of(SemanticInput.FORCED_INTERRUPT, SemanticInput.UI_DANGER_CLOSE);
        if (context.action().hardControl() || exclusiveUiOwnsInput(context.ui())) {
            return new InputRoutingContext(legal, false);
        }
        legal.add(SemanticInput.WORLD_INTERACTION);
        legal.add(SemanticInput.VANILLA_FALLBACK);
        if (context.weapon() == WeaponState.DRAWING) {
            legal.add(SemanticInput.DODGE);
            return new InputRoutingContext(legal, true);
        }
        if (ready(context) && context.action() == ActionState.IDLE) {
            legal.addAll(
                    Set.of(
                            SemanticInput.DODGE,
                            SemanticInput.DEFENSIVE_RESPONSE,
                            SemanticInput.PRIMARY,
                            SemanticInput.SECONDARY,
                            SemanticInput.SIGNATURE,
                            SemanticInput.AUXILIARY));
        }
        return new InputRoutingContext(legal, authoredQueueWindowOpen);
    }

    private static SemanticInput attack(InputPolicyContext context) {
        return context.weapon() == WeaponState.READY || context.weapon() == WeaponState.DRAWING
                ? SemanticInput.PRIMARY
                : SemanticInput.VANILLA_FALLBACK;
    }

    private static SemanticInput use(InputPolicyContext context) {
        boolean combatWeapon =
                context.weapon() == WeaponState.READY || context.weapon() == WeaponState.DRAWING;
        if (combatWeapon && context.engagement() == EngagementState.ENGAGED) {
            return SemanticInput.SECONDARY;
        }
        if (context.hardWorldTarget()) {
            return SemanticInput.WORLD_INTERACTION;
        }
        return combatWeapon ? SemanticInput.SECONDARY : SemanticInput.VANILLA_FALLBACK;
    }

    private static boolean ready(InputPolicyContext context) {
        return context.weapon() == WeaponState.READY;
    }

    private static boolean exclusiveUiOwnsInput(UiState ui) {
        return ui != UiState.NONE && ui != UiState.VANILLA_INVENTORY;
    }
}
