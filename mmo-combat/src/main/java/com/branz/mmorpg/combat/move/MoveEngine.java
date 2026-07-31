package com.branz.mmorpg.combat.move;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.input.DirectionSnapshot;
import com.branz.mmorpg.combat.input.SemanticInput;
import com.branz.mmorpg.content.definition.ContentDefinition;
import com.branz.mmorpg.content.schema.DefinitionType;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable move registry compiled atomically from the active content snapshot. */
public final class MoveEngine {
    private final Map<DefinitionId, MoveDefinition> definitions;

    private MoveEngine(Map<DefinitionId, MoveDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public static Result<MoveEngine, MoveEngineErrorCode> compile(ContentSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        LinkedHashMap<DefinitionId, MoveDefinition> compiled = new LinkedHashMap<>();
        for (ContentDefinition source : snapshot.definitions().byType(DefinitionType.MOVE)) {
            Result<MoveDefinition, MoveEngineErrorCode> result = compileDefinition(source);
            if (result instanceof Result.Failure<MoveDefinition, MoveEngineErrorCode> failure) {
                return Result.failure(
                        failure.error(), source.id().value() + ": " + failure.detail());
            }
            MoveDefinition definition =
                    ((Result.Success<MoveDefinition, MoveEngineErrorCode>) result).value();
            compiled.put(definition.id(), definition);
        }
        return Result.success(new MoveEngine(compiled));
    }

    public Optional<MoveDefinition> find(DefinitionId id) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(id, "id")));
    }

    public Collection<MoveDefinition> all() {
        return definitions.values();
    }

    private static Result<MoveDefinition, MoveEngineErrorCode> compileDefinition(
            ContentDefinition source) {
        JsonNode body = source.body();
        try {
            MoveDefinition.InputBranch input =
                    new MoveDefinition.InputBranch(
                            inputAction(text(body, "input.action")),
                            DirectionSnapshot.valueOf(text(body, "input.direction")),
                            text(body, "input.branch"));
            MoveDefinition.PhaseDurations phases =
                    new MoveDefinition.PhaseDurations(
                            integer(body, "phases.windup_ticks"),
                            integer(body, "phases.active_ticks"),
                            integer(body, "phases.recovery_ticks"));
            MoveDefinition.ResourceCost costs = costs(body);
            MoveDefinition.Movement movement =
                    new MoveDefinition.Movement(
                            text(body, "movement.curve"),
                            number(body, "movement.facing_turn_degrees"));
            List<MoveDefinition.Hitbox> hitboxes = hitboxes(body.path("hitboxes"));
            MoveDefinition.Outputs outputs =
                    new MoveDefinition.Outputs(
                            MoveDefinition.PhysicalDamageType.valueOf(
                                    text(body, "outputs.health.physical_type")),
                            number(body, "outputs.health.move_coefficient"),
                            integer(body, "outputs.posture"),
                            integer(body, "outputs.guard_pressure"));
            MoveDefinition.CancelWindows cancels =
                    new MoveDefinition.CancelWindows(
                            integer(body, "cancels.dodge_from_tick"),
                            chainWindows(body.path("cancels").path("chain_windows")));
            MoveDefinition definition =
                    new MoveDefinition(
                            source.id(),
                            text(body, "family"),
                            input,
                            phases,
                            integer(body, "commit_tick"),
                            costs,
                            movement,
                            hitboxes,
                            outputs,
                            cancels,
                            text(body, "interrupt_resistance"),
                            text(body, "presentation.archetype"),
                            new MoveDefinition.CombatProfiles(
                                    number(body, "profiles.pve_multiplier"),
                                    number(body, "profiles.pvp_multiplier")));
            return Result.success(definition);
        } catch (IllegalArgumentException exception) {
            return Result.failure(classify(exception), exception.getMessage());
        }
    }

    private static MoveDefinition.ResourceCost costs(JsonNode body) {
        try {
            return new MoveDefinition.ResourceCost(
                    optionalInteger(body, "costs.stamina"),
                    optionalInteger(body, "costs.mana"),
                    optionalInteger(body, "costs.health"),
                    optionalInteger(body, "costs.setup_stamina"));
        } catch (IllegalArgumentException exception) {
            throw new MoveCompileException(
                    MoveEngineErrorCode.MOVE_COST_INVALID, exception.getMessage());
        }
    }

    private static List<MoveDefinition.Hitbox> hitboxes(JsonNode nodes) {
        if (!nodes.isArray() || nodes.isEmpty()) {
            throw new MoveCompileException(
                    MoveEngineErrorCode.MOVE_HITBOX_INVALID,
                    "hitboxes must contain at least one entry");
        }
        ArrayList<MoveDefinition.Hitbox> result = new ArrayList<>();
        try {
            for (JsonNode node : nodes) {
                result.add(
                        new MoveDefinition.Hitbox(
                                requiredInteger(node.path("tick"), "tick"),
                                MoveDefinition.HitboxShape.valueOf(
                                        requiredText(node.path("shape"), "shape")),
                                requiredNumber(node.path("range"), "range"),
                                requiredNumber(node.path("angle_degrees"), "angle_degrees"),
                                requiredNumber(node.path("height"), "height"),
                                requiredInteger(node.path("max_targets"), "max_targets"),
                                requiredText(node.path("hit_group"), "hit_group"),
                                projectile(
                                        MoveDefinition.HitboxShape.valueOf(
                                                requiredText(node.path("shape"), "shape")),
                                        node.path("projectile"))));
            }
        } catch (IllegalArgumentException exception) {
            throw new MoveCompileException(
                    MoveEngineErrorCode.MOVE_HITBOX_INVALID, exception.getMessage());
        }
        return List.copyOf(result);
    }

    private static Optional<MoveDefinition.ProjectileDefinition> projectile(
            MoveDefinition.HitboxShape shape, JsonNode node) {
        if (shape != MoveDefinition.HitboxShape.PROJECTILE) {
            if (!node.isMissingNode() && !node.isNull()) {
                throw new IllegalArgumentException(
                        "projectile fields are valid only for PROJECTILE shape");
            }
            return Optional.empty();
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("PROJECTILE shape requires projectile fields");
        }
        Result<DefinitionId, ?> ammo =
                DefinitionId.parse(requiredText(node.path("ammo_category"), "ammo_category"));
        if (ammo instanceof Result.Failure<?, ?> failure) {
            throw new IllegalArgumentException(
                    "projectile ammo_category is invalid: " + failure.detail());
        }
        return Optional.of(
                new MoveDefinition.ProjectileDefinition(
                        requiredNumber(node.path("speed"), "speed"),
                        requiredNumber(node.path("gravity_per_tick"), "gravity_per_tick"),
                        requiredNumber(node.path("drag_per_tick"), "drag_per_tick"),
                        requiredNumber(node.path("collision_radius"), "collision_radius"),
                        requiredInteger(node.path("lifetime_ticks"), "lifetime_ticks"),
                        requiredInteger(node.path("pierce_count"), "pierce_count"),
                        ((Result.Success<DefinitionId, ?>) ammo).value()));
    }

    private static List<MoveDefinition.ChainWindow> chainWindows(JsonNode nodes) {
        if (!nodes.isArray()) {
            throw new MoveCompileException(
                    MoveEngineErrorCode.MOVE_WINDOW_INVALID,
                    "cancels.chain_windows must be an array");
        }
        ArrayList<MoveDefinition.ChainWindow> result = new ArrayList<>();
        try {
            for (JsonNode node : nodes) {
                result.add(
                        new MoveDefinition.ChainWindow(
                                requiredInteger(node.path("from_tick"), "from_tick"),
                                requiredInteger(node.path("to_tick"), "to_tick"),
                                requiredText(node.path("branch"), "branch")));
            }
        } catch (IllegalArgumentException exception) {
            throw new MoveCompileException(
                    MoveEngineErrorCode.MOVE_WINDOW_INVALID, exception.getMessage());
        }
        return List.copyOf(result);
    }

    private static SemanticInput inputAction(String value) {
        SemanticInput action = SemanticInput.valueOf(value);
        if (action != SemanticInput.PRIMARY
                && action != SemanticInput.SECONDARY
                && action != SemanticInput.SIGNATURE
                && action != SemanticInput.AUXILIARY) {
            throw new IllegalArgumentException("input.action is not a move input branch");
        }
        return action;
    }

    private static MoveEngineErrorCode classify(IllegalArgumentException exception) {
        if (exception instanceof MoveCompileException compileException) {
            return compileException.code();
        }
        String message = exception.getMessage();
        if (message != null && message.contains("hitbox")) {
            return MoveEngineErrorCode.MOVE_HITBOX_INVALID;
        }
        if (message != null && (message.contains("timeline") || message.contains("phase"))) {
            return MoveEngineErrorCode.MOVE_TIMELINE_INVALID;
        }
        if (message != null && message.contains("chain")) {
            return MoveEngineErrorCode.MOVE_WINDOW_INVALID;
        }
        return MoveEngineErrorCode.MOVE_FIELD_INVALID;
    }

    private static String text(JsonNode root, String path) {
        return requiredText(at(root, path), path);
    }

    private static int integer(JsonNode root, String path) {
        return requiredInteger(at(root, path), path);
    }

    private static int optionalInteger(JsonNode root, String path) {
        JsonNode node = at(root, path);
        return node.isMissingNode() || node.isNull() ? 0 : requiredInteger(node, path);
    }

    private static double number(JsonNode root, String path) {
        return requiredNumber(at(root, path), path);
    }

    private static JsonNode at(JsonNode root, String path) {
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            current = current.path(segment);
        }
        return current;
    }

    private static String requiredText(JsonNode node, String path) {
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalArgumentException(path + " must be non-blank text");
        }
        return node.textValue();
    }

    private static int requiredInteger(JsonNode node, String path) {
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new IllegalArgumentException(path + " must be an integer");
        }
        return node.intValue();
    }

    private static double requiredNumber(JsonNode node, String path) {
        if (!node.isNumber() || !Double.isFinite(node.doubleValue())) {
            throw new IllegalArgumentException(path + " must be a finite number");
        }
        return node.doubleValue();
    }

    private static final class MoveCompileException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        private final MoveEngineErrorCode code;

        private MoveCompileException(MoveEngineErrorCode code, String message) {
            super(message);
            this.code = Objects.requireNonNull(code, "code");
        }

        private MoveEngineErrorCode code() {
            return code;
        }
    }
}
