package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.provider.ProviderHealthEntry;
import com.branz.mmorpg.api.provider.ProviderRequirement;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.move.MoveEngine;
import com.branz.mmorpg.combat.move.MoveEngineErrorCode;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.integrations.PluginCapabilityProbe;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.items.definition.ItemEngineErrorCode;
import com.branz.mmorpg.items.projection.ProjectionTokenSigner;
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
    private ResourcePackGate resourcePackGate;
    private SceneHubController sceneHubController;
    private MmoCommandController commandController;
    private TestItemProjectionService testItemProjections;
    private TestItemProjectionController testItemProjectionController;
    private DatabaseRuntime databaseRuntime;
    private CharacterSessionController characterSessionController;
    private CombatSessionController combatSessionController;

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
        BukkitItemProjectionCodec projectionCodec =
                new BukkitItemProjectionCodec(this, ProjectionTokenSigner.random());
        testItemProjections = new TestItemProjectionService(projectionCodec);
        testItemProjectionController = new TestItemProjectionController(testItemProjections);
        characterSessionController =
                new CharacterSessionController(
                        this,
                        new CharacterSessionService(databaseRuntime),
                        new BukkitInventoryProjectionService(projectionCodec),
                        activeItemEngine.get(),
                        databaseRuntime.settings());
        int weaponDrawTicks = getConfig().getInt("combat.weapon-draw-ticks", 6);
        int weaponSheatheTicks = getConfig().getInt("combat.weapon-sheathe-ticks", 4);
        int engagementExitTicks = getConfig().getInt("combat.engagement-exit-ticks", 160);
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
        if (weaponDrawTicks < 1 || weaponSheatheTicks < 1 || engagementExitTicks < 1) {
            activeItemEngine.set(null);
            activeMoveEngine.set(null);
            resourcePackGate = null;
            characterSessionController = null;
            return "Combat weapon draw/sheathe/engagement exit ticks must be positive.";
        }
        combatSessionController =
                new CombatSessionController(
                        this,
                        characterSessionController,
                        activeMoveEngine.get(),
                        trainingWeapon.power(),
                        weaponDrawTicks,
                        weaponSheatheTicks,
                        engagementExitTicks,
                        dodgeProfile);
        ChronicleController chronicleController =
                new ChronicleController(this, chronicle, characterSessionController::ready);
        characterSessionController.addReadyHandler(chronicleController::reconcile);
        characterSessionController.addReadyHandler(combatSessionController::onCharacterReady);
        resourcePackGate.setReadyHandler(characterSessionController::onPackReady);
        sceneHubController =
                new SceneHubController(
                        this,
                        lifecycle,
                        resourcePackGate,
                        chronicle,
                        characterSessionController,
                        snapshot.manifest().contentVersion());
        commandController =
                new MmoCommandController(
                        this,
                        lifecycle,
                        resourcePackGate,
                        activeSnapshot::get,
                        activeItemEngine::get,
                        activeMoveEngine::get,
                        sceneHubController,
                        characterSessionController,
                        combatSessionController);
        getServer().getPluginManager().registerEvents(chronicleController, this);
        return null;
    }

    private void registerMilestoneThreeRuntime() {
        getServer().getPluginManager().registerEvents(testItemProjectionController, this);
        getServer().getPluginManager().registerEvents(resourcePackGate, this);
        getServer().getPluginManager().registerEvents(characterSessionController, this);
        getServer().getPluginManager().registerEvents(combatSessionController, this);
        getServer().getPluginManager().registerEvents(sceneHubController, this);
        getServer().getPluginManager().registerEvents(commandController, this);
        Objects.requireNonNull(getCommand("mmo"), "mmo command").setExecutor(commandController);
        characterSessionController.start();
        combatSessionController.start();
        getLogger()
                .info(
                        "Milestone 3 runtime ready; item definitions="
                                + activeItemEngine.get().all().size()
                                + ", move definitions="
                                + activeMoveEngine.get().all().size()
                                + ", Scene preview=COMPACT_2D");
    }

    private void scheduleSmokeShutdown() {
        if (Boolean.getBoolean("mmo.bootstrap.smoke-test")) {
            getLogger().info("Bootstrap smoke test completed; scheduling a clean shutdown.");
            getServer().getScheduler().runTaskLater(this, getServer()::shutdown, 1L);
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
