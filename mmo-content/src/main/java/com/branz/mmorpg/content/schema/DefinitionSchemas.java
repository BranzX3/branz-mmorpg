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
                                optionalAllowed(
                                        "weapon_profile.offhand_policy",
                                        STRING,
                                        Set.of("ANY", "EMPTY", "SHIELD"),
                                        "",
                                        "Off-hand requirement checked before combat readiness."),
                                optionalRanged(
                                        "weapon_profile.guard.cone_degrees",
                                        NUMBER,
                                        0.000001,
                                        360,
                                        "degrees",
                                        "Directional weapon-guard coverage."),
                                optionalRanged(
                                        "weapon_profile.guard.physical_block_ratio",
                                        NUMBER,
                                        0,
                                        1,
                                        "ratio",
                                        "Physical damage blocked by weapon guard."),
                                optionalRanged(
                                        "weapon_profile.guard.perfect_window_ticks",
                                        INTEGER,
                                        1,
                                        20,
                                        "ticks",
                                        "Unmodified perfect-guard window."),
                                optionalRanged(
                                        "weapon_profile.guard.maximum_stability",
                                        NUMBER,
                                        0.000001,
                                        null,
                                        "stability",
                                        "Maximum weapon-guard stability."),
                                optionalRanged(
                                        "weapon_profile.guard.recovery_delay_ticks",
                                        INTEGER,
                                        0,
                                        null,
                                        "ticks",
                                        "Delay before stability recovery."),
                                optionalRanged(
                                        "weapon_profile.guard.inactive_recovery_per_second",
                                        NUMBER,
                                        0,
                                        null,
                                        "stability_per_second",
                                        "Stability recovery while guard is lowered."),
                                optionalRanged(
                                        "weapon_profile.guard.active_recovery_per_second",
                                        NUMBER,
                                        0,
                                        null,
                                        "stability_per_second",
                                        "Stability recovery while guard is held."),
                                optionalRanged(
                                        "weapon_profile.guard.break_ticks",
                                        INTEGER,
                                        1,
                                        null,
                                        "ticks",
                                        "Guard-break recovery duration."),
                                optionalRanged(
                                        "weapon_profile.guard.stability_after_break",
                                        NUMBER,
                                        0,
                                        null,
                                        "stability",
                                        "Stability restored after Guard Break."),
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
                                        "Durability consumed at spell commit."),
                                optionalRanged(
                                        "shield_profile.guard.cone_degrees",
                                        NUMBER,
                                        0.000001,
                                        360,
                                        "degrees",
                                        "Directional shield-guard coverage."),
                                optionalRanged(
                                        "shield_profile.guard.physical_block_ratio",
                                        NUMBER,
                                        0,
                                        1,
                                        "ratio",
                                        "Physical damage blocked by shield guard."),
                                optionalRanged(
                                        "shield_profile.guard.perfect_window_ticks",
                                        INTEGER,
                                        1,
                                        20,
                                        "ticks",
                                        "Unmodified shield perfect-guard window."),
                                optionalRanged(
                                        "shield_profile.guard.maximum_stability",
                                        NUMBER,
                                        0.000001,
                                        null,
                                        "stability",
                                        "Maximum shield stability."),
                                optionalRanged(
                                        "shield_profile.guard.recovery_delay_ticks",
                                        INTEGER,
                                        0,
                                        null,
                                        "ticks",
                                        "Delay before shield stability recovery."),
                                optionalRanged(
                                        "shield_profile.guard.inactive_recovery_per_second",
                                        NUMBER,
                                        0,
                                        null,
                                        "stability_per_second",
                                        "Shield stability recovery while lowered."),
                                optionalRanged(
                                        "shield_profile.guard.active_recovery_per_second",
                                        NUMBER,
                                        0,
                                        null,
                                        "stability_per_second",
                                        "Shield stability recovery while held."),
                                optionalRanged(
                                        "shield_profile.guard.break_ticks",
                                        INTEGER,
                                        1,
                                        null,
                                        "ticks",
                                        "Shield Guard Break duration."),
                                optionalRanged(
                                        "shield_profile.guard.stability_after_break",
                                        NUMBER,
                                        0,
                                        null,
                                        "stability",
                                        "Shield stability restored after Guard Break."),
                                optionalAllowed(
                                        "consumable_profile.category",
                                        STRING,
                                        Set.of(
                                                "BODY_TONIC",
                                                "ELEMENTAL_WARD",
                                                "WEAPON_COATING",
                                                "UTILITY_PREPARATION",
                                                "MEAL"),
                                        "",
                                        "Exclusive active-effect category."),
                                optionalRanged(
                                        "consumable_profile.windup_ticks",
                                        INTEGER,
                                        1,
                                        null,
                                        "ticks",
                                        "Use windup duration."),
                                optionalRanged(
                                        "consumable_profile.commit_tick",
                                        INTEGER,
                                        1,
                                        null,
                                        "ticks",
                                        "Consumption commit offset."),
                                optionalRanged(
                                        "consumable_profile.recovery_ticks",
                                        INTEGER,
                                        0,
                                        null,
                                        "ticks",
                                        "Post-use recovery duration."),
                                optionalRanged(
                                        "consumable_profile.effect_duration_ticks",
                                        INTEGER,
                                        1,
                                        null,
                                        "ticks",
                                        "Active effect duration."),
                                optional(
                                        "consumable_profile.rare",
                                        BOOLEAN,
                                        "",
                                        "Whether replacement requires confirmation.")),
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
                DefinitionType.TECHNIQUE,
                definition(
                        DefinitionType.TECHNIQUE,
                        List.of(
                                required("family", STRING, "", "Compatible weapon family or ANY."),
                                allowed(
                                        "branch",
                                        STRING,
                                        Set.of(
                                                "PRIMARY_1",
                                                "PRIMARY_2",
                                                "PRIMARY_3",
                                                "PRIMARY_DIRECTIONAL_FORWARD",
                                                "PRIMARY_DIRECTIONAL_BACK",
                                                "SECONDARY",
                                                "SECONDARY_DIRECTIONAL",
                                                "DODGE_FOLLOWUP",
                                                "SIGNATURE_F",
                                                "UTILITY_Q",
                                                "DEFENSIVE_FOLLOWUP",
                                                "FINISHER"),
                                        "",
                                        "Logical moveset branch owned by the technique."),
                                required(
                                        "move",
                                        STRING,
                                        "",
                                        "Move definition activated by the branch."),
                                allowed(
                                        "mode",
                                        STRING,
                                        Set.of("REPLACE", "AUGMENT"),
                                        "",
                                        "Whether the technique replaces or augments its branch."),
                                required(
                                        "mastery_discipline",
                                        STRING,
                                        "",
                                        "Mastery discipline used by learning and teaching readiness."),
                                required(
                                        "supernatural",
                                        BOOLEAN,
                                        "",
                                        "Whether the technique consumes Attunement Capacity."),
                                ranged(
                                        "attunement_cost",
                                        INTEGER,
                                        0,
                                        32,
                                        "capacity",
                                        "Active supernatural load; mundane techniques use zero."),
                                allowed(
                                        "learning_readiness",
                                        STRING,
                                        Set.of(
                                                "UNFAMILIAR",
                                                "DEVELOPING",
                                                "RELIABLE",
                                                "REFINED",
                                                "EXCEPTIONAL"),
                                        "",
                                        "Minimum family Mastery band required by the student."),
                                allowed(
                                        "teaching_readiness",
                                        STRING,
                                        Set.of(
                                                "UNFAMILIAR",
                                                "DEVELOPING",
                                                "RELIABLE",
                                                "REFINED",
                                                "EXCEPTIONAL"),
                                        "",
                                        "Minimum family Mastery band required to teach."),
                                array("tags", false, 1, null, "Attunement/resonance tags."),
                                array(
                                        "conflicts_with_tags",
                                        false,
                                        1,
                                        null,
                                        "Tags that make this technique incompatible.")),
                        List.of(to(DefinitionType.MOVE, "move"))));
        schemas.put(
                DefinitionType.FORM,
                definition(
                        DefinitionType.FORM,
                        List.of(
                                array("families", true, 1, 6, "Compatible weapon families or ANY."),
                                required(
                                        "tradeoff",
                                        STRING,
                                        "",
                                        "Authored mechanical benefit/cost summary."),
                                ranged(
                                        "attunement_cost",
                                        INTEGER,
                                        0,
                                        32,
                                        "capacity",
                                        "Active supernatural load."),
                                ranged(
                                        "resource.stamina_cost_multiplier",
                                        NUMBER,
                                        0.5,
                                        1.5,
                                        "multiplier",
                                        "Form stamina behavior."),
                                ranged(
                                        "resource.mana_cost_multiplier",
                                        NUMBER,
                                        0.5,
                                        1.5,
                                        "multiplier",
                                        "Form mana behavior."),
                                array("tags", false, 1, null, "Attunement/resonance tags."),
                                array(
                                        "conflicts_with_tags",
                                        false,
                                        1,
                                        null,
                                        "Tags that make this form incompatible."),
                                allowed(
                                        "acquisition.source_type",
                                        STRING,
                                        Set.of(
                                                "MENTOR",
                                                "WORLD_DISCOVERY",
                                                "BOSS_KNOWLEDGE",
                                                "FACTION_QUEST"),
                                        "",
                                        "Server event family that can grant this Form."),
                                required(
                                        "acquisition.source",
                                        STRING,
                                        "",
                                        "Stable server-owned acquisition source ID."),
                                required(
                                        "acquisition.mastery_discipline",
                                        STRING,
                                        "",
                                        "Mastery discipline checked before learning."),
                                allowed(
                                        "acquisition.readiness",
                                        STRING,
                                        Set.of(
                                                "UNFAMILIAR",
                                                "DEVELOPING",
                                                "RELIABLE",
                                                "REFINED",
                                                "EXCEPTIONAL"),
                                        "",
                                        "Minimum qualitative Mastery band."),
                                array(
                                        "acquisition.prerequisite_knowledge",
                                        false,
                                        1,
                                        null,
                                        "Permanent prerequisites encoded as TYPE:definition.id."),
                                array(
                                        "acquisition.world_flags",
                                        false,
                                        1,
                                        null,
                                        "Required durable world/trial flags.")),
                        List.of()));
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
                                array(
                                        "requirements.attunement_tags",
                                        false,
                                        1,
                                        null,
                                        "Spell resonance and Attunement tags."),
                                array(
                                        "requirements.conflicts_with_tags",
                                        false,
                                        1,
                                        null,
                                        "Tags that make this spell incompatible."),
                                allowed(
                                        "acquisition.source_type",
                                        STRING,
                                        Set.of(
                                                "MENTOR",
                                                "WORLD_DISCOVERY",
                                                "BOSS_KNOWLEDGE",
                                                "FACTION_QUEST"),
                                        "",
                                        "Server event family that can grant this Spell."),
                                required(
                                        "acquisition.source",
                                        STRING,
                                        "",
                                        "Stable server-owned acquisition source ID."),
                                required(
                                        "acquisition.mastery_discipline",
                                        STRING,
                                        "",
                                        "Mastery discipline checked before learning."),
                                allowed(
                                        "acquisition.readiness",
                                        STRING,
                                        Set.of(
                                                "UNFAMILIAR",
                                                "DEVELOPING",
                                                "RELIABLE",
                                                "REFINED",
                                                "EXCEPTIONAL"),
                                        "",
                                        "Minimum qualitative Mastery band."),
                                array(
                                        "acquisition.prerequisite_knowledge",
                                        false,
                                        1,
                                        null,
                                        "Permanent prerequisites encoded as TYPE:definition.id."),
                                array(
                                        "acquisition.world_flags",
                                        false,
                                        1,
                                        null,
                                        "Required durable world/trial flags."),
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
                                optionalRanged(
                                        "direct.range",
                                        NUMBER,
                                        0.000001,
                                        32,
                                        "blocks",
                                        "Maximum direct-delivery target range."),
                                optionalRanged(
                                        "direct.maximum_targets",
                                        INTEGER,
                                        1,
                                        1,
                                        "targets",
                                        "Maximum direct targets per commit."),
                                optionalRanged(
                                        "channel.pulse_interval_ticks",
                                        INTEGER,
                                        1,
                                        100,
                                        "ticks",
                                        "Channel interval between committed pulses."),
                                optionalRanged(
                                        "channel.maximum_pulses",
                                        INTEGER,
                                        1,
                                        100,
                                        "pulses",
                                        "Maximum pulses before a channel ends."),
                                optionalRanged(
                                        "channel.mana_per_pulse",
                                        INTEGER,
                                        0,
                                        null,
                                        "mana",
                                        "Mana paid by each emitted channel pulse."),
                                optionalRanged(
                                        "channel.range",
                                        NUMBER,
                                        0.000001,
                                        32,
                                        "blocks",
                                        "Maximum server-owned channel target range."),
                                optionalRanged(
                                        "channel.maximum_targets_per_pulse",
                                        INTEGER,
                                        1,
                                        1,
                                        "targets",
                                        "Maximum targets hit by one channel pulse."),
                                optionalRanged(
                                        "zone.placement_range",
                                        NUMBER,
                                        0.000001,
                                        32,
                                        "blocks",
                                        "Maximum crosshair placement range."),
                                optionalRanged(
                                        "zone.radius",
                                        NUMBER,
                                        0.000001,
                                        12,
                                        "blocks",
                                        "Zone target radius."),
                                optionalRanged(
                                        "zone.duration_ticks",
                                        INTEGER,
                                        1,
                                        400,
                                        "ticks",
                                        "Bounded committed zone lifetime."),
                                optionalRanged(
                                        "zone.pulse_interval_ticks",
                                        INTEGER,
                                        1,
                                        400,
                                        "ticks",
                                        "Zone interval between target pulses."),
                                optionalRanged(
                                        "zone.maximum_targets_per_pulse",
                                        INTEGER,
                                        1,
                                        16,
                                        "targets",
                                        "Maximum zone targets per pulse."),
                                optionalRanged(
                                        "imbuement.duration_ticks",
                                        INTEGER,
                                        1,
                                        1200,
                                        "ticks",
                                        "Encounter-scoped weapon Imbuement lifetime."),
                                optionalRanged(
                                        "imbuement.maximum_charges",
                                        INTEGER,
                                        1,
                                        32,
                                        "hits",
                                        "Maximum physical hits empowered by the Imbuement."),
                                optionalRanged(
                                        "imbuement.power_coefficient",
                                        NUMBER,
                                        0.000001,
                                        2,
                                        "coefficient",
                                        "Weapon-power coefficient for each empowered hit."),
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
                                allowed(
                                        "ailment_type",
                                        STRING,
                                        Set.of(
                                                "BURN",
                                                "BLEED",
                                                "POISON",
                                                "FROST",
                                                "SHOCK",
                                                "CORRUPTION"),
                                        "",
                                        "Core ailment identity."),
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
                                        "buildup_decay_per_tick",
                                        NUMBER,
                                        0,
                                        null,
                                        "buildup_per_tick",
                                        "Buildup lost after the decay delay."),
                                ranged(
                                        "active_duration_ticks",
                                        INTEGER,
                                        1,
                                        null,
                                        "ticks",
                                        "Active-effect duration."),
                                allowed(
                                        "reapplication",
                                        STRING,
                                        Set.of("REFRESH", "INTENSIFY", "REJECT"),
                                        "",
                                        "Active threshold reapplication behavior."),
                                ranged(
                                        "maximum_tier",
                                        INTEGER,
                                        1,
                                        null,
                                        "tier",
                                        "Maximum active tier."),
                                required("resistance_channel", STRING, "", "Resistance channel."),
                                array(
                                        "cleanse_tags",
                                        true,
                                        1,
                                        null,
                                        "At least one authored cleanse tag."),
                                allowed(
                                        "persistence",
                                        STRING,
                                        Set.of("CLEAR_ON_DEATH", "PERSIST_THROUGH_DEATH"),
                                        "",
                                        "Death/reset persistence rule."),
                                ranged(
                                        "profiles.pve_multiplier",
                                        NUMBER,
                                        0.000001,
                                        null,
                                        "multiplier",
                                        "PvE active-effect profile."),
                                ranged(
                                        "profiles.pvp_multiplier",
                                        NUMBER,
                                        0.000001,
                                        null,
                                        "multiplier",
                                        "PvP active-effect profile."),
                                required(
                                        "presentation.visual_cue",
                                        STRING,
                                        "",
                                        "Stable visual cue identity."),
                                required(
                                        "presentation.audio_cue",
                                        STRING,
                                        "",
                                        "Stable audio cue identity.")),
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
                                optionalAllowed(
                                        "sharing",
                                        STRING,
                                        Set.of("PERSONAL", "SHARED"),
                                        "",
                                        "Persistent extraction-state sharing mode."),
                                optional("discipline", STRING, "", "Lifeskill discipline suffix."),
                                optionalRanged(
                                        "maximum_charges",
                                        INTEGER,
                                        1,
                                        null,
                                        "charges",
                                        "Maximum extraction charges."),
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
                                optionalRanged(
                                        "reservation_timeout_seconds",
                                        INTEGER,
                                        1,
                                        null,
                                        "seconds",
                                        "Wall-clock reservation timeout."),
                                ranged(
                                        "recovery_seconds",
                                        INTEGER,
                                        0,
                                        null,
                                        "seconds",
                                        "Wall-clock recovery duration."),
                                optionalRanged(
                                        "durability_cost",
                                        INTEGER,
                                        1,
                                        null,
                                        "durability",
                                        "Exact tool durability cost."),
                                array(
                                        "required_tool_tags",
                                        false,
                                        1,
                                        null,
                                        "Required authoritative tool tags."),
                                optional(
                                        "tool_definition",
                                        STRING,
                                        "",
                                        "Durable tool definition used by the Paper Node Lab."),
                                optionalRanged(
                                        "rank_evidence",
                                        NUMBER,
                                        0.000001,
                                        null,
                                        "evidence",
                                        "Committed rank evidence per harvest."),
                                array(
                                        "rank_thresholds",
                                        false,
                                        30,
                                        30,
                                        "Thirty authored cumulative rank thresholds."),
                                optionalRanged(
                                        "rank_thresholds.*",
                                        NUMBER,
                                        0,
                                        null,
                                        "evidence",
                                        "Cumulative evidence threshold."),
                                array("base_yields", true, 1, null, "Base item yields."),
                                optionalRanged(
                                        "base_yields.*.quantity",
                                        INTEGER,
                                        1,
                                        null,
                                        "items",
                                        "Committed base-yield quantity.")),
                        List.of(
                                to(DefinitionType.ITEM, "tool_definition"),
                                to(DefinitionType.ITEM, "base_yields", "*", "item"),
                                to(DefinitionType.ITEM, "byproducts", "*", "item"))));
        schemas.put(
                DefinitionType.ENCOUNTER,
                definition(
                        DefinitionType.ENCOUNTER,
                        List.of(
                                array(
                                        "reward_pool.entries",
                                        true,
                                        1,
                                        16,
                                        "Weighted personal reward entries."),
                                ranged(
                                        "reward_pool.entries.*.weight",
                                        INTEGER,
                                        1,
                                        1_000_000,
                                        "weight",
                                        "Relative reward roll weight."),
                                ranged(
                                        "reward_pool.entries.*.min_quantity",
                                        INTEGER,
                                        1,
                                        4096,
                                        "items",
                                        "Minimum stackable reward quantity."),
                                ranged(
                                        "reward_pool.entries.*.max_quantity",
                                        INTEGER,
                                        1,
                                        4096,
                                        "items",
                                        "Maximum stackable reward quantity."),
                                ranged(
                                        "eligibility.damage_and_posture_floor",
                                        INTEGER,
                                        1,
                                        null,
                                        "points",
                                        "Meaningful damage/posture contribution floor."),
                                ranged(
                                        "eligibility.guard_and_control_floor",
                                        INTEGER,
                                        1,
                                        null,
                                        "points",
                                        "Meaningful guard/control contribution floor."),
                                ranged(
                                        "eligibility.healing_and_support_floor",
                                        INTEGER,
                                        1,
                                        null,
                                        "points",
                                        "Meaningful healing/support contribution floor."),
                                ranged(
                                        "eligibility.objective_action_floor",
                                        INTEGER,
                                        1,
                                        null,
                                        "actions",
                                        "Meaningful objective contribution floor."),
                                ranged(
                                        "eligibility.maximum_idle_ticks",
                                        INTEGER,
                                        1,
                                        null,
                                        "ticks",
                                        "Maximum inactivity before victory."),
                                ranged(
                                        "eligibility.late_join_hp_ratio",
                                        NUMBER,
                                        0,
                                        1,
                                        "ratio",
                                        "Boss HP cutoff for late join eligibility.")),
                        List.of(to(DefinitionType.ITEM, "reward_pool", "entries", "*", "item"))));
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

    private static FieldRule optionalAllowed(
            String path, FieldValueType type, Set<String> values, String unit, String description) {
        return rule(path, type, false, null, null, null, null, values, unit, description);
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
