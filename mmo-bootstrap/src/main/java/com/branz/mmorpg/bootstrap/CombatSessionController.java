package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.action.ActionPhase;
import com.branz.mmorpg.combat.action.ActionTimeline;
import com.branz.mmorpg.combat.action.ActionTimelineErrorCode;
import com.branz.mmorpg.combat.action.ActionTraceEvent;
import com.branz.mmorpg.combat.action.ActionTraceEventType;
import com.branz.mmorpg.combat.action.CombatResources;
import com.branz.mmorpg.combat.action.ResourceCommitState;
import com.branz.mmorpg.combat.bow.BowDrawEngine;
import com.branz.mmorpg.combat.bow.BowDrawPhase;
import com.branz.mmorpg.combat.bow.BowDrawProfile;
import com.branz.mmorpg.combat.bow.BowDrawRuntime;
import com.branz.mmorpg.combat.bow.BowReleaseOutcome;
import com.branz.mmorpg.combat.bow.BowReleaseResolution;
import com.branz.mmorpg.combat.bow.BowShotCharge;
import com.branz.mmorpg.combat.bow.BowTickResolution;
import com.branz.mmorpg.combat.cc.CcApplication;
import com.branz.mmorpg.combat.cc.CcEngine;
import com.branz.mmorpg.combat.cc.CcRequest;
import com.branz.mmorpg.combat.cc.CcRuntime;
import com.branz.mmorpg.combat.cc.CcSeverity;
import com.branz.mmorpg.combat.crossbow.CrossbowEngine;
import com.branz.mmorpg.combat.crossbow.CrossbowFireResolution;
import com.branz.mmorpg.combat.crossbow.CrossbowPersistentState;
import com.branz.mmorpg.combat.crossbow.CrossbowPhase;
import com.branz.mmorpg.combat.crossbow.CrossbowReloadProfile;
import com.branz.mmorpg.combat.crossbow.CrossbowRuntime;
import com.branz.mmorpg.combat.crossbow.CrossbowTickOutcome;
import com.branz.mmorpg.combat.crossbow.CrossbowTickResolution;
import com.branz.mmorpg.combat.damage.ConditionalAdvantage;
import com.branz.mmorpg.combat.damage.PhysicalDamageBreakdown;
import com.branz.mmorpg.combat.damage.PhysicalDamageRequest;
import com.branz.mmorpg.combat.damage.PhysicalDamageResolver;
import com.branz.mmorpg.combat.dodge.DodgeEngine;
import com.branz.mmorpg.combat.dodge.DodgeErrorCode;
import com.branz.mmorpg.combat.dodge.DodgePhase;
import com.branz.mmorpg.combat.dodge.DodgeProfile;
import com.branz.mmorpg.combat.dodge.DodgeRuntime;
import com.branz.mmorpg.combat.engagement.EngagementRuntime;
import com.branz.mmorpg.combat.engagement.EngagementTickContext;
import com.branz.mmorpg.combat.engagement.EngagementTracker;
import com.branz.mmorpg.combat.guard.CombatDefenseOutcome;
import com.branz.mmorpg.combat.guard.CombatDefenseResolution;
import com.branz.mmorpg.combat.guard.CombatDefenseResolver;
import com.branz.mmorpg.combat.guard.GuardEngine;
import com.branz.mmorpg.combat.guard.GuardErrorCode;
import com.branz.mmorpg.combat.guard.GuardHitRequest;
import com.branz.mmorpg.combat.guard.GuardProfile;
import com.branz.mmorpg.combat.guard.GuardRuntime;
import com.branz.mmorpg.combat.health.CombatHealthEngine;
import com.branz.mmorpg.combat.health.CombatHealthProfile;
import com.branz.mmorpg.combat.health.CombatHealthResolution;
import com.branz.mmorpg.combat.health.CombatHealthRuntime;
import com.branz.mmorpg.combat.hitbox.ArcDebugGeometry;
import com.branz.mmorpg.combat.hitbox.ArcHitboxQuery;
import com.branz.mmorpg.combat.hitbox.CombatVector;
import com.branz.mmorpg.combat.hitbox.ResolvedTarget;
import com.branz.mmorpg.combat.hitbox.SweptArcHitboxQuery;
import com.branz.mmorpg.combat.hitbox.SweptArcHitboxResolver;
import com.branz.mmorpg.combat.hitbox.SweptArcResolution;
import com.branz.mmorpg.combat.hitbox.TargetCollider;
import com.branz.mmorpg.combat.input.ClientAction;
import com.branz.mmorpg.combat.input.CombatInputPolicy;
import com.branz.mmorpg.combat.input.CombatInputRequest;
import com.branz.mmorpg.combat.input.DirectionSnapshot;
import com.branz.mmorpg.combat.input.HostileAutoDrawPolicy;
import com.branz.mmorpg.combat.input.InputBufferClearReason;
import com.branz.mmorpg.combat.input.InputDeduplicationKey;
import com.branz.mmorpg.combat.input.InputObservation;
import com.branz.mmorpg.combat.input.InputPolicyContext;
import com.branz.mmorpg.combat.input.InputRejectionCode;
import com.branz.mmorpg.combat.input.InputRouteOutcome;
import com.branz.mmorpg.combat.input.InputRouter;
import com.branz.mmorpg.combat.input.InputRoutingContext;
import com.branz.mmorpg.combat.input.PrimaryAttackIngressDecision;
import com.branz.mmorpg.combat.input.PrimaryAttackIngressPolicy;
import com.branz.mmorpg.combat.input.PrimaryAttackInputCoordinator;
import com.branz.mmorpg.combat.input.SemanticInput;
import com.branz.mmorpg.combat.input.SneakPressDecision;
import com.branz.mmorpg.combat.input.SneakPressResolver;
import com.branz.mmorpg.combat.input.SneakPressWindow;
import com.branz.mmorpg.combat.move.MoveDefinition;
import com.branz.mmorpg.combat.move.MoveEngine;
import com.branz.mmorpg.combat.poise.PoiseEngine;
import com.branz.mmorpg.combat.poise.PoiseProfile;
import com.branz.mmorpg.combat.poise.PoiseResolution;
import com.branz.mmorpg.combat.poise.PoiseRuntime;
import com.branz.mmorpg.combat.posture.PostureEngine;
import com.branz.mmorpg.combat.posture.PosturePhase;
import com.branz.mmorpg.combat.posture.PostureProfile;
import com.branz.mmorpg.combat.posture.PostureResolution;
import com.branz.mmorpg.combat.posture.PostureRuntime;
import com.branz.mmorpg.combat.projectile.ProjectileEngine;
import com.branz.mmorpg.combat.projectile.ProjectileIdentity;
import com.branz.mmorpg.combat.projectile.ProjectileProfile;
import com.branz.mmorpg.combat.projectile.ProjectileRuntime;
import com.branz.mmorpg.combat.projectile.ProjectileTickQuery;
import com.branz.mmorpg.combat.projectile.ProjectileTickResolution;
import com.branz.mmorpg.combat.resource.FlaskRestoration;
import com.branz.mmorpg.combat.state.ActionState;
import com.branz.mmorpg.combat.state.EngagementState;
import com.branz.mmorpg.combat.state.UiState;
import com.branz.mmorpg.combat.state.WeaponState;
import com.branz.mmorpg.combat.trace.ActionSimulationCommand;
import com.branz.mmorpg.combat.trace.ActionTimelineSimulator;
import com.branz.mmorpg.combat.trace.CombatSimulationErrorCode;
import com.branz.mmorpg.combat.trace.CombatTrace;
import com.branz.mmorpg.combat.weapon.SelectedHotbarSlot;
import com.branz.mmorpg.combat.weapon.SelectedSlotKind;
import com.branz.mmorpg.combat.weapon.WeaponTransitionErrorCode;
import com.branz.mmorpg.combat.weapon.WeaponTransitionMachine;
import com.branz.mmorpg.combat.weapon.WeaponTransitionSnapshot;
import com.branz.mmorpg.items.definition.AmmoFamily;
import com.branz.mmorpg.items.definition.BowWeaponProfile;
import com.branz.mmorpg.items.definition.CatalystProfile;
import com.branz.mmorpg.items.definition.CrossbowWeaponProfile;
import com.branz.mmorpg.items.definition.GuardCombatProfile;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.items.definition.QuiverProfile;
import com.branz.mmorpg.items.definition.WeaponCombatProfile;
import com.branz.mmorpg.items.definition.WeaponLoadoutErrorCode;
import com.branz.mmorpg.items.definition.WeaponLoadoutPolicy;
import com.branz.mmorpg.items.definition.WeaponLoadoutResolution;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.magic.cast.ChannelPulseResolution;
import com.branz.mmorpg.magic.cast.SpellCastEngine;
import com.branz.mmorpg.magic.cast.SpellCastErrorCode;
import com.branz.mmorpg.magic.cast.SpellCastPhase;
import com.branz.mmorpg.magic.cast.SpellCastRuntime;
import com.branz.mmorpg.magic.damage.ArcaneDamageBreakdown;
import com.branz.mmorpg.magic.damage.ArcaneDamageRequest;
import com.branz.mmorpg.magic.damage.ArcaneDamageResolver;
import com.branz.mmorpg.magic.definition.ArcaneSchool;
import com.branz.mmorpg.magic.definition.SpellDefinition;
import com.branz.mmorpg.magic.definition.SpellDeliveryType;
import com.branz.mmorpg.magic.definition.SpellEngine;
import com.branz.mmorpg.magic.effect.ImbuementHitResolution;
import com.branz.mmorpg.magic.effect.RunicImbuementEngine;
import com.branz.mmorpg.magic.effect.RunicImbuementRuntime;
import com.branz.mmorpg.magic.effect.ZoneEngine;
import com.branz.mmorpg.magic.effect.ZoneRuntime;
import com.branz.mmorpg.magic.effect.ZoneTickResolution;
import com.branz.mmorpg.persistence.progression.ProgressionEvidenceExecution;
import com.branz.mmorpg.progression.build.BuildEngine;
import com.branz.mmorpg.progression.build.BuildErrorCode;
import com.branz.mmorpg.progression.build.BuildResolution;
import com.branz.mmorpg.progression.build.MovesetBranch;
import com.branz.mmorpg.progression.evidence.BodyConditioningAxis;
import com.branz.mmorpg.progression.evidence.CombatEvidenceAccumulator;
import com.branz.mmorpg.progression.evidence.EncounterOutcome;
import com.branz.mmorpg.progression.evidence.EvidenceCandidate;
import com.branz.mmorpg.progression.evidence.EvidenceTargetKind;
import com.branz.mmorpg.progression.knowledge.KnowledgeKey;
import com.branz.mmorpg.social.pvp.PvpCombatProfile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

/** Main-thread Paper adapter for the deterministic training-move combat kernel. */
final class CombatSessionController implements Listener {
    private static final DefinitionId TRAINING_MOVE =
            DefinitionId.of("move.training_blade.primary_1");
    private static final DefinitionId TRAINING_GREATSWORD_MOVE =
            DefinitionId.of("move.training_greatsword.committed_cleave");
    private static final DefinitionId TRAINING_GREATSWORD_ITEM =
            DefinitionId.of("weapon.training_greatsword");
    private static final DefinitionId TRAINING_SWORD_SHIELD_MOVE =
            DefinitionId.of("move.training_sword_shield.primary_1");
    private static final DefinitionId TRAINING_SWORD_SHIELD_ITEM =
            DefinitionId.of("weapon.training_sword");
    private static final DefinitionId TRAINING_BOW_MOVE =
            DefinitionId.of("move.training_bow.quick_shot");
    private static final DefinitionId TRAINING_BOW_ITEM = DefinitionId.of("weapon.training_bow");
    private static final DefinitionId TRAINING_CROSSBOW_MOVE =
            DefinitionId.of("move.training_crossbow.shot");
    private static final DefinitionId TRAINING_CROSSBOW_ITEM =
            DefinitionId.of("weapon.training_crossbow");
    private static final DefinitionId TRAINING_STAFF_MOVE =
            DefinitionId.of("move.training_staff.primary_1");
    private static final DefinitionId TRAINING_STAFF_ITEM =
            DefinitionId.of("weapon.training_staff");
    private static final DefinitionId EMBER_FIRE_LANCE = DefinitionId.of("spell.ember.fire_lance");
    private static final DefinitionId EMBER_CINDER_SNAP =
            DefinitionId.of("spell.ember.cinder_snap");
    private static final DefinitionId EMBER_SCORCHING_GROUND =
            DefinitionId.of("spell.ember.scorching_ground");
    private static final DefinitionId EMBER_FLAME_TORRENT =
            DefinitionId.of("spell.ember.flame_torrent");
    private static final DefinitionId RUNIC_EMBER_EDGE = DefinitionId.of("spell.runic.ember_edge");
    private static final List<DefinitionId> TRAINING_STAFF_SPELL_ORDER =
            List.of(
                    EMBER_CINDER_SNAP,
                    EMBER_FIRE_LANCE,
                    EMBER_SCORCHING_GROUND,
                    EMBER_FLAME_TORRENT,
                    RUNIC_EMBER_EDGE);
    private static final int MAXIMUM_ACTIVE_ZONES_PER_CASTER = 4;
    private static final int MAXIMUM_PROGRESSION_EVIDENCE_BATCH = 256;
    private static final long PROGRESSION_RETRY_TICKS = 10L;
    private static final String TRAINING_DUMMY_TAG = "branzmmo.training_dummy";
    private static final String SELF_CREATED_LOOP_TAG = "branzmmo.self_created_loop";
    private static final String ZERO_RISK_TAG = "branzmmo.zero_risk";

    private final JavaPlugin plugin;
    private final CharacterSessionController characters;
    private final MoveEngine moves;
    private final SpellEngine spells;
    private final ItemEngine items;
    private final BuildEngine builds;
    private final String contentVersion;
    private final MoveDefinition trainingMove;
    private final MoveDefinition trainingGreatswordMove;
    private final MoveDefinition trainingSwordShieldMove;
    private final MoveDefinition trainingBowMove;
    private final MoveDefinition trainingCrossbowMove;
    private final MoveDefinition trainingStaffMove;
    private final SpellDefinition emberFireLance;
    private final Map<DefinitionId, SpellDefinition> trainingStaffSpells;
    private final CatalystProfile trainingStaffCatalyst;
    private final int trainingStaffMaximumDurability;
    private final DefinitionId trainingBowAmmo;
    private final AmmoFamily trainingBowAmmoFamily;
    private final DefinitionId trainingCrossbowAmmo;
    private final AmmoFamily trainingCrossbowAmmoFamily;
    private final double trainingWeaponPower;
    private final double trainingGreatswordPower;
    private final double trainingSwordShieldPower;
    private final double trainingBowPower;
    private final double trainingCrossbowPower;
    private final double trainingStaffPower;
    private final BowDrawEngine bowDraws;
    private final CrossbowEngine crossbows;
    private final SpellCastEngine spellCasts = new SpellCastEngine();
    private final ZoneEngine zones = new ZoneEngine();
    private final RunicImbuementEngine imbuements = new RunicImbuementEngine();
    private final ArcaneDamageResolver arcaneDamage = new ArcaneDamageResolver();
    private final int maximumActiveProjectilesPerCaster;
    private final WeaponTransitionMachine weapons;
    private final CombatInputPolicy inputPolicy = new CombatInputPolicy();
    private final SweptArcHitboxResolver hitboxes = new SweptArcHitboxResolver();
    private final ArcDebugGeometry arcDebugGeometry = new ArcDebugGeometry();
    private final PhysicalDamageResolver damage = new PhysicalDamageResolver();
    private final ProjectileEngine projectiles = new ProjectileEngine();
    private final EngagementTracker engagement;
    private final DodgeProfile dodgeProfile;
    private final DodgeEngine dodges = new DodgeEngine();
    private final SneakPressResolver sneakPresses = new SneakPressResolver();
    private final GuardEngine defaultGuards;
    private final double trainingIncomingGuardPressure;
    private final double trainingIncomingHealthDamage;
    private final double environmentalHealthScale;
    private final double trainingIncomingPoiseDamage;
    private final CcSeverity trainingIncomingCcSeverity;
    private final int trainingIncomingCcTicks;
    private final double trainingPerfectGuardPostureDamage;
    private final PostureEngine postures = new PostureEngine(PostureProfile.trainingNormal());
    private final PoiseEngine poise = new PoiseEngine(PoiseProfile.trainingPlayer());
    private final CcEngine crowdControl = new CcEngine();
    private final CombatHealthEngine playerHealth =
            new CombatHealthEngine(CombatHealthProfile.trainingPlayer());
    private final CombatHealthEngine enemyHealth =
            new CombatHealthEngine(CombatHealthProfile.trainingEnemy());
    private final Map<UUID, LiveSession> sessions = new HashMap<>();
    private final Map<UUID, CombatHealthRuntime> trainingTargetHealth = new HashMap<>();
    private final Map<UUID, PostureRuntime> trainingTargetPosture = new HashMap<>();
    private final Map<UUID, LiveProjectile> activeProjectiles = new HashMap<>();
    private final Map<UUID, LiveSpellZone> activeZones = new HashMap<>();
    private final Map<UUID, java.util.Set<UUID>> debugViewers = new HashMap<>();
    private SuccessfulCombatActionObserver successfulActionObserver =
            SuccessfulCombatActionObserver.NONE;
    private BiConsumer<Player, String> consumableInterruptObserver = (player, reason) -> {};
    private LethalDamageObserver lethalDamageObserver = LethalDamageObserver.NONE;
    private PvpCombatPolicy pvpCombatPolicy = PvpCombatPolicy.NONE;
    private Predicate<Player> damageImmunityObserver = player -> false;
    private BiConsumer<Player, String> hostileActionObserver = (player, reason) -> {};
    private int tickTaskId = -1;

    CombatSessionController(
            JavaPlugin plugin,
            CharacterSessionController characters,
            ItemEngine items,
            MoveEngine moves,
            SpellEngine spells,
            BuildEngine builds,
            String contentVersion,
            double trainingWeaponPower,
            int maximumActiveProjectilesPerCaster,
            int drawTicks,
            int sheatheTicks,
            int engagementExitTicks,
            DodgeProfile dodgeProfile,
            GuardEngine guards,
            double trainingIncomingGuardPressure,
            double trainingIncomingHealthDamage,
            double environmentalHealthScale,
            double trainingIncomingPoiseDamage,
            CcSeverity trainingIncomingCcSeverity,
            int trainingIncomingCcTicks,
            double trainingPerfectGuardPostureDamage) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.items = Objects.requireNonNull(items, "items");
        this.moves = Objects.requireNonNull(moves, "moves");
        this.spells = Objects.requireNonNull(spells, "spells");
        this.builds = Objects.requireNonNull(builds, "builds");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        if (contentVersion.isBlank()) {
            throw new IllegalArgumentException("contentVersion must not be blank");
        }
        trainingMove =
                moves.find(TRAINING_MOVE)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "active content is missing " + TRAINING_MOVE));
        trainingGreatswordMove = requirePrimaryMove(TRAINING_GREATSWORD_MOVE, "GREATSWORD");
        trainingSwordShieldMove = requirePrimaryMove(TRAINING_SWORD_SHIELD_MOVE, "SWORD_SHIELD");
        WeaponCombatProfile greatswordWeapon =
                requireWeaponProfile(TRAINING_GREATSWORD_ITEM, "GREATSWORD");
        WeaponCombatProfile swordShieldWeapon =
                requireWeaponProfile(TRAINING_SWORD_SHIELD_ITEM, "SWORD_SHIELD");
        trainingGreatswordPower = greatswordWeapon.power();
        trainingSwordShieldPower = swordShieldWeapon.power();
        trainingBowMove =
                moves.find(TRAINING_BOW_MOVE)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "active content is missing " + TRAINING_BOW_MOVE));
        if (!trainingBowMove.family().equals("BOW")
                || trainingBowMove.input().action() != SemanticInput.SECONDARY
                || trainingBowMove.hitboxes().size() != 1
                || trainingBowMove.hitboxes().getFirst().projectile().isEmpty()) {
            throw new IllegalArgumentException(
                    "training Bow move requires one SECONDARY PROJECTILE hitbox");
        }
        trainingBowAmmo =
                trainingBowMove.hitboxes().getFirst().projectile().orElseThrow().ammoCategory();
        trainingBowAmmoFamily =
                items.find(trainingBowAmmo)
                        .flatMap(ItemDefinition::ammoProfile)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "training Bow projectile requires an authored ammo profile"))
                        .family();
        trainingCrossbowMove =
                moves.find(TRAINING_CROSSBOW_MOVE)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "active content is missing "
                                                        + TRAINING_CROSSBOW_MOVE));
        if (!trainingCrossbowMove.family().equals("CROSSBOW")
                || trainingCrossbowMove.input().action() != SemanticInput.SECONDARY
                || trainingCrossbowMove.hitboxes().size() != 1
                || trainingCrossbowMove.hitboxes().getFirst().projectile().isEmpty()) {
            throw new IllegalArgumentException(
                    "training Crossbow move requires one SECONDARY PROJECTILE hitbox");
        }
        trainingCrossbowAmmo =
                trainingCrossbowMove
                        .hitboxes()
                        .getFirst()
                        .projectile()
                        .orElseThrow()
                        .ammoCategory();
        trainingCrossbowAmmoFamily =
                items.find(trainingCrossbowAmmo)
                        .flatMap(ItemDefinition::ammoProfile)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "training Crossbow projectile requires an authored ammo profile"))
                        .family();
        if (!Double.isFinite(trainingWeaponPower) || trainingWeaponPower <= 0) {
            throw new IllegalArgumentException("trainingWeaponPower must be positive");
        }
        this.trainingWeaponPower = trainingWeaponPower;
        WeaponCombatProfile bowWeapon =
                items.find(TRAINING_BOW_ITEM)
                        .flatMap(ItemDefinition::weaponProfile)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "active content is missing Bow weapon profile"));
        BowWeaponProfile bow =
                bowWeapon
                        .bowProfile()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "training Bow requires Bow handling profile"));
        trainingBowPower = bowWeapon.power();
        bowDraws =
                new BowDrawEngine(
                        new BowDrawProfile(
                                bow.minimumDrawTicks(),
                                bow.fullDrawTicks(),
                                bow.freeFullDrawHoldTicks(),
                                bow.strainStaminaPerSecond(),
                                bow.minimumVelocityMultiplier(),
                                bow.minimumPostureMultiplier(),
                                bow.maximumPenetrationPercentage()));
        WeaponCombatProfile crossbowWeapon =
                items.find(TRAINING_CROSSBOW_ITEM)
                        .flatMap(ItemDefinition::weaponProfile)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "active content is missing Crossbow weapon profile"));
        CrossbowWeaponProfile crossbow =
                crossbowWeapon
                        .crossbowProfile()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "training Crossbow requires reload checkpoint timings"));
        trainingCrossbowPower = crossbowWeapon.power();
        crossbows =
                new CrossbowEngine(
                        new CrossbowReloadProfile(
                                crossbow.boltPlacementTicks(), crossbow.lockingTicks()));
        trainingStaffMove =
                moves.find(TRAINING_STAFF_MOVE)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "active content is missing "
                                                        + TRAINING_STAFF_MOVE));
        if (!trainingStaffMove.family().equals("STAFF")
                || trainingStaffMove.input().action() != SemanticInput.PRIMARY) {
            throw new IllegalArgumentException("training Staff move requires PRIMARY input");
        }
        ItemDefinition trainingStaffDefinition =
                items.find(TRAINING_STAFF_ITEM)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "active content is missing "
                                                        + TRAINING_STAFF_ITEM));
        WeaponCombatProfile staffWeapon =
                trainingStaffDefinition
                        .weaponProfile()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "training Staff requires weapon profile"));
        if (!staffWeapon.family().equals("STAFF")) {
            throw new IllegalArgumentException("training Staff requires STAFF family");
        }
        trainingStaffPower = staffWeapon.power();
        trainingStaffCatalyst =
                trainingStaffDefinition
                        .catalystProfile()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "training Staff requires catalyst profile"));
        trainingStaffMaximumDurability =
                trainingStaffDefinition
                        .baseMaxDurability()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "training Staff requires base durability"));
        emberFireLance =
                spells.find(EMBER_FIRE_LANCE)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "active content is missing " + EMBER_FIRE_LANCE));
        if (emberFireLance.deliveryType() != SpellDeliveryType.PROJECTILE
                || !trainingStaffCatalyst
                        .tags()
                        .containsAll(emberFireLance.requirements().catalystTags())) {
            throw new IllegalArgumentException(
                    "Ember Fire Lance requires a compatible Staff projectile catalyst");
        }
        LinkedHashMap<DefinitionId, SpellDefinition> staffSpells = new LinkedHashMap<>();
        for (DefinitionId spellId : TRAINING_STAFF_SPELL_ORDER) {
            SpellDefinition spell =
                    spells.find(spellId)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "active content is missing " + spellId));
            if (!trainingStaffCatalyst.tags().containsAll(spell.requirements().catalystTags())) {
                throw new IllegalArgumentException(
                        spellId + " requires an incompatible Staff catalyst");
            }
            staffSpells.put(spellId, spell);
        }
        trainingStaffSpells = Map.copyOf(staffSpells);
        if (maximumActiveProjectilesPerCaster < 1 || maximumActiveProjectilesPerCaster > 128) {
            throw new IllegalArgumentException(
                    "maximumActiveProjectilesPerCaster must be between 1 and 128");
        }
        this.maximumActiveProjectilesPerCaster = maximumActiveProjectilesPerCaster;
        weapons = new WeaponTransitionMachine(drawTicks, sheatheTicks);
        engagement = new EngagementTracker(engagementExitTicks);
        this.dodgeProfile = Objects.requireNonNull(dodgeProfile, "dodgeProfile");
        defaultGuards = Objects.requireNonNull(guards, "guards");
        if (!Double.isFinite(trainingIncomingGuardPressure) || trainingIncomingGuardPressure <= 0) {
            throw new IllegalArgumentException("trainingIncomingGuardPressure must be positive");
        }
        this.trainingIncomingGuardPressure = trainingIncomingGuardPressure;
        if (!Double.isFinite(trainingIncomingHealthDamage) || trainingIncomingHealthDamage <= 0) {
            throw new IllegalArgumentException("trainingIncomingHealthDamage must be positive");
        }
        this.trainingIncomingHealthDamage = trainingIncomingHealthDamage;
        if (!Double.isFinite(environmentalHealthScale) || environmentalHealthScale <= 0) {
            throw new IllegalArgumentException("environmentalHealthScale must be positive");
        }
        this.environmentalHealthScale = environmentalHealthScale;
        if (!Double.isFinite(trainingIncomingPoiseDamage) || trainingIncomingPoiseDamage <= 0) {
            throw new IllegalArgumentException("trainingIncomingPoiseDamage must be positive");
        }
        this.trainingIncomingPoiseDamage = trainingIncomingPoiseDamage;
        this.trainingIncomingCcSeverity =
                Objects.requireNonNull(trainingIncomingCcSeverity, "trainingIncomingCcSeverity");
        if (trainingIncomingCcTicks < 1) {
            throw new IllegalArgumentException("trainingIncomingCcTicks must be positive");
        }
        this.trainingIncomingCcTicks = trainingIncomingCcTicks;
        if (!Double.isFinite(trainingPerfectGuardPostureDamage)
                || trainingPerfectGuardPostureDamage <= 0) {
            throw new IllegalArgumentException(
                    "trainingPerfectGuardPostureDamage must be positive");
        }
        this.trainingPerfectGuardPostureDamage = trainingPerfectGuardPostureDamage;
    }

    void setSuccessfulActionObserver(SuccessfulCombatActionObserver observer) {
        if (successfulActionObserver != SuccessfulCombatActionObserver.NONE) {
            throw new IllegalStateException("successful combat action observer is already set");
        }
        successfulActionObserver = Objects.requireNonNull(observer, "observer");
    }

    private MoveDefinition requirePrimaryMove(DefinitionId id, String family) {
        MoveDefinition move =
                moves.find(id)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "active content is missing " + id));
        if (!move.family().equals(family) || move.input().action() != SemanticInput.PRIMARY) {
            throw new IllegalArgumentException(id + " must be a PRIMARY move for " + family);
        }
        return move;
    }

    private WeaponCombatProfile requireWeaponProfile(DefinitionId id, String family) {
        WeaponCombatProfile profile =
                items.find(id)
                        .flatMap(ItemDefinition::weaponProfile)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "active content is missing weapon profile " + id));
        if (!profile.family().equals(family)) {
            throw new IllegalArgumentException(id + " must use weapon family " + family);
        }
        return profile;
    }

    void start() {
        tickTaskId =
                plugin.getServer()
                        .getScheduler()
                        .scheduleSyncRepeatingTask(plugin, this::tickAll, 1L, 1L);
    }

    void shutdown() {
        if (tickTaskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(tickTaskId);
            tickTaskId = -1;
        }
        sessions.clear();
        activeProjectiles.clear();
        activeZones.clear();
        trainingTargetHealth.clear();
        trainingTargetPosture.clear();
        debugViewers.clear();
    }

    void onCharacterReady(Player player) {
        long tick = plugin.getServer().getCurrentTick();
        GuardEngine guardEngine = guardEngineFor(player).orElse(defaultGuards);
        LiveSession session =
                new LiveSession(
                        EngagementRuntime.initial(tick),
                        GuardRuntime.initial(guardEngine.profile(), tick),
                        guardEngine,
                        PoiseRuntime.initial(tick),
                        CcRuntime.initial(tick),
                        CombatHealthRuntime.full(playerHealth.profile(), tick));
        session.guardAuthorityKey = guardAuthorityKey(player);
        sessions.put(player.getUniqueId(), session);
        updateHealthPresentation(player, session);
        select(session, selectedSlot(player, player.getInventory().getHeldItemSlot()));
        restoreEquippedCrossbow(player, session, tick);
    }

    void onEquipmentChanged(Player player) {
        LiveSession session = sessions.get(Objects.requireNonNull(player, "player").getUniqueId());
        if (session == null || !characters.ready(player)) {
            return;
        }
        cancelAction(session, "EQUIPMENT_CHANGE");
        cancelBow(session, "EQUIPMENT_CHANGE");
        cancelCrossbow(session, "EQUIPMENT_CHANGE");
        cancelSpell(session, "EQUIPMENT_CHANGE");
        session.imbuement = null;
        releaseGuard(session);
        session.input.clearBuffer(InputBufferClearReason.WEAPON_SWAP);
        session.weapon = weapons.resetTransient();
        select(session, selectedSlot(player, player.getInventory().getHeldItemSlot()));
        restoreEquippedCrossbow(player, session, plugin.getServer().getCurrentTick());
    }

    Optional<CombatSessionStatus> status(Player player) {
        LiveSession session = sessions.get(Objects.requireNonNull(player, "player").getUniqueId());
        if (session == null) {
            return Optional.empty();
        }
        CombatResources resources =
                session.timeline == null ? session.resources : session.timeline.resources();
        Optional<DefinitionId> selectedAmmo = characters.quiverPreparation(player).selectedAmmo();
        return Optional.of(
                new CombatSessionStatus(
                        session.engagement.state(),
                        engagement.remainingExitTicks(
                                session.engagement, plugin.getServer().getCurrentTick()),
                        session.weapon.state(),
                        Optional.ofNullable(session.timeline).map(ActionTimeline::phase),
                        Optional.ofNullable(session.bowDraw).map(BowDrawRuntime::phase),
                        (int)
                                Math.max(
                                        0,
                                        session.bowRecoveryUntilTick
                                                - plugin.getServer().getCurrentTick()),
                        Optional.ofNullable(session.crossbow).map(CrossbowRuntime::phase),
                        (int)
                                Math.max(
                                        0,
                                        session.crossbowRecoveryUntilTick
                                                - plugin.getServer().getCurrentTick()),
                        session.pendingCrossbowCommit != null,
                        Optional.ofNullable(session.spellCast).map(SpellCastRuntime::phase),
                        session.pendingSpellCommit != null,
                        Optional.ofNullable(session.selectedSpell),
                        activeZonesFor(player.getUniqueId()),
                        session.imbuement == null ? 0 : session.imbuement.remainingCharges(),
                        activeProjectilesFor(player.getUniqueId()),
                        session.pendingBowLaunch != null,
                        selectedAmmo,
                        selectedAmmo
                                .map(ammo -> characters.quiverAmmoQuantity(player, ammo))
                                .orElse(0L),
                        characters.quiverUsedCapacity(player),
                        characters
                                .equippedQuiverProfile(player)
                                .map(QuiverProfile::capacity)
                                .orElse(0),
                        (int)
                                Math.max(
                                        0,
                                        session.ammoSwitchHandlingUntilTick
                                                - plugin.getServer().getCurrentTick()),
                        dodgeProfile.load(),
                        Optional.ofNullable(session.dodge)
                                .map(
                                        runtime ->
                                                runtime.phaseAt(
                                                        plugin.getServer().getCurrentTick())),
                        session.guardEngine.phaseAt(
                                session.guard, plugin.getServer().getCurrentTick()),
                        session.guard.stability(),
                        session.crowdControl.active(),
                        session.crowdControl
                                .active()
                                .map(
                                        ignored ->
                                                (int)
                                                        Math.max(
                                                                0,
                                                                session.crowdControl
                                                                                .activeUntilTick()
                                                                        - plugin.getServer()
                                                                                .getCurrentTick()))
                                .orElse(0),
                        session.health.current(),
                        playerHealth.profile().maximum(),
                        session.health.dead(),
                        resources.stamina(),
                        resources.reservedStamina(),
                        resources.mana(),
                        resources.reservedMana(),
                        Optional.ofNullable(session.lastResolution)));
    }

    void setConsumableInterruptObserver(BiConsumer<Player, String> observer) {
        consumableInterruptObserver = Objects.requireNonNull(observer, "observer");
    }

    void setLethalDamageObserver(LethalDamageObserver observer) {
        lethalDamageObserver = Objects.requireNonNull(observer, "observer");
    }

    void setPvpCombatPolicy(PvpCombatPolicy policy) {
        pvpCombatPolicy = Objects.requireNonNull(policy, "policy");
    }

    void setDamageImmunityObserver(Predicate<Player> observer) {
        damageImmunityObserver = Objects.requireNonNull(observer, "observer");
    }

    void setHostileActionObserver(BiConsumer<Player, String> observer) {
        hostileActionObserver = Objects.requireNonNull(observer, "observer");
    }

    boolean forceLethalDamage(Player player) {
        Objects.requireNonNull(player, "player");
        LiveSession session = sessions.get(player.getUniqueId());
        if (session == null || session.health.dead()) {
            return false;
        }
        long tick = plugin.getServer().getCurrentTick();
        session.health = playerHealth.kill(session.health, tick);
        completePlayerLethalDamage(player, session);
        return true;
    }

    boolean engaged(Player player) {
        LiveSession session = sessions.get(Objects.requireNonNull(player, "player").getUniqueId());
        return session != null && session.engagement.state() == EngagementState.ENGAGED;
    }

    void resetPvpParticipant(Player player) {
        LiveSession session = sessions.get(Objects.requireNonNull(player, "player").getUniqueId());
        if (session != null && !player.isDead()) {
            resetPvpParticipant(player, session, 1.0);
        }
    }

    boolean holdLethalDamage(Player player) {
        Objects.requireNonNull(player, "player");
        LiveSession session = sessions.get(player.getUniqueId());
        if (session == null || session.health.dead()) {
            return false;
        }
        long tick = plugin.getServer().getCurrentTick();
        session.health = playerHealth.kill(session.health, tick);
        enterDowned(player, session, tick);
        return true;
    }

    boolean reviveFromDowned(Player player, double healthRatio) {
        Objects.requireNonNull(player, "player");
        if (!Double.isFinite(healthRatio) || healthRatio <= 0 || healthRatio > 1) {
            throw new IllegalArgumentException("healthRatio must be in (0, 1]");
        }
        LiveSession session = sessions.get(player.getUniqueId());
        if (session == null || session.action != ActionState.DOWNED) {
            return false;
        }
        long tick = plugin.getServer().getCurrentTick();
        session.health =
                new CombatHealthRuntime(playerHealth.profile().maximum() * healthRatio, tick, tick);
        session.weapon = weapons.resetTransient();
        session.action = ActionState.IDLE;
        session.crowdControl = CcRuntime.initial(tick);
        session.poise = PoiseRuntime.initial(tick);
        updateHealthPresentation(player, session);
        return true;
    }

    boolean restoreRevived(Player player, double healthRatio) {
        Objects.requireNonNull(player, "player");
        if (!Double.isFinite(healthRatio) || healthRatio <= 0 || healthRatio > 1) {
            throw new IllegalArgumentException("healthRatio must be in (0, 1]");
        }
        LiveSession session = sessions.get(player.getUniqueId());
        if (session == null || session.health.dead() || session.action == ActionState.DEAD) {
            return false;
        }
        long tick = plugin.getServer().getCurrentTick();
        session.health =
                new CombatHealthRuntime(playerHealth.profile().maximum() * healthRatio, tick, tick);
        session.weapon = weapons.resetTransient();
        session.action = ActionState.IDLE;
        updateHealthPresentation(player, session);
        return true;
    }

    boolean killPlayer(Player player) {
        Objects.requireNonNull(player, "player");
        LiveSession session = sessions.get(player.getUniqueId());
        if (session == null || session.health.dead()) {
            return false;
        }
        session.health = playerHealth.kill(session.health, plugin.getServer().getCurrentTick());
        player.setHealth(0);
        return true;
    }

    boolean isDowned(Player player) {
        LiveSession session = sessions.get(Objects.requireNonNull(player, "player").getUniqueId());
        return session != null && session.action == ActionState.DOWNED;
    }

    boolean beginFlaskUse(Player player, UUID operationId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(operationId, "operationId");
        LiveSession session = sessions.get(player.getUniqueId());
        long tick = plugin.getServer().getCurrentTick();
        PvpCombatProfile pvpProfile = pvpCombatPolicy.activeProfile(player).orElse(null);
        if (pvpProfile != null && !pvpProfile.flaskAllowed()) {
            return false;
        }
        if (session == null
                || session.flaskUseOperationId != null
                || session.health.dead()
                || session.action != ActionState.IDLE
                || session.weapon.state() != WeaponState.SHEATHED
                || session.timeline != null
                || session.bowDraw != null
                || session.pendingBowLaunch != null
                || session.crossbow != null
                        && (session.crossbow.phase() == CrossbowPhase.COCKING
                                || session.crossbow.phase() == CrossbowPhase.LOCKING)
                || session.pendingCrossbowCommit != null
                || session.crossbowRecoveryUntilTick > tick
                || session.spellCast != null
                || session.pendingSpellCommit != null
                || session.dodge != null
                || session.guard.active()
                || session.crowdControl.active().isPresent()
                || session.pendingAmmoCycleId != null
                || session.ammoSwitchHandlingUntilTick > tick
                || session.bowRecoveryUntilTick > tick) {
            return false;
        }
        session.flaskUseOperationId = operationId;
        session.action = ActionState.WINDUP;
        session.input.clearBuffer(InputBufferClearReason.ACTION_STARTED);
        releaseGuard(session);
        return true;
    }

    boolean beginConsumableUse(Player player, UUID operationId) {
        PvpCombatProfile pvpProfile = pvpCombatPolicy.activeProfile(player).orElse(null);
        if (pvpProfile != null && !pvpProfile.externalBuffsAllowed()) {
            return false;
        }
        return beginFlaskUse(player, operationId);
    }

    void markFlaskCommitting(Player player, UUID operationId) {
        LiveSession session = sessions.get(Objects.requireNonNull(player, "player").getUniqueId());
        if (ownsFlaskUse(session, operationId) && !session.action.hardControl()) {
            session.action = ActionState.RECOVERY;
        }
    }

    void markConsumableCommitting(Player player, UUID operationId) {
        markFlaskCommitting(player, operationId);
    }

    boolean applyFlaskRestoration(Player player, UUID operationId, FlaskRestoration restoration) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(restoration, "restoration");
        LiveSession session = sessions.get(player.getUniqueId());
        if (!ownsFlaskUse(session, operationId) || session.health.dead()) {
            return false;
        }
        long tick = plugin.getServer().getCurrentTick();
        double healingMultiplier =
                pvpCombatPolicy
                        .activeProfile(player)
                        .map(PvpCombatProfile::healingMultiplier)
                        .orElse(1.0);
        if (restoration.maximumHealthRatio() > 0) {
            CombatHealthResolution healed =
                    playerHealth.heal(
                            session.health,
                            tick,
                            playerHealth.profile().maximum()
                                    * restoration.maximumHealthRatio()
                                    * healingMultiplier);
            session.health = healed.runtime();
        }
        if (restoration.maximumManaRatio() > 0) {
            int mana =
                    (int)
                            Math.round(
                                    session.resources.maximumMana()
                                            * restoration.maximumManaRatio());
            session.resources = session.resources.restoreMana(mana);
            session.manaRegenRemainder = 0;
        }
        if (restoration.stamina() > 0) {
            session.resources = session.resources.restoreStamina(restoration.stamina());
            session.staminaRegenRemainder = 0;
        }
        updateHealthPresentation(player, session);
        return true;
    }

    void endFlaskUse(Player player, UUID operationId) {
        LiveSession session = sessions.get(Objects.requireNonNull(player, "player").getUniqueId());
        if (!ownsFlaskUse(session, operationId)) {
            return;
        }
        session.flaskUseOperationId = null;
        if (!session.action.hardControl()) {
            session.action = ActionState.IDLE;
        }
    }

    void endConsumableUse(Player player, UUID operationId) {
        endFlaskUse(player, operationId);
    }

    private static boolean ownsFlaskUse(LiveSession session, UUID operationId) {
        return session != null
                && Objects.requireNonNull(operationId, "operationId")
                        .equals(session.flaskUseOperationId);
    }

    Optional<Boolean> toggleDebug(Player viewer, Player target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        if (!sessions.containsKey(target.getUniqueId())) {
            return Optional.empty();
        }
        java.util.Set<UUID> viewers =
                debugViewers.computeIfAbsent(
                        target.getUniqueId(), ignored -> new java.util.HashSet<>());
        boolean enabled;
        if (viewers.remove(viewer.getUniqueId())) {
            enabled = false;
        } else {
            viewers.add(viewer.getUniqueId());
            enabled = true;
        }
        if (viewers.isEmpty()) {
            debugViewers.remove(target.getUniqueId());
        }
        return Optional.of(enabled);
    }

    Optional<CombatTrace> latestTrace(Player target) {
        Objects.requireNonNull(target, "target");
        LiveSession session = sessions.get(target.getUniqueId());
        return session == null ? Optional.empty() : Optional.ofNullable(session.lastTrace);
    }

    Result<CombatTrace, CombatSimulationErrorCode> replayTrace(CombatTrace trace) {
        return new ActionTimelineSimulator().replay(Objects.requireNonNull(trace, "trace"), moves);
    }

    void startTrainingMove(Player player) {
        LiveSession session = sessions.get(player.getUniqueId());
        if (session == null || !characters.ready(player)) {
            player.sendActionBar(
                    Component.text("Combat session is not ready.", NamedTextColor.RED));
            return;
        }
        if (session.weapon.state() != WeaponState.READY) {
            player.sendActionBar(
                    Component.text(
                            "Equip the training blade in main hand and wait for READY.",
                            NamedTextColor.YELLOW));
            return;
        }
        startMove(player, session);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHeldSlot(PlayerItemHeldEvent event) {
        LiveSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        if (ammoCycleIntent(event.getPlayer())) {
            event.setCancelled(true);
            cyclePreparedAmmo(
                    event.getPlayer(),
                    session,
                    AmmoCycleInputPolicy.scrollDirection(
                            event.getPreviousSlot(), event.getNewSlot()));
            return;
        }
        cancelAction(session, "WEAPON_SWAP");
        cancelBow(session, "WEAPON_SWAP");
        cancelCrossbow(session, "WEAPON_SWAP");
        cancelSpell(session, "WEAPON_SWAP");
        session.imbuement = null;
        releaseGuard(session);
        session.input.clearBuffer(InputBufferClearReason.WEAPON_SWAP);
        select(session, selectedSlot(event.getPlayer(), event.getNewSlot()));
    }

    private boolean ammoCycleIntent(Player player) {
        return AmmoCycleInputPolicy.ownsScroll(
                player.isSneaking(),
                direction(player.getCurrentInput()),
                equippedWeaponFamily(player).orElse(""));
    }

    private void cyclePreparedAmmo(Player player, LiveSession session, int direction) {
        if (session.weapon.state() != WeaponState.READY
                || session.action != ActionState.IDLE
                || session.timeline != null
                || session.bowDraw != null
                || session.pendingBowLaunch != null
                || session.pendingCrossbowCommit != null
                || session.spellCast != null
                || session.pendingAmmoCycleId != null) {
            player.sendActionBar(Component.text("AMMO SWITCH ACTION LOCKED", NamedTextColor.RED));
            return;
        }
        QuiverProfile profile = characters.equippedQuiverProfile(player).orElse(null);
        com.branz.mmorpg.items.quiver.QuiverPreparation current =
                characters.quiverPreparation(player);
        if (profile == null || current.preparedAmmo().size() < 2) {
            player.sendActionBar(
                    Component.text(
                            "Prepare at least two compatible ammo types.", NamedTextColor.RED));
            return;
        }
        com.branz.mmorpg.items.quiver.QuiverPreparation desired = current.cycle(direction);
        UUID operationId = UUID.randomUUID();
        session.pendingAmmoCycleId = operationId;
        session.lastResolution = "AMMO SWITCH COMMITTING";
        player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.YELLOW));
        characters.updateQuiverPreparation(
                player,
                desired,
                operationId,
                contentVersion,
                result -> {
                    LiveSession active = sessions.get(player.getUniqueId());
                    if (active != session || !operationId.equals(session.pendingAmmoCycleId)) {
                        return;
                    }
                    session.pendingAmmoCycleId = null;
                    if (result
                            instanceof
                            Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>
                                    failure) {
                        session.lastResolution = "AMMO SWITCH FAILED " + failure.error().code();
                        player.sendActionBar(
                                Component.text(session.lastResolution, NamedTextColor.RED));
                        return;
                    }
                    if (session.engagement.state() == EngagementState.ENGAGED) {
                        session.ammoSwitchHandlingUntilTick =
                                plugin.getServer().getCurrentTick()
                                        + profile.ammoSwitchHandlingTicks();
                    } else {
                        session.ammoSwitchHandlingUntilTick = -1;
                    }
                    session.lastResolution =
                            "AMMO SELECTED " + desired.selectedAmmo().orElseThrow().value();
                    player.sendActionBar(
                            Component.text(session.lastResolution, NamedTextColor.GREEN));
                });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        LiveSession session = sessions.get(player.getUniqueId());
        if (session == null || !characters.ready(player)) {
            return;
        }
        if (routePrimaryAttack(player, session)) {
            event.setCancelled(true);
        }
    }

    /**
     * Converges arm-swing and direct entity-hit ingress into one semantic PRIMARY path. Returning
     * true means MMO combat owns the physical attack and vanilla damage must never leak through,
     * including duplicate observations and action-locked states.
     */
    private boolean routePrimaryAttack(Player player, LiveSession session) {
        SelectedHotbarSlot selected = selectedSlot(player, player.getInventory().getHeldItemSlot());
        PrimaryAttackIngressDecision ingress =
                PrimaryAttackIngressPolicy.decide(
                        session.weapon.state(), selected.kind(), session.timeline != null);
        if (!ingress.mmoOwned()) {
            return false;
        }
        if (!ingress.routePrimary()) {
            return true;
        }
        if (ingress.beginDraw()) {
            select(session, selected);
        }
        MoveDefinition primary = primaryMove(player).orElse(null);
        if (primary == null) {
            return true;
        }
        SemanticInput intent = resolvedIntent(player, session, ClientAction.ATTACK).orElse(null);
        if (intent != SemanticInput.PRIMARY) {
            return true;
        }
        Optional<InputRouteOutcome> routed =
                PrimaryAttackInputCoordinator.route(
                        session.input,
                        plugin.getServer().getCurrentTick(),
                        primary.input().branch(),
                        routingContext(player, session));
        routed.ifPresent(outcome -> handleRoute(player, session, outcome));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVanillaCombatDamage(EntityDamageByEntityEvent event) {
        if (event.getDamageSource().getCausingEntity() instanceof Player attacker) {
            LiveSession attackerSession = sessions.get(attacker.getUniqueId());
            if (attackerSession != null
                    && characters.ready(attacker)
                    && routePrimaryAttack(attacker, attackerSession)) {
                event.setCancelled(true);
                return;
            }
        }
        if (!(event.getEntity() instanceof Player defender)) {
            return;
        }
        Player playerSource =
                event.getDamageSource().getCausingEntity() instanceof Player player ? player : null;
        if (playerSource != null && pvpCombatPolicy.profile(playerSource, defender).isEmpty()) {
            event.setCancelled(true);
            return;
        }
        LiveSession defenderSession = sessions.get(defender.getUniqueId());
        if (defenderSession == null) {
            if (playerSource != null) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (damageImmunityObserver.test(defender)) {
            defender.sendActionBar(Component.text("REVIVE PROTECTION", NamedTextColor.AQUA));
            return;
        }
        if (defenderSession.health.dead()) {
            return;
        }
        if (!(event.getDamageSource().getCausingEntity() instanceof LivingEntity source)) {
            applyScaledVanillaDamage(defender, defenderSession, event, "ENTITY");
            return;
        }
        PvpCombatProfile pvpProfile = null;
        if (source instanceof Player attacker) {
            pvpProfile = pvpCombatPolicy.profile(attacker, defender).orElse(null);
            if (pvpProfile == null) {
                return;
            }
        }
        double incomingHealth =
                pvpProfile == null
                        ? trainingIncomingHealthDamage
                        : trainingIncomingHealthDamage * pvpProfile.damageMultiplier();
        double incomingGuardPressure =
                pvpProfile == null
                        ? trainingIncomingGuardPressure
                        : trainingIncomingGuardPressure * pvpProfile.guardPressureMultiplier();
        CombatDefenseResolution resolved =
                new CombatDefenseResolver(dodges, defenderSession.guardEngine)
                        .resolve(
                                Optional.ofNullable(defenderSession.dodge),
                                defenderSession.guard,
                                plugin.getServer().getCurrentTick(),
                                true,
                                guardHitRequest(
                                        defender,
                                        defenderSession,
                                        source,
                                        incomingHealth,
                                        incomingGuardPressure));
        long currentTick = plugin.getServer().getCurrentTick();
        defenderSession.guard = resolved.guardRuntime();
        if (resolved.staminaSpent() > 0) {
            defenderSession.resources =
                    defenderSession.resources.spendStamina(resolved.staminaSpent()).orElseThrow();
            defenderSession.lastStaminaSpendTick = plugin.getServer().getCurrentTick();
            defenderSession.staminaRegenRemainder = 0;
        }
        CombatHealthResolution healthResolution =
                playerHealth.damage(defenderSession.health, currentTick, resolved.finalDamage());
        defenderSession.health = healthResolution.runtime();
        if (!healthResolution.lethalNow()) {
            updateHealthPresentation(defender, defenderSession);
        }
        if (resolved.outcome() != CombatDefenseOutcome.DODGED) {
            markIncomingHostile(defender, defenderSession);
        }
        defenderSession.lastResolution =
                resolved.outcome()
                        + " damage="
                        + roundOne(healthResolution.appliedAmount())
                        + " health="
                        + roundOne(defenderSession.health.current())
                        + " stability="
                        + roundOne(resolved.guardRuntime().stability());
        NamedTextColor feedbackColor =
                switch (resolved.outcome()) {
                    case DODGED -> NamedTextColor.AQUA;
                    case PERFECT_GUARD -> NamedTextColor.GOLD;
                    case GUARDED, GUARD_BREAK -> NamedTextColor.YELLOW;
                    case HIT -> NamedTextColor.RED;
                };
        defender.sendActionBar(Component.text(defenderSession.lastResolution, feedbackColor));
        if (resolved.outcome() == CombatDefenseOutcome.HIT && !healthResolution.lethalNow()) {
            applyIncomingPoise(
                    defender,
                    defenderSession,
                    source,
                    currentTick,
                    pvpProfile == null ? 1.0 : pvpProfile.guardPressureMultiplier());
        }
        if (resolved.outcome() == CombatDefenseOutcome.PERFECT_GUARD
                && !(source instanceof Player)) {
            PostureResolution posture =
                    applyPostureDamage(
                            source.getUniqueId(), currentTick, trainingPerfectGuardPostureDamage);
            defenderSession.lastResolution =
                    "PERFECT_GUARD attacker-posture="
                            + postureLabel(posture.runtime(), currentTick);
            defender.sendActionBar(
                    Component.text(defenderSession.lastResolution, NamedTextColor.GOLD));
        } else if (resolved.outcome() == CombatDefenseOutcome.GUARD_BREAK) {
            applyCc(
                    defender,
                    defenderSession,
                    CcSeverity.HEAVY_STAGGER,
                    24,
                    source instanceof Player);
        }
        if (healthResolution.lethalNow()) {
            completePlayerLethalDamage(defender, defenderSession);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGuardUse(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                        && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        LiveSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null
                || !characters.ready(event.getPlayer())
                || session.weapon.state() != WeaponState.READY) {
            return;
        }
        String family = equippedWeaponFamily(event.getPlayer()).orElse("");
        if (family.equals("BOW")) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                    && session.engagement.state() != EngagementState.ENGAGED) {
                return;
            }
            event.setCancelled(true);
            Result<CombatInputRequest, InputRejectionCode> observed =
                    session.input.observe(
                            new InputObservation(
                                    plugin.getServer().getCurrentTick(),
                                    SemanticInput.SECONDARY,
                                    DirectionSnapshot.NEUTRAL,
                                    trainingBowMove.input().branch(),
                                    new InputDeduplicationKey("MAIN_HAND", "BOW_DRAW_TOGGLE")));
            if (!(observed
                    instanceof Result.Success<CombatInputRequest, InputRejectionCode> input)) {
                return;
            }
            if (session.bowDraw != null) {
                releaseBow(event.getPlayer(), session);
                return;
            }
            Result<SemanticInput, InputRejectionCode> semantic =
                    inputPolicy.resolve(
                            ClientAction.USE, policyContext(event.getPlayer(), session));
            if (semantic instanceof Result.Success<SemanticInput, InputRejectionCode> success
                    && success.value() == SemanticInput.SECONDARY) {
                Result<InputRouteOutcome, InputRejectionCode> routed =
                        session.input.routeFrame(
                                List.of(input.value()), routingContext(event.getPlayer(), session));
                if (routed instanceof Result.Success<InputRouteOutcome, InputRejectionCode>) {
                    startBowDraw(event.getPlayer(), session);
                }
            }
            return;
        }
        if (family.equals("CROSSBOW")) {
            event.setCancelled(true);
            Result<CombatInputRequest, InputRejectionCode> observed =
                    session.input.observe(
                            new InputObservation(
                                    plugin.getServer().getCurrentTick(),
                                    SemanticInput.SECONDARY,
                                    DirectionSnapshot.NEUTRAL,
                                    trainingCrossbowMove.input().branch(),
                                    new InputDeduplicationKey("MAIN_HAND", "CROSSBOW_USE")));
            if (!(observed
                    instanceof Result.Success<CombatInputRequest, InputRejectionCode> input)) {
                return;
            }
            Result<SemanticInput, InputRejectionCode> semantic =
                    inputPolicy.resolve(
                            ClientAction.USE, policyContext(event.getPlayer(), session));
            if (semantic instanceof Result.Success<SemanticInput, InputRejectionCode> success
                    && success.value() == SemanticInput.SECONDARY) {
                Result<InputRouteOutcome, InputRejectionCode> routed =
                        session.input.routeFrame(
                                List.of(input.value()), routingContext(event.getPlayer(), session));
                if (routed instanceof Result.Success<InputRouteOutcome, InputRejectionCode>) {
                    handleCrossbowUse(event.getPlayer(), session);
                }
            }
            return;
        }
        if (family.equals("STAFF")) {
            event.setCancelled(true);
            handleStaffUse(event.getPlayer(), session);
            return;
        }
        if (guardEngineFor(event.getPlayer()).isEmpty()
                || session.engagement.state() != EngagementState.ENGAGED) {
            return;
        }
        Result<SemanticInput, InputRejectionCode> semantic =
                inputPolicy.resolve(ClientAction.USE, policyContext(event.getPlayer(), session));
        if (!(semantic instanceof Result.Success<SemanticInput, InputRejectionCode> success)
                || success.value() != SemanticInput.SECONDARY) {
            return;
        }
        event.setCancelled(true);
        toggleGuard(event.getPlayer(), session);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStaffSpellCycle(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        LiveSession session = sessions.get(player.getUniqueId());
        if (session == null
                || !characters.ready(player)
                || !equippedWeaponFamily(player).orElse("").equals("STAFF")) {
            return;
        }
        event.setCancelled(true);
        if (session.spellCast != null || session.pendingSpellCommit != null) {
            player.sendActionBar(Component.text("SPELL SELECTION LOCKED", NamedTextColor.RED));
            return;
        }
        List<SpellDefinition> available = attunedStaffSpells(player);
        if (available.isEmpty()) {
            session.selectedSpell = null;
            player.sendActionBar(
                    Component.text("ATTUNE A STAFF SPELL AT REST", NamedTextColor.RED));
            return;
        }
        int current =
                session.selectedSpell == null
                        ? -1
                        : java.util.stream.IntStream.range(0, available.size())
                                .filter(
                                        index ->
                                                available
                                                        .get(index)
                                                        .id()
                                                        .equals(session.selectedSpell))
                                .findFirst()
                                .orElse(-1);
        SpellDefinition selected = available.get((current + 1) % available.size());
        session.selectedSpell = selected.id();
        session.lastResolution = "SELECTED " + spellLabel(selected);
        player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.LIGHT_PURPLE));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSneak(PlayerToggleSneakEvent event) {
        LiveSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || !characters.ready(event.getPlayer())) {
            return;
        }
        if (!event.isSneaking()) {
            session.sneakPress = null;
            return;
        }
        if (!combatDodgeContext(session)) {
            return;
        }
        long tick = plugin.getServer().getCurrentTick();
        DirectionSnapshot direction = direction(event.getPlayer().getCurrentInput());
        session.sneakPress = new SneakPressWindow(tick);
        if (direction != DirectionSnapshot.NEUTRAL) {
            event.setCancelled(true);
            session.sneakPress = null;
            requestDodge(event.getPlayer(), session, direction);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnvironmentalDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent
                || !(event.getEntity() instanceof Player player)) {
            return;
        }
        LiveSession session = sessions.get(player.getUniqueId());
        if (session == null || session.health.dead()) {
            return;
        }
        event.setCancelled(true);
        if (damageImmunityObserver.test(player)) {
            player.sendActionBar(Component.text("REVIVE PROTECTION", NamedTextColor.AQUA));
            return;
        }
        applyScaledVanillaDamage(player, session, event, "ENVIRONMENT");
    }

    private void applyScaledVanillaDamage(
            Player player, LiveSession session, EntityDamageEvent event, String sourceLabel) {
        long tick = plugin.getServer().getCurrentTick();
        CombatHealthResolution resolution =
                playerHealth.damage(
                        session.health, tick, event.getFinalDamage() * environmentalHealthScale);
        session.health = resolution.runtime();
        session.lastResolution =
                sourceLabel
                        + " "
                        + event.getCause()
                        + " damage="
                        + roundOne(resolution.appliedAmount())
                        + " health="
                        + roundOne(session.health.current());
        player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.RED));
        if (resolution.lethalNow()) {
            completePlayerLethalDamage(player, session);
        } else {
            updateHealthPresentation(player, session);
        }
    }

    private void completePlayerLethalDamage(Player player, LiveSession session) {
        LethalDamageDisposition disposition = lethalDamageObserver.observe(player);
        if (disposition == LethalDamageDisposition.DEATH) {
            player.setHealth(0);
            return;
        }
        if (disposition == LethalDamageDisposition.SAFE_DEFEAT) {
            resetPvpParticipant(player, session, 0.25);
            return;
        }
        long tick = plugin.getServer().getCurrentTick();
        enterDowned(player, session, tick);
        if (disposition == LethalDamageDisposition.PENDING_COMMIT) {
            player.sendActionBar(Component.text("DOWNED STATE COMMITTING", NamedTextColor.YELLOW));
        }
    }

    private void resetPvpParticipant(Player player, LiveSession session, double healthRatio) {
        long tick = plugin.getServer().getCurrentTick();
        cancelAction(session, "PVP_RESET");
        cancelBow(session, "PVP_RESET");
        cancelCrossbow(session, "PVP_RESET");
        cancelSpell(session, "PVP_RESET");
        removeOwnerProjectiles(player.getUniqueId());
        removeOwnerSpellEffects(player.getUniqueId());
        session.imbuement = null;
        session.input.clearBuffer(InputBufferClearReason.HARD_CC);
        releaseGuard(session);
        session.dodge = null;
        session.dodgeDirection = null;
        session.sneakPress = null;
        session.crowdControl = CcRuntime.initial(tick);
        session.poise = PoiseRuntime.initial(tick);
        session.health =
                new CombatHealthRuntime(playerHealth.profile().maximum() * healthRatio, tick, tick);
        session.resources = CombatResources.full(1000, 100, 100);
        session.engagement = EngagementRuntime.initial(tick);
        session.weapon = weapons.resetTransient();
        session.crossbow = null;
        session.crossbowItemId = null;
        session.action = ActionState.IDLE;
        select(session, selectedSlot(player, player.getInventory().getHeldItemSlot()));
        updateHealthPresentation(player, session);
    }

    private void enterDowned(Player player, LiveSession session, long tick) {
        cancelAction(session, "DOWNED");
        cancelBow(session, "DOWNED");
        cancelCrossbow(session, "DOWNED");
        cancelSpell(session, "DOWNED");
        removeOwnerProjectiles(player.getUniqueId());
        removeOwnerSpellEffects(player.getUniqueId());
        session.imbuement = null;
        session.input.clearBuffer(InputBufferClearReason.HARD_CC);
        releaseGuard(session);
        session.dodge = null;
        session.dodgeDirection = null;
        session.sneakPress = null;
        session.crowdControl = CcRuntime.initial(tick);
        session.poise = PoiseRuntime.initial(tick);
        session.health =
                new CombatHealthRuntime(playerHealth.profile().maximum() * 0.01, tick, tick);
        session.weapon = weapons.interrupt(session.weapon, ActionState.DOWNED);
        session.action = ActionState.DOWNED;
        updateHealthPresentation(player, session);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Mob)) {
            return;
        }
        UUID threatOwner = event.getEntity().getUniqueId();
        sessions.values().forEach(session -> session.threatOwners.remove(threatOwner));
        if (!(event.getTarget() instanceof Player player)) {
            return;
        }
        LiveSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.threatOwners.add(threatOwner);
        session.engagement =
                engagement.alert(session.engagement, plugin.getServer().getCurrentTick());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        UUID threatOwner = event.getEntity().getUniqueId();
        sessions.values().forEach(session -> session.threatOwners.remove(threatOwner));
        sessions.forEach(
                (playerId, session) -> {
                    Player player = plugin.getServer().getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        queueProgressionEvidence(
                                player,
                                session,
                                session.combatEvidence.completeTarget(
                                        new CharacterId(playerId),
                                        threatOwner,
                                        contentVersion,
                                        EncounterOutcome.ABANDONED));
                    }
                });
        trainingTargetHealth.remove(threatOwner);
        trainingTargetPosture.remove(threatOwner);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        LiveSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.flaskUseOperationId != null) {
            consumableInterruptObserver.accept(player, "DEATH");
        }
        completeOpenCombatEvidence(player, session, EncounterOutcome.DEFEAT);
        long tick = plugin.getServer().getCurrentTick();
        session.health = playerHealth.kill(session.health, tick);
        cancelAction(session, "DEATH");
        cancelBow(session, "DEATH");
        cancelCrossbow(session, "DEATH");
        cancelSpell(session, "DEATH");
        removeOwnerProjectiles(player.getUniqueId());
        removeOwnerSpellEffects(player.getUniqueId());
        session.imbuement = null;
        session.input.clearBuffer(InputBufferClearReason.DEATH);
        releaseGuard(session);
        session.dodge = null;
        session.dodgeDirection = null;
        session.sneakPress = null;
        session.crowdControl = CcRuntime.initial(tick);
        session.poise = PoiseRuntime.initial(tick);
        session.weapon = weapons.interrupt(session.weapon, ActionState.DEAD);
        session.action = ActionState.DEAD;
        session.threatOwners.clear();
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        LiveSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        long tick = plugin.getServer().getCurrentTick();
        if (!session.health.dead()) {
            session.health = playerHealth.kill(session.health, tick);
        }
        session.health = playerHealth.respawn(session.health, tick);
        session.bowDraw = null;
        session.bowRecoveryUntilTick = -1;
        restoreEquippedCrossbow(player, session, tick);
        session.engagement = EngagementRuntime.initial(tick);
        GuardEngine guardEngine = guardEngineFor(player).orElse(defaultGuards);
        session.guardEngine = guardEngine;
        session.guard = GuardRuntime.initial(guardEngine.profile(), tick);
        session.poise = PoiseRuntime.initial(tick);
        session.crowdControl = CcRuntime.initial(tick);
        session.resources = CombatResources.full(1000, 100, 100);
        session.weapon = weapons.resetTransient();
        session.action = ActionState.IDLE;
        select(session, selectedSlot(player, player.getInventory().getHeldItemSlot()));
        plugin.getServer()
                .getScheduler()
                .runTask(plugin, () -> updateHealthPresentation(player, session));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        sessions.remove(playerId);
        removeOwnerProjectiles(playerId);
        removeOwnerSpellEffects(playerId);
        debugViewers.remove(playerId);
        debugViewers.values().forEach(viewers -> viewers.remove(playerId));
        debugViewers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        LiveSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        boolean sameWorld = event.getFrom().getWorld() == event.getTo().getWorld();
        boolean authoritativeDodgeStep = sameWorld && session.applyingDodgeTeleport;
        if (authoritativeDodgeStep) {
            return;
        }
        if (session.flaskUseOperationId != null) {
            consumableInterruptObserver.accept(
                    event.getPlayer(), sameWorld ? "FORCED_TELEPORT" : "WORLD_CHANGE");
        }
        completeOpenCombatEvidence(event.getPlayer(), session, EncounterOutcome.ABANDONED);
        cancelAction(session, sameWorld ? "FORCED_TELEPORT" : "WORLD_CHANGE");
        cancelBow(session, sameWorld ? "FORCED_TELEPORT" : "WORLD_CHANGE");
        cancelCrossbow(session, sameWorld ? "FORCED_TELEPORT" : "WORLD_CHANGE");
        cancelSpell(session, sameWorld ? "FORCED_TELEPORT" : "WORLD_CHANGE");
        removeOwnerProjectiles(event.getPlayer().getUniqueId());
        removeOwnerSpellEffects(event.getPlayer().getUniqueId());
        session.imbuement = null;
        session.input.clearBuffer(InputBufferClearReason.WORLD_CHANGE);
        releaseGuard(session);
        session.dodge = null;
        session.dodgeDirection = null;
        session.sneakPress = null;
        session.previousActionTransform = null;
        session.weapon = weapons.resetTransient();
        session.action = ActionState.IDLE;
        select(
                session,
                selectedSlot(
                        event.getPlayer(), event.getPlayer().getInventory().getHeldItemSlot()));
    }

    private void tickAll() {
        tickTrainingPosture();
        tickProjectiles();
        tickSpellZones();
        for (Map.Entry<UUID, LiveSession> entry : sessions.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            LiveSession session = entry.getValue();
            tickCrowdControl(session);
            WeaponState priorWeapon = session.weapon.state();
            session.weapon = weapons.tick(session.weapon);
            if (priorWeapon != WeaponState.READY && session.weapon.state() == WeaponState.READY) {
                Optional<String> readinessFailure = combatReadinessFailure(player);
                if (readinessFailure.isPresent()) {
                    player.sendActionBar(
                            Component.text(readinessFailure.orElseThrow(), NamedTextColor.RED));
                    session.input.clearBuffer(InputBufferClearReason.WEAPON_SWAP);
                } else {
                    player.sendActionBar(
                            Component.text(
                                    equippedWeaponFamily(player).orElse("Combat weapon") + " READY",
                                    NamedTextColor.GREEN));
                    pollBuffered(player, session);
                }
            }
            tickSneakPress(player, session);
            tickBow(player, session);
            tickCrossbow(player, session);
            tickSpell(player, session);
            if (session.imbuement != null
                    && !session.imbuement.activeAt(plugin.getServer().getCurrentTick())) {
                session.imbuement = null;
            }
            tickDodge(player, session);
            refreshGuardEngine(player, session, plugin.getServer().getCurrentTick());
            session.guard =
                    session.guardEngine.tick(session.guard, plugin.getServer().getCurrentTick());
            session.poise = poise.tick(session.poise, plugin.getServer().getCurrentTick());
            tickAction(player, session);
            tickEngagement(player, session);
            session.health =
                    playerHealth.tickOpenWorld(
                            session.health,
                            plugin.getServer().getCurrentTick(),
                            session.engagement.state() == EngagementState.EXPLORATION);
            updateHealthPresentation(player, session);
            regenerateStamina(session);
            regenerateMana(session);
        }
    }

    private void tickAction(Player player, LiveSession session) {
        if (session.timeline == null) {
            return;
        }
        CombatTransform currentTransform = combatTransform(player);
        ActionPhase prior = session.timeline.phase();
        ResourceCommitState priorResourceState = session.timeline.resourceState();
        int priorTraceSize = session.timeline.trace().size();
        Result<ActionTimeline, ActionTimelineErrorCode> advanced = session.timeline.advance();
        if (advanced instanceof Result.Failure<ActionTimeline, ActionTimelineErrorCode>) {
            return;
        }
        session.timeline =
                ((Result.Success<ActionTimeline, ActionTimelineErrorCode>) advanced).value();
        MoveDefinition activeMove = session.timeline.move();
        if (priorResourceState == ResourceCommitState.RESERVED
                && session.timeline.resourceState() == ResourceCommitState.COMMITTED) {
            session.lastStaminaSpendTick = plugin.getServer().getCurrentTick();
            session.staminaRegenRemainder = 0;
            observeCommittedCombatAction(
                    session,
                    activeMove.family(),
                    Objects.requireNonNull(session.activeMoveEvidenceActionId),
                    activeMove.id());
            markHostile(player, session);
        }
        if (session.timeline.phase() != prior) {
            player.sendActionBar(
                    Component.text(
                            activeMove.id().value()
                                    + " "
                                    + session.timeline.phase()
                                    + " tick="
                                    + session.timeline.tick()
                                    + " stamina="
                                    + session.timeline.resources().stamina(),
                            NamedTextColor.AQUA));
        }
        for (ActionTraceEvent event :
                session.timeline.trace().subList(priorTraceSize, session.timeline.trace().size())) {
            if (event.type() == ActionTraceEventType.HITBOX_OPENED) {
                resolveTrainingHitbox(player, session, currentTransform);
            }
        }
        session.previousActionTransform = currentTransform;
        if (session.timeline.phase().terminal()) {
            session.resources = session.timeline.resources();
            finishTrace(session, session.timeline);
            session.timeline = null;
            session.previousActionTransform = null;
            session.activeMoveEvidenceActionId = null;
            session.action = ActionState.IDLE;
        } else {
            session.action = actionState(session.timeline.phase());
        }
    }

    private void resolveTrainingHitbox(
            Player player, LiveSession session, CombatTransform currentTransform) {
        MoveDefinition activeMove = session.timeline.move();
        MoveDefinition.Hitbox hitbox = activeMove.hitboxes().getFirst();
        if (hitbox.shape() != MoveDefinition.HitboxShape.ARC) {
            session.lastResolution = "unsupported " + hitbox.shape();
            return;
        }
        CombatTransform previousTransform =
                session.previousActionTransform == null
                                || !session.previousActionTransform
                                        .worldId()
                                        .equals(currentTransform.worldId())
                        ? currentTransform
                        : session.previousActionTransform;
        ArcHitboxQuery previousQuery = arcQuery(hitbox, previousTransform);
        ArcHitboxQuery currentQuery = arcQuery(hitbox, currentTransform);
        CombatVector midpoint = midpoint(previousTransform.origin(), currentTransform.origin());
        double xRadius =
                hitbox.range()
                        + Math.abs(currentTransform.origin().x() - previousTransform.origin().x())
                                / 2.0
                        + 1;
        double yRadius =
                hitbox.height()
                        + Math.abs(currentTransform.origin().y() - previousTransform.origin().y())
                                / 2.0
                        + 1;
        double zRadius =
                hitbox.range()
                        + Math.abs(currentTransform.origin().z() - previousTransform.origin().z())
                                / 2.0
                        + 1;
        Map<UUID, LivingEntity> entities = new HashMap<>();
        List<TargetCollider> candidates =
                player
                        .getWorld()
                        .getNearbyEntities(
                                new Location(
                                        player.getWorld(),
                                        midpoint.x(),
                                        midpoint.y(),
                                        midpoint.z()),
                                xRadius,
                                yRadius,
                                zRadius)
                        .stream()
                        .filter(LivingEntity.class::isInstance)
                        .map(LivingEntity.class::cast)
                        .filter(entity -> entity != player)
                        .peek(entity -> entities.put(entity.getUniqueId(), entity))
                        .map(
                                entity ->
                                        new TargetCollider(
                                                entity.getUniqueId(),
                                                new CombatVector(
                                                        entity.getLocation().getX(),
                                                        entity.getLocation().getY(),
                                                        entity.getLocation().getZ()),
                                                Math.max(
                                                                entity.getBoundingBox().getWidthX(),
                                                                entity.getBoundingBox().getWidthZ())
                                                        / 2.0,
                                                entity.getBoundingBox().getHeight(),
                                                eligibleCombatTarget(player, entity),
                                                player.hasLineOfSight(entity),
                                                false))
                        .toList();
        SweptArcResolution sweep =
                hitboxes.resolve(new SweptArcHitboxQuery(previousQuery, currentQuery), candidates);
        List<ResolvedTarget> resolved = sweep.targets();
        renderArcDebug(
                player,
                previousQuery,
                currentQuery,
                sweep.sampledOrigins(),
                candidates,
                resolved,
                entities);
        if (sweep.samplingCapped()) {
            session.lastResolution = "SWEEP_REJECTED motion exceeds 16 blocks/tick";
            player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.RED));
            return;
        }
        if (resolved.isEmpty()) {
            session.lastResolution = "MISS tick=" + session.timeline.tick();
            player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.GRAY));
            return;
        }
        double totalDamage = 0;
        int postureBreaks = 0;
        int deaths = 0;
        String firstPosture = null;
        String firstHealth = null;
        long currentTick = plugin.getServer().getCurrentTick();
        for (ResolvedTarget target : resolved) {
            LivingEntity entity = entities.get(target.entityId());
            boolean pvpTarget = entity instanceof Player;
            PostureRuntime posture = postureAt(target.entityId(), currentTick);
            boolean postureBroken =
                    !pvpTarget && postures.phaseAt(posture, currentTick) == PosturePhase.BROKEN;
            double armor =
                    entity.getAttribute(Attribute.ARMOR) == null
                            ? 0
                            : Objects.requireNonNull(entity.getAttribute(Attribute.ARMOR))
                                    .getValue();
            PhysicalDamageBreakdown breakdown =
                    damage.resolve(
                            new PhysicalDamageRequest(
                                    meleePower(activeMove.family()),
                                    activeMove.outputs().moveCoefficient(),
                                    0,
                                    armor,
                                    0,
                                    0,
                                    0,
                                    postureBroken
                                            ? java.util.Set.of(ConditionalAdvantage.POSTURE_BREAK)
                                            : java.util.Set.of(),
                                    pvpTarget
                                            ? activeMove.profiles().pvpMultiplier()
                                            : activeMove.profiles().pveMultiplier()));
            double resolvedDamage = breakdown.finalDamage();
            int resolvedPosture = activeMove.outputs().posture();
            if (session.imbuement != null) {
                RunicImbuementRuntime imbued = session.imbuement;
                ImbuementHitResolution consumed = imbuements.consume(imbued, currentTick);
                session.imbuement = consumed.remainingRuntime().orElse(null);
                if (consumed.applied()) {
                    SpellDefinition rune = imbued.spell();
                    ArcaneDamageBreakdown bonus =
                            arcaneDamage.resolve(
                                    new ArcaneDamageRequest(
                                            rune.output().arcaneSchool(),
                                            meleePower(activeMove.family()),
                                            rune.imbuement().orElseThrow().powerCoefficient(),
                                            0,
                                            postureBroken
                                                    ? java.util.Set.of(
                                                            ConditionalAdvantage.POSTURE_BREAK)
                                                    : java.util.Set.of(),
                                            pvpTarget
                                                    ? rune.profiles().pvpMultiplier()
                                                    : rune.profiles().pveMultiplier()));
                    resolvedDamage += bonus.finalDamage();
                    resolvedPosture += rune.output().posture();
                }
            }
            if (entity instanceof Player defender) {
                double applied =
                        applyPvpHit(
                                player,
                                defender,
                                resolvedDamage,
                                resolvedPosture,
                                activeMove.id().value());
                totalDamage += applied;
                if (firstHealth == null) {
                    LiveSession defenderSession = sessions.get(defender.getUniqueId());
                    firstHealth =
                            defenderSession == null
                                    ? "unavailable"
                                    : roundOne(defenderSession.health.current())
                                            + "/"
                                            + roundOne(playerHealth.profile().maximum());
                }
                if (firstPosture == null) {
                    firstPosture = "PVP";
                }
                continue;
            }
            CombatHealthRuntime targetHealth =
                    trainingTargetHealth.computeIfAbsent(
                            target.entityId(),
                            ignored ->
                                    CombatHealthRuntime.full(enemyHealth.profile(), currentTick));
            CombatHealthResolution healthResolution =
                    enemyHealth.damage(targetHealth, currentTick, resolvedDamage);
            trainingTargetHealth.put(target.entityId(), healthResolution.runtime());
            renderMeleeHitFeedback(entity, healthResolution.appliedAmount());
            observeSuccessfulCombatAction(
                    player,
                    session,
                    entity,
                    activeMove.family(),
                    Objects.requireNonNull(session.activeMoveEvidenceActionId),
                    activeMove.id(),
                    meleePower(activeMove.family()));
            totalDamage += healthResolution.appliedAmount();
            PostureResolution postureResolution =
                    postures.damage(posture, currentTick, resolvedPosture);
            trainingTargetPosture.put(target.entityId(), postureResolution.runtime());
            if (postureResolution.justBroke()) {
                postureBreaks++;
            }
            if (firstPosture == null) {
                firstPosture = postureLabel(postureResolution.runtime(), currentTick);
            }
            if (firstHealth == null) {
                firstHealth =
                        roundOne(healthResolution.runtime().current())
                                + "/"
                                + roundOne(enemyHealth.profile().maximum());
            }
            if (healthResolution.lethalNow()) {
                deaths++;
                completeCombatTarget(player, session, entity, EncounterOutcome.VICTORY);
                entity.setHealth(0);
            }
        }
        session.lastResolution =
                "HIT targets="
                        + resolved.size()
                        + " damage="
                        + Math.round(totalDamage * 10.0) / 10.0
                        + " health="
                        + firstHealth
                        + " posture="
                        + firstPosture
                        + (postureBreaks > 0 ? " breaks=" + postureBreaks : "")
                        + (deaths > 0 ? " deaths=" + deaths : "");
        player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.GREEN));
    }

    private void tickProjectiles() {
        java.util.Iterator<Map.Entry<UUID, LiveProjectile>> iterator =
                activeProjectiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, LiveProjectile> entry = iterator.next();
            LiveProjectile live = entry.getValue();
            org.bukkit.World world = plugin.getServer().getWorld(live.worldId());
            Player owner = plugin.getServer().getPlayer(live.runtime().identity().ownerEntityId());
            if (world == null || owner == null || !owner.isOnline()) {
                iterator.remove();
                continue;
            }
            ProjectileRuntime runtime = live.runtime();
            CombatVector start = runtime.position();
            CombatVector completeEnd = start.add(runtime.velocity());
            double segmentLength = runtime.velocity().length();
            OptionalDouble blockContact =
                    blockContactFraction(
                            world,
                            start,
                            runtime.velocity(),
                            segmentLength,
                            runtime.profile().collisionRadius());
            CombatVector midpoint = midpoint(start, completeEnd);
            double radius = segmentLength / 2.0 + runtime.profile().collisionRadius() + 1.5;
            Map<UUID, LivingEntity> entities = new HashMap<>();
            List<TargetCollider> candidates =
                    world
                            .getNearbyEntities(
                                    new Location(world, midpoint.x(), midpoint.y(), midpoint.z()),
                                    radius,
                                    radius,
                                    radius)
                            .stream()
                            .filter(LivingEntity.class::isInstance)
                            .map(LivingEntity.class::cast)
                            .filter(entity -> entity != owner)
                            .peek(entity -> entities.put(entity.getUniqueId(), entity))
                            .map(
                                    entity ->
                                            new TargetCollider(
                                                    entity.getUniqueId(),
                                                    new CombatVector(
                                                            entity.getLocation().getX(),
                                                            entity.getLocation().getY(),
                                                            entity.getLocation().getZ()),
                                                    Math.max(
                                                                    entity.getBoundingBox()
                                                                            .getWidthX(),
                                                                    entity.getBoundingBox()
                                                                            .getWidthZ())
                                                            / 2.0,
                                                    entity.getBoundingBox().getHeight(),
                                                    eligibleCombatTarget(owner, entity),
                                                    true,
                                                    false))
                            .toList();
            ProjectileTickResolution resolution =
                    projectiles.advance(new ProjectileTickQuery(runtime, candidates, blockContact));
            world.spawnParticle(
                    live.context().arcaneSchool().isPresent() ? Particle.FLAME : Particle.CRIT,
                    new Location(
                            world,
                            resolution.pathEnd().x(),
                            resolution.pathEnd().y(),
                            resolution.pathEnd().z()),
                    2,
                    0.02,
                    0.02,
                    0.02,
                    0);
            renderProjectileDebug(owner.getUniqueId(), world, resolution);
            applyProjectileHits(owner, live, resolution, entities);
            if (resolution.runtime().status().terminal()) {
                iterator.remove();
            } else {
                entry.setValue(
                        new LiveProjectile(live.worldId(), resolution.runtime(), live.context()));
            }
        }
    }

    private static OptionalDouble blockContactFraction(
            org.bukkit.World world,
            CombatVector start,
            CombatVector velocity,
            double segmentLength,
            double collisionRadius) {
        if (segmentLength < 1.0e-9) {
            return OptionalDouble.empty();
        }
        org.bukkit.util.Vector direction =
                new org.bukkit.util.Vector(velocity.x(), velocity.y(), velocity.z()).normalize();
        double nearest = Double.POSITIVE_INFINITY;
        double[][] offsets = {
            {0, 0, 0},
            {collisionRadius, 0, 0},
            {-collisionRadius, 0, 0},
            {0, collisionRadius, 0},
            {0, -collisionRadius, 0},
            {0, 0, collisionRadius},
            {0, 0, -collisionRadius}
        };
        for (double[] offset : offsets) {
            Location origin =
                    new Location(
                            world,
                            start.x() + offset[0],
                            start.y() + offset[1],
                            start.z() + offset[2]);
            RayTraceResult trace =
                    world.rayTraceBlocks(
                            origin, direction, segmentLength, FluidCollisionMode.NEVER, true);
            if (trace != null) {
                nearest =
                        Math.min(
                                nearest,
                                trace.getHitPosition().distance(origin.toVector()) / segmentLength);
            }
        }
        return Double.isFinite(nearest)
                ? OptionalDouble.of(Math.max(0, Math.min(1, nearest)))
                : OptionalDouble.empty();
    }

    private void applyProjectileHits(
            Player owner,
            LiveProjectile live,
            ProjectileTickResolution resolution,
            Map<UUID, LivingEntity> entities) {
        LiveSession ownerSession = sessions.get(owner.getUniqueId());
        for (com.branz.mmorpg.combat.projectile.ProjectileHit hit : resolution.hits()) {
            LivingEntity entity = entities.get(hit.entityId());
            if (entity == null || entity.isDead()) {
                continue;
            }
            boolean pvpTarget = entity instanceof Player;
            long tick = plugin.getServer().getCurrentTick();
            PostureRuntime posture = postureAt(hit.entityId(), tick);
            java.util.EnumSet<ConditionalAdvantage> advantages =
                    java.util.EnumSet.noneOf(ConditionalAdvantage.class);
            if (postures.phaseAt(posture, tick) == PosturePhase.BROKEN) {
                advantages.add(ConditionalAdvantage.POSTURE_BREAK);
            }
            if (hit.weakPoint()) {
                advantages.add(ConditionalAdvantage.WEAK_POINT);
            }
            double armor =
                    entity.getAttribute(Attribute.ARMOR) == null
                            ? 0
                            : Objects.requireNonNull(entity.getAttribute(Attribute.ARMOR))
                                    .getValue();
            double finalDamage;
            if (live.context().arcaneSchool().isPresent()) {
                ArcaneDamageBreakdown breakdown =
                        arcaneDamage.resolve(
                                new ArcaneDamageRequest(
                                        live.context().arcaneSchool().orElseThrow(),
                                        live.context().sourcePower(),
                                        live.context().powerCoefficient(),
                                        0,
                                        advantages,
                                        pvpTarget
                                                ? live.context().pvpMultiplier()
                                                : live.context().pveMultiplier()));
                finalDamage = breakdown.finalDamage();
            } else {
                PhysicalDamageBreakdown breakdown =
                        damage.resolve(
                                new PhysicalDamageRequest(
                                        live.context().sourcePower(),
                                        live.context().powerCoefficient(),
                                        0,
                                        armor,
                                        live.context().penetrationPercentage(),
                                        0,
                                        0,
                                        advantages,
                                        pvpTarget
                                                ? live.context().pvpMultiplier()
                                                : live.context().pveMultiplier()));
                finalDamage = breakdown.finalDamage();
            }
            int postureDamage =
                    (int) Math.round(live.context().posture() * live.context().postureMultiplier());
            if (entity instanceof Player defender) {
                double applied =
                        applyPvpHit(owner, defender, finalDamage, postureDamage, "PROJECTILE");
                if (ownerSession != null) {
                    ownerSession.lastResolution = "PROJECTILE PVP HIT damage=" + roundOne(applied);
                    owner.sendActionBar(
                            Component.text(ownerSession.lastResolution, NamedTextColor.GREEN));
                }
                continue;
            }
            CombatHealthRuntime targetHealth =
                    trainingTargetHealth.computeIfAbsent(
                            hit.entityId(),
                            ignored -> CombatHealthRuntime.full(enemyHealth.profile(), tick));
            CombatHealthResolution health = enemyHealth.damage(targetHealth, tick, finalDamage);
            trainingTargetHealth.put(hit.entityId(), health.runtime());
            PostureResolution postureResolution = postures.damage(posture, tick, postureDamage);
            trainingTargetPosture.put(hit.entityId(), postureResolution.runtime());
            if (ownerSession != null) {
                DefinitionId sourceId = live.runtime().identity().sourceMoveId();
                String family =
                        live.context().arcaneSchool().isPresent()
                                ? "STAFF"
                                : moves.find(sourceId)
                                        .map(MoveDefinition::family)
                                        .orElse("ENDURANCE");
                observeSuccessfulCombatAction(
                        owner,
                        ownerSession,
                        entity,
                        family,
                        live.runtime().identity().projectileId(),
                        sourceId,
                        live.context().sourcePower());
                ownerSession.lastResolution =
                        "PROJECTILE HIT damage="
                                + roundOne(health.appliedAmount())
                                + " health="
                                + roundOne(health.runtime().current())
                                + " posture="
                                + postureLabel(postureResolution.runtime(), tick);
                owner.sendActionBar(
                        Component.text(ownerSession.lastResolution, NamedTextColor.GREEN));
            }
            if (health.lethalNow()) {
                if (ownerSession != null) {
                    completeCombatTarget(owner, ownerSession, entity, EncounterOutcome.VICTORY);
                }
                entity.setHealth(0);
            }
        }
    }

    private void renderProjectileDebug(
            UUID ownerId, org.bukkit.World world, ProjectileTickResolution resolution) {
        java.util.Set<UUID> viewerIds = debugViewers.get(ownerId);
        if (viewerIds == null) {
            return;
        }
        for (UUID viewerId : List.copyOf(viewerIds)) {
            Player viewer = plugin.getServer().getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline() || viewer.getWorld() != world) {
                continue;
            }
            viewer.spawnParticle(
                    Particle.FLAME,
                    new Location(
                            world,
                            resolution.pathEnd().x(),
                            resolution.pathEnd().y(),
                            resolution.pathEnd().z()),
                    1,
                    0,
                    0,
                    0,
                    0);
        }
    }

    private void renderArcDebug(
            Player owner,
            ArcHitboxQuery previousQuery,
            ArcHitboxQuery currentQuery,
            List<CombatVector> sampledOrigins,
            List<TargetCollider> candidates,
            List<ResolvedTarget> resolved,
            Map<UUID, LivingEntity> entities) {
        java.util.Set<UUID> viewerIds = debugViewers.get(owner.getUniqueId());
        if (viewerIds == null || viewerIds.isEmpty()) {
            return;
        }
        java.util.Set<UUID> selected =
                resolved.stream()
                        .map(ResolvedTarget::entityId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<CombatVector> previousOutline = arcDebugGeometry.outline(previousQuery, 12, 4);
        List<CombatVector> currentOutline = arcDebugGeometry.outline(currentQuery, 12, 4);
        for (UUID viewerId : List.copyOf(viewerIds)) {
            Player viewer = plugin.getServer().getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline() || viewer.getWorld() != owner.getWorld()) {
                continue;
            }
            for (CombatVector point : previousOutline) {
                viewer.spawnParticle(
                        Particle.ELECTRIC_SPARK,
                        new Location(owner.getWorld(), point.x(), point.y() + 0.15, point.z()),
                        1,
                        0,
                        0,
                        0,
                        0);
            }
            for (CombatVector point : currentOutline) {
                viewer.spawnParticle(
                        Particle.END_ROD,
                        new Location(owner.getWorld(), point.x(), point.y() + 0.15, point.z()),
                        1,
                        0,
                        0,
                        0,
                        0);
            }
            for (CombatVector point : sampledOrigins) {
                viewer.spawnParticle(
                        Particle.CLOUD,
                        new Location(owner.getWorld(), point.x(), point.y() + 0.1, point.z()),
                        1,
                        0,
                        0,
                        0,
                        0);
            }
            for (TargetCollider candidate : candidates) {
                LivingEntity entity = entities.get(candidate.entityId());
                if (entity == null) {
                    continue;
                }
                Particle marker =
                        selected.contains(candidate.entityId())
                                ? Particle.HAPPY_VILLAGER
                                : candidate.lineOfSight()
                                        ? Particle.SMOKE
                                        : Particle.ANGRY_VILLAGER;
                viewer.spawnParticle(
                        marker,
                        entity.getLocation().add(0, entity.getHeight() * 0.6, 0),
                        4,
                        0.15,
                        0.15,
                        0.15,
                        0);
            }
        }
    }

    private static ArcHitboxQuery arcQuery(
            MoveDefinition.Hitbox hitbox, CombatTransform transform) {
        return new ArcHitboxQuery(
                transform.origin(),
                transform.forward(),
                hitbox.range(),
                hitbox.angleDegrees(),
                -0.5,
                hitbox.height(),
                hitbox.maxTargets());
    }

    private static CombatTransform combatTransform(Player player) {
        Location location = player.getLocation();
        org.bukkit.util.Vector direction = location.getDirection().setY(0);
        if (direction.lengthSquared() < 1.0e-9) {
            double yaw = Math.toRadians(location.getYaw());
            direction = new org.bukkit.util.Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        return new CombatTransform(
                player.getWorld().getUID(),
                new CombatVector(location.getX(), location.getY(), location.getZ()),
                new CombatVector(direction.getX(), 0, direction.getZ()).normalizedHorizontal());
    }

    private static CombatVector midpoint(CombatVector first, CombatVector second) {
        return new CombatVector(
                (first.x() + second.x()) / 2.0,
                (first.y() + second.y()) / 2.0,
                (first.z() + second.z()) / 2.0);
    }

    private PostureRuntime postureAt(UUID entityId, long tick) {
        PostureRuntime current =
                trainingTargetPosture.computeIfAbsent(
                        entityId, ignored -> PostureRuntime.initial(postures.profile(), tick));
        PostureRuntime advanced = postures.tick(current, tick);
        trainingTargetPosture.put(entityId, advanced);
        return advanced;
    }

    private PostureResolution applyPostureDamage(UUID entityId, long tick, double amount) {
        PostureResolution resolution = postures.damage(postureAt(entityId, tick), tick, amount);
        trainingTargetPosture.put(entityId, resolution.runtime());
        return resolution;
    }

    private String postureLabel(PostureRuntime runtime, long tick) {
        if (postures.phaseAt(runtime, tick) == PosturePhase.BROKEN) {
            return "BROKEN(" + Math.max(0, runtime.brokenUntilTick() - tick) + "t)";
        }
        return roundOne(runtime.current()) + "/" + roundOne(postures.profile().maximum());
    }

    private void tickTrainingPosture() {
        long tick = plugin.getServer().getCurrentTick();
        java.util.Iterator<Map.Entry<UUID, PostureRuntime>> iterator =
                trainingTargetPosture.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PostureRuntime> entry = iterator.next();
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                iterator.remove();
                trainingTargetHealth.remove(entry.getKey());
                continue;
            }
            entry.setValue(postures.tick(entry.getValue(), tick));
        }
    }

    private void tickCrowdControl(LiveSession session) {
        boolean wasActive = session.crowdControl.active().isPresent();
        session.crowdControl =
                crowdControl.tick(session.crowdControl, plugin.getServer().getCurrentTick());
        if (wasActive && session.crowdControl.active().isEmpty()) {
            session.action = ActionState.IDLE;
        }
    }

    private void applyCc(
            Player player,
            LiveSession session,
            CcSeverity severity,
            int durationTicks,
            boolean pvp) {
        long tick = plugin.getServer().getCurrentTick();
        CcApplication application =
                crowdControl.apply(
                        session.crowdControl,
                        tick,
                        new CcRequest(severity, durationTicks, false, pvp));
        session.crowdControl = application.runtime();
        if (!application.applied()) {
            session.lastResolution = "CC " + severity + " " + application.outcome();
            return;
        }
        if (session.flaskUseOperationId != null) {
            consumableInterruptObserver.accept(player, "CC_" + severity);
        }
        cancelAction(session, "CC_" + severity);
        cancelBow(session, "CC_" + severity);
        cancelCrossbow(session, "CC_" + severity);
        cancelSpell(session, "CC_" + severity);
        session.input.clearBuffer(InputBufferClearReason.HARD_CC);
        releaseGuard(session);
        session.dodge = null;
        session.dodgeDirection = null;
        session.lastDodgeMovementElapsed = -1;
        session.sneakPress = null;
        ActionState forcedAction = actionState(severity);
        session.action = forcedAction;
        session.weapon = weapons.interrupt(session.weapon, forcedAction);
        session.lastResolution =
                "CC "
                        + severity
                        + " "
                        + application.outcome()
                        + " duration="
                        + application.effectiveDurationTicks()
                        + "t";
        player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.RED));
    }

    private void applyIncomingPoise(
            Player player, LiveSession session, LivingEntity source, long tick) {
        applyIncomingPoise(player, session, source, tick, 1.0);
    }

    private void applyIncomingPoise(
            Player player,
            LiveSession session,
            LivingEntity source,
            long tick,
            double poiseMultiplier) {
        PoiseResolution poiseResolution =
                poise.apply(
                        session.poise,
                        tick,
                        trainingIncomingPoiseDamage,
                        poiseMultiplier,
                        trainingIncomingCcSeverity);
        session.poise = poiseResolution.runtime();
        poiseResolution
                .triggeredSeverity()
                .ifPresent(
                        severity ->
                                applyCc(
                                        player,
                                        session,
                                        severity,
                                        trainingIncomingCcTicks,
                                        source instanceof Player));
    }

    private static ActionState actionState(CcSeverity severity) {
        return switch (severity) {
            case FLINCH, STAGGER, HEAVY_STAGGER, KNOCKBACK -> ActionState.STAGGERED;
            case KNOCKDOWN, LAUNCH -> ActionState.KNOCKED_DOWN;
            case GRAB -> ActionState.GRABBED;
        };
    }

    private void regenerateStamina(LiveSession session) {
        if (session.health.dead()
                || session.timeline != null
                || session.dodge != null
                || session.guard.active()
                || (session.bowDraw != null && session.bowDraw.phase() == BowDrawPhase.STRAINED)
                || session.resources.stamina() >= session.resources.maximumStamina()) {
            return;
        }
        long currentTick = plugin.getServer().getCurrentTick();
        if (currentTick - session.lastStaminaSpendTick < 20) {
            return;
        }
        session.staminaRegenRemainder += 12.0 / 20.0;
        int whole = (int) session.staminaRegenRemainder;
        if (whole > 0) {
            session.resources = session.resources.restoreStamina(whole);
            session.staminaRegenRemainder -= whole;
        }
    }

    private void tickSneakPress(Player player, LiveSession session) {
        if (session.sneakPress == null) {
            return;
        }
        DirectionSnapshot direction = direction(player.getCurrentInput());
        SneakPressDecision decision =
                sneakPresses.resolve(
                        session.sneakPress,
                        plugin.getServer().getCurrentTick(),
                        player.isSneaking(),
                        direction);
        if (decision == SneakPressDecision.DODGE) {
            session.sneakPress = null;
            player.setSneaking(false);
            requestDodge(player, session, direction);
        } else if (decision != SneakPressDecision.WAITING) {
            session.sneakPress = null;
        }
    }

    private void requestDodge(Player player, LiveSession session, DirectionSnapshot direction) {
        long tick = plugin.getServer().getCurrentTick();
        if (!dodgeWindowOpen(session)) {
            player.sendActionBar(Component.text("Dodge window is closed.", NamedTextColor.RED));
            return;
        }
        Result<SemanticInput, InputRejectionCode> semantic =
                inputPolicy.resolve(
                        ClientAction.SNEAK_PRESS, policyContext(player, session, direction));
        if (!(semantic instanceof Result.Success<SemanticInput, InputRejectionCode> intent)
                || intent.value() != SemanticInput.DODGE) {
            return;
        }
        CombatResources postCancelResources = resourcesAfterDodgeCancel(session);
        Result<DodgeRuntime, DodgeErrorCode> started =
                dodges.start(
                        Optional.ofNullable(session.dodge),
                        dodgeProfile,
                        direction,
                        postCancelResources.availableStamina(),
                        tick);
        if (started instanceof Result.Failure<DodgeRuntime, DodgeErrorCode> failure) {
            player.sendActionBar(
                    Component.text("Dodge rejected: " + failure.error(), NamedTextColor.RED));
            return;
        }
        Result<CombatInputRequest, InputRejectionCode> observed =
                session.input.observe(
                        new InputObservation(
                                tick,
                                intent.value(),
                                direction,
                                "dodge",
                                new InputDeduplicationKey("FEET", "SNEAK_PRESS")));
        if (!(observed instanceof Result.Success<CombatInputRequest, InputRejectionCode> success)) {
            return;
        }
        Result<InputRouteOutcome, InputRejectionCode> routed =
                session.input.routeFrame(
                        List.of(success.value()), dodgeRoutingContext(player, session));
        if (!(routed instanceof Result.Success<InputRouteOutcome, InputRejectionCode>)) {
            return;
        }

        DodgeRuntime runtime = ((Result.Success<DodgeRuntime, DodgeErrorCode>) started).value();
        session.resources =
                postCancelResources.spendStamina(dodgeProfile.staminaCost()).orElseThrow();
        releaseGuard(session);
        cancelBow(session, "DODGE");
        cancelCrossbow(session, "DODGE");
        cancelSpell(session, "DODGE");
        session.timeline = null;
        session.previousActionTransform = null;
        session.action = ActionState.IDLE;
        session.dodge = runtime;
        session.dodgeDirection = dodgeVector(player, direction);
        session.lastDodgeMovementElapsed = -1;
        session.lastStaminaSpendTick = tick;
        session.staminaRegenRemainder = 0;
        applyDodgeMovement(player, session, tick);
        player.sendActionBar(
                Component.text(
                        "DODGE " + dodgeProfile.load() + " stamina=" + session.resources.stamina(),
                        NamedTextColor.AQUA));
    }

    private void tickDodge(Player player, LiveSession session) {
        if (session.dodge == null) {
            return;
        }
        long tick = plugin.getServer().getCurrentTick();
        applyDodgeMovement(player, session, tick);
        DodgePhase phase = session.dodge.phaseAt(tick);
        if (phase == DodgePhase.COMPLETE) {
            session.dodge = null;
            session.dodgeDirection = null;
            session.lastDodgeMovementElapsed = -1;
        }
    }

    private void applyDodgeMovement(Player player, LiveSession session, long tick) {
        if (session.dodge == null || !session.dodge.movementAppliesAt(tick)) {
            return;
        }
        long elapsed = session.dodge.elapsed(tick);
        if (elapsed <= session.lastDodgeMovementElapsed) {
            return;
        }
        session.lastDodgeMovementElapsed = elapsed;
        double step = dodgeProfile.travelDistance() / dodgeProfile.movementTicks();
        org.bukkit.util.Vector displacement = session.dodgeDirection.clone().multiply(step);
        if (!collisionFree(player, displacement)) {
            session.lastResolution = "DODGE blocked by world collision";
            return;
        }
        org.bukkit.Location destination = player.getLocation().add(displacement);
        session.applyingDodgeTeleport = true;
        try {
            player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } finally {
            session.applyingDodgeTeleport = false;
        }
    }

    private static boolean collisionFree(Player player, org.bukkit.util.Vector displacement) {
        org.bukkit.util.BoundingBox destination =
                player.getBoundingBox().clone().shift(displacement);
        int minX = (int) Math.floor(destination.getMinX());
        int maxX = (int) Math.floor(destination.getMaxX());
        int minY = (int) Math.floor(destination.getMinY());
        int maxY = (int) Math.floor(destination.getMaxY());
        int minZ = (int) Math.floor(destination.getMinZ());
        int maxZ = (int) Math.floor(destination.getMaxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (player.getWorld()
                            .getBlockAt(x, y, z)
                            .getCollisionShape()
                            .overlaps(destination)) {
                        return false;
                    }
                }
            }
        }
        return player.getWorld()
                        .rayTraceBlocks(
                                player.getLocation().add(0, 0.1, 0),
                                displacement,
                                displacement.length(),
                                FluidCollisionMode.NEVER,
                                true)
                == null;
    }

    private CombatResources resourcesAfterDodgeCancel(LiveSession session) {
        if (session.timeline == null) {
            return session.resources;
        }
        int cancellationTick = session.timeline.tick();
        Result<ActionTimeline, ActionTimelineErrorCode> cancelled =
                session.timeline.cancel("DODGE");
        if (cancelled instanceof Result.Success<ActionTimeline, ActionTimelineErrorCode> success) {
            session.activeTraceCommands.add(
                    new ActionSimulationCommand(
                            cancellationTick, ActionSimulationCommand.Type.CANCEL, "DODGE"));
            finishTrace(session, success.value());
            return success.value().resources();
        }
        return session.timeline.resources();
    }

    private void startBowDraw(Player player, LiveSession session) {
        long tick = plugin.getServer().getCurrentTick();
        if (session.action != ActionState.IDLE
                || session.timeline != null
                || session.spellCast != null
                || session.dodge != null
                || session.guard.active()
                || session.pendingAmmoCycleId != null
                || session.ammoSwitchHandlingUntilTick > tick
                || session.bowRecoveryUntilTick > tick) {
            player.sendActionBar(Component.text("Bow draw is action-locked.", NamedTextColor.RED));
            return;
        }
        if (activeProjectilesFor(player.getUniqueId()) >= maximumActiveProjectilesPerCaster) {
            player.sendActionBar(
                    Component.text(
                            "Projectile limit reached for this caster.", NamedTextColor.RED));
            return;
        }
        session.bowDraw = bowDraws.start(tick);
        session.action = ActionState.CHANNELING;
        session.input.clearBuffer(InputBufferClearReason.ACTION_STARTED);
        player.sendActionBar(
                Component.text("BOW DRAWING — RMB again to release", NamedTextColor.AQUA));
    }

    private void tickBow(Player player, LiveSession session) {
        long tick = plugin.getServer().getCurrentTick();
        if (session.bowDraw == null) {
            if (session.bowRecoveryUntilTick >= 0 && tick >= session.bowRecoveryUntilTick) {
                session.bowRecoveryUntilTick = -1;
                if (!session.action.hardControl()) {
                    session.action = ActionState.IDLE;
                }
            }
            return;
        }
        BowDrawPhase previous = session.bowDraw.phase();
        BowTickResolution resolution =
                bowDraws.tick(session.bowDraw, tick, session.resources.availableStamina());
        spendBowStamina(session, resolution.staminaSpent(), tick);
        if (resolution.loweredForExhaustion()) {
            session.bowDraw = null;
            session.action = ActionState.IDLE;
            session.lastResolution = "BOW CANCELLED exhausted";
            player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.RED));
            return;
        }
        session.bowDraw = resolution.runtime();
        session.action = ActionState.CHANNELING;
        if (previous != session.bowDraw.phase()) {
            player.sendActionBar(
                    Component.text(
                            "BOW "
                                    + session.bowDraw.phase()
                                    + " stamina="
                                    + session.resources.stamina(),
                            session.bowDraw.phase() == BowDrawPhase.STRAINED
                                    ? NamedTextColor.RED
                                    : NamedTextColor.AQUA));
        }
    }

    private void releaseBow(Player player, LiveSession session) {
        long tick = plugin.getServer().getCurrentTick();
        BowReleaseResolution release =
                bowDraws.release(session.bowDraw, tick, session.resources.availableStamina());
        spendBowStamina(session, release.staminaSpent(), tick);
        session.bowDraw = null;
        if (release.outcome() != BowReleaseOutcome.FIRED) {
            session.action = ActionState.IDLE;
            session.lastResolution = "BOW " + release.outcome();
            player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.RED));
            return;
        }
        if (activeProjectilesFor(player.getUniqueId()) >= maximumActiveProjectilesPerCaster) {
            session.action = ActionState.IDLE;
            session.lastResolution = "BOW PROJECTILE_LIMIT";
            player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.RED));
            return;
        }
        DefinitionId selectedAmmo =
                characters.quiverPreparation(player).selectedAmmo().orElse(null);
        QuiverProfile quiver = characters.equippedQuiverProfile(player).orElse(null);
        boolean compatible =
                selectedAmmo != null
                        && quiver != null
                        && characters.quiverAmmoQuantity(player, selectedAmmo) > 0
                        && items.find(selectedAmmo)
                                .flatMap(ItemDefinition::ammoProfile)
                                .filter(ammo -> ammo.family() == trainingBowAmmoFamily)
                                .filter(quiver::supports)
                                .isPresent();
        if (!compatible) {
            session.action = ActionState.IDLE;
            session.lastResolution = quiver == null ? "BOW NO QUIVER" : "BOW NO PREPARED AMMO";
            player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.RED));
            return;
        }
        PendingBowLaunch pending =
                pendingBowLaunch(
                        player, release.shot().orElseThrow(), UUID.randomUUID(), selectedAmmo);
        session.pendingBowLaunch = pending;
        session.action = ActionState.ACTIVE;
        session.lastResolution = "BOW AMMO COMMITTING";
        player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.YELLOW));
        if (pvpInventorySnapshotActive(player)) {
            LoadedCharacterSession character = characters.active(player).orElse(null);
            if (character == null) {
                session.pendingBowLaunch = null;
                session.action = ActionState.IDLE;
                return;
            }
            completeBowAmmoCommit(
                    player.getUniqueId(), session, pending, Result.success(character));
            return;
        }
        characters.consumeAmmo(
                player,
                pending.ammoDefinitionId(),
                pending.projectileId(),
                contentVersion,
                result -> completeBowAmmoCommit(player.getUniqueId(), session, pending, result));
    }

    private void spendBowStamina(LiveSession session, int amount, long tick) {
        if (amount == 0) {
            return;
        }
        session.resources = session.resources.spendStamina(amount).orElseThrow();
        session.lastStaminaSpendTick = tick;
        session.staminaRegenRemainder = 0;
    }

    private PendingBowLaunch pendingBowLaunch(
            Player player, BowShotCharge shot, UUID projectileId, DefinitionId ammoDefinitionId) {
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector look = eye.getDirection().normalize();
        CombatVector direction = new CombatVector(look.getX(), look.getY(), look.getZ());
        CombatVector origin =
                new CombatVector(eye.getX(), eye.getY(), eye.getZ()).add(direction.multiply(0.35));
        return new PendingBowLaunch(
                projectileId,
                player.getWorld().getUID(),
                origin,
                direction,
                shot,
                ammoDefinitionId);
    }

    private void completeBowAmmoCommit(
            UUID playerId,
            LiveSession expectedSession,
            PendingBowLaunch pending,
            Result<LoadedCharacterSession, CharacterSessionErrorCode> result) {
        LiveSession session = sessions.get(playerId);
        if (session != expectedSession || session.pendingBowLaunch != pending) {
            return;
        }
        session.pendingBowLaunch = null;
        Player player = plugin.getServer().getPlayer(playerId);
        if (result
                instanceof
                Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode> failure) {
            session.action = ActionState.IDLE;
            session.lastResolution =
                    failure.error() == CharacterSessionErrorCode.CHARACTER_AMMO_UNAVAILABLE
                            ? "BOW NO AMMO"
                            : "BOW AMMO COMMIT FAILED " + failure.error().code();
            if (player != null && player.isOnline()) {
                player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.RED));
            }
            return;
        }
        if (player == null
                || !player.isOnline()
                || player.isDead()
                || !player.getWorld().getUID().equals(pending.worldId())) {
            session.action = ActionState.IDLE;
            session.lastResolution = "BOW COMMITTED WITHOUT LIVE PROJECTILE";
            return;
        }
        if (activeProjectilesFor(playerId) >= maximumActiveProjectilesPerCaster) {
            session.action = ActionState.IDLE;
            session.lastResolution = "BOW COMMITTED PROJECTILE LIMIT";
            player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.RED));
            return;
        }
        launchTrainingProjectile(player, session, pending);
        long tick = plugin.getServer().getCurrentTick();
        session.bowRecoveryUntilTick = tick + trainingBowMove.phases().recoveryTicks();
        session.action = ActionState.RECOVERY;
    }

    private void launchTrainingProjectile(
            Player player, LiveSession session, PendingBowLaunch pending) {
        MoveDefinition.Hitbox hitbox = trainingBowMove.hitboxes().getFirst();
        MoveDefinition.ProjectileDefinition authored = hitbox.projectile().orElseThrow();
        ProjectileProfile profile =
                new ProjectileProfile(
                        authored.speed(),
                        authored.gravityPerTick(),
                        authored.dragPerTick(),
                        authored.collisionRadius(),
                        authored.lifetimeTicks(),
                        authored.pierceCount());
        ProjectileIdentity identity =
                new ProjectileIdentity(
                        pending.projectileId(),
                        player.getUniqueId(),
                        trainingBowMove.id(),
                        contentVersion,
                        Optional.of(pending.ammoDefinitionId()),
                        hitbox.hitGroup());
        ProjectileRuntime runtime =
                ProjectileRuntime.launch(
                        identity,
                        profile,
                        pending.origin(),
                        pending.direction(),
                        pending.charge().velocityMultiplier());
        activeProjectiles.put(
                pending.projectileId(),
                new LiveProjectile(
                        pending.worldId(),
                        runtime,
                        ProjectileCombatContext.physical(
                                trainingBowPower,
                                trainingBowMove,
                                pending.charge().postureMultiplier(),
                                pending.charge().penetrationPercentage())));
        observeCommittedCombatAction(session, "BOW", pending.projectileId(), trainingBowMove.id());
        markHostile(player, session);
        session.lastResolution =
                "BOW FIRED charge="
                        + roundOne(pending.charge().drawRatio() * 100)
                        + "% projectile="
                        + pending.projectileId().toString().substring(0, 8)
                        + " ammo="
                        + characters.quiverAmmoQuantity(player, pending.ammoDefinitionId());
        player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.GREEN));
    }

    private void cancelBow(LiveSession session, String reason) {
        if (session.bowDraw != null || session.pendingBowLaunch != null) {
            if (session.bowDraw != null) {
                bowDraws.cancel(session.bowDraw, plugin.getServer().getCurrentTick());
            }
            session.bowDraw = null;
            session.pendingBowLaunch = null;
            session.lastResolution = "BOW CANCELLED " + reason;
        }
        session.pendingAmmoCycleId = null;
        session.ammoSwitchHandlingUntilTick = -1;
        session.bowRecoveryUntilTick = -1;
        if (!session.action.hardControl()) {
            session.action = ActionState.IDLE;
        }
    }

    private int activeProjectilesFor(UUID ownerId) {
        return (int)
                activeProjectiles.values().stream()
                        .filter(
                                projectile ->
                                        projectile
                                                .runtime()
                                                .identity()
                                                .ownerEntityId()
                                                .equals(ownerId))
                        .count();
    }

    private void removeOwnerProjectiles(UUID ownerId) {
        activeProjectiles
                .values()
                .removeIf(
                        projectile ->
                                projectile.runtime().identity().ownerEntityId().equals(ownerId));
    }

    private void toggleGuard(Player player, LiveSession session) {
        long tick = plugin.getServer().getCurrentTick();
        Result<CombatInputRequest, InputRejectionCode> observed =
                session.input.observe(
                        new InputObservation(
                                tick,
                                SemanticInput.DEFENSIVE_RESPONSE,
                                DirectionSnapshot.NEUTRAL,
                                "weapon_guard",
                                new InputDeduplicationKey("MAIN_HAND", "GUARD_TOGGLE")));
        if (!(observed instanceof Result.Success<CombatInputRequest, InputRejectionCode> input)) {
            return;
        }
        Result<InputRouteOutcome, InputRejectionCode> routed =
                session.input.routeFrame(List.of(input.value()), routingContext(player, session));
        if (!(routed instanceof Result.Success<InputRouteOutcome, InputRejectionCode>)) {
            player.sendActionBar(Component.text("Guard is action-locked.", NamedTextColor.RED));
            return;
        }
        if (session.guard.active()) {
            releaseGuard(session);
            player.sendActionBar(Component.text("GUARD released", NamedTextColor.GRAY));
            return;
        }
        Optional<GuardEngine> available = guardEngineFor(player);
        if (available.isEmpty()) {
            player.sendActionBar(
                    Component.text(
                            combatReadinessFailure(player)
                                    .orElse("This weapon has no defensive response."),
                            NamedTextColor.RED));
            return;
        }
        refreshGuardEngine(player, session, tick);
        Result<GuardRuntime, GuardErrorCode> started =
                session.guardEngine.start(session.guard, tick);
        if (started instanceof Result.Failure<GuardRuntime, GuardErrorCode> failure) {
            player.sendActionBar(
                    Component.text("Guard rejected: " + failure.error(), NamedTextColor.RED));
            return;
        }
        session.guard = ((Result.Success<GuardRuntime, GuardErrorCode>) started).value();
        player.sendActionBar(Component.text("WEAPON GUARD", NamedTextColor.YELLOW));
    }

    private void releaseGuard(LiveSession session) {
        if (!session.guard.active()) {
            return;
        }
        Result<GuardRuntime, GuardErrorCode> released =
                session.guardEngine.release(session.guard, plugin.getServer().getCurrentTick());
        if (released instanceof Result.Success<GuardRuntime, GuardErrorCode> success) {
            session.guard = success.value();
        }
    }

    private GuardHitRequest guardHitRequest(
            Player defender, LiveSession session, LivingEntity source) {
        return guardHitRequest(
                defender,
                session,
                source,
                trainingIncomingHealthDamage,
                trainingIncomingGuardPressure);
    }

    private GuardHitRequest guardHitRequest(
            Player defender,
            LiveSession session,
            LivingEntity source,
            double incomingHealthDamage,
            double incomingGuardPressure) {
        org.bukkit.Location facingLocation = defender.getLocation().clone();
        facingLocation.setPitch(0);
        org.bukkit.util.Vector facing = facingLocation.getDirection();
        org.bukkit.util.Vector toAttacker =
                source.getLocation().toVector().subtract(defender.getLocation().toVector());
        toAttacker.setY(0);
        if (toAttacker.lengthSquared() < 1.0e-9) {
            toAttacker = facing.clone();
        }
        return new GuardHitRequest(
                incomingHealthDamage,
                incomingGuardPressure,
                true,
                true,
                new CombatVector(facing.getX(), 0, facing.getZ()),
                new CombatVector(toAttacker.getX(), 0, toAttacker.getZ()),
                session.resources.availableStamina());
    }

    private void updateHealthPresentation(Player player, LiveSession session) {
        if (session.health.dead() || player.isDead()) {
            return;
        }
        org.bukkit.attribute.AttributeInstance maximumHealth =
                player.getAttribute(Attribute.MAX_HEALTH);
        double vanillaMaximum = maximumHealth == null ? 20.0 : maximumHealth.getValue();
        double ratio = session.health.current() / playerHealth.profile().maximum();
        double presented = Math.max(0.5, Math.min(vanillaMaximum, vanillaMaximum * ratio));
        if (!player.isHealthScaled()) {
            player.setHealthScaled(true);
        }
        if (Double.compare(player.getHealthScale(), 20.0) != 0) {
            player.setHealthScale(20.0);
        }
        if (Double.compare(player.getHealth(), presented) != 0) {
            player.setHealth(presented);
        }
    }

    private void renderMeleeHitFeedback(LivingEntity entity, double appliedDamage) {
        MeleeHitFeedbackPolicy.forAppliedDamage(appliedDamage)
                .ifPresent(
                        feedback -> {
                            Location location =
                                    entity.getLocation().add(0.0, entity.getHeight() * 0.6, 0.0);
                            entity.getWorld()
                                    .spawnParticle(
                                            Particle.DAMAGE_INDICATOR,
                                            location,
                                            feedback.particleCount(),
                                            0.18,
                                            0.18,
                                            0.18,
                                            0.02);
                            entity.getWorld()
                                    .playSound(
                                            location,
                                            Sound.ENTITY_PLAYER_ATTACK_STRONG,
                                            feedback.volume(),
                                            feedback.pitch());
                        });
    }

    private static double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private InputRoutingContext dodgeRoutingContext(Player player, LiveSession session) {
        InputRoutingContext base = routingContext(player, session);
        if (base.legalNow().contains(SemanticInput.DODGE)) {
            return base;
        }
        java.util.EnumSet<SemanticInput> legal = java.util.EnumSet.copyOf(base.legalNow());
        legal.add(SemanticInput.DODGE);
        return new InputRoutingContext(legal, base.bufferWindowOpen());
    }

    private boolean combatDodgeContext(LiveSession session) {
        return session.engagement.state() != EngagementState.EXPLORATION
                && (session.weapon.state() == WeaponState.READY
                        || session.weapon.state() == WeaponState.DRAWING);
    }

    private boolean dodgeWindowOpen(LiveSession session) {
        if (!combatDodgeContext(session) || session.action.hardControl()) {
            return false;
        }
        return session.timeline == null
                || session.timeline.tick() >= session.timeline.move().cancels().dodgeFromTick();
    }

    private static DirectionSnapshot direction(Input input) {
        double forward = (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0);
        double strafe = (input.isLeft() ? 1 : 0) - (input.isRight() ? 1 : 0);
        return DirectionSnapshot.fromAxes(forward, strafe);
    }

    private static org.bukkit.util.Vector dodgeVector(Player player, DirectionSnapshot direction) {
        org.bukkit.util.Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 1.0e-9) {
            forward = new org.bukkit.util.Vector(0, 0, 1);
        } else {
            forward.normalize();
        }
        org.bukkit.util.Vector left =
                new org.bukkit.util.Vector(forward.getZ(), 0, -forward.getX());
        return switch (direction) {
            case FORWARD -> forward;
            case BACK -> forward.multiply(-1);
            case LEFT -> left;
            case RIGHT -> left.multiply(-1);
            case NEUTRAL -> throw new IllegalArgumentException("neutral dodge direction");
        };
    }

    private void tickEngagement(Player player, LiveSession session) {
        EngagementState previousState = session.engagement.state();
        session.threatOwners.removeIf(
                entityId -> {
                    Entity entity = plugin.getServer().getEntity(entityId);
                    return !(entity instanceof Mob mob)
                            || !entity.isValid()
                            || !player.equals(mob.getTarget());
                });
        session.engagement =
                engagement.tick(
                        session.engagement,
                        plugin.getServer().getCurrentTick(),
                        new EngagementTickContext(
                                !session.threatOwners.isEmpty(),
                                false,
                                session.action == ActionState.DOWNED
                                        || session.action == ActionState.DEAD));
        if (session.engagement.state() != EngagementState.ENGAGED) {
            releaseGuard(session);
        }
        if (previousState != EngagementState.EXPLORATION
                && session.engagement.state() == EngagementState.EXPLORATION) {
            completeOpenCombatEvidence(player, session, EncounterOutcome.RETREAT);
        }
    }

    private void markIncomingHostile(Player player, LiveSession session) {
        markHostile(player, session);
        SelectedHotbarSlot combatSlot = selectedSlot(player, 0);
        boolean combatReady = combatReadinessFailure(player).isEmpty();
        if (!HostileAutoDrawPolicy.shouldBeginDraw(
                session.weapon.state(), combatSlot.kind(), combatReady, session.health.dead())) {
            return;
        }
        player.getInventory().setHeldItemSlot(combatSlot.slot());
        select(session, combatSlot);
    }

    private void markHostile(Player player, LiveSession session) {
        session.engagement =
                engagement.hostileActivity(session.engagement, plugin.getServer().getCurrentTick());
        if (player.getOpenInventory().getTopInventory().getHolder()
                instanceof SceneInventoryHolder) {
            player.closeInventory();
        }
    }

    private void observeCommittedCombatAction(
            LiveSession session, String family, UUID actionId, DefinitionId moveOrSpellId) {
        session.combatEvidence.observeCommittedAction(
                progressionDiscipline(family), actionId, moveOrSpellId);
    }

    private void observeSuccessfulCombatAction(
            Player player,
            LiveSession session,
            LivingEntity target,
            String family,
            UUID actionId,
            DefinitionId moveOrSpellId,
            double demonstratedCapability) {
        boolean observed =
                session.combatEvidence.observeSuccessfulAction(
                        target.getUniqueId(),
                        progressionTargetKind(target),
                        target.getType().name().toLowerCase(java.util.Locale.ROOT),
                        progressionChallenge(target),
                        progressionDiscipline(family),
                        conditioningAxis(family),
                        actionId,
                        moveOrSpellId,
                        demonstratedCapability,
                        combatStress(session, family));
        successfulActionObserver.observe(
                new CharacterId(player.getUniqueId()),
                actionId,
                moveOrSpellId,
                plugin.getServer().getCurrentTick());
        hostileActionObserver.accept(player, "SUCCESSFUL_HOSTILE_ACTION");
        if (!observed) {
            plugin.getLogger()
                    .warning(
                            "Progression evidence target bound reached for "
                                    + player.getUniqueId()
                                    + "; this target will not produce evidence.");
        }
    }

    private void completeCombatTarget(
            Player player, LiveSession session, LivingEntity target, EncounterOutcome outcome) {
        session.combatEvidence.observeStress(combatStress(session, "ENDURANCE"));
        queueProgressionEvidence(
                player,
                session,
                session.combatEvidence.completeTarget(
                        new CharacterId(player.getUniqueId()),
                        target.getUniqueId(),
                        contentVersion,
                        outcome));
    }

    private void completeOpenCombatEvidence(
            Player player, LiveSession session, EncounterOutcome outcome) {
        if (session.combatEvidence.activeTargetCount() == 0) {
            return;
        }
        session.combatEvidence.observeStress(combatStress(session, "ENDURANCE"));
        queueProgressionEvidence(
                player,
                session,
                session.combatEvidence.completeAll(
                        new CharacterId(player.getUniqueId()), contentVersion, outcome));
    }

    private void queueProgressionEvidence(
            Player player, LiveSession session, List<EvidenceCandidate> candidates) {
        if (candidates.isEmpty()) {
            return;
        }
        session.pendingProgressionEvidence.addAll(candidates);
        flushProgressionEvidence(player, session);
    }

    private void flushProgressionEvidence(Player player, LiveSession session) {
        if (session.progressionEvidenceCommitInFlight
                || session.pendingProgressionEvidence.isEmpty()
                || sessions.get(player.getUniqueId()) != session
                || !player.isOnline()) {
            return;
        }
        List<EvidenceCandidate> batch = new ArrayList<>();
        while (!session.pendingProgressionEvidence.isEmpty()
                && batch.size() < MAXIMUM_PROGRESSION_EVIDENCE_BATCH) {
            batch.add(session.pendingProgressionEvidence.removeFirst());
        }
        session.progressionEvidenceCommitInFlight = true;
        characters.recordProgressionEvidence(
                player,
                batch,
                result -> completeProgressionEvidenceFlush(player, session, batch, result));
    }

    private void completeProgressionEvidenceFlush(
            Player player,
            LiveSession session,
            List<EvidenceCandidate> batch,
            Result<ProgressionEvidenceCommitResult, CharacterSessionErrorCode> result) {
        session.progressionEvidenceCommitInFlight = false;
        if (result
                instanceof
                Result.Failure<ProgressionEvidenceCommitResult, CharacterSessionErrorCode>
                        failure) {
            boolean retryable =
                    failure.error() == CharacterSessionErrorCode.CHARACTER_PERSISTENCE_UNAVAILABLE
                            || failure.error()
                                            == CharacterSessionErrorCode
                                                    .CHARACTER_TRANSACTION_REJECTED
                                    && failure.detail().contains("Another durable");
            if (retryable && sessions.get(player.getUniqueId()) == session && player.isOnline()) {
                for (int index = batch.size() - 1; index >= 0; index--) {
                    session.pendingProgressionEvidence.addFirst(batch.get(index));
                }
                plugin.getServer()
                        .getScheduler()
                        .runTaskLater(
                                plugin,
                                () -> flushProgressionEvidence(player, session),
                                PROGRESSION_RETRY_TICKS);
                return;
            }
            plugin.getLogger()
                    .warning(
                            "Combat progression evidence failed for "
                                    + player.getUniqueId()
                                    + ": "
                                    + failure.error().code()
                                    + " "
                                    + failure.detail());
            return;
        }
        List<ProgressionEvidenceExecution> executions =
                ((Result.Success<ProgressionEvidenceCommitResult, CharacterSessionErrorCode>)
                                result)
                        .value()
                        .executions();
        String readiness =
                executions.stream()
                        .filter(execution -> execution.evidence().decision().accepted())
                        .map(
                                execution ->
                                        execution.evidence().candidate().track().id().value()
                                                + "="
                                                + execution.evidence().decision().resultingBand())
                        .distinct()
                        .collect(java.util.stream.Collectors.joining(", "));
        if (!readiness.isBlank() && player.isOnline()) {
            player.sendMessage(
                    Component.text("Combat learning recorded: " + readiness, NamedTextColor.AQUA));
        }
        flushProgressionEvidence(player, session);
    }

    private double combatStress(LiveSession session, String family) {
        CombatResources resources =
                session.timeline == null ? session.resources : session.timeline.resources();
        double healthStress = 1.0 - session.health.current() / playerHealth.profile().maximum();
        double resourceStress =
                "STAFF".equalsIgnoreCase(family)
                        ? 1.0 - resources.mana() / 100.0
                        : 1.0 - resources.stamina() / 100.0;
        return Math.max(0.0, Math.min(1.5, Math.max(healthStress, resourceStress)));
    }

    private static String progressionDiscipline(String family) {
        return family.toLowerCase(java.util.Locale.ROOT);
    }

    private static BodyConditioningAxis conditioningAxis(String family) {
        return switch (family.toUpperCase(java.util.Locale.ROOT)) {
            case "GREATSWORD" -> BodyConditioningAxis.MIGHT;
            case "SWORD_SHIELD" -> BodyConditioningAxis.FORTITUDE;
            case "BOW", "CROSSBOW" -> BodyConditioningAxis.COORDINATION;
            case "STAFF" -> BodyConditioningAxis.COMPOSURE;
            default -> BodyConditioningAxis.ENDURANCE;
        };
    }

    private static EvidenceTargetKind progressionTargetKind(LivingEntity target) {
        if (target.isInvulnerable()) {
            return EvidenceTargetKind.INVULNERABLE_TARGET;
        }
        if (target.getScoreboardTags().contains(TRAINING_DUMMY_TAG)) {
            return EvidenceTargetKind.TRAINING_DUMMY;
        }
        if (target.getScoreboardTags().contains(SELF_CREATED_LOOP_TAG)) {
            return EvidenceTargetKind.SELF_CREATED_LOOP;
        }
        if (target.getScoreboardTags().contains(ZERO_RISK_TAG)) {
            return EvidenceTargetKind.ZERO_RISK_INTERACTION;
        }
        return EvidenceTargetKind.MEANINGFUL_ENCOUNTER;
    }

    private static double progressionChallenge(LivingEntity target) {
        double health = attributeValue(target, Attribute.MAX_HEALTH);
        double attack = attributeValue(target, Attribute.ATTACK_DAMAGE);
        double armor = attributeValue(target, Attribute.ARMOR);
        return Math.max(1.0, health * 2.0 + attack * 6.0 + armor * 3.0);
    }

    private static double attributeValue(LivingEntity target, Attribute attribute) {
        return target.getAttribute(attribute) == null
                ? 0.0
                : Objects.requireNonNull(target.getAttribute(attribute)).getValue();
    }

    private void pollBuffered(Player player, LiveSession session) {
        Result<InputRouteOutcome, InputRejectionCode> result =
                session.input.pollBuffered(
                        plugin.getServer().getCurrentTick(), routingContext(player, session));
        if (result instanceof Result.Success<InputRouteOutcome, InputRejectionCode> success) {
            handleRoute(player, session, success.value());
        }
    }

    private void handleRoute(Player player, LiveSession session, InputRouteOutcome outcome) {
        if (outcome.decision() == com.branz.mmorpg.combat.input.InputRouteDecision.EXECUTED) {
            startMove(player, session);
        } else {
            player.sendActionBar(Component.text("Primary opener buffered.", NamedTextColor.YELLOW));
        }
    }

    private void startMove(Player player, LiveSession session) {
        MoveDefinition activeMove = primaryMove(player).orElse(null);
        if (activeMove == null) {
            player.sendActionBar(
                    Component.text(
                            combatReadinessFailure(player)
                                    .orElse("Primary strike requires a supported training weapon."),
                            NamedTextColor.RED));
            return;
        }
        if (session.action != ActionState.IDLE
                || session.timeline != null
                || session.bowDraw != null
                || session.spellCast != null
                || session.dodge != null
                || session.guard.active()) {
            if (session.dodge != null) {
                player.sendActionBar(
                        Component.text("Attack locked during dodge recovery.", NamedTextColor.RED));
            }
            if (session.guard.active()) {
                player.sendActionBar(
                        Component.text("Release guard before attacking.", NamedTextColor.RED));
            }
            return;
        }
        Result<ActionTimeline, ActionTimelineErrorCode> started =
                ActionTimeline.start(activeMove, session.resources);
        if (started instanceof Result.Failure<ActionTimeline, ActionTimelineErrorCode> failure) {
            player.sendActionBar(
                    Component.text("Move rejected: " + failure.error().code(), NamedTextColor.RED));
            return;
        }
        session.timeline =
                ((Result.Success<ActionTimeline, ActionTimelineErrorCode>) started).value();
        session.activeMoveEvidenceActionId = UUID.randomUUID();
        session.previousActionTransform = combatTransform(player);
        session.activeTraceInitialResources = session.resources;
        session.activeTraceCommands.clear();
        session.action = actionState(session.timeline.phase());
        player.sendActionBar(
                Component.text(
                        activeMove.id().value()
                                + " started; stamina reserved="
                                + activeMove.costs().stamina(),
                        NamedTextColor.AQUA));
    }

    private void cancelAction(LiveSession session, String reason) {
        if (session.timeline == null) {
            return;
        }
        int cancellationTick = session.timeline.tick();
        Result<ActionTimeline, ActionTimelineErrorCode> cancelled = session.timeline.cancel(reason);
        if (cancelled instanceof Result.Success<ActionTimeline, ActionTimelineErrorCode> success) {
            session.resources = success.value().resources();
            session.activeTraceCommands.add(
                    new ActionSimulationCommand(
                            cancellationTick, ActionSimulationCommand.Type.CANCEL, reason));
            finishTrace(session, success.value());
        }
        session.timeline = null;
        session.previousActionTransform = null;
        session.activeMoveEvidenceActionId = null;
        session.action = ActionState.IDLE;
    }

    private void finishTrace(LiveSession session, ActionTimeline terminalTimeline) {
        if (session.activeTraceInitialResources == null) {
            return;
        }
        session.lastTrace =
                new CombatTrace(
                        contentVersion,
                        terminalTimeline.move().id(),
                        session.activeTraceInitialResources,
                        session.activeTraceCommands,
                        terminalTimeline.trace(),
                        terminalTimeline.resources(),
                        terminalTimeline.phase());
        session.activeTraceInitialResources = null;
        session.activeTraceCommands.clear();
    }

    private Optional<SemanticInput> resolvedIntent(
            Player player, LiveSession session, ClientAction action) {
        Result<SemanticInput, InputRejectionCode> resolved =
                inputPolicy.resolve(action, policyContext(player, session));
        return resolved instanceof Result.Success<SemanticInput, InputRejectionCode> success
                ? Optional.of(success.value())
                : Optional.empty();
    }

    private InputRoutingContext routingContext(Player player, LiveSession session) {
        if (session.dodge != null) {
            return InputRoutingContext.legal(
                    java.util.EnumSet.of(
                            SemanticInput.FORCED_INTERRUPT, SemanticInput.UI_DANGER_CLOSE));
        }
        return inputPolicy.routingContext(policyContext(player, session), false);
    }

    private InputPolicyContext policyContext(Player player, LiveSession session) {
        return policyContext(player, session, DirectionSnapshot.NEUTRAL);
    }

    private InputPolicyContext policyContext(
            Player player, LiveSession session, DirectionSnapshot direction) {
        UiState ui =
                player.getOpenInventory().getTopInventory().getHolder()
                                instanceof SceneInventoryHolder
                        ? UiState.SCENE
                        : UiState.NONE;
        return new InputPolicyContext(
                session.engagement.state(),
                session.weapon.state(),
                session.action,
                ui,
                false,
                direction);
    }

    private SelectedHotbarSlot selectedSlot(Player player, int slot) {
        if (slot == ChronicleService.HOTBAR_SLOT) {
            return new SelectedHotbarSlot(slot, SelectedSlotKind.CHRONICLE);
        }
        boolean mainHandEquipped =
                characters
                        .active(player)
                        .flatMap(
                                session ->
                                        session.snapshot()
                                                .equipment()
                                                .item(EquipmentSlot.MAIN_HAND))
                        .isPresent();
        return mainHandEquipped && slot == 0
                ? SelectedHotbarSlot.combatWeapon(slot)
                : new SelectedHotbarSlot(slot, SelectedSlotKind.TOOL_OR_BLOCK);
    }

    private Optional<String> equippedWeaponFamily(Player player) {
        return equippedDefinition(player, EquipmentSlot.MAIN_HAND)
                .flatMap(ItemDefinition::weaponProfile)
                .map(WeaponCombatProfile::family);
    }

    private Optional<ItemDefinition> equippedDefinition(Player player, EquipmentSlot slot) {
        return characters
                .active(player)
                .flatMap(
                        session ->
                                session.snapshot()
                                        .equipment()
                                        .item(slot)
                                        .flatMap(
                                                itemId ->
                                                        session.snapshot().itemRecords().stream()
                                                                .filter(
                                                                        record ->
                                                                                record.itemId()
                                                                                        .equals(
                                                                                                itemId))
                                                                .findFirst()
                                                                .flatMap(
                                                                        record ->
                                                                                items.find(
                                                                                        record
                                                                                                .definitionId()))));
    }

    private Optional<String> combatReadinessFailure(Player player) {
        ItemDefinition main = equippedDefinition(player, EquipmentSlot.MAIN_HAND).orElse(null);
        if (main == null || main.weaponProfile().isEmpty()) {
            return Optional.empty();
        }
        Result<WeaponLoadoutResolution, WeaponLoadoutErrorCode> resolved =
                WeaponLoadoutPolicy.resolve(
                        main, equippedDefinition(player, EquipmentSlot.OFF_HAND));
        if (resolved
                instanceof
                Result.Failure<WeaponLoadoutResolution, WeaponLoadoutErrorCode> failure) {
            return Optional.of("Combat not ready: " + failure.detail());
        }
        String family =
                ((Result.Success<WeaponLoadoutResolution, WeaponLoadoutErrorCode>) resolved)
                        .value()
                        .weapon()
                        .family();
        LoadedCharacterSession character = characters.active(player).orElse(null);
        if (character == null) {
            return Optional.of("Combat not ready: character build is unavailable.");
        }
        Result<BuildResolution, BuildErrorCode> build =
                builds.resolve(character.snapshot().build(), family, learnedKnowledge(character));
        return build instanceof Result.Failure<BuildResolution, BuildErrorCode> failure
                ? Optional.of("Combat not ready: " + failure.detail())
                : Optional.empty();
    }

    private Optional<GuardEngine> guardEngineFor(Player player) {
        if (combatReadinessFailure(player).isPresent()) {
            return Optional.empty();
        }
        ItemDefinition main = equippedDefinition(player, EquipmentSlot.MAIN_HAND).orElse(null);
        if (main == null) {
            return Optional.empty();
        }
        Result<WeaponLoadoutResolution, WeaponLoadoutErrorCode> result =
                WeaponLoadoutPolicy.resolve(
                        main, equippedDefinition(player, EquipmentSlot.OFF_HAND));
        if (!(result
                instanceof
                Result.Success<WeaponLoadoutResolution, WeaponLoadoutErrorCode> success)) {
            return Optional.empty();
        }
        WeaponCombatProfile weapon = success.value().weapon();
        if (weapon.family().equals("SWORD")) {
            return Optional.of(defaultGuards);
        }
        return success.value().guardProfile().map(value -> new GuardEngine(toGuardProfile(value)));
    }

    private String guardAuthorityKey(Player player) {
        Optional<GuardEngine> engine = guardEngineFor(player);
        if (engine.isEmpty()) {
            return "NONE";
        }
        String family = equippedWeaponFamily(player).orElse("NONE");
        String offHand =
                characters
                        .active(player)
                        .flatMap(
                                session ->
                                        session.snapshot().equipment().item(EquipmentSlot.OFF_HAND))
                        .map(ItemId::toString)
                        .orElse("EMPTY");
        return family + ":" + offHand + ":" + engine.orElseThrow().profile();
    }

    private void refreshGuardEngine(Player player, LiveSession session, long tick) {
        String key = guardAuthorityKey(player);
        if (key.equals(session.guardAuthorityKey)) {
            return;
        }
        session.guardEngine = guardEngineFor(player).orElse(defaultGuards);
        session.guard = GuardRuntime.initial(session.guardEngine.profile(), tick);
        session.guardAuthorityKey = key;
    }

    private static GuardProfile toGuardProfile(GuardCombatProfile profile) {
        return new GuardProfile(
                profile.coneDegrees(),
                profile.physicalBlockRatio(),
                profile.perfectWindowTicks(),
                profile.maximumStability(),
                profile.recoveryDelayTicks(),
                profile.inactiveRecoveryPerSecond(),
                profile.activeRecoveryPerSecond(),
                profile.breakTicks(),
                profile.stabilityAfterBreak());
    }

    private double meleePower(String family) {
        return switch (family) {
            case "GREATSWORD" -> trainingGreatswordPower;
            case "SWORD_SHIELD" -> trainingSwordShieldPower;
            case "STAFF" -> trainingStaffPower;
            default -> trainingWeaponPower;
        };
    }

    private void regenerateMana(LiveSession session) {
        if (session.health.dead()
                || session.spellCast != null
                || session.resources.mana() >= session.resources.maximumMana()) {
            return;
        }
        long currentTick = plugin.getServer().getCurrentTick();
        boolean engaged = session.engagement.state() == EngagementState.ENGAGED;
        if (engaged && currentTick - session.lastManaCommitTick < 60) {
            return;
        }
        session.manaRegenRemainder += (engaged ? 2.0 : 8.0) / 20.0;
        int whole = (int) session.manaRegenRemainder;
        if (whole > 0) {
            session.resources = session.resources.restoreMana(whole);
            session.manaRegenRemainder -= whole;
        }
    }

    private Optional<MoveDefinition> primaryMove(Player player) {
        if (combatReadinessFailure(player).isPresent()) {
            return Optional.empty();
        }
        return equippedWeaponFamily(player)
                .flatMap(
                        family -> {
                            MoveDefinition fallback =
                                    switch (family) {
                                        case "SWORD" -> trainingMove;
                                        case "GREATSWORD" -> trainingGreatswordMove;
                                        case "SWORD_SHIELD" -> trainingSwordShieldMove;
                                        case "STAFF" -> trainingStaffMove;
                                        default -> null;
                                    };
                            if (fallback == null) {
                                return Optional.empty();
                            }
                            return buildResolution(player, family)
                                    .flatMap(
                                            resolution ->
                                                    Optional.ofNullable(
                                                                    resolution
                                                                            .resolvedMoves()
                                                                            .get(
                                                                                    MovesetBranch
                                                                                            .PRIMARY_1))
                                                            .flatMap(moves::find)
                                                            .or(() -> Optional.of(fallback)))
                                    .map(move -> moveWithBuildCosts(player, family, move));
                        });
    }

    private Optional<BuildResolution> buildResolution(Player player, String family) {
        return characters
                .active(player)
                .flatMap(
                        character -> {
                            Result<BuildResolution, BuildErrorCode> result =
                                    builds.resolve(
                                            character.snapshot().build(),
                                            family,
                                            learnedKnowledge(character));
                            return result
                                            instanceof
                                            Result.Success<BuildResolution, BuildErrorCode> success
                                    ? Optional.of(success.value())
                                    : Optional.empty();
                        });
    }

    private MoveDefinition moveWithBuildCosts(Player player, String family, MoveDefinition move) {
        BuildResolution build = buildResolution(player, family).orElse(null);
        if (build == null) {
            return move;
        }
        MoveDefinition.ResourceCost costs = move.costs();
        int stamina = build.scaleStaminaCost(costs.stamina());
        int setupStamina = Math.min(stamina, build.scaleStaminaCost(costs.setupStamina()));
        int mana = build.scaleManaCost(costs.mana());
        if (stamina == costs.stamina()
                && setupStamina == costs.setupStamina()
                && mana == costs.mana()) {
            return move;
        }
        return new MoveDefinition(
                move.id(),
                move.family(),
                move.input(),
                move.phases(),
                move.commitTick(),
                new MoveDefinition.ResourceCost(stamina, mana, costs.health(), setupStamina),
                move.movement(),
                move.hitboxes(),
                move.outputs(),
                move.cancels(),
                move.interruptResistance(),
                move.presentationArchetype(),
                move.profiles());
    }

    private static Set<KnowledgeKey> learnedKnowledge(LoadedCharacterSession character) {
        return character.snapshot().learnedKnowledge().stream()
                .map(record -> record.knowledge())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static SpellDefinition spellWithBuildCosts(
            SpellDefinition spell, BuildResolution build) {
        int manaCost = build.scaleManaCost(spell.manaCost());
        Optional<SpellDefinition.Channel> channel =
                spell.channel()
                        .map(
                                value ->
                                        new SpellDefinition.Channel(
                                                value.pulseIntervalTicks(),
                                                value.maximumPulses(),
                                                build.scaleManaCost(value.manaPerPulse()),
                                                value.range(),
                                                value.maximumTargetsPerPulse()));
        if (manaCost == spell.manaCost() && channel.equals(spell.channel())) {
            return spell;
        }
        return new SpellDefinition(
                spell.id(),
                spell.artId(),
                spell.castType(),
                spell.targetType(),
                spell.deliveryType(),
                spell.requirements(),
                manaCost,
                spell.phases(),
                spell.interruption(),
                spell.projectile(),
                spell.direct(),
                channel,
                spell.zone(),
                spell.imbuement(),
                spell.output(),
                spell.presentationArchetype(),
                spell.profiles());
    }

    private void select(LiveSession session, SelectedHotbarSlot selected) {
        Result<WeaponTransitionSnapshot, WeaponTransitionErrorCode> result =
                weapons.select(session.weapon, selected);
        if (result
                instanceof
                Result.Success<WeaponTransitionSnapshot, WeaponTransitionErrorCode> success) {
            session.weapon = success.value();
        }
    }

    private static ActionState actionState(ActionPhase phase) {
        return switch (phase) {
            case WINDUP -> ActionState.WINDUP;
            case ACTIVE -> ActionState.ACTIVE;
            case RECOVERY -> ActionState.RECOVERY;
            case COMPLETE, CANCELLED -> ActionState.IDLE;
        };
    }

    private record CombatTransform(UUID worldId, CombatVector origin, CombatVector forward) {
        private CombatTransform {
            Objects.requireNonNull(worldId, "worldId");
            Objects.requireNonNull(origin, "origin");
            forward = Objects.requireNonNull(forward, "forward").normalizedHorizontal();
        }
    }

    private record LiveProjectile(
            UUID worldId, ProjectileRuntime runtime, ProjectileCombatContext context) {
        private LiveProjectile {
            Objects.requireNonNull(worldId, "worldId");
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(context, "context");
        }
    }

    private void removeOwnerSpellEffects(UUID ownerId) {
        activeZones.values().removeIf(zone -> zone.ownerId().equals(ownerId));
    }

    private record LiveSpellZone(
            UUID ownerId, UUID worldId, CombatVector origin, ZoneRuntime runtime) {
        private LiveSpellZone {
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(worldId, "worldId");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(runtime, "runtime");
        }
    }

    private void handleStaffUse(Player player, LiveSession session) {
        long tick = plugin.getServer().getCurrentTick();
        if (session.pendingSpellCommit != null) {
            player.sendActionBar(Component.text("SPELL COMMITTING", NamedTextColor.YELLOW));
            return;
        }
        if (session.spellCast == null) {
            startStaffCast(player, session, tick);
            return;
        }
        if (session.spellCast.phase() == SpellCastPhase.CHANNELING) {
            session.spellCast = spellCasts.stopChannel(session.spellCast, tick);
            session.resources = session.spellCast.resources();
            session.action = ActionState.RECOVERY;
            session.lastResolution = spellLabel(session.spellCast.spell()) + " CHANNEL ENDED";
            player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.GOLD));
            return;
        }
        beginStaffSpellCommit(player, session, tick);
    }

    private void startStaffCast(Player player, LiveSession session, long tick) {
        if (session.action != ActionState.IDLE
                || session.timeline != null
                || session.bowDraw != null
                || session.crossbow != null
                        && (session.crossbow.phase() == CrossbowPhase.COCKING
                                || session.crossbow.phase() == CrossbowPhase.LOCKING)
                || session.dodge != null
                || session.guard.active()
                || session.ammoSwitchHandlingUntilTick > tick) {
            player.sendActionBar(Component.text("STAFF CAST LOCKED", NamedTextColor.RED));
            return;
        }
        CatalystDurability durability;
        try {
            durability =
                    characters
                            .equippedCatalystDurability(player, trainingStaffMaximumDurability)
                            .orElse(null);
        } catch (IllegalArgumentException exception) {
            player.sendActionBar(Component.text("CATALYST STATE INVALID", NamedTextColor.RED));
            return;
        }
        if (durability == null
                || durability.current() < trainingStaffCatalyst.durabilityCostPerCommit()) {
            player.sendActionBar(Component.text("CATALYST BROKEN", NamedTextColor.RED));
            return;
        }
        LoadedCharacterSession character = characters.active(player).orElse(null);
        SpellDefinition selected = selectedStaffSpell(player, session).orElse(null);
        if (character == null || selected == null) {
            player.sendActionBar(
                    Component.text("ATTUNE A STAFF SPELL AT REST", NamedTextColor.RED));
            return;
        }
        BuildResolution build = buildResolution(player, "STAFF").orElse(null);
        if (build == null) {
            player.sendActionBar(Component.text("BUILD NOT READY", NamedTextColor.RED));
            return;
        }
        SpellDefinition activeSpell = spellWithBuildCosts(selected, build);
        Result<SpellCastRuntime, SpellCastErrorCode> started =
                spellCasts.start(
                        activeSpell,
                        session.resources,
                        trainingStaffCatalyst.tags(),
                        character.snapshot().build().attunementCapacity(),
                        tick);
        if (!(started instanceof Result.Success<SpellCastRuntime, SpellCastErrorCode> success)) {
            SpellCastErrorCode error =
                    ((Result.Failure<SpellCastRuntime, SpellCastErrorCode>) started).error();
            player.sendActionBar(
                    Component.text("SPELL REJECTED " + error.code(), NamedTextColor.RED));
            return;
        }
        session.spellCast = success.value();
        session.spellOrigin =
                new CombatVector(
                        player.getLocation().getX(),
                        player.getLocation().getY(),
                        player.getLocation().getZ());
        session.resources = session.spellCast.resources();
        session.action =
                session.spellCast.phase() == SpellCastPhase.READY
                        ? ActionState.ACTIVE
                        : ActionState.WINDUP;
        session.lastResolution =
                spellLabel(activeSpell)
                        + " "
                        + session.spellCast.phase()
                        + " mana-reserved="
                        + activeSpell.manaCost();
        player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.GOLD));
        if (session.spellCast.phase() == SpellCastPhase.READY) {
            beginStaffSpellCommit(player, session, tick);
        }
    }

    private void tickSpell(Player player, LiveSession session) {
        if (session.spellCast == null || session.pendingSpellCommit != null) {
            return;
        }
        if (session.spellCast.spell().interruption().movement() && session.spellOrigin != null) {
            Location current = player.getLocation();
            CombatVector delta =
                    new CombatVector(current.getX(), current.getY(), current.getZ())
                            .subtract(session.spellOrigin);
            if (delta.length() > 0.2) {
                cancelSpell(session, "MOVEMENT");
                player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.RED));
                return;
            }
        }
        long tick = plugin.getServer().getCurrentTick();
        SpellCastPhase prior = session.spellCast.phase();
        session.spellCast = spellCasts.advance(session.spellCast, tick);
        session.resources = session.spellCast.resources();
        SpellDefinition spell = session.spellCast.spell();
        if (session.spellCast.phase() == SpellCastPhase.READY) {
            beginStaffSpellCommit(player, session, tick);
            return;
        }
        if (session.spellCast.phase() == SpellCastPhase.CHARGING) {
            session.action = ActionState.CHANNELING;
            if (prior != SpellCastPhase.CHARGING) {
                session.lastResolution = spellLabel(spell) + " CHARGING";
                player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.GOLD));
            }
            if (spellCasts.maximumChargeReached(session.spellCast, tick)) {
                beginStaffSpellCommit(player, session, tick);
            }
            return;
        }
        if (session.spellCast.phase() == SpellCastPhase.CHANNELING) {
            session.action = ActionState.CHANNELING;
            ChannelPulseResolution pulse = spellCasts.pulse(session.spellCast, tick);
            session.spellCast = pulse.runtime();
            session.resources = session.spellCast.resources();
            if (pulse.pulseEmitted()) {
                applyChannelPulse(player, session, spell);
                session.lastManaCommitTick = tick;
            }
            if (pulse.endedForInsufficientMana()) {
                session.lastResolution = spellLabel(spell) + " ENDED: NO MANA";
                player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.RED));
            }
            if (session.spellCast.phase() == SpellCastPhase.RECOVERY) {
                session.action = ActionState.RECOVERY;
            }
            return;
        }
        if (session.spellCast.phase() == SpellCastPhase.RECOVERY) {
            session.action = ActionState.RECOVERY;
        } else if (session.spellCast.phase() == SpellCastPhase.COMPLETE) {
            session.resources = session.spellCast.resources();
            session.spellCast = null;
            session.spellOrigin = null;
            session.spellEvidenceActionId = null;
            session.action = ActionState.IDLE;
        }
    }

    private void beginStaffSpellCommit(Player player, LiveSession session, long tick) {
        if (session.spellCast == null || !spellCasts.canRelease(session.spellCast, tick)) {
            player.sendActionBar(Component.text("SPELL NOT READY", NamedTextColor.RED));
            return;
        }
        SpellDefinition spell = session.spellCast.spell();
        if (spell.deliveryType() == SpellDeliveryType.PROJECTILE
                && activeProjectilesFor(player.getUniqueId())
                        >= maximumActiveProjectilesPerCaster) {
            player.sendActionBar(
                    Component.text(
                            "Projectile limit reached for this caster.", NamedTextColor.RED));
            return;
        }
        if (spell.deliveryType() == SpellDeliveryType.ZONE
                && activeZonesFor(player.getUniqueId()) >= MAXIMUM_ACTIVE_ZONES_PER_CASTER) {
            player.sendActionBar(
                    Component.text("Zone limit reached for this caster.", NamedTextColor.RED));
            return;
        }
        ItemId catalystItemId = characters.equippedMainHandItemId(player).orElse(null);
        if (catalystItemId == null) {
            cancelSpell(session, "CATALYST_MISSING");
            return;
        }
        UUID effectId = UUID.randomUUID();
        PendingSpellCommit pending =
                new PendingSpellCommit(
                        effectId, catalystItemId, pendingSpellLaunch(player, effectId));
        session.pendingSpellCommit = pending;
        session.spellCommitInterrupted = false;
        session.action = ActionState.ACTIVE;
        session.lastResolution = spellLabel(spell) + " COMMITTING";
        player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.YELLOW));
        if (pvpInventorySnapshotActive(player)) {
            LoadedCharacterSession character = characters.active(player).orElse(null);
            if (character == null) {
                session.pendingSpellCommit = null;
                cancelSpell(session, "CHARACTER_NOT_READY");
                return;
            }
            completeStaffSpellCommit(
                    player.getUniqueId(), session, pending, Result.success(character));
            return;
        }
        characters.commitCatalystUse(
                player,
                catalystItemId,
                TRAINING_STAFF_ITEM,
                trainingStaffMaximumDurability,
                trainingStaffCatalyst.durabilityCostPerCommit(),
                spell.id(),
                effectId,
                contentVersion,
                result -> completeStaffSpellCommit(player.getUniqueId(), session, pending, result));
    }

    private void completeStaffSpellCommit(
            UUID playerId,
            LiveSession expectedSession,
            PendingSpellCommit pending,
            Result<LoadedCharacterSession, CharacterSessionErrorCode> result) {
        LiveSession session = sessions.get(playerId);
        if (session != expectedSession || session.pendingSpellCommit != pending) {
            return;
        }
        session.pendingSpellCommit = null;
        Player player = plugin.getServer().getPlayer(playerId);
        if (result
                instanceof
                Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode> failure) {
            cancelSpell(session, "COMMIT_FAILED_" + failure.error().code());
            if (player != null && player.isOnline()) {
                player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.RED));
            }
            return;
        }
        long tick = plugin.getServer().getCurrentTick();
        Result<SpellCastRuntime, SpellCastErrorCode> released =
                spellCasts.release(session.spellCast, tick);
        if (!(released instanceof Result.Success<SpellCastRuntime, SpellCastErrorCode> success)) {
            session.spellCast = null;
            session.spellOrigin = null;
            session.action = ActionState.IDLE;
            session.lastResolution = "SPELL RELEASE FAILED AFTER COMMIT";
            return;
        }
        session.spellCast = success.value();
        session.resources = session.spellCast.resources();
        session.lastManaCommitTick = tick;
        session.manaRegenRemainder = 0;
        session.spellEvidenceActionId = pending.launch().effectId();
        observeCommittedCombatAction(
                session, "STAFF", pending.launch().effectId(), session.spellCast.spell().id());
        if (session.spellCommitInterrupted
                || player == null
                || !player.isOnline()
                || player.isDead()
                || !player.getWorld().getUID().equals(pending.launch().worldId())) {
            session.spellCast = null;
            session.spellOrigin = null;
            session.spellEvidenceActionId = null;
            if (!session.action.hardControl()) {
                session.action = ActionState.IDLE;
            }
            session.lastResolution = "SPELL COMMITTED WITHOUT LIVE EFFECT";
            return;
        }
        commitStaffSpellEffect(player, session, pending.launch());
        session.action =
                session.spellCast.phase() == SpellCastPhase.CHANNELING
                        ? ActionState.CHANNELING
                        : ActionState.RECOVERY;
        player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.GOLD));
    }

    private PendingSpellLaunch pendingSpellLaunch(Player player, UUID effectId) {
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector look = eye.getDirection().normalize();
        CombatVector direction = new CombatVector(look.getX(), look.getY(), look.getZ());
        CombatVector origin =
                new CombatVector(eye.getX(), eye.getY(), eye.getZ()).add(direction.multiply(0.35));
        return new PendingSpellLaunch(effectId, player.getWorld().getUID(), origin, direction);
    }

    private void launchStaffSpellProjectile(
            Player player, LiveSession session, PendingSpellLaunch pending) {
        SpellDefinition spell = session.spellCast.spell();
        SpellDefinition.Projectile authored = spell.projectile().orElseThrow();
        ProjectileProfile profile =
                new ProjectileProfile(
                        authored.speed(),
                        authored.gravityPerTick(),
                        authored.dragPerTick(),
                        authored.collisionRadius(),
                        authored.lifetimeTicks(),
                        authored.pierceCount());
        ProjectileIdentity identity =
                new ProjectileIdentity(
                        pending.effectId(),
                        player.getUniqueId(),
                        spell.id(),
                        contentVersion,
                        Optional.empty(),
                        authored.hitGroup());
        ProjectileRuntime runtime =
                ProjectileRuntime.launch(
                        identity, profile, pending.origin(), pending.direction(), 1.0);
        activeProjectiles.put(
                pending.effectId(),
                new LiveProjectile(
                        pending.worldId(),
                        runtime,
                        ProjectileCombatContext.arcane(trainingStaffPower, spell)));
        markHostile(player, session);
        CatalystDurability durability =
                characters
                        .equippedCatalystDurability(player, trainingStaffMaximumDurability)
                        .orElseThrow();
        session.lastResolution =
                spellLabel(spell)
                        + " RELEASED mana="
                        + session.resources.mana()
                        + " catalyst="
                        + durability.current()
                        + "/"
                        + durability.maximum();
    }

    private void commitStaffSpellEffect(
            Player player, LiveSession session, PendingSpellLaunch pending) {
        SpellDefinition spell = session.spellCast.spell();
        switch (spell.deliveryType()) {
            case PROJECTILE -> launchStaffSpellProjectile(player, session, pending);
            case DIRECT -> applyDirectSpell(player, session, spell, pending.effectId());
            case ZONE -> launchSpellZone(player, session, pending, spell);
            case BEAM -> {
                session.lastResolution =
                        spellLabel(spell) + " CHANNEL STARTED mana=" + session.resources.mana();
                markHostile(player, session);
            }
            case IMBUE -> {
                session.imbuement = imbuements.start(spell, plugin.getServer().getCurrentTick());
                session.lastResolution =
                        spellLabel(spell)
                                + " APPLIED charges="
                                + session.imbuement.remainingCharges();
            }
            case SUMMON -> throw new IllegalStateException("Training Staff has no SUMMON spell");
            default ->
                    throw new IllegalStateException(
                            "Training Staff has unsupported delivery " + spell.deliveryType());
        }
    }

    private void applyDirectSpell(
            Player player, LiveSession session, SpellDefinition spell, UUID actionId) {
        SpellDefinition.Direct direct = spell.direct().orElseThrow();
        Location eye = player.getEyeLocation();
        RayTraceResult trace =
                player.getWorld()
                        .rayTraceEntities(
                                eye,
                                eye.getDirection(),
                                direct.range(),
                                0.35,
                                entity ->
                                        entity instanceof LivingEntity
                                                && eligibleCombatTarget(
                                                        player, (LivingEntity) entity));
        if (trace == null || !(trace.getHitEntity() instanceof LivingEntity target)) {
            session.lastResolution = spellLabel(spell) + " MISS";
            return;
        }
        applyArcaneSpellHit(player, target, spell, spell.output().powerCoefficient(), actionId);
        markHostile(player, session);
    }

    private void launchSpellZone(
            Player player, LiveSession session, PendingSpellLaunch pending, SpellDefinition spell) {
        SpellDefinition.Zone profile = spell.zone().orElseThrow();
        Location eye = player.getEyeLocation();
        RayTraceResult trace =
                player.getWorld()
                        .rayTraceBlocks(
                                eye,
                                eye.getDirection(),
                                profile.placementRange(),
                                FluidCollisionMode.NEVER,
                                true);
        org.bukkit.util.Vector position =
                trace == null
                        ? eye.toVector()
                                .add(
                                        eye.getDirection()
                                                .normalize()
                                                .multiply(profile.placementRange()))
                        : trace.getHitPosition();
        CombatVector origin = new CombatVector(position.getX(), position.getY(), position.getZ());
        ZoneRuntime runtime = zones.start(spell, plugin.getServer().getCurrentTick());
        activeZones.put(
                pending.effectId(),
                new LiveSpellZone(player.getUniqueId(), pending.worldId(), origin, runtime));
        session.lastResolution =
                spellLabel(spell) + " ZONE ACTIVE duration=" + profile.durationTicks() + "t";
        markHostile(player, session);
    }

    private void applyChannelPulse(Player player, LiveSession session, SpellDefinition spell) {
        SpellDefinition.Channel channel = spell.channel().orElseThrow();
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector direction = eye.getDirection().normalize();
        player.getWorld()
                .spawnParticle(
                        Particle.FLAME,
                        eye.clone().add(direction.clone().multiply(channel.range() / 2)),
                        10,
                        0.25,
                        0.25,
                        0.25,
                        0.02);
        RayTraceResult trace =
                player.getWorld()
                        .rayTraceEntities(
                                eye,
                                direction,
                                channel.range(),
                                0.4,
                                entity ->
                                        entity instanceof LivingEntity
                                                && eligibleCombatTarget(
                                                        player, (LivingEntity) entity));
        if (trace != null && trace.getHitEntity() instanceof LivingEntity target) {
            applyArcaneSpellHit(
                    player,
                    target,
                    spell,
                    spell.output().powerCoefficient(),
                    Objects.requireNonNull(session.spellEvidenceActionId));
            markHostile(player, session);
        } else {
            session.lastResolution =
                    spellLabel(spell) + " PULSE " + session.spellCast.pulsesCompleted() + " MISS";
        }
    }

    private void tickSpellZones() {
        long tick = plugin.getServer().getCurrentTick();
        java.util.Iterator<Map.Entry<UUID, LiveSpellZone>> iterator =
                activeZones.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, LiveSpellZone> entry = iterator.next();
            LiveSpellZone live = entry.getValue();
            Player owner = plugin.getServer().getPlayer(live.ownerId());
            org.bukkit.World world = plugin.getServer().getWorld(live.worldId());
            if (owner == null || !owner.isOnline() || world == null) {
                iterator.remove();
                continue;
            }
            ZoneTickResolution advanced = zones.advance(live.runtime(), tick);
            if (advanced.runtime().expired()) {
                iterator.remove();
                continue;
            }
            entry.setValue(
                    new LiveSpellZone(
                            live.ownerId(), live.worldId(), live.origin(), advanced.runtime()));
            if (!advanced.pulseEmitted()) {
                continue;
            }
            SpellDefinition spell = advanced.runtime().spell();
            SpellDefinition.Zone profile = spell.zone().orElseThrow();
            Location center =
                    new Location(world, live.origin().x(), live.origin().y(), live.origin().z());
            world.spawnParticle(
                    Particle.FLAME,
                    center,
                    18,
                    profile.radius() / 2,
                    0.2,
                    profile.radius() / 2,
                    0.02);
            world.getNearbyEntities(center, profile.radius(), 2.5, profile.radius()).stream()
                    .filter(LivingEntity.class::isInstance)
                    .map(LivingEntity.class::cast)
                    .filter(target -> eligibleCombatTarget(owner, target))
                    .sorted(
                            java.util.Comparator.<LivingEntity>comparingDouble(
                                            target -> target.getLocation().distanceSquared(center))
                                    .thenComparing(target -> target.getUniqueId().toString()))
                    .limit(profile.maximumTargetsPerPulse())
                    .forEach(
                            target ->
                                    applyArcaneSpellHit(
                                            owner,
                                            target,
                                            spell,
                                            spell.output().powerCoefficient(),
                                            entry.getKey()));
        }
    }

    private double applyArcaneSpellHit(
            Player owner,
            LivingEntity target,
            SpellDefinition spell,
            double powerCoefficient,
            UUID actionId) {
        long tick = plugin.getServer().getCurrentTick();
        boolean pvpTarget = target instanceof Player;
        PostureRuntime posture = postureAt(target.getUniqueId(), tick);
        java.util.EnumSet<ConditionalAdvantage> advantages =
                java.util.EnumSet.noneOf(ConditionalAdvantage.class);
        if (postures.phaseAt(posture, tick) == PosturePhase.BROKEN) {
            advantages.add(ConditionalAdvantage.POSTURE_BREAK);
        }
        ArcaneDamageBreakdown breakdown =
                arcaneDamage.resolve(
                        new ArcaneDamageRequest(
                                spell.output().arcaneSchool(),
                                trainingStaffPower,
                                powerCoefficient,
                                0,
                                advantages,
                                pvpTarget
                                        ? spell.profiles().pvpMultiplier()
                                        : spell.profiles().pveMultiplier()));
        if (target instanceof Player defender) {
            return applyPvpHit(
                    owner,
                    defender,
                    breakdown.finalDamage(),
                    spell.output().posture(),
                    spell.id().value());
        }
        CombatHealthRuntime current =
                trainingTargetHealth.computeIfAbsent(
                        target.getUniqueId(),
                        ignored -> CombatHealthRuntime.full(enemyHealth.profile(), tick));
        CombatHealthResolution health = enemyHealth.damage(current, tick, breakdown.finalDamage());
        trainingTargetHealth.put(target.getUniqueId(), health.runtime());
        PostureResolution postureResolution =
                postures.damage(posture, tick, spell.output().posture());
        trainingTargetPosture.put(target.getUniqueId(), postureResolution.runtime());
        LiveSession session = sessions.get(owner.getUniqueId());
        if (session != null) {
            observeSuccessfulCombatAction(
                    owner, session, target, "STAFF", actionId, spell.id(), trainingStaffPower);
            session.lastResolution =
                    spellLabel(spell)
                            + " HIT damage="
                            + roundOne(health.appliedAmount())
                            + " health="
                            + roundOne(health.runtime().current())
                            + " posture="
                            + postureLabel(postureResolution.runtime(), tick);
            owner.sendActionBar(Component.text(session.lastResolution, NamedTextColor.GREEN));
        }
        if (health.lethalNow()) {
            if (session != null) {
                completeCombatTarget(owner, session, target, EncounterOutcome.VICTORY);
            }
            target.setHealth(0);
        }
        return health.appliedAmount();
    }

    private boolean eligibleCombatTarget(Player attacker, LivingEntity target) {
        if (target == attacker || target instanceof ArmorStand || target.isDead()) {
            return false;
        }
        return !(target instanceof Player defender)
                || pvpCombatPolicy.profile(attacker, defender).isPresent();
    }

    private double applyPvpHit(
            Player attacker,
            Player defender,
            double incomingHealthDamage,
            double incomingPostureDamage,
            String sourceLabel) {
        PvpCombatProfile profile = pvpCombatPolicy.profile(attacker, defender).orElse(null);
        LiveSession defenderSession = sessions.get(defender.getUniqueId());
        if (profile == null
                || defenderSession == null
                || defenderSession.health.dead()
                || damageImmunityObserver.test(defender)) {
            return 0;
        }
        long tick = plugin.getServer().getCurrentTick();
        CombatDefenseResolution resolved =
                new CombatDefenseResolver(dodges, defenderSession.guardEngine)
                        .resolve(
                                Optional.ofNullable(defenderSession.dodge),
                                defenderSession.guard,
                                tick,
                                true,
                                guardHitRequest(
                                        defender,
                                        defenderSession,
                                        attacker,
                                        incomingHealthDamage,
                                        Math.max(1.0, incomingPostureDamage)
                                                * profile.guardPressureMultiplier()));
        defenderSession.guard = resolved.guardRuntime();
        if (resolved.staminaSpent() > 0) {
            defenderSession.resources =
                    defenderSession.resources.spendStamina(resolved.staminaSpent()).orElseThrow();
            defenderSession.lastStaminaSpendTick = tick;
            defenderSession.staminaRegenRemainder = 0;
        }
        CombatHealthResolution health =
                playerHealth.damage(defenderSession.health, tick, resolved.finalDamage());
        defenderSession.health = health.runtime();
        if (resolved.outcome() != CombatDefenseOutcome.DODGED) {
            markHostile(defender, defenderSession);
            LiveSession attackerSession = sessions.get(attacker.getUniqueId());
            if (attackerSession != null) {
                markHostile(attacker, attackerSession);
            }
        }
        if (resolved.outcome() == CombatDefenseOutcome.HIT && !health.lethalNow()) {
            PoiseResolution poiseResolution =
                    poise.apply(
                            defenderSession.poise,
                            tick,
                            Math.max(0, incomingPostureDamage),
                            profile.guardPressureMultiplier(),
                            CcSeverity.FLINCH);
            defenderSession.poise = poiseResolution.runtime();
            poiseResolution
                    .triggeredSeverity()
                    .ifPresent(
                            severity ->
                                    applyCc(
                                            defender,
                                            defenderSession,
                                            severity,
                                            trainingIncomingCcTicks,
                                            true));
        } else if (resolved.outcome() == CombatDefenseOutcome.GUARD_BREAK) {
            applyCc(defender, defenderSession, CcSeverity.HEAVY_STAGGER, 24, true);
        }
        defenderSession.lastResolution =
                sourceLabel
                        + " "
                        + resolved.outcome()
                        + " damage="
                        + roundOne(health.appliedAmount())
                        + " health="
                        + roundOne(defenderSession.health.current());
        defender.sendActionBar(Component.text(defenderSession.lastResolution, NamedTextColor.RED));
        if (health.lethalNow()) {
            completePlayerLethalDamage(defender, defenderSession);
        } else {
            updateHealthPresentation(defender, defenderSession);
        }
        return health.appliedAmount();
    }

    private List<SpellDefinition> attunedStaffSpells(Player player) {
        return characters
                .active(player)
                .map(
                        character ->
                                TRAINING_STAFF_SPELL_ORDER.stream()
                                        .filter(
                                                character.snapshot().build().attunedEffects()
                                                        ::contains)
                                        .map(trainingStaffSpells::get)
                                        .filter(Objects::nonNull)
                                        .toList())
                .orElseGet(List::of);
    }

    private Optional<SpellDefinition> selectedStaffSpell(Player player, LiveSession session) {
        List<SpellDefinition> available = attunedStaffSpells(player);
        if (available.isEmpty()) {
            session.selectedSpell = null;
            return Optional.empty();
        }
        SpellDefinition selected =
                available.stream()
                        .filter(spell -> spell.id().equals(session.selectedSpell))
                        .findFirst()
                        .orElse(available.getFirst());
        session.selectedSpell = selected.id();
        return Optional.of(selected);
    }

    private int activeZonesFor(UUID ownerId) {
        return (int)
                activeZones.values().stream()
                        .filter(zone -> zone.ownerId().equals(ownerId))
                        .count();
    }

    private static String spellLabel(SpellDefinition spell) {
        String id = spell.id().value();
        return id.substring(id.lastIndexOf('.') + 1)
                .replace('_', ' ')
                .toUpperCase(java.util.Locale.ROOT);
    }

    private void cancelSpell(LiveSession session, String reason) {
        if (session.spellCast == null) {
            return;
        }
        if (session.pendingSpellCommit != null) {
            session.spellCommitInterrupted = true;
            session.lastResolution = "SPELL INTERRUPTED DURING COMMIT " + reason;
            return;
        }
        Result<SpellCastRuntime, SpellCastErrorCode> cancelled =
                spellCasts.cancel(session.spellCast);
        if (cancelled instanceof Result.Success<SpellCastRuntime, SpellCastErrorCode> success) {
            session.resources = success.value().resources();
        }
        session.spellCast = null;
        session.spellOrigin = null;
        session.spellEvidenceActionId = null;
        session.lastResolution = "SPELL CANCELLED " + reason;
        if (!session.action.hardControl()) {
            session.action = ActionState.IDLE;
        }
    }

    private void handleCrossbowUse(Player player, LiveSession session) {
        long tick = plugin.getServer().getCurrentTick();
        restoreEquippedCrossbow(player, session, tick);
        if (session.crossbow == null) {
            player.sendActionBar(Component.text("CROSSBOW STATE UNAVAILABLE", NamedTextColor.RED));
            return;
        }
        if (session.pendingCrossbowCommit != null
                || session.action != ActionState.IDLE
                || session.timeline != null
                || session.spellCast != null
                || session.dodge != null
                || session.guard.active()
                || session.pendingAmmoCycleId != null
                || session.ammoSwitchHandlingUntilTick > tick
                || session.crossbowRecoveryUntilTick > tick) {
            player.sendActionBar(Component.text("CROSSBOW ACTION LOCKED", NamedTextColor.RED));
            return;
        }
        if (session.crossbow.phase() == CrossbowPhase.LOADED) {
            fireCrossbow(player, session, tick);
            return;
        }
        if (session.crossbow.phase() == CrossbowPhase.UNLOADED
                && compatibleCrossbowBolt(player).isEmpty()) {
            session.lastResolution =
                    characters.equippedQuiverProfile(player).isEmpty()
                            ? "CROSSBOW NO QUIVER"
                            : "CROSSBOW NO PREPARED BOLT";
            player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.RED));
            return;
        }
        Result<CrossbowRuntime, com.branz.mmorpg.combat.crossbow.CrossbowErrorCode> started =
                crossbows.beginOrResume(session.crossbow, tick);
        if (!(started
                instanceof
                Result.Success<CrossbowRuntime, com.branz.mmorpg.combat.crossbow.CrossbowErrorCode>
                        success)) {
            player.sendActionBar(Component.text("CROSSBOW ACTION LOCKED", NamedTextColor.RED));
            return;
        }
        session.crossbow = success.value();
        session.action = ActionState.CHANNELING;
        session.input.clearBuffer(InputBufferClearReason.ACTION_STARTED);
        session.lastResolution = "CROSSBOW " + session.crossbow.phase();
        player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.AQUA));
    }

    private void tickCrossbow(Player player, LiveSession session) {
        long tick = plugin.getServer().getCurrentTick();
        restoreEquippedCrossbow(player, session, tick);
        if (session.crossbowRecoveryUntilTick >= 0 && tick >= session.crossbowRecoveryUntilTick) {
            session.crossbowRecoveryUntilTick = -1;
            if (!session.action.hardControl()) {
                session.action = ActionState.IDLE;
            }
        }
        if (session.crossbow == null || session.pendingCrossbowCommit != null) {
            return;
        }
        CrossbowTickResolution resolution = crossbows.tick(session.crossbow, tick);
        if (resolution.outcome() == CrossbowTickOutcome.BOLT_BIND_REQUIRED) {
            beginCrossbowBoltCommit(player, session);
        } else if (resolution.outcome() == CrossbowTickOutcome.LOADED_CHECKPOINT_REQUIRED) {
            beginCrossbowLoadedCommit(player, session);
        }
    }

    private void beginCrossbowBoltCommit(Player player, LiveSession session) {
        DefinitionId bolt = compatibleCrossbowBolt(player).orElse(null);
        if (bolt == null || session.crossbowItemId == null) {
            session.crossbow =
                    crossbows.interrupt(session.crossbow, plugin.getServer().getCurrentTick());
            session.action = ActionState.IDLE;
            session.lastResolution = "CROSSBOW NO BOLT AT CHECKPOINT";
            player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.RED));
            return;
        }
        PendingCrossbowCommit pending =
                new PendingCrossbowCommit(
                        UUID.randomUUID(),
                        CrossbowCommitKind.BOLT_BIND,
                        session.crossbowItemId,
                        bolt,
                        null);
        session.pendingCrossbowCommit = pending;
        session.action = ActionState.ACTIVE;
        session.lastResolution = "CROSSBOW BOLT COMMITTING";
        player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.YELLOW));
        if (pvpInventorySnapshotActive(player)) {
            Result<CrossbowRuntime, com.branz.mmorpg.combat.crossbow.CrossbowErrorCode> placed =
                    crossbows.boltPlaced(
                            session.crossbow,
                            plugin.getServer().getCurrentTick(),
                            pending.ammoDefinitionId());
            session.pendingCrossbowCommit = null;
            if (placed
                    instanceof
                    Result.Success<
                                    CrossbowRuntime,
                                    com.branz.mmorpg.combat.crossbow.CrossbowErrorCode>
                            success) {
                session.crossbow = success.value();
                Result<CrossbowRuntime, com.branz.mmorpg.combat.crossbow.CrossbowErrorCode>
                        resumed =
                                crossbows.beginOrResume(
                                        session.crossbow, plugin.getServer().getCurrentTick());
                if (resumed
                        instanceof
                        Result.Success<
                                        CrossbowRuntime,
                                        com.branz.mmorpg.combat.crossbow.CrossbowErrorCode>
                                resumedSuccess) {
                    session.crossbow = resumedSuccess.value();
                    session.action = ActionState.CHANNELING;
                    session.lastResolution = "CROSSBOW LOCKING (PVP SNAPSHOT)";
                    return;
                }
            }
            cancelCrossbow(session, "PVP_BOLT_CHECKPOINT_FAILED");
            return;
        }
        characters.bindCrossbowBolt(
                player,
                pending.crossbowItemId(),
                pending.ammoDefinitionId(),
                pending.operationId(),
                contentVersion,
                result -> completeCrossbowCommit(player.getUniqueId(), session, pending, result));
    }

    private void beginCrossbowLoadedCommit(Player player, LiveSession session) {
        DefinitionId boundAmmo = session.crossbow.boundAmmo().orElseThrow();
        if (session.crossbowItemId == null) {
            cancelCrossbow(session, "ITEM_MISSING");
            return;
        }
        PendingCrossbowCommit pending =
                new PendingCrossbowCommit(
                        UUID.randomUUID(),
                        CrossbowCommitKind.LOADED,
                        session.crossbowItemId,
                        boundAmmo,
                        null);
        session.pendingCrossbowCommit = pending;
        session.action = ActionState.ACTIVE;
        session.lastResolution = "CROSSBOW LOADED COMMITTING";
        player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.YELLOW));
        if (pvpInventorySnapshotActive(player)) {
            Result<CrossbowRuntime, com.branz.mmorpg.combat.crossbow.CrossbowErrorCode> loaded =
                    crossbows.loaded(session.crossbow, plugin.getServer().getCurrentTick());
            session.pendingCrossbowCommit = null;
            if (loaded
                    instanceof
                    Result.Success<
                                    CrossbowRuntime,
                                    com.branz.mmorpg.combat.crossbow.CrossbowErrorCode>
                            success) {
                session.crossbow = success.value();
                session.action = ActionState.IDLE;
                session.lastResolution = "CROSSBOW LOADED (PVP SNAPSHOT)";
                return;
            }
            cancelCrossbow(session, "PVP_LOAD_CHECKPOINT_FAILED");
            return;
        }
        characters.completeCrossbowLoad(
                player,
                pending.crossbowItemId(),
                pending.ammoDefinitionId(),
                pending.operationId(),
                contentVersion,
                result -> completeCrossbowCommit(player.getUniqueId(), session, pending, result));
    }

    private void fireCrossbow(Player player, LiveSession session, long tick) {
        if (activeProjectilesFor(player.getUniqueId()) >= maximumActiveProjectilesPerCaster) {
            player.sendActionBar(
                    Component.text(
                            "Projectile limit reached for this caster.", NamedTextColor.RED));
            return;
        }
        Result<CrossbowFireResolution, com.branz.mmorpg.combat.crossbow.CrossbowErrorCode> fired =
                crossbows.fire(session.crossbow, tick);
        if (!(fired
                        instanceof
                        Result.Success<
                                        CrossbowFireResolution,
                                        com.branz.mmorpg.combat.crossbow.CrossbowErrorCode>
                                success)
                || session.crossbowItemId == null) {
            player.sendActionBar(Component.text("CROSSBOW NOT LOADED", NamedTextColor.RED));
            return;
        }
        CrossbowFireResolution fire = success.value();
        UUID projectileId = UUID.randomUUID();
        PendingCrossbowLaunch launch =
                pendingCrossbowLaunch(player, projectileId, fire.boundAmmoDefinitionId());
        PendingCrossbowCommit pending =
                new PendingCrossbowCommit(
                        projectileId,
                        CrossbowCommitKind.FIRE,
                        session.crossbowItemId,
                        fire.boundAmmoDefinitionId(),
                        launch);
        session.crossbow = fire.runtime();
        session.pendingCrossbowCommit = pending;
        session.action = ActionState.ACTIVE;
        session.lastResolution = "CROSSBOW FIRE COMMITTING";
        player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.YELLOW));
        if (pvpInventorySnapshotActive(player)) {
            session.pendingCrossbowCommit = null;
            completeCrossbowFire(player, session, pending.launch());
            return;
        }
        characters.fireCrossbow(
                player,
                pending.crossbowItemId(),
                pending.ammoDefinitionId(),
                pending.operationId(),
                contentVersion,
                result -> completeCrossbowCommit(player.getUniqueId(), session, pending, result));
    }

    private void completeCrossbowCommit(
            UUID playerId,
            LiveSession expectedSession,
            PendingCrossbowCommit pending,
            Result<LoadedCharacterSession, CharacterSessionErrorCode> result) {
        LiveSession session = sessions.get(playerId);
        if (session != expectedSession || session.pendingCrossbowCommit != pending) {
            return;
        }
        session.pendingCrossbowCommit = null;
        Player player = plugin.getServer().getPlayer(playerId);
        if (result
                instanceof
                Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode> failure) {
            session.crossbow =
                    crossbows.interrupt(session.crossbow, plugin.getServer().getCurrentTick());
            restoreEquippedCrossbow(player, session, plugin.getServer().getCurrentTick());
            session.action = ActionState.IDLE;
            session.lastResolution =
                    pending.kind() == CrossbowCommitKind.BOLT_BIND
                                    && failure.error()
                                            == CharacterSessionErrorCode.CHARACTER_AMMO_UNAVAILABLE
                            ? "CROSSBOW NO BOLT"
                            : "CROSSBOW COMMIT FAILED " + failure.error().code();
            if (player != null && player.isOnline()) {
                player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.RED));
            }
            return;
        }
        long tick = plugin.getServer().getCurrentTick();
        restoreEquippedCrossbow(player, session, tick);
        if (pending.kind() == CrossbowCommitKind.BOLT_BIND) {
            Result<CrossbowRuntime, com.branz.mmorpg.combat.crossbow.CrossbowErrorCode> resumed =
                    crossbows.beginOrResume(session.crossbow, tick);
            if (resumed
                    instanceof
                    Result.Success<
                                    CrossbowRuntime,
                                    com.branz.mmorpg.combat.crossbow.CrossbowErrorCode>
                            success) {
                session.crossbow = success.value();
                session.action = ActionState.CHANNELING;
                session.lastResolution = "CROSSBOW LOCKING";
            } else {
                session.action = ActionState.IDLE;
                session.lastResolution = "CROSSBOW RESUME FAILED";
            }
        } else if (pending.kind() == CrossbowCommitKind.LOADED) {
            session.action = ActionState.IDLE;
            session.lastResolution = "CROSSBOW LOADED";
        } else {
            completeCrossbowFire(player, session, pending.launch());
        }
        if (player != null && player.isOnline()) {
            player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.GREEN));
        }
    }

    private void completeCrossbowFire(
            Player player, LiveSession session, PendingCrossbowLaunch pending) {
        Result<CrossbowRuntime, com.branz.mmorpg.combat.crossbow.CrossbowErrorCode> settled =
                crossbows.completeFire(session.crossbow, plugin.getServer().getCurrentTick());
        if (settled
                instanceof
                Result.Success<CrossbowRuntime, com.branz.mmorpg.combat.crossbow.CrossbowErrorCode>
                        success) {
            session.crossbow = success.value();
        } else {
            session.action = ActionState.IDLE;
            session.lastResolution = "CROSSBOW FIRE SETTLE FAILED";
            return;
        }
        if (player == null
                || !player.isOnline()
                || player.isDead()
                || !player.getWorld().getUID().equals(pending.worldId())) {
            session.action = ActionState.IDLE;
            session.lastResolution = "CROSSBOW COMMITTED WITHOUT LIVE PROJECTILE";
            return;
        }
        if (activeProjectilesFor(player.getUniqueId()) >= maximumActiveProjectilesPerCaster) {
            session.action = ActionState.IDLE;
            session.lastResolution = "CROSSBOW COMMITTED PROJECTILE LIMIT";
            return;
        }
        launchCrossbowProjectile(player, session, pending);
        session.crossbowRecoveryUntilTick =
                plugin.getServer().getCurrentTick() + trainingCrossbowMove.phases().recoveryTicks();
        session.action = ActionState.RECOVERY;
    }

    private PendingCrossbowLaunch pendingCrossbowLaunch(
            Player player, UUID projectileId, DefinitionId ammoDefinitionId) {
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector look = eye.getDirection().normalize();
        CombatVector direction = new CombatVector(look.getX(), look.getY(), look.getZ());
        CombatVector origin =
                new CombatVector(eye.getX(), eye.getY(), eye.getZ()).add(direction.multiply(0.35));
        return new PendingCrossbowLaunch(
                projectileId, player.getWorld().getUID(), origin, direction, ammoDefinitionId);
    }

    private void launchCrossbowProjectile(
            Player player, LiveSession session, PendingCrossbowLaunch pending) {
        MoveDefinition.Hitbox hitbox = trainingCrossbowMove.hitboxes().getFirst();
        MoveDefinition.ProjectileDefinition authored = hitbox.projectile().orElseThrow();
        ProjectileProfile profile =
                new ProjectileProfile(
                        authored.speed(),
                        authored.gravityPerTick(),
                        authored.dragPerTick(),
                        authored.collisionRadius(),
                        authored.lifetimeTicks(),
                        authored.pierceCount());
        ProjectileIdentity identity =
                new ProjectileIdentity(
                        pending.projectileId(),
                        player.getUniqueId(),
                        trainingCrossbowMove.id(),
                        contentVersion,
                        Optional.of(pending.ammoDefinitionId()),
                        hitbox.hitGroup());
        ProjectileRuntime runtime =
                ProjectileRuntime.launch(
                        identity, profile, pending.origin(), pending.direction(), 1.0);
        activeProjectiles.put(
                pending.projectileId(),
                new LiveProjectile(
                        pending.worldId(),
                        runtime,
                        ProjectileCombatContext.physical(
                                trainingCrossbowPower, trainingCrossbowMove, 1.0, 0.0)));
        observeCommittedCombatAction(
                session, "CROSSBOW", pending.projectileId(), trainingCrossbowMove.id());
        markHostile(player, session);
        session.lastResolution =
                "CROSSBOW FIRED projectile="
                        + pending.projectileId().toString().substring(0, 8)
                        + " bolts="
                        + characters.quiverAmmoQuantity(player, pending.ammoDefinitionId());
    }

    private Optional<DefinitionId> compatibleCrossbowBolt(Player player) {
        DefinitionId selected = characters.quiverPreparation(player).selectedAmmo().orElse(null);
        QuiverProfile quiver = characters.equippedQuiverProfile(player).orElse(null);
        boolean compatible =
                selected != null
                        && quiver != null
                        && characters.quiverAmmoQuantity(player, selected) > 0
                        && items.find(selected)
                                .flatMap(ItemDefinition::ammoProfile)
                                .filter(ammo -> ammo.family() == trainingCrossbowAmmoFamily)
                                .filter(quiver::supports)
                                .isPresent();
        return compatible ? Optional.of(selected) : Optional.empty();
    }

    private void restoreEquippedCrossbow(Player player, LiveSession session, long tick) {
        if (pvpInventorySnapshotActive(player)
                && session.crossbow != null
                && equippedWeaponFamily(player).filter("CROSSBOW"::equals).isPresent()) {
            return;
        }
        if (player == null
                || !equippedWeaponFamily(player).filter("CROSSBOW"::equals).isPresent()) {
            if (session.pendingCrossbowCommit == null) {
                session.crossbow = null;
                session.crossbowItemId = null;
            }
            return;
        }
        ItemId itemId = characters.equippedMainHandItemId(player).orElse(null);
        CrossbowPersistentState state;
        try {
            state = characters.equippedCrossbowState(player).orElse(null);
        } catch (IllegalArgumentException exception) {
            session.crossbow = null;
            session.crossbowItemId = itemId;
            session.lastResolution = "CROSSBOW PERSISTED STATE INVALID";
            return;
        }
        boolean validBoundAmmo =
                state == null
                        || state.boundAmmo().isEmpty()
                        || state.boundAmmo()
                                .flatMap(items::find)
                                .flatMap(ItemDefinition::ammoProfile)
                                .filter(ammo -> ammo.family() == AmmoFamily.BOLT)
                                .isPresent();
        if (!validBoundAmmo) {
            session.crossbow = null;
            session.crossbowItemId = itemId;
            session.lastResolution = "CROSSBOW BOUND AMMO INVALID";
            return;
        }
        if (itemId == null || state == null) {
            session.crossbow = null;
            session.crossbowItemId = itemId;
            return;
        }
        boolean changedItem = !itemId.equals(session.crossbowItemId);
        boolean changedCheckpoint =
                session.crossbow != null
                        && session.pendingCrossbowCommit == null
                        && !session.crossbow.persistentState().equals(state);
        if (session.crossbow == null || changedItem || changedCheckpoint) {
            session.crossbow = CrossbowRuntime.restore(state, tick);
            session.crossbowItemId = itemId;
        }
    }

    private boolean pvpInventorySnapshotActive(Player player) {
        return pvpCombatPolicy
                .activeProfile(player)
                .map(profile -> !profile.durabilityLossAllowed())
                .orElse(false);
    }

    private void cancelCrossbow(LiveSession session, String reason) {
        boolean active =
                session.crossbow != null
                        && (session.crossbow.phase() == CrossbowPhase.COCKING
                                || session.crossbow.phase() == CrossbowPhase.LOCKING);
        if (session.crossbow != null) {
            session.crossbow =
                    crossbows.interrupt(session.crossbow, plugin.getServer().getCurrentTick());
        }
        if (active || session.pendingCrossbowCommit != null) {
            session.lastResolution = "CROSSBOW INTERRUPTED " + reason;
        }
        session.pendingCrossbowCommit = null;
        session.crossbowRecoveryUntilTick = -1;
        if (!session.action.hardControl()) {
            session.action = ActionState.IDLE;
        }
    }

    private record ProjectileCombatContext(
            double sourcePower,
            double powerCoefficient,
            int posture,
            double pveMultiplier,
            double pvpMultiplier,
            double postureMultiplier,
            double penetrationPercentage,
            Optional<ArcaneSchool> arcaneSchool) {
        private ProjectileCombatContext {
            if (!Double.isFinite(sourcePower)
                    || sourcePower <= 0
                    || !Double.isFinite(powerCoefficient)
                    || powerCoefficient <= 0
                    || posture < 0
                    || !Double.isFinite(pveMultiplier)
                    || pveMultiplier <= 0
                    || !Double.isFinite(pvpMultiplier)
                    || pvpMultiplier <= 0
                    || !Double.isFinite(postureMultiplier)
                    || postureMultiplier <= 0
                    || !Double.isFinite(penetrationPercentage)
                    || penetrationPercentage < 0) {
                throw new IllegalArgumentException("invalid projectile combat context");
            }
            Objects.requireNonNull(arcaneSchool, "arcaneSchool");
            if (arcaneSchool.isPresent() && penetrationPercentage != 0) {
                throw new IllegalArgumentException(
                        "arcane projectiles cannot carry physical penetration");
            }
        }

        private static ProjectileCombatContext physical(
                double power,
                MoveDefinition move,
                double postureMultiplier,
                double penetrationPercentage) {
            Objects.requireNonNull(move, "move");
            return new ProjectileCombatContext(
                    power,
                    move.outputs().moveCoefficient(),
                    move.outputs().posture(),
                    move.profiles().pveMultiplier(),
                    move.profiles().pvpMultiplier(),
                    postureMultiplier,
                    penetrationPercentage,
                    Optional.empty());
        }

        private static ProjectileCombatContext arcane(double power, SpellDefinition spell) {
            Objects.requireNonNull(spell, "spell");
            return new ProjectileCombatContext(
                    power,
                    spell.output().powerCoefficient(),
                    spell.output().posture(),
                    spell.profiles().pveMultiplier(),
                    spell.profiles().pvpMultiplier(),
                    1.0,
                    0,
                    Optional.of(spell.output().arcaneSchool()));
        }
    }

    private record PendingBowLaunch(
            UUID projectileId,
            UUID worldId,
            CombatVector origin,
            CombatVector direction,
            BowShotCharge charge,
            DefinitionId ammoDefinitionId) {
        private PendingBowLaunch {
            Objects.requireNonNull(projectileId, "projectileId");
            Objects.requireNonNull(worldId, "worldId");
            Objects.requireNonNull(origin, "origin");
            direction = Objects.requireNonNull(direction, "direction").normalized();
            Objects.requireNonNull(charge, "charge");
            Objects.requireNonNull(ammoDefinitionId, "ammoDefinitionId");
        }
    }

    private enum CrossbowCommitKind {
        BOLT_BIND,
        LOADED,
        FIRE
    }

    private record PendingCrossbowLaunch(
            UUID projectileId,
            UUID worldId,
            CombatVector origin,
            CombatVector direction,
            DefinitionId ammoDefinitionId) {
        private PendingCrossbowLaunch {
            Objects.requireNonNull(projectileId, "projectileId");
            Objects.requireNonNull(worldId, "worldId");
            Objects.requireNonNull(origin, "origin");
            direction = Objects.requireNonNull(direction, "direction").normalized();
            Objects.requireNonNull(ammoDefinitionId, "ammoDefinitionId");
        }
    }

    private record PendingCrossbowCommit(
            UUID operationId,
            CrossbowCommitKind kind,
            ItemId crossbowItemId,
            DefinitionId ammoDefinitionId,
            PendingCrossbowLaunch launch) {
        private PendingCrossbowCommit {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(crossbowItemId, "crossbowItemId");
            Objects.requireNonNull(ammoDefinitionId, "ammoDefinitionId");
            if ((kind == CrossbowCommitKind.FIRE) != (launch != null)) {
                throw new IllegalArgumentException(
                        "Only a Crossbow FIRE commit carries a projectile launch snapshot");
            }
        }
    }

    private record PendingSpellLaunch(
            UUID effectId, UUID worldId, CombatVector origin, CombatVector direction) {
        private PendingSpellLaunch {
            Objects.requireNonNull(effectId, "effectId");
            Objects.requireNonNull(worldId, "worldId");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(direction, "direction");
        }
    }

    private record PendingSpellCommit(
            UUID operationId, ItemId catalystItemId, PendingSpellLaunch launch) {
        private PendingSpellCommit {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(catalystItemId, "catalystItemId");
            Objects.requireNonNull(launch, "launch");
            if (!operationId.equals(launch.effectId())) {
                throw new IllegalArgumentException(
                        "spell operation and effect identity must match");
            }
        }
    }

    private static final class LiveSession {
        private EngagementRuntime engagement;
        private WeaponTransitionSnapshot weapon = WeaponTransitionSnapshot.initial();
        private ActionState action = ActionState.IDLE;
        private CombatResources resources = CombatResources.full(1000, 100, 100);
        private final InputRouter input = new InputRouter();
        private ActionTimeline timeline;
        private BowDrawRuntime bowDraw;
        private PendingBowLaunch pendingBowLaunch;
        private ItemId crossbowItemId;
        private CrossbowRuntime crossbow;
        private PendingCrossbowCommit pendingCrossbowCommit;
        private SpellCastRuntime spellCast;
        private CombatVector spellOrigin;
        private PendingSpellCommit pendingSpellCommit;
        private boolean spellCommitInterrupted;
        private DefinitionId selectedSpell;
        private RunicImbuementRuntime imbuement;
        private long crossbowRecoveryUntilTick = -1;
        private UUID pendingAmmoCycleId;
        private long ammoSwitchHandlingUntilTick = -1;
        private long bowRecoveryUntilTick = -1;
        private CombatTransform previousActionTransform;
        private DodgeRuntime dodge;
        private GuardRuntime guard;
        private GuardEngine guardEngine;
        private String guardAuthorityKey = "NONE";
        private PoiseRuntime poise;
        private CcRuntime crowdControl;
        private CombatHealthRuntime health;
        private CombatResources activeTraceInitialResources;
        private final java.util.List<ActionSimulationCommand> activeTraceCommands =
                new java.util.ArrayList<>();
        private CombatTrace lastTrace;
        private org.bukkit.util.Vector dodgeDirection;
        private boolean applyingDodgeTeleport;
        private long lastDodgeMovementElapsed = -1;
        private SneakPressWindow sneakPress;
        private long lastStaminaSpendTick = Long.MIN_VALUE / 2;
        private double staminaRegenRemainder;
        private long lastManaCommitTick = Long.MIN_VALUE / 2;
        private double manaRegenRemainder;
        private String lastResolution;
        private final java.util.Set<UUID> threatOwners = new java.util.HashSet<>();
        private final CombatEvidenceAccumulator combatEvidence = new CombatEvidenceAccumulator();
        private final ArrayDeque<EvidenceCandidate> pendingProgressionEvidence = new ArrayDeque<>();
        private boolean progressionEvidenceCommitInFlight;
        private UUID activeMoveEvidenceActionId;
        private UUID spellEvidenceActionId;
        private UUID flaskUseOperationId;

        private LiveSession(
                EngagementRuntime engagement,
                GuardRuntime guard,
                GuardEngine guardEngine,
                PoiseRuntime poise,
                CcRuntime crowdControl,
                CombatHealthRuntime health) {
            this.engagement = Objects.requireNonNull(engagement, "engagement");
            this.guard = Objects.requireNonNull(guard, "guard");
            this.guardEngine = Objects.requireNonNull(guardEngine, "guardEngine");
            this.poise = Objects.requireNonNull(poise, "poise");
            this.crowdControl = Objects.requireNonNull(crowdControl, "crowdControl");
            this.health = Objects.requireNonNull(health, "health");
        }
    }
}
