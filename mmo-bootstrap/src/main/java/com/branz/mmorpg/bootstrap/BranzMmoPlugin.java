package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.provider.ProviderHealthEntry;
import com.branz.mmorpg.api.provider.ProviderRequirement;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.move.MoveEngine;
import com.branz.mmorpg.combat.move.MoveEngineErrorCode;
import com.branz.mmorpg.combat.status.AilmentDefinitionEngine;
import com.branz.mmorpg.combat.status.AilmentDefinitionEngineErrorCode;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.integrations.PluginCapabilityProbe;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.items.definition.ItemEngineErrorCode;
import com.branz.mmorpg.items.projection.ProjectionTokenSigner;
import com.branz.mmorpg.magic.definition.SpellEngine;
import com.branz.mmorpg.magic.definition.SpellEngineErrorCode;
import com.branz.mmorpg.progression.build.BuildEngine;
import com.branz.mmorpg.progression.build.BuildErrorCode;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public final class BranzMmoPlugin extends JavaPlugin {
    private final BootstrapLifecycle lifecycle = new BootstrapLifecycle();
    private final AtomicReference<ContentSnapshot> activeSnapshot = new AtomicReference<>();
    private final AtomicReference<ItemEngine> activeItemEngine = new AtomicReference<>();
    private final AtomicReference<MoveEngine> activeMoveEngine = new AtomicReference<>();
    private final AtomicReference<SpellEngine> activeSpellEngine = new AtomicReference<>();
    private final AtomicReference<BuildEngine> activeBuildEngine = new AtomicReference<>();
    private final AtomicReference<AilmentDefinitionEngine> activeAilmentEngine =
            new AtomicReference<>();
    private ResourcePackGate resourcePackGate;
    private SceneHubController sceneHubController;
    private MmoCommandController commandController;
    private TestItemProjectionService testItemProjections;
    private TestItemProjectionController testItemProjectionController;
    private DatabaseRuntime databaseRuntime;
    private CharacterSessionController characterSessionController;
    private CombatSessionController combatSessionController;
    private FlaskHotbarController flaskHotbarController;
    private ConsumableHotbarController consumableHotbarController;
    private LiveTeachingSessionController liveTeachingSessionController;
    private KnowledgeAcquisitionController knowledgeAcquisitionController;
    private BossEncounterController bossEncounterController;
    private PartyController partyController;
    private DownedController downedController;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lifecycle.beginStartup();
        getServer().getPluginManager().registerEvents(new PlayerSessionGate(lifecycle), this);

        Path contentRoot = contentRoot();
        DatabaseSettings databaseSettings;
        try {
            databaseSettings = DatabaseSettings.from(getConfig(), getDataFolder().toPath());
        } catch (IllegalArgumentException exception) {
            lifecycle.completeStartup(StartupStatus.MAINTENANCE);
            getLogger().severe("Invalid database configuration: " + exception.getMessage());
            scheduleSmokeShutdown();
            return;
        }
        Map<String, ProviderRequirement> requirements = providerRequirements();
        String walletPluginName = getConfig().getString("providers.wallet.plugin-name", "Wallet");
        PluginCapabilityProbe capabilityProbe =
                BukkitPluginCapabilityProbe.capture(
                        getServer().getPluginManager(), pluginNames(walletPluginName));
        ProviderRegistryFactory providerFactory =
                snapshot ->
                        new ProviderAdapterFactory()
                                .create(
                                        snapshot,
                                        capabilityProbe,
                                        Clock.systemUTC(),
                                        requirements,
                                        walletPluginName);

        getLogger().info("Loading and validating content snapshot from " + contentRoot);
        getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        this,
                        () -> {
                            DatabaseRuntime startedDatabase = null;
                            String databaseFailure = null;
                            try {
                                if (databaseSettings.mode() == DatabaseMode.DISABLED) {
                                    databaseFailure =
                                            "Database mode is DISABLED; durable MMO sessions are unavailable.";
                                } else {
                                    startedDatabase = DatabaseRuntime.start(databaseSettings);
                                }
                            } catch (Exception exception) {
                                databaseFailure =
                                        exception.getClass().getSimpleName()
                                                + ": "
                                                + exception.getMessage();
                            }
                            StartupDecision decision =
                                    new ContentStartupGate()
                                            .evaluate(contentRoot, providerFactory, Instant.now());
                            RuntimeStartupResult startup =
                                    startedDatabase == null
                                            ? new RuntimeStartupResult(
                                                    decision,
                                                    java.util.Optional.empty(),
                                                    java.util.Optional.of(databaseFailure))
                                            : new RuntimeStartupResult(
                                                    decision,
                                                    java.util.Optional.of(startedDatabase),
                                                    java.util.Optional.empty());
                            getServer()
                                    .getScheduler()
                                    .runTask(this, () -> applyStartupDecision(startup));
                        });
    }

    @Override
    public void onDisable() {
        if (sceneHubController != null) {
            sceneHubController.shutdown();
        }
        if (resourcePackGate != null) {
            resourcePackGate.clear();
        }
        if (testItemProjections != null) {
            getServer().getOnlinePlayers().forEach(testItemProjections::removeAll);
        }
        if (liveTeachingSessionController != null) {
            liveTeachingSessionController.shutdown();
            liveTeachingSessionController = null;
        }
        if (downedController != null) {
            downedController.shutdown();
            downedController = null;
        }
        if (partyController != null) {
            partyController.shutdown();
            partyController = null;
        }
        if (bossEncounterController != null) {
            bossEncounterController.shutdown();
            bossEncounterController = null;
        }
        if (flaskHotbarController != null) {
            flaskHotbarController.shutdown();
            flaskHotbarController = null;
        }
        if (consumableHotbarController != null) {
            consumableHotbarController.shutdown();
            consumableHotbarController = null;
        }
        if (combatSessionController != null) {
            combatSessionController.shutdown();
            combatSessionController = null;
        }
        if (characterSessionController != null) {
            characterSessionController.shutdown();
            characterSessionController = null;
        }
        if (databaseRuntime != null) {
            databaseRuntime.close();
            databaseRuntime = null;
        }
        activeItemEngine.set(null);
        activeMoveEngine.set(null);
        activeSpellEngine.set(null);
        activeBuildEngine.set(null);
        activeAilmentEngine.set(null);
        activeSnapshot.set(null);
        lifecycle.disable();
        getLogger().info("Branz MMO platform disabled cleanly.");
    }

    private void applyStartupDecision(RuntimeStartupResult startup) {
        StartupDecision decision = startup.contentDecision();
        if (startup.databaseFailure().isPresent()) {
            startup.databaseRuntime().ifPresent(DatabaseRuntime::close);
            lifecycle.completeStartup(StartupStatus.MAINTENANCE);
            getLogger()
                    .severe(
                            "PostgreSQL runtime failed: "
                                    + startup.databaseFailure().orElseThrow());
            scheduleSmokeShutdown();
            return;
        }
        databaseRuntime = startup.databaseRuntime().orElseThrow();
        if (decision.acceptsSessions()) {
            ContentSnapshot snapshot = decision.snapshot().orElseThrow();
            String runtimeFailure = prepareMilestoneThreeRuntime(snapshot);
            if (runtimeFailure != null) {
                lifecycle.completeStartup(StartupStatus.MAINTENANCE);
                getLogger().severe(runtimeFailure);
                databaseRuntime.close();
                databaseRuntime = null;
                scheduleSmokeShutdown();
                return;
            }
        }
        if (!lifecycle.completeStartup(decision.status())) {
            return;
        }
        decision.snapshot().ifPresent(activeSnapshot::set);
        if (decision.acceptsSessions()) {
            registerMilestoneThreeRuntime();
        }
        logProviderHealth(decision);
        for (String reason : decision.reasons()) {
            getLogger().warning(reason);
        }
        if (decision.acceptsSessions()) {
            ContentSnapshot snapshot = decision.snapshot().orElseThrow();
            getLogger()
                    .info(
                            "Branz MMO startup "
                                    + decision.status()
                                    + "; content="
                                    + snapshot.manifest().contentVersion()
                                    + ", definitions="
                                    + snapshot.definitions().size());
            getLogger()
                    .info(
                            "PostgreSQL runtime ready; mode="
                                    + databaseRuntime.settings().mode()
                                    + ", server-instance="
                                    + databaseRuntime.serverInstanceId().value());
        } else {
            databaseRuntime.close();
            databaseRuntime = null;
            getLogger()
                    .severe(
                            "Branz MMO entered safe maintenance mode; player sessions are blocked.");
        }
        scheduleSmokeShutdown();
    }

    private String prepareMilestoneThreeRuntime(ContentSnapshot snapshot) {
        Result<ItemEngine, ItemEngineErrorCode> compiled = ItemEngine.compile(snapshot);
        if (compiled instanceof Result.Failure<ItemEngine, ItemEngineErrorCode> failure) {
            return "Item Engine rejected active content: "
                    + failure.error().code()
                    + " "
                    + failure.detail();
        }
        activeItemEngine.set(((Result.Success<ItemEngine, ItemEngineErrorCode>) compiled).value());
        Result<MoveEngine, MoveEngineErrorCode> compiledMoves = MoveEngine.compile(snapshot);
        if (compiledMoves instanceof Result.Failure<MoveEngine, MoveEngineErrorCode> failure) {
            activeItemEngine.set(null);
            return "Move Engine rejected active content: "
                    + failure.error().code()
                    + " "
                    + failure.detail();
        }
        activeMoveEngine.set(
                ((Result.Success<MoveEngine, MoveEngineErrorCode>) compiledMoves).value());
        Result<SpellEngine, SpellEngineErrorCode> compiledSpells = SpellEngine.compile(snapshot);
        if (compiledSpells instanceof Result.Failure<SpellEngine, SpellEngineErrorCode> failure) {
            activeItemEngine.set(null);
            activeMoveEngine.set(null);
            return "Spell Engine rejected active content: "
                    + failure.error().code()
                    + " "
                    + failure.detail();
        }
        activeSpellEngine.set(
                ((Result.Success<SpellEngine, SpellEngineErrorCode>) compiledSpells).value());
        Result<BuildEngine, BuildErrorCode> compiledBuilds = BuildEngine.compile(snapshot);
        if (compiledBuilds instanceof Result.Failure<BuildEngine, BuildErrorCode> failure) {
            activeItemEngine.set(null);
            activeMoveEngine.set(null);
            activeSpellEngine.set(null);
            return "Build Engine rejected active content: "
                    + failure.error().code()
                    + " "
                    + failure.detail();
        }
        activeBuildEngine.set(
                ((Result.Success<BuildEngine, BuildErrorCode>) compiledBuilds).value());
        Result<AilmentDefinitionEngine, AilmentDefinitionEngineErrorCode> compiledAilments =
                AilmentDefinitionEngine.compile(snapshot);
        if (compiledAilments
                instanceof
                Result.Failure<AilmentDefinitionEngine, AilmentDefinitionEngineErrorCode> failure) {
            activeItemEngine.set(null);
            activeMoveEngine.set(null);
            activeSpellEngine.set(null);
            activeBuildEngine.set(null);
            return "Ailment Engine rejected active content: "
                    + failure.error().code()
                    + " "
                    + failure.detail();
        }
        AilmentDefinitionEngine ailments =
                ((Result.Success<AilmentDefinitionEngine, AilmentDefinitionEngineErrorCode>)
                                compiledAilments)
                        .value();
        if (ailments.all().size() != com.branz.mmorpg.combat.status.AilmentType.values().length) {
            activeItemEngine.set(null);
            activeMoveEngine.set(null);
            activeSpellEngine.set(null);
            activeBuildEngine.set(null);
            return "Active content must define all six core ailments exactly once.";
        }
        activeAilmentEngine.set(ailments);
        ChronicleService chronicle = new ChronicleService(this);
        resourcePackGate = new ResourcePackGate(this, snapshot);
        if (!resourcePackGate.configurationValid()) {
            activeItemEngine.set(null);
            activeMoveEngine.set(null);
            resourcePackGate = null;
            return "Resource-pack URL/SHA-256 does not match the active content manifest.";
        }
        if (activeMoveEngine
                .get()
                .find(
                        com.branz.mmorpg.api.identity.DefinitionId.of(
                                "move.training_blade.primary_1"))
                .isEmpty()) {
            activeItemEngine.set(null);
            activeMoveEngine.set(null);
            resourcePackGate = null;
            return "Active content is missing the required training combat move.";
        }
        com.branz.mmorpg.combat.move.MoveDefinition trainingBowMove =
                activeMoveEngine
                        .get()
                        .find(
                                com.branz.mmorpg.api.identity.DefinitionId.of(
                                        "move.training_bow.quick_shot"))
                        .orElse(null);
        if (trainingBowMove == null
                || !trainingBowMove.family().equals("BOW")
                || trainingBowMove.input().action()
                        != com.branz.mmorpg.combat.input.SemanticInput.SECONDARY
                || trainingBowMove.hitboxes().size() != 1
                || trainingBowMove.hitboxes().getFirst().projectile().isEmpty()) {
            activeItemEngine.set(null);
            activeMoveEngine.set(null);
            resourcePackGate = null;
            return "Training Bow move requires one SECONDARY PROJECTILE hitbox.";
        }
        com.branz.mmorpg.items.definition.WeaponCombatProfile trainingWeapon =
                activeItemEngine
                        .get()
                        .find(
                                com.branz.mmorpg.api.identity.DefinitionId.of(
                                        "weapon.training_blade"))
                        .flatMap(com.branz.mmorpg.items.definition.ItemDefinition::weaponProfile)
                        .orElse(null);
        if (trainingWeapon == null || !trainingWeapon.family().equals("SWORD")) {
            activeItemEngine.set(null);
            activeMoveEngine.set(null);
            resourcePackGate = null;
            return "Training blade requires a SWORD weapon_profile.";
        }
        com.branz.mmorpg.items.definition.WeaponCombatProfile trainingBow =
                activeItemEngine
                        .get()
                        .find(com.branz.mmorpg.api.identity.DefinitionId.of("weapon.training_bow"))
                        .flatMap(com.branz.mmorpg.items.definition.ItemDefinition::weaponProfile)
                        .orElse(null);
        if (trainingBow == null
                || !trainingBow.family().equals("BOW")
                || trainingBow.bowProfile().isEmpty()) {
            activeItemEngine.set(null);
            activeMoveEngine.set(null);
            resourcePackGate = null;
            return "Training Bow requires a BOW weapon_profile with handling fields.";
        }
        com.branz.mmorpg.items.definition.AmmoProfile trainingArrow =
                activeItemEngine
                        .get()
                        .find(com.branz.mmorpg.api.identity.DefinitionId.of("ammo.training_arrow"))
                        .flatMap(com.branz.mmorpg.items.definition.ItemDefinition::ammoProfile)
                        .orElse(null);
        com.branz.mmorpg.items.definition.QuiverProfile trainingQuiver =
                activeItemEngine
                        .get()
                        .find(
                                com.branz.mmorpg.api.identity.DefinitionId.of(
                                        "equipment.training_quiver"))
                        .flatMap(com.branz.mmorpg.items.definition.ItemDefinition::quiverProfile)
                        .orElse(null);
        if (trainingArrow == null
                || trainingQuiver == null
                || !trainingQuiver.supports(trainingArrow)) {
            activeItemEngine.set(null);
            activeMoveEngine.set(null);
            resourcePackGate = null;
            return "Training Bow requires compatible ammo and Quiver profiles.";
        }
        com.branz.mmorpg.combat.move.MoveDefinition trainingCrossbowMove =
                activeMoveEngine
                        .get()
                        .find(
                                com.branz.mmorpg.api.identity.DefinitionId.of(
                                        "move.training_crossbow.shot"))
                        .orElse(null);
        if (trainingCrossbowMove == null
                || !trainingCrossbowMove.family().equals("CROSSBOW")
                || trainingCrossbowMove.input().action()
                        != com.branz.mmorpg.combat.input.SemanticInput.SECONDARY
                || trainingCrossbowMove.hitboxes().size() != 1
                || trainingCrossbowMove.hitboxes().getFirst().projectile().isEmpty()) {
            activeItemEngine.set(null);
            activeMoveEngine.set(null);
            resourcePackGate = null;
            return "Training Crossbow move requires one SECONDARY PROJECTILE hitbox.";
        }
        com.branz.mmorpg.items.definition.WeaponCombatProfile trainingCrossbow =
                activeItemEngine
                        .get()
                        .find(
                                com.branz.mmorpg.api.identity.DefinitionId.of(
                                        "weapon.training_crossbow"))
                        .flatMap(com.branz.mmorpg.items.definition.ItemDefinition::weaponProfile)
                        .orElse(null);
        com.branz.mmorpg.items.definition.AmmoProfile trainingBolt =
                activeItemEngine
                        .get()
                        .find(com.branz.mmorpg.api.identity.DefinitionId.of("ammo.training_bolt"))
                        .flatMap(com.branz.mmorpg.items.definition.ItemDefinition::ammoProfile)
                        .orElse(null);
        com.branz.mmorpg.items.definition.QuiverProfile trainingBoltQuiver =
                activeItemEngine
                        .get()
                        .find(
                                com.branz.mmorpg.api.identity.DefinitionId.of(
                                        "equipment.training_bolt_quiver"))
                        .flatMap(com.branz.mmorpg.items.definition.ItemDefinition::quiverProfile)
                        .orElse(null);
        if (trainingCrossbow == null
                || !trainingCrossbow.family().equals("CROSSBOW")
                || trainingCrossbow.crossbowProfile().isEmpty()
                || trainingBolt == null
                || trainingBolt.family() != com.branz.mmorpg.items.definition.AmmoFamily.BOLT
                || trainingBoltQuiver == null
                || !trainingBoltQuiver.supports(trainingBolt)) {
            activeItemEngine.set(null);
            activeMoveEngine.set(null);
            resourcePackGate = null;
            return "Training Crossbow requires checkpoint timing plus compatible Bolt and Quiver profiles.";
        }
        com.branz.mmorpg.combat.move.MoveDefinition trainingStaffMove =
                activeMoveEngine
                        .get()
                        .find(
                                com.branz.mmorpg.api.identity.DefinitionId.of(
                                        "move.training_staff.primary_1"))
                        .orElse(null);
        com.branz.mmorpg.items.definition.ItemDefinition trainingStaffDefinition =
                activeItemEngine
                        .get()
                        .find(
                                com.branz.mmorpg.api.identity.DefinitionId.of(
                                        "weapon.training_staff"))
                        .orElse(null);
        com.branz.mmorpg.items.definition.WeaponCombatProfile trainingStaff =
                trainingStaffDefinition == null
                        ? null
                        : trainingStaffDefinition.weaponProfile().orElse(null);
        com.branz.mmorpg.items.definition.CatalystProfile trainingCatalyst =
                trainingStaffDefinition == null
                        ? null
                        : trainingStaffDefinition.catalystProfile().orElse(null);
        com.branz.mmorpg.magic.definition.SpellDefinition fireLance =
                activeSpellEngine
                        .get()
                        .find(
                                com.branz.mmorpg.api.identity.DefinitionId.of(
                                        "spell.ember.fire_lance"))
                        .orElse(null);
        if (trainingStaffMove == null
                || !trainingStaffMove.family().equals("STAFF")
                || trainingStaffMove.input().action()
                        != com.branz.mmorpg.combat.input.SemanticInput.PRIMARY
                || trainingStaff == null
                || !trainingStaff.family().equals("STAFF")
                || trainingCatalyst == null
                || fireLance == null
                || fireLance.deliveryType()
                        != com.branz.mmorpg.magic.definition.SpellDeliveryType.PROJECTILE
                || !trainingCatalyst.tags().containsAll(fireLance.requirements().catalystTags())) {
            activeItemEngine.set(null);
            activeMoveEngine.set(null);
            activeSpellEngine.set(null);
            resourcePackGate = null;
            return "Training Staff requires a compatible primary move, catalyst and Ember Fire Lance.";
        }
        BukkitItemProjectionCodec projectionCodec =
                new BukkitItemProjectionCodec(this, ProjectionTokenSigner.random());
        testItemProjections = new TestItemProjectionService(projectionCodec);
        testItemProjectionController = new TestItemProjectionController(testItemProjections);
        characterSessionController =
                new CharacterSessionController(
                        this,
                        new CharacterSessionService(databaseRuntime, activeBuildEngine.get()),
                        new BukkitInventoryProjectionService(projectionCodec),
                        activeItemEngine.get(),
                        databaseRuntime.settings());
        int weaponDrawTicks = getConfig().getInt("combat.weapon-draw-ticks", 6);
        int weaponSheatheTicks = getConfig().getInt("combat.weapon-sheathe-ticks", 4);
        int engagementExitTicks = getConfig().getInt("combat.engagement-exit-ticks", 160);
        int maximumActiveProjectilesPerCaster =
                getConfig().getInt("combat.max-active-projectiles-per-caster", 32);
        double trainingIncomingGuardPressure =
                getConfig().getDouble("combat.training-incoming-guard-pressure", 10.0);
        double trainingIncomingHealthDamage =
                getConfig().getDouble("combat.training-incoming-health-damage", 100.0);
        double environmentalHealthScale =
                getConfig().getDouble("combat.environmental-health-scale", 50.0);
        double trainingIncomingPoiseDamage =
                getConfig().getDouble("combat.training-incoming-poise-damage", 35.0);
        int trainingIncomingCcTicks = getConfig().getInt("combat.training-incoming-cc-ticks", 6);
        double trainingPerfectGuardPostureDamage =
                getConfig().getDouble("combat.training-perfect-guard-posture-damage", 8.0);
        com.branz.mmorpg.combat.cc.CcSeverity trainingIncomingCcSeverity;
        try {
            trainingIncomingCcSeverity =
                    com.branz.mmorpg.combat.cc.CcSeverity.valueOf(
                            getConfig()
                                    .getString("combat.training-incoming-cc-severity", "FLINCH")
                                    .trim()
                                    .toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            activeItemEngine.set(null);
            activeMoveEngine.set(null);
            resourcePackGate = null;
            characterSessionController = null;
            return "Combat training-incoming-cc-severity is not a supported CC severity.";
        }
        com.branz.mmorpg.combat.dodge.DodgeProfile dodgeProfile;
        try {
            dodgeProfile =
                    com.branz.mmorpg.combat.dodge.DodgeProfile.canonical(
                            com.branz.mmorpg.combat.dodge.DodgeLoad.valueOf(
                                    getConfig()
                                            .getString("combat.training-dodge-load", "MEDIUM")
                                            .trim()
                                            .toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            activeItemEngine.set(null);
            activeMoveEngine.set(null);
            resourcePackGate = null;
            characterSessionController = null;
            return "Combat training-dodge-load must be LIGHT, MEDIUM, HEAVY or OVERLOADED.";
        }
        if (weaponDrawTicks < 1
                || weaponSheatheTicks < 1
                || engagementExitTicks < 1
                || maximumActiveProjectilesPerCaster < 1
                || maximumActiveProjectilesPerCaster > 128
                || !Double.isFinite(trainingIncomingGuardPressure)
                || trainingIncomingGuardPressure <= 0
                || !Double.isFinite(trainingIncomingHealthDamage)
                || trainingIncomingHealthDamage <= 0
                || !Double.isFinite(environmentalHealthScale)
                || environmentalHealthScale <= 0
                || !Double.isFinite(trainingIncomingPoiseDamage)
                || trainingIncomingPoiseDamage <= 0
                || trainingIncomingCcTicks < 1
                || !Double.isFinite(trainingPerfectGuardPostureDamage)
                || trainingPerfectGuardPostureDamage <= 0) {
            activeItemEngine.set(null);
            activeMoveEngine.set(null);
            resourcePackGate = null;
            characterSessionController = null;
            return "Combat tick and training defense settings must be positive.";
        }
        combatSessionController =
                new CombatSessionController(
                        this,
                        characterSessionController,
                        activeItemEngine.get(),
                        activeMoveEngine.get(),
                        activeSpellEngine.get(),
                        activeBuildEngine.get(),
                        snapshot.manifest().contentVersion(),
                        trainingWeapon.power(),
                        maximumActiveProjectilesPerCaster,
                        weaponDrawTicks,
                        weaponSheatheTicks,
                        engagementExitTicks,
                        dodgeProfile,
                        new com.branz.mmorpg.combat.guard.GuardEngine(
                                com.branz.mmorpg.combat.guard.GuardProfile.trainingWeapon()),
                        trainingIncomingGuardPressure,
                        trainingIncomingHealthDamage,
                        environmentalHealthScale,
                        trainingIncomingPoiseDamage,
                        trainingIncomingCcSeverity,
                        trainingIncomingCcTicks,
                        trainingPerfectGuardPostureDamage);
        flaskHotbarController =
                new FlaskHotbarController(
                        this,
                        characterSessionController,
                        combatSessionController,
                        snapshot.manifest().contentVersion());
        consumableHotbarController =
                new ConsumableHotbarController(
                        this,
                        characterSessionController,
                        combatSessionController,
                        activeItemEngine.get(),
                        projectionCodec,
                        snapshot.manifest().contentVersion());
        combatSessionController.setConsumableInterruptObserver(
                (player, reason) -> {
                    flaskHotbarController.interruptFromCombat(player, reason);
                    consumableHotbarController.interruptFromCombat(player, reason);
                });
        liveTeachingSessionController =
                new LiveTeachingSessionController(
                        this,
                        characterSessionController,
                        combatSessionController,
                        activeBuildEngine.get(),
                        snapshot.manifest().contentVersion());
        knowledgeAcquisitionController =
                new KnowledgeAcquisitionController(
                        characterSessionController,
                        activeBuildEngine.get(),
                        snapshot.manifest().contentVersion());
        bossEncounterController =
                new BossEncounterController(
                        this,
                        characterSessionController,
                        flaskHotbarController,
                        databaseRuntime.bossEncounters(),
                        snapshot.manifest().contentVersion());
        partyController =
                new PartyController(this, characterSessionController, bossEncounterController);
        bossEncounterController.setPartyParticipantResolver(partyController::onlineMembers);
        downedController =
                new DownedController(
                        this,
                        combatSessionController,
                        bossEncounterController,
                        databaseRuntime.downedEncounters(),
                        snapshot.manifest().contentVersion());
        combatSessionController.setLethalDamageObserver(downedController::interceptLethal);
        combatSessionController.setDamageImmunityObserver(downedController::protectedFromDamage);
        combatSessionController.setHostileActionObserver(downedController::observeHostileAction);
        combatSessionController.setSuccessfulActionObserver(
                liveTeachingSessionController::observeSuccessfulAction);
        ChronicleController chronicleController =
                new ChronicleController(this, chronicle, characterSessionController::ready);
        characterSessionController.addReadyHandler(chronicleController::reconcile);
        characterSessionController.addReadyHandler(combatSessionController::onCharacterReady);
        characterSessionController.addReadyHandler(flaskHotbarController::onCharacterReady);
        characterSessionController.addReadyHandler(consumableHotbarController::onCharacterReady);
        characterSessionController.addReadyHandler(bossEncounterController::onCharacterReady);
        characterSessionController.addReadyHandler(partyController::onCharacterReady);
        characterSessionController.addReadyHandler(downedController::onCharacterReady);
        resourcePackGate.setReadyHandler(characterSessionController::onPackReady);
        sceneHubController =
                new SceneHubController(
                        this,
                        lifecycle,
                        resourcePackGate,
                        chronicle,
                        characterSessionController,
                        activeItemEngine.get(),
                        activeBuildEngine.get(),
                        combatSessionController,
                        snapshot.manifest().contentVersion());
        commandController =
                new MmoCommandController(
                        this,
                        lifecycle,
                        resourcePackGate,
                        activeSnapshot::get,
                        activeItemEngine::get,
                        activeMoveEngine::get,
                        activeAilmentEngine::get,
                        sceneHubController,
                        characterSessionController,
                        combatSessionController,
                        liveTeachingSessionController,
                        knowledgeAcquisitionController,
                        bossEncounterController,
                        partyController,
                        downedController);
        getServer().getPluginManager().registerEvents(chronicleController, this);
        return null;
    }

    private void registerMilestoneThreeRuntime() {
        getServer().getPluginManager().registerEvents(testItemProjectionController, this);
        getServer().getPluginManager().registerEvents(resourcePackGate, this);
        getServer().getPluginManager().registerEvents(characterSessionController, this);
        getServer().getPluginManager().registerEvents(combatSessionController, this);
        getServer().getPluginManager().registerEvents(flaskHotbarController, this);
        getServer().getPluginManager().registerEvents(consumableHotbarController, this);
        getServer().getPluginManager().registerEvents(liveTeachingSessionController, this);
        getServer().getPluginManager().registerEvents(bossEncounterController, this);
        getServer().getPluginManager().registerEvents(partyController, this);
        getServer().getPluginManager().registerEvents(downedController, this);
        getServer().getPluginManager().registerEvents(sceneHubController, this);
        getServer().getPluginManager().registerEvents(commandController, this);
        Objects.requireNonNull(getCommand("mmo"), "mmo command").setExecutor(commandController);
        characterSessionController.start();
        combatSessionController.start();
        flaskHotbarController.start();
        consumableHotbarController.start();
        liveTeachingSessionController.start();
        bossEncounterController.start();
        partyController.start();
        downedController.start();
        getLogger()
                .info(
                        "Milestone 3 runtime ready; item definitions="
                                + activeItemEngine.get().all().size()
                                + ", move definitions="
                                + activeMoveEngine.get().all().size()
                                + ", spell definitions="
                                + activeSpellEngine.get().all().size()
                                + ", technique definitions="
                                + activeBuildEngine.get().techniques().size()
                                + ", form definitions="
                                + activeBuildEngine.get().forms().size()
                                + ", ailment definitions="
                                + activeAilmentEngine.get().all().size()
                                + ", Scene preview=COMPACT_2D");
    }

    private void scheduleSmokeShutdown() {
        if (Boolean.getBoolean("mmo.bootstrap.smoke-test")) {
            getLogger().info("Bootstrap smoke test completed; scheduling a clean shutdown.");
            getServer().getScheduler().runTaskLater(this, getServer()::shutdown, 20L);
        }
    }

    private void logProviderHealth(StartupDecision decision) {
        for (ProviderHealthEntry entry : decision.providerHealth().providers()) {
            getLogger()
                    .info(
                            "Provider "
                                    + entry.providerId()
                                    + "="
                                    + entry.status()
                                    + " ["
                                    + entry.requirement()
                                    + "]: "
                                    + entry.message());
        }
    }

    private Path contentRoot() {
        String override = System.getProperty("mmo.content.path");
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        String configured = getConfig().getString("content.path", "content");
        Path path = Path.of(configured);
        return path.isAbsolute()
                ? path.normalize()
                : getDataFolder().toPath().resolve(path).toAbsolutePath().normalize();
    }

    private Map<String, ProviderRequirement> providerRequirements() {
        LinkedHashMap<String, ProviderRequirement> requirements = new LinkedHashMap<>();
        ConfigurationSection providers = getConfig().getConfigurationSection("providers");
        for (String key : ProviderAdapterFactory.defaultRequirements().keySet()) {
            if (providers == null || providers.getBoolean(key + ".enabled", true)) {
                boolean required =
                        providers != null && providers.getBoolean(key + ".required", false);
                requirements.put(
                        key,
                        required ? ProviderRequirement.REQUIRED : ProviderRequirement.OPTIONAL);
            }
        }
        return Map.copyOf(requirements);
    }

    private static List<String> pluginNames(String walletPluginName) {
        List<String> names = new ArrayList<>();
        names.add("Oraxen");
        names.add("MythicMobs");
        names.add("packetevents");
        names.add("WorldGuard");
        names.add(walletPluginName);
        return List.copyOf(names);
    }
}
