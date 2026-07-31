package com.branz.mmorpg.content.schema;

import static com.branz.mmorpg.content.schema.FieldValueType.ARRAY;
import static com.branz.mmorpg.content.schema.FieldValueType.BOOLEAN;
import static com.branz.mmorpg.content.schema.FieldValueType.INTEGER;
import static com.branz.mmorpg.content.schema.FieldValueType.NUMBER;
import static com.branz.mmorpg.content.schema.FieldValueType.OBJECT;
import static com.branz.mmorpg.content.schema.FieldValueType.STRING;
import static com.branz.mmorpg.content.schema.ReferenceRule.to;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared runtime/tool schema metadata for supported Milestone-1 definitions. */
public final class DefinitionSchemas {
    private static final Map<DefinitionType, DefinitionSchema> SCHEMAS = createSchemas();

    private DefinitionSchemas() {}

    public static DefinitionSchema schema(DefinitionType type) {
        return SCHEMAS.get(type);
    }

    public static Map<DefinitionType, DefinitionSchema> all() {
        return SCHEMAS;
    }

    private static Map<DefinitionType, DefinitionSchema> createSchemas() {
        EnumMap<DefinitionType, DefinitionSchema> schemas = new EnumMap<>(DefinitionType.class);
        for (DefinitionType type : DefinitionType.values()) {
            schemas.put(type, definition(type, List.of(), List.of()));
        }
        schemas.put(
                DefinitionType.ITEM,
                definition(
                        DefinitionType.ITEM,
                        List.of(
                                required("asset_id", STRING, "", "Stable presentation asset ID."),
                                required(
                                        "item_class",
                                        STRING,
                                        "",
                                        "Unique/durable or stackable-lot item class."),
                                optional(
                                        "weapon_profile.family",
                                        STRING,
                                        "",
                                        "Weapon-family runtime identity."),
                                optionalRanged(
                                        "weapon_profile.power",
                                        NUMBER,
                                        0.000001,
                                        null,
                                        "power",
                                        "Base deterministic weapon power."),
                                optionalRanged(
                                        "weapon_profile.bow.minimum_draw_ticks",
                                        INTEGER,
                                        1,
                                        99,
                                        "ticks",
                                        "Earliest valid Bow release."),
                                optionalRanged(
                                        "weapon_profile.bow.full_draw_ticks",
                                        INTEGER,
                                        2,
                                        100,
                                        "ticks",
                                        "Tick at which Bow charge reaches full draw."),
                                optionalRanged(
                                        "weapon_profile.bow.free_full_draw_hold_ticks",
                                        INTEGER,
                                        0,
                                        200,
                                        "ticks",
                                        "Full-draw hold before strain begins."),
                                optionalRanged(
                                        "weapon_profile.bow.strain_stamina_per_second",
                                        NUMBER,
                                        0.000001,
                                        20,
                                        "stamina_per_second",
                                        "Strained Bow hold drain."),
                                optionalRanged(
                                        "weapon_profile.bow.minimum_velocity_multiplier",
                                        NUMBER,
                                        0.000001,
                                        1,
                                        "ratio",
                                        "Quick-shot projectile velocity floor."),
                                optionalRanged(
                                        "weapon_profile.bow.minimum_posture_multiplier",
                                        NUMBER,
                                        0.000001,
                                        1,
                                        "ratio",
                                        "Quick-shot posture-output floor."),
                                optionalRanged(
                                        "weapon_profile.bow.maximum_penetration_percentage",
                                        NUMBER,
                                        0,
                                        0.40,
                                        "ratio",
                                        "Full-draw armor penetration."),
                                optionalRanged(
                                        "weapon_profile.crossbow.bolt_placement_ticks",
                                        INTEGER,
                                        1,
                                        null,
                                        "ticks",
                                        "Ticks before a selected bolt binds durably to the Crossbow."),
                                optionalRanged(
                                        "weapon_profile.crossbow.locking_ticks",
                                        INTEGER,
                                        1,
                                        null,
                                        "ticks",
                                        "Ticks from the BOLT_PLACED checkpoint to LOADED."),
                                optional(
                                        "ammo_profile.family",
                                        STRING,
                                        "",
                                        "Ammo family: ARROW or BOLT."),
                                optionalRanged(
                                        "quiver_profile.capacity",
                                        INTEGER,
                                        1,
                                        4096,
                                        "ammo_units",
                                        "Maximum Quiver storage capacity."),
                                array(
                                        "quiver_profile.supported_ammo_families",
                                        false,
                                        1,
                                        2,
                                        "Compatible ARROW/BOLT families."),
                                optionalRanged(
                                        "quiver_profile.prepared_ammo_category_count",
                                        INTEGER,
                                        1,
                                        4,
                                        "categories",
                                        "Maximum prepared ammo categories."),
                                optionalRanged(
                                        "quiver_profile.ammo_switch_handling_ticks",
                                        INTEGER,
                                        0,
                                        40,
                                        "ticks",
                                        "Engaged ammo-switch handling lock."),
                                array(
                                        "catalyst_profile.tags",
                                        false,
                                        1,
                                        null,
                                        "Catalyst compatibility tags."),
                                optionalRanged(
                                        "catalyst_profile.channel_stability",
                                        NUMBER,
                                        0,
                                        1,
                                        "ratio",
                                        "Bounded catalyst channel stability."),
                                optionalRanged(
                                        "catalyst_profile.durability_cost_per_commit",
                                        INTEGER,
                                        1,
                                        null,
                                        "durability",
                                        "Durability consumed at spell commit.")),
                        List.of(to(DefinitionType.TRAIT, "traits", "authored_pool", "*"))));
        schemas.put(
                DefinitionType.MOVE,
                definition(
                        DefinitionType.MOVE,
                        List.of(
                                required("family", STRING, "", "Owning weapon family."),
                                required("input", OBJECT, "", "Input branch."),
                                allowed(
                                        "input.action",
                                        STRING,
                                        Set.of("PRIMARY", "SECONDARY", "SIGNATURE", "AUXILIARY"),
                                        "",
                                        "Semantic combat input."),
                                allowed(
                                        "input.direction",
                                        STRING,
                                        Set.of("FORWARD", "BACK", "LEFT", "RIGHT", "NEUTRAL"),
                                        "",
                                        "Four-way direction snapshot."),
                                required("input.branch", STRING, "", "Moveset branch identity."),
                                required("phases", OBJECT, "", "Action timeline phases."),
                                ranged(
                                        "phases.windup_ticks",
                                        INTEGER,
                                        0,
                                        40,
                                        "ticks",
                                        "Windup duration."),
                                ranged(
                                        "phases.active_ticks",
                                        INTEGER,
                                        1,
                                        40,
                                        "ticks",
                                        "Active duration."),
                                ranged(
                                        "phases.recovery_ticks",
                                        INTEGER,
                                        0,
                                        40,
                                        "ticks",
                                        "Recovery duration."),
                                ranged(
                                        "commit_tick",
                                        INTEGER,
                                        0,
                                        null,
                                        "ticks",
                                        "Cost commit tick."),
                                required("costs", OBJECT, "", "Reserved action resource costs."),
                                ranged(
                                        "costs.stamina",
                                        INTEGER,
                                        0,
                                        null,
                                        "stamina",
                                        "Total stamina reservation."),
                                ranged(
                                        "costs.mana",
                                        INTEGER,
                                        0,
                                        null,
                                        "mana",
                                        "Total mana reservation."),
                                ranged(
                                        "costs.health",
                                        INTEGER,
                                        0,
                                        null,
                                        "health",
                                        "Non-lethal health reservation."),
                                ranged(
                                        "costs.setup_stamina",
                                        INTEGER,
                                        0,
                                        null,
                                        "stamina",
                                        "Stamina retained when cancelled before commit."),
                                required(
                                        "movement",
                                        OBJECT,
                                        "",
                                        "Server-authored movement and facing."),
                                required("movement.curve", STRING, "", "Movement-curve archetype."),
                                ranged(
                                        "movement.facing_turn_degrees",
                                        NUMBER,
                                        0,
                                        35,
                                        "degrees",
                                        "Maximum authored facing assistance."),
                                array("hitboxes", true, 1, null, "Authoritative hitbox sequence."),
                                ranged(
                                        "hitboxes.*.tick",
                                        INTEGER,
                                        0,
                                        null,
                                        "ticks",
                                        "Active timeline tick."),
                                allowed(
                                        "hitboxes.*.shape",
                                        STRING,
                                        Set.of(
                                                "ARC",
                                                "CAPSULE",
                                                "BOX",
                                                "SPHERE",
                                                "RAY",
                                                "PROJECTILE"),
                                        "",
                                        "Supported hitbox shape."),
                                ranged(
                                        "hitboxes.*.max_targets",
                                        INTEGER,
                                        1,
                                        8,
                                        "targets",
                                        "Maximum targets per hitbox."),
                                ranged(
                                        "hitboxes.*.range",
                                        NUMBER,
                                        0.000001,
                                        null,
                                        "blocks",
                                        "Authoritative hitbox reach."),
                                ranged(
                                        "hitboxes.*.angle_degrees",
                                        NUMBER,
                                        0,
                                        360,
                                        "degrees",
                                        "Hitbox angular span."),
                                ranged(
                                        "hitboxes.*.height",
                                        NUMBER,
                                        0.000001,
                                        null,
                                        "blocks",
                                        "Hitbox vertical span."),
                                required(
                                        "hitboxes.*.hit_group",
                                        STRING,
                                        "",
                                        "Once-per-group hit identity."),
                                optionalRanged(
                                        "hitboxes.*.projectile.speed",
                                        NUMBER,
                                        0.000001,
                                        8,
                                        "blocks_per_tick",
                                        "Projectile launch speed."),
                                optionalRanged(
                                        "hitboxes.*.projectile.gravity_per_tick",
                                        NUMBER,
                                        0,
                                        1,
                                        "blocks_per_tick_squared",
                                        "Downward projectile acceleration."),
                                optionalRanged(
                                        "hitboxes.*.projectile.drag_per_tick",
                                        NUMBER,
                                        0.000001,
                                        1,
                                        "ratio_per_tick",
                                        "Velocity retained per tick."),
                                optionalRanged(
                                        "hitboxes.*.projectile.collision_radius",
                                        NUMBER,
                                        0.000001,
                                        2,
                                        "blocks",
                                        "Swept projectile radius."),
                                optionalRanged(
                                        "hitboxes.*.projectile.lifetime_ticks",
                                        INTEGER,
                                        1,
                                        400,
                                        "ticks",
                                        "Maximum authoritative lifetime."),
                                optionalRanged(
                                        "hitboxes.*.projectile.pierce_count",
                                        INTEGER,
                                        0,
                                        7,
                                        "targets",
                                        "Additional contacts after the first."),
                                optional(
                                        "hitboxes.*.projectile.ammo_category",
                                        STRING,
                                        "",
                                        "Ammo definition carried by the projectile."),
                                required("outputs", OBJECT, "", "Authoritative impact outputs."),
                                allowed(
                                        "outputs.health.physical_type",
                                        STRING,
                                        Set.of("SLASH", "PIERCE", "BLUNT"),
                                        "",
                                        "Physical damage channel."),
                                ranged(
                                        "outputs.health.move_coefficient",
                                        NUMBER,
                                        0.000001,
                                        null,
                                        "coefficient",
                                        "Weapon-power coefficient."),
                                ranged(
                                        "outputs.posture",
                                        INTEGER,
                                        0,
                                        null,
                                        "posture",
                                        "Enemy posture output."),
                                ranged(
                                        "outputs.guard_pressure",
                                        INTEGER,
                                        0,
                                        null,
                                        "guard pressure",
                                        "Guard Stability pressure."),
                                required("cancels", OBJECT, "", "Authored cancel windows."),
                                ranged(
                                        "cancels.dodge_from_tick",
                                        INTEGER,
                                        0,
                                        null,
                                        "ticks",
                                        "First legal dodge-cancel tick."),
                                required(
                                        "cancels.chain_windows",
                                        ARRAY,
                                        "",
                                        "Authored chain windows."),
                                ranged(
                                        "cancels.chain_windows.*.from_tick",
                                        INTEGER,
                                        0,
                                        null,
                                        "ticks",
                                        "Chain-window start."),
                                ranged(
                                        "cancels.chain_windows.*.to_tick",
                                        INTEGER,
                                        0,
                                        null,
                                        "ticks",
                                        "Chain-window end."),
                                required(
                                        "cancels.chain_windows.*.branch",
                                        STRING,
                                        "",
                                        "Destination branch."),
                                required(
                                        "interrupt_resistance",
                                        STRING,
                                        "",
                                        "Authored interruption profile."),
                                required(
                                        "presentation.archetype",
                                        STRING,
                                        "",
                                        "Presentation-only animation/VFX archetype."),
                                ranged(
                                        "profiles.pve_multiplier",
                                        NUMBER,
                                        0.000001,
                                        null,
                                        "multiplier",
                                        "PvE output profile."),
                                ranged(
                                        "profiles.pvp_multiplier",
                                        NUMBER,
                                        0.000001,
                                        null,
                                        "multiplier",
                                        "PvP output profile.")),
                        List.of(
                                to(
                                        DefinitionType.ITEM,
                                        "hitboxes",
                                        "*",
                                        "projectile",
                                        "ammo_category"))));
        schemas.put(
                DefinitionType.SPELL,
                definition(
                        DefinitionType.SPELL,
                        List.of(
                                allowed(
                                        "cast_type",
                                        STRING,
                                        Set.of(
                                                "INSTANT", "WINDUP", "CHARGE", "CHANNEL", "SUSTAIN",
                                                "RITUAL"),
                                        "",
                                        "Spell cast mode."),
                                allowed(
                                        "target_type",
                                        STRING,
                                        Set.of(
                                                "SELF",
                                                "CROSSHAIR_ENTITY",
                                                "CROSSHAIR_POINT",
                                                "GROUND_AREA",
                                                "CONE",
                                                "PROJECTILE",
                                                "TETHERED_ALLY"),
                                        "",
                                        "Spell targeting mode."),
                                allowed(
                                        "delivery",
                                        STRING,
                                        Set.of(
                                                "DIRECT",
                                                "PROJECTILE",
                                                "BEAM",
                                                "ZONE",
                                                "SUMMON",
                                                "IMBUE"),
                                        "",
                                        "Spell delivery mode."),
                                required("art", STRING, "", "Owning magic art identity."),
                                array(
                                        "requirements.catalyst_tags",
                                        true,
                                        1,
                                        null,
                                        "Required catalyst compatibility tags."),
                                ranged(
                                        "requirements.attunement",
                                        INTEGER,
                                        0,
                                        null,
                                        "capacity",
                                        "Required active attunement capacity."),
                                ranged(
                                        "cost.mana",
                                        INTEGER,
                                        0,
                                        null,
                                        "mana",
                                        "Mana reserved at start and spent at release."),
                                ranged(
                                        "phases.windup_ticks",
                                        INTEGER,
                                        0,
                                        200,
                                        "ticks",
                                        "Pre-charge windup duration."),
                                ranged(
                                        "phases.minimum_charge_ticks",
                                        INTEGER,
                                        0,
                                        200,
                                        "ticks",
                                        "Earliest release after charge begins."),
                                ranged(
                                        "phases.maximum_charge_ticks",
                                        INTEGER,
                                        0,
                                        200,
                                        "ticks",
                                        "Forced release boundary after charge begins."),
                                ranged(
                                        "phases.recovery_ticks",
                                        INTEGER,
                                        0,
                                        200,
                                        "ticks",
                                        "Post-commit recovery duration."),
                                required(
                                        "interruption.movement",
                                        BOOLEAN,
                                        "",
                                        "Whether movement interrupts the cast."),
                                required(
                                        "interruption.damage",
                                        BOOLEAN,
                                        "",
                                        "Whether health damage interrupts the cast."),
                                required(
                                        "interruption.flinch",
                                        BOOLEAN,
                                        "",
                                        "Whether Flinch interrupts the cast."),
                                required(
                                        "interruption.stagger",
                                        BOOLEAN,
                                        "",
                                        "Whether Stagger interrupts the cast."),
                                required(
                                        "interruption.silence",
                                        BOOLEAN,
                                        "",
                                        "Whether Silence interrupts the cast."),
                                required(
                                        "interruption.weapon_swap",
                                        BOOLEAN,
                                        "",
                                        "Whether weapon swap interrupts the cast."),
                                optionalRanged(
                                        "projectile.speed",
                                        NUMBER,
                                        0.000001,
                                        8,
                                        "blocks_per_tick",
                                        "Spell projectile launch speed."),
                                optionalRanged(
                                        "projectile.gravity_per_tick",
                                        NUMBER,
                                        0,
                                        1,
                                        "blocks_per_tick_squared",
                                        "Spell projectile gravity."),
                                optionalRanged(
                                        "projectile.drag_per_tick",
                                        NUMBER,
                                        0.000001,
                                        1,
                                        "ratio_per_tick",
                                        "Spell projectile drag."),
                                optionalRanged(
                                        "projectile.collision_radius",
                                        NUMBER,
                                        0.000001,
                                        2,
                                        "blocks",
                                        "Spell projectile collision radius."),
                                optionalRanged(
                                        "projectile.lifetime_ticks",
                                        INTEGER,
                                        1,
                                        400,
                                        "ticks",
                                        "Spell projectile lifetime."),
                                optionalRanged(
                                        "projectile.pierce_count",
                                        INTEGER,
                                        0,
                                        7,
                                        "targets",
                                        "Spell projectile pierce count."),
                                optional(
                                        "projectile.hit_group",
                                        STRING,
                                        "",
                                        "Spell projectile once-per-target hit group."),
                                allowed(
                                        "output.arcane_school",
                                        STRING,
                                        Set.of("FIRE", "FROST", "STORM", "VOID", "PURE_ARCANE"),
                                        "",
                                        "Arcane damage channel."),
                                ranged(
                                        "output.power_coefficient",
                                        NUMBER,
                                        0.000001,
                                        null,
                                        "coefficient",
                                        "Catalyst-power coefficient."),
                                ranged(
                                        "output.posture",
                                        INTEGER,
                                        0,
                                        null,
                                        "posture",
                                        "Posture output."),
                                ranged(
                                        "output.guard_pressure",
                                        INTEGER,
                                        0,
                                        null,
                                        "guard_pressure",
                                        "Guard-pressure output."),
                                required(
                                        "presentation.archetype",
                                        STRING,
                                        "",
                                        "Client presentation archetype."),
                                ranged(
                                        "profiles.pve_multiplier",
                                        NUMBER,
                                        0.000001,
                                        null,
                                        "multiplier",
                                        "PvE output profile."),
                                ranged(
                                        "profiles.pvp_multiplier",
                                        NUMBER,
                                        0.000001,
                                        null,
                                        "multiplier",
                                        "PvP output profile.")),
                        List.of()));
        schemas.put(
                DefinitionType.STATUS,
                definition(
                        DefinitionType.STATUS,
                        List.of(
                                ranged(
                                        "buildup_max",
                                        NUMBER,
                                        0.000001,
                                        null,
                                        "buildup",
                                        "Trigger threshold."),
                                ranged(
                                        "buildup_decay_delay_ticks",
                                        INTEGER,
                                        0,
                                        null,
                                        "ticks",
                                        "Delay before buildup decay."),
                                ranged(
                                        "active_duration_ticks",
                                        INTEGER,
                                        1,
                                        null,
                                        "ticks",
                                        "Active-effect duration."),
                                required("resistance_channel", STRING, "", "Resistance channel."),
                                array(
                                        "cleanse_tags",
                                        true,
                                        1,
                                        null,
                                        "At least one authored cleanse tag.")),
                        List.of()));
        schemas.put(
                DefinitionType.SCENE,
                definition(
                        DefinitionType.SCENE,
                        List.of(
                                required(
                                        "open_requirements",
                                        OBJECT,
                                        "",
                                        "Scene entry requirements."),
                                required("preview", OBJECT, "", "Preview placement profile."),
                                ranged(
                                        "preview.preferred_distance",
                                        NUMBER,
                                        2.5,
                                        3.0,
                                        "blocks",
                                        "Validated local preview distance."),
                                array("modes", true, 1, null, "Available Scene modes."),
                                array(
                                        "close_triggers",
                                        true,
                                        1,
                                        null,
                                        "Safety interruption triggers.")),
                        List.of()));
        schemas.put(
                DefinitionType.CITY,
                definition(
                        DefinitionType.CITY,
                        List.of(
                                array(
                                        "production_categories",
                                        true,
                                        3,
                                        5,
                                        "Primary production categories."),
                                array("import_needs", true, 3, 5, "Authored import needs."),
                                array("trade_goods", true, 2, 4, "Authored city trade goods."),
                                required(
                                        "workshop_specialties", ARRAY, "", "Workshop specialties."),
                                required("base_demand", OBJECT, "", "Category demand indices."),
                                ranged(
                                        "base_demand.*",
                                        NUMBER,
                                        0,
                                        200,
                                        "demand index",
                                        "City demand index.")),
                        List.of(to(DefinitionType.TRADE_GOOD, "trade_goods", "*"))));
        schemas.put(
                DefinitionType.LIFESKILL_NODE,
                definition(
                        DefinitionType.LIFESKILL_NODE,
                        List.of(
                                allowed(
                                        "node_type",
                                        STRING,
                                        Set.of(
                                                "COMMON",
                                                "RICH",
                                                "RARE",
                                                "REGIONAL",
                                                "EVENT",
                                                "CORRUPTED"),
                                        "",
                                        "Resource node lifecycle type."),
                                ranged(
                                        "action_ticks",
                                        INTEGER,
                                        30,
                                        120,
                                        "ticks",
                                        "Gathering action duration."),
                                ranged(
                                        "commit_tick",
                                        INTEGER,
                                        0,
                                        120,
                                        "ticks",
                                        "Gathering commit tick."),
                                ranged(
                                        "recovery_seconds",
                                        INTEGER,
                                        0,
                                        null,
                                        "seconds",
                                        "Wall-clock recovery duration."),
                                array("base_yields", true, 1, null, "Base item yields.")),
                        List.of(
                                to(DefinitionType.ITEM, "base_yields", "*", "item"),
                                to(DefinitionType.ITEM, "byproducts", "*", "item"))));
        schemas.put(
                DefinitionType.WORKER_JOB,
                definition(
                        DefinitionType.WORKER_JOB,
                        List.of(
                                required("role", STRING, "", "Worker role."),
                                required("city", STRING, "", "Owning city definition."),
                                ranged(
                                        "duration_seconds",
                                        INTEGER,
                                        1,
                                        null,
                                        "seconds",
                                        "Job duration."),
                                required("costs", OBJECT, "", "Reserved job costs."),
                                array("outputs", true, 1, null, "Committed job outputs."),
                                required(
                                        "offline_allowed",
                                        BOOLEAN,
                                        "",
                                        "Whether timestamp-based offline work is allowed."),
                                ranged(
                                        "queue_cap_hours",
                                        NUMBER,
                                        0,
                                        24,
                                        "hours",
                                        "Maximum offline queue duration.")),
                        List.of(
                                to(DefinitionType.CITY, "city"),
                                to(DefinitionType.NODE_REGION, "required_node_knowledge"),
                                to(DefinitionType.ITEM, "costs", "food_item"),
                                to(DefinitionType.ITEM, "outputs", "*", "item"),
                                to(DefinitionType.ITEM, "rare_outputs", "*", "item"))));
        schemas.put(
                DefinitionType.MOUNT,
                definition(
                        DefinitionType.MOUNT,
                        List.of(
                                required("species", STRING, "", "Mount species."),
                                required("base_stats", OBJECT, "", "Bounded base mount stats."),
                                required("cargo", OBJECT, "", "Cargo capacity profile."),
                                allowed(
                                        "permanent_death",
                                        BOOLEAN,
                                        Set.of("false"),
                                        "",
                                        "V1 mounts cannot die permanently."),
                                allowed(
                                        "mounted_combat",
                                        BOOLEAN,
                                        Set.of("false"),
                                        "",
                                        "V1 mounted combat is disabled.")),
                        List.of()));
        return Collections.unmodifiableMap(schemas);
    }

    private static DefinitionSchema definition(
            DefinitionType type, List<FieldRule> ownRules, List<ReferenceRule> references) {
        List<FieldRule> fields = new ArrayList<>();
        fields.add(
                required(
                        "definition_id",
                        STRING,
                        "",
                        "Stable lowercase dotted definition identity."));
        fields.add(
                ranged(
                        "schema_version",
                        INTEGER,
                        1,
                        null,
                        "schema version",
                        "Public content schema version."));
        fields.addAll(ownRules);
        return new DefinitionSchema(type, fields, references);
    }

    private static FieldRule required(
            String path, FieldValueType type, String unit, String description) {
        return rule(path, type, true, null, null, null, null, Set.of(), unit, description);
    }

    private static FieldRule ranged(
            String path,
            FieldValueType type,
            Number minimum,
            Number maximum,
            String unit,
            String description) {
        return rule(
                path,
                type,
                true,
                minimum == null ? null : minimum.doubleValue(),
                maximum == null ? null : maximum.doubleValue(),
                null,
                null,
                Set.of(),
                unit,
                description);
    }

    private static FieldRule optional(
            String path, FieldValueType type, String unit, String description) {
        return rule(path, type, false, null, null, null, null, Set.of(), unit, description);
    }

    private static FieldRule optionalRanged(
            String path,
            FieldValueType type,
            Number minimum,
            Number maximum,
            String unit,
            String description) {
        return rule(
                path,
                type,
                false,
                minimum == null ? null : minimum.doubleValue(),
                maximum == null ? null : maximum.doubleValue(),
                null,
                null,
                Set.of(),
                unit,
                description);
    }

    private static FieldRule array(
            String path, boolean required, Integer minItems, Integer maxItems, String description) {
        return rule(
                path, ARRAY, required, null, null, minItems, maxItems, Set.of(), "", description);
    }

    private static FieldRule allowed(
            String path, FieldValueType type, Set<String> values, String unit, String description) {
        return rule(path, type, true, null, null, null, null, values, unit, description);
    }

    private static FieldRule rule(
            String path,
            FieldValueType type,
            boolean required,
            Double minimum,
            Double maximum,
            Integer minItems,
            Integer maxItems,
            Set<String> allowedValues,
            String unit,
            String description) {
        return new FieldRule(
                Arrays.asList(path.split("\\.")),
                type,
                required,
                minimum,
                maximum,
                minItems,
                maxItems,
                allowedValues,
                unit,
                description);
    }
}
