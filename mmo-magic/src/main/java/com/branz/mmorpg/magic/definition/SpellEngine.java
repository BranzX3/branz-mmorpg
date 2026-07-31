package com.branz.mmorpg.magic.definition;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.definition.ContentDefinition;
import com.branz.mmorpg.content.schema.DefinitionType;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable spell registry compiled atomically from the active content snapshot. */
public final class SpellEngine {
    private final Map<DefinitionId, SpellDefinition> definitions;

    private SpellEngine(Map<DefinitionId, SpellDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public static Result<SpellEngine, SpellEngineErrorCode> compile(ContentSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        LinkedHashMap<DefinitionId, SpellDefinition> compiled = new LinkedHashMap<>();
        for (ContentDefinition source : snapshot.definitions().byType(DefinitionType.SPELL)) {
            try {
                SpellDefinition definition = compileDefinition(source);
                compiled.put(definition.id(), definition);
            } catch (IllegalArgumentException exception) {
                return Result.failure(
                        SpellEngineErrorCode.SPELL_FIELD_INVALID,
                        source.id().value() + ": " + exception.getMessage());
            }
        }
        return Result.success(new SpellEngine(compiled));
    }

    public Optional<SpellDefinition> find(DefinitionId id) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(id, "id")));
    }

    public Collection<SpellDefinition> all() {
        return definitions.values();
    }

    private static SpellDefinition compileDefinition(ContentDefinition source) {
        JsonNode body = source.body();
        SpellDeliveryType delivery = SpellDeliveryType.valueOf(text(body, "delivery"));
        JsonNode projectileNode = body.get("projectile");
        boolean projectileDeclared = projectileNode != null && !projectileNode.isNull();
        if ((delivery == SpellDeliveryType.PROJECTILE) != projectileDeclared) {
            throw new IllegalArgumentException(
                    "PROJECTILE delivery requires projectile fields and other deliveries forbid them");
        }
        return new SpellDefinition(
                source.id(),
                definitionId(body, "art"),
                SpellCastType.valueOf(text(body, "cast_type")),
                SpellTargetType.valueOf(text(body, "target_type")),
                delivery,
                new SpellDefinition.Requirements(
                        textSet(body, "requirements.catalyst_tags"),
                        integer(body, "requirements.attunement")),
                integer(body, "cost.mana"),
                new SpellDefinition.Phases(
                        integer(body, "phases.windup_ticks"),
                        integer(body, "phases.minimum_charge_ticks"),
                        integer(body, "phases.maximum_charge_ticks"),
                        integer(body, "phases.recovery_ticks")),
                new SpellDefinition.Interruption(
                        bool(body, "interruption.movement"),
                        bool(body, "interruption.damage"),
                        bool(body, "interruption.flinch"),
                        bool(body, "interruption.stagger"),
                        bool(body, "interruption.silence"),
                        bool(body, "interruption.weapon_swap")),
                delivery == SpellDeliveryType.PROJECTILE
                        ? Optional.of(
                                new SpellDefinition.Projectile(
                                        number(body, "projectile.speed"),
                                        number(body, "projectile.gravity_per_tick"),
                                        number(body, "projectile.drag_per_tick"),
                                        number(body, "projectile.collision_radius"),
                                        integer(body, "projectile.lifetime_ticks"),
                                        integer(body, "projectile.pierce_count"),
                                        text(body, "projectile.hit_group")))
                        : Optional.empty(),
                new SpellDefinition.Output(
                        ArcaneSchool.valueOf(text(body, "output.arcane_school")),
                        number(body, "output.power_coefficient"),
                        integer(body, "output.posture"),
                        integer(body, "output.guard_pressure")),
                text(body, "presentation.archetype"),
                new SpellDefinition.CombatProfiles(
                        number(body, "profiles.pve_multiplier"),
                        number(body, "profiles.pvp_multiplier")));
    }

    private static DefinitionId definitionId(JsonNode root, String path) {
        Result<DefinitionId, ?> parsed = DefinitionId.parse(text(root, path));
        if (parsed instanceof Result.Failure<?, ?> failure) {
            throw new IllegalArgumentException(path + " is invalid: " + failure.detail());
        }
        return ((Result.Success<DefinitionId, ?>) parsed).value();
    }

    private static Set<String> textSet(JsonNode root, String path) {
        JsonNode node = at(root, path);
        if (!node.isArray() || node.isEmpty()) {
            throw new IllegalArgumentException(path + " must contain at least one entry");
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode entry : node) {
            if (!entry.isTextual() || entry.textValue().isBlank()) {
                throw new IllegalArgumentException(path + " entries must be non-blank text");
            }
            values.add(entry.textValue());
        }
        return Set.copyOf(values);
    }

    private static String text(JsonNode root, String path) {
        JsonNode node = at(root, path);
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalArgumentException(path + " must be non-blank text");
        }
        return node.textValue();
    }

    private static int integer(JsonNode root, String path) {
        JsonNode node = at(root, path);
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new IllegalArgumentException(path + " must be an integer");
        }
        return node.intValue();
    }

    private static double number(JsonNode root, String path) {
        JsonNode node = at(root, path);
        if (!node.isNumber() || !Double.isFinite(node.doubleValue())) {
            throw new IllegalArgumentException(path + " must be a finite number");
        }
        return node.doubleValue();
    }

    private static boolean bool(JsonNode root, String path) {
        JsonNode node = at(root, path);
        if (!node.isBoolean()) {
            throw new IllegalArgumentException(path + " must be a boolean");
        }
        return node.booleanValue();
    }

    private static JsonNode at(JsonNode root, String path) {
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            current = current.path(segment);
        }
        return current;
    }
}
