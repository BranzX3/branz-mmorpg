package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.content.ContentReloadResult;
import com.branz.mmorpg.api.combat.CombatPolicy;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.lifeskill.LifeSkillQuery;
import com.branz.mmorpg.api.lifeskill.LifeSkillMutationService;
import com.branz.mmorpg.api.player.PlayerProfileRepository;
import com.branz.mmorpg.api.mastery.CombatMasteryService;
import com.branz.mmorpg.api.item.LoadoutService;
import com.branz.mmorpg.api.item.InventoryService;
import com.branz.mmorpg.api.item.InventoryRepository;
import com.branz.mmorpg.api.item.EquipmentService;
import com.branz.mmorpg.api.item.EquipmentSlot;
import com.branz.mmorpg.api.gathering.GatheringService;
import com.branz.mmorpg.api.gathering.GatheringNodeRepository;
import com.branz.mmorpg.api.gathering.GatheringNodeState;
import com.branz.mmorpg.api.gathering.GatheringNodeInstance;
import com.branz.mmorpg.api.gathering.WorldBlockPosition;
import com.branz.mmorpg.api.crafting.CraftingService;
import com.branz.mmorpg.api.crafting.CraftingRepository;
import com.branz.mmorpg.api.economy.EconomyPaymentPort;
import com.branz.mmorpg.api.economy.AdminCurrencyPort;
import com.branz.mmorpg.api.item.LootService;
import com.branz.mmorpg.api.mob.MobRepository;
import com.branz.mmorpg.api.encounter.EncounterRepository;
import com.branz.mmorpg.api.encounter.EncounterService;
import com.branz.mmorpg.api.social.PartyRepository;
import com.branz.mmorpg.api.social.PartyService;
import com.branz.mmorpg.api.social.TradeRepository;
import com.branz.mmorpg.api.social.TradeService;
import com.branz.mmorpg.api.telemetry.TelemetryService;
import com.branz.mmorpg.quest.api.QuestContentService;
import com.branz.mmorpg.quest.api.QuestService;
import com.branz.mmorpg.quest.paper.PaperQuestRuntime;
import com.branz.mmorpg.api.player.DuplicateLoginPolicy;
import com.branz.mmorpg.api.player.PlayerSession;
import com.branz.mmorpg.api.runtime.Scheduler;
import com.branz.mmorpg.api.runtime.TransactionRunner;
import com.branz.mmorpg.api.service.ServiceStatus;
import com.branz.mmorpg.content.AtomicContentService;
import com.branz.mmorpg.core.player.PlayerSessionService;
import com.branz.mmorpg.core.character.PermanentCharacterClassService;
import com.branz.mmorpg.core.character.CharacterClassProgressionService;
import com.branz.mmorpg.core.build.CharacterBuildService;
import com.branz.mmorpg.core.event.SimpleEventBus;
import com.branz.mmorpg.core.stat.PlayerAttributeService;
import com.branz.mmorpg.core.lifeskill.LifeSkillProgressionService;
import com.branz.mmorpg.core.mastery.DefaultCombatMasteryService;
import com.branz.mmorpg.core.item.DefaultLoadoutService;
import com.branz.mmorpg.core.item.DefaultInventoryService;
import com.branz.mmorpg.core.item.DefaultLootService;
import com.branz.mmorpg.core.item.DefaultEquipmentService;
import com.branz.mmorpg.core.item.StarterKitDeliveryService;
import com.branz.mmorpg.core.gathering.DefaultGatheringService;
import com.branz.mmorpg.core.crafting.DefaultCraftingService;
import com.branz.mmorpg.core.encounter.DefaultEncounterService;
import com.branz.mmorpg.core.social.DefaultPartyService;
import com.branz.mmorpg.core.social.DefaultTradeService;
import com.branz.mmorpg.core.telemetry.InMemoryTelemetryService;
import com.branz.mmorpg.core.runtime.ExecutorScheduler;
import com.branz.mmorpg.core.runtime.SystemGameClock;
import com.branz.mmorpg.core.service.ServiceContainer;
import com.branz.mmorpg.storage.DatabaseConfig;
import com.branz.mmorpg.storage.DatabaseManager;
import com.branz.mmorpg.storage.JdbcPlayerProfileRepository;
import com.branz.mmorpg.storage.JdbcCharacterClassSelectionRepository;
import com.branz.mmorpg.storage.JdbcCharacterClassProgressionRepository;
import com.branz.mmorpg.storage.JdbcTransactionRunner;
import com.branz.mmorpg.storage.FilePendingSessionSaveStore;
import com.branz.mmorpg.storage.JdbcCombatMasteryRepository;
import com.branz.mmorpg.storage.JdbcInventoryRepository;
import com.branz.mmorpg.storage.JdbcGatheringNodeRepository;
import com.branz.mmorpg.storage.JdbcCraftingRepository;
import com.branz.mmorpg.storage.JdbcMobRepository;
import com.branz.mmorpg.storage.JdbcEncounterRepository;
import com.branz.mmorpg.storage.JdbcPartyRepository;
import com.branz.mmorpg.storage.JdbcTradeRepository;
import com.branz.mmorpg.storage.JdbcPendingSlotItemRepository;
import com.branz.mmorpg.storage.JdbcStarterKitDeliveryRepository;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class BranzMMORPGPlugin extends JavaPlugin {
    private AtomicContentService contentService;
    private DatabaseManager databaseManager;
    private ServiceContainer serviceContainer;
    private Scheduler scheduler;
    private TransactionRunner transactionRunner;
    private PlayerSessionService sessionService;
    private PlayerProfileRepository profileRepository;
    private PermanentCharacterClassService characterClassService;
    private CharacterClassProgressionService characterClassProgression;
    private SimpleEventBus characterClassEvents;
    private PlayerAttributeService attributeService;
    private LifeSkillProgressionService lifeSkillProgression;
    private DefaultCombatMasteryService combatMasteryService;
    private DefaultLoadoutService loadoutService;
    private CharacterBuildService characterBuildService;
    private DefaultInventoryService inventoryService;
    private DefaultLootService lootService;
    private InventoryRepository inventoryRepository;
    private DefaultEquipmentService equipmentService;
    private GatheringNodeRepository gatheringRepository;
    private DefaultGatheringService gatheringService;
    private CraftingRepository craftingRepository;
    private DefaultCraftingService craftingService;
    private EconomyPaymentPort economyPayment;
    private AdminCurrencyPort adminCurrency;
    private MobRepository mobRepository;
    private EncounterRepository encounterRepository;
    private DefaultEncounterService encounterService;
    private PartyRepository partyRepository;
    private DefaultPartyService partyService;
    private TradeRepository tradeRepository;
    private DefaultTradeService tradeService;
    private PaperCombatRuntime combatRuntime;
    private PaperSkillRuntime skillRuntime;
    private PaperStatusRuntime statusRuntime;
    private PaperItemRuntime itemRuntime;
    private PaperClassCompassRuntime classCompassRuntime;
    private PaperGatheringRuntime gatheringRuntime;
    private PaperCraftingRuntime craftingRuntime;
    private PaperMobRuntime mobRuntime;
    private PaperEncounterRuntime encounterRuntime;
    private PaperSocialRuntime socialRuntime;
    private PaperHudRuntime hudRuntime;
    private InMemoryTelemetryService telemetryService;
    private PaperTelemetryRuntime telemetryRuntime;
    private PaperQuestRuntime questRuntime;
    private Path contentDirectory;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBundledContent();

        try {
            contentDirectory = getDataFolder().toPath()
                    .resolve(getConfig().getString("content.directory", "content"))
                    .normalize();
            contentService = new AtomicContentService();
            telemetryService = new InMemoryTelemetryService();
            ContentReloadResult initialLoad = contentService.reload(contentDirectory);
            if (!initialLoad.successful()) {
                logDiagnostics("Initial content load failed", initialLoad);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            if (getConfig().getBoolean("database.enabled", false)) {
                databaseManager = DatabaseManager.connect(readDatabaseConfig());
                transactionRunner = new JdbcTransactionRunner(databaseManager);
                getLogger().info("Database connected and migrations applied.");
            } else {
                getLogger().warning("Database is disabled; persistent gameplay services must remain offline.");
            }

            // Registration order is dependency order; the container rolls back
            // everything it started if any service fails.
            serviceContainer = new ServiceContainer();
            scheduler = serviceContainer.register(new ExecutorScheduler(
                    getConfig().getInt("core.async-pool-size", 4),
                    runnable -> getServer().getScheduler().runTask(this, runnable)));
            if (databaseManager != null) {
                // Sessions require storage. Without it there is nowhere to load a
                // profile from, and inventing a blank one is exactly what the
                // fail-closed rule forbids.
                profileRepository = new JdbcPlayerProfileRepository(databaseManager);
                sessionService = serviceContainer.register(new PlayerSessionService(
                        profileRepository,
                        scheduler,
                        new SystemGameClock(),
                        () -> contentService.snapshot().revision(),
                        duplicateLoginPolicy(),
                        new FilePendingSessionSaveStore(
                                getDataFolder().toPath().resolve(getConfig().getString(
                                        "player-session.recovery-directory",
                                        "recovery/player-profiles"))),
                        Math.max(1, getConfig().getInt("player-session.save-max-attempts", 3))));
                characterClassEvents = new SimpleEventBus(failure -> getLogger().log(
                        Level.WARNING, "Permanent class event subscriber failed", failure));
                characterClassService = new PermanentCharacterClassService(
                        sessionService, contentService,
                        new JdbcCharacterClassSelectionRepository(databaseManager),
                        characterClassEvents, new SystemGameClock());
                attributeService = new PlayerAttributeService(
                        sessionService, contentService, characterClassEvents, new SystemGameClock());
                characterClassProgression = new CharacterClassProgressionService(
                        sessionService, contentService,
                        new JdbcCharacterClassProgressionRepository(databaseManager),
                        attributeService, characterClassEvents, new SystemGameClock());
                characterClassEvents.subscribe(
                        com.branz.mmorpg.api.character.CharacterClassSelected.class,
                        selected -> activateCombatProfile(selected.playerId()));
                lifeSkillProgression = new LifeSkillProgressionService(
                        profileRepository, sessionService, new SystemGameClock(),
                        contentService::snapshot, characterClassEvents);
                combatMasteryService = new DefaultCombatMasteryService(
                        new JdbcCombatMasteryRepository(databaseManager),
                        contentService::snapshot, new SystemGameClock(), characterClassEvents);
                inventoryRepository = new JdbcInventoryRepository(databaseManager);
                inventoryService = new DefaultInventoryService(
                        inventoryRepository,
                        contentService::snapshot, new SystemGameClock());
                lootService = new DefaultLootService(
                        inventoryService, contentService::snapshot, new SystemGameClock());
                gatheringRepository = new JdbcGatheringNodeRepository(databaseManager);
                gatheringService = new DefaultGatheringService(
                        gatheringRepository, sessionService,
                        contentService::snapshot, new SystemGameClock(),
                        characterClassEvents);
                craftingRepository = new JdbcCraftingRepository(databaseManager);
                economyPayment = new PaperWalletEconomyAdapter(this);
                adminCurrency = (AdminCurrencyPort) economyPayment;
                craftingService = new DefaultCraftingService(
                        craftingRepository, economyPayment, sessionService,
                        contentService::snapshot, new SystemGameClock());
                mobRepository = new JdbcMobRepository(databaseManager);
                encounterRepository = new JdbcEncounterRepository(databaseManager);
                encounterService = new DefaultEncounterService(
                        encounterRepository, lootService,
                        contentService::snapshot, new SystemGameClock());
                partyRepository = new JdbcPartyRepository(databaseManager);
                partyService = new DefaultPartyService(
                        partyRepository, new SystemGameClock());
                tradeRepository = new JdbcTradeRepository(databaseManager);
                tradeService = new DefaultTradeService(
                        tradeRepository, contentService::snapshot, new SystemGameClock());
            }
            serviceContainer.startAll();

            if (sessionService != null) {
                combatRuntime = new PaperCombatRuntime(
                        this, sessionService, attributeService, combatPolicy());
                loadoutService = new DefaultLoadoutService(sessionService,
                        contentService::snapshot,
                        (playerId, now) -> combatRuntime.engine().combatState()
                                .inCombat(playerId, now),
                        new SystemGameClock());
                characterBuildService = new CharacterBuildService(
                        sessionService, contentService, loadoutService,
                        characterClassProgression, combatMasteryService,
                        new SystemGameClock());
                equipmentService = new DefaultEquipmentService(
                        inventoryRepository, contentService::snapshot,
                        (playerId, now) -> combatRuntime.engine().combatState()
                                .inCombat(playerId, now),
                        new SystemGameClock());
                itemRuntime = new PaperItemRuntime(
                        this, sessionService, inventoryService, contentService,
                        readItemTokenSecret());
                getServer().getPluginManager().registerEvents(itemRuntime, this);
                StarterKitDeliveryService starterKitDelivery = new StarterKitDeliveryService(
                        new JdbcStarterKitDeliveryRepository(databaseManager), inventoryService,
                        equipmentService, loadoutService, sessionService, contentService,
                        new SystemGameClock());
                classCompassRuntime = new PaperClassCompassRuntime(
                        this, sessionService, contentService, characterClassService,
                        characterClassProgression, starterKitDelivery,
                        new JdbcPendingSlotItemRepository(databaseManager), itemRuntime,
                        scheduler, readItemTokenSecret());
                getServer().getPluginManager().registerEvents(classCompassRuntime, this);
                gatheringRuntime = new PaperGatheringRuntime(
                        this, gatheringService, contentService, scheduler, itemRuntime);
                getServer().getPluginManager().registerEvents(gatheringRuntime, this);
                getServer().getScheduler().runTaskTimer(
                        this, gatheringRuntime::tick, 1L, 1L);
                getServer().getScheduler().runTaskTimer(
                        this, gatheringRuntime::refresh, 20L, 20L);
                craftingRuntime = new PaperCraftingRuntime(
                        this, craftingService, sessionService,
                        contentService, scheduler, itemRuntime);
                getServer().getPluginManager().registerEvents(craftingRuntime, this);
                getServer().getPluginManager().registerEvents(new PlayerSessionListener(
                        this, sessionService, attributeService,
                        this::activateCombatProfile, playerId -> {
                            classCompassRuntime.sessionReady(playerId);
                            craftingRuntime.sessionReady(playerId);
                        }, playerId -> {
                            characterClassProgression.forget(playerId);
                            combatMasteryService.forget(playerId);
                        }), this);
                getServer().getPluginManager().registerEvents(combatRuntime, this);
                getServer().getScheduler().runTaskTimer(this,
                        combatRuntime::sweepCombatState, 20L, 20L);
                statusRuntime = new PaperStatusRuntime(
                        this, combatRuntime, attributeService, contentService);
                getServer().getPluginManager().registerEvents(statusRuntime, this);
                getServer().getScheduler().runTaskTimer(this, statusRuntime::tick, 1L, 1L);
                skillRuntime = new PaperSkillRuntime(
                        this, sessionService, contentService, combatRuntime, statusRuntime,
                        loadoutService, itemRuntime, telemetryService, attributeService,
                        characterClassProgression);
                skillRuntime.inputReserved(classCompassRuntime::isHeldCompass);
                combatRuntime.basicAttackHandler(skillRuntime::basicAttack);
                combatRuntime.damageListener(this::rewardCombatDamage);
                getServer().getPluginManager().registerEvents(skillRuntime, this);
                getServer().getScheduler().runTaskTimer(this, skillRuntime::tick, 1L, 1L);
                hudRuntime = new PaperHudRuntime(this, skillRuntime, statusRuntime);
                getServer().getPluginManager().registerEvents(hudRuntime, this);
                getServer().getScheduler().runTaskTimer(
                        this, hudRuntime::flush, 1L, 5L);
                getServer().getScheduler().runTaskTimer(
                        this, hudRuntime::markOnlineDirty, 20L, 20L);
                mobRuntime = new PaperMobRuntime(
                        this, mobRepository, contentService, scheduler, lootService,
                        partyService);
                getServer().getPluginManager().registerEvents(mobRuntime, this);
                getServer().getScheduler().runTaskTimer(this, mobRuntime::tick, 1L, 1L);
                encounterRuntime = new PaperEncounterRuntime(
                        this, encounterService, contentService, scheduler, mobRuntime);
                getServer().getPluginManager().registerEvents(encounterRuntime, this);
                getServer().getScheduler().runTaskTimer(
                        this, encounterRuntime::tick, 20L, 20L);
                socialRuntime = new PaperSocialRuntime(this, tradeService, scheduler);
                getServer().getPluginManager().registerEvents(socialRuntime, this);
                Path questDirectory = getDataFolder().toPath().resolve(
                        getConfig().getString("quest.directory", "quest-content")).normalize();
                questRuntime = new PaperQuestRuntime(
                        this, databaseManager, contentService, scheduler,
                        inventoryService, combatMasteryService, partyService,
                        adminCurrency, questDirectory, new SystemGameClock(),
                        telemetryService);
                questRuntime.encounterStarter(encounterRuntime::startDurable);
                inventoryService.mutationListener(change -> {
                    if (change.acquisition()) {
                        questRuntime.itemAcquired(change.playerId(), change.itemId(),
                                change.quantity(), change.operationId());
                    }
                });
                combatMasteryService.mutationListener(change ->
                        questRuntime.masteryChanged(change.playerId(), change.masteryId(),
                                change.operationId()));
                skillRuntime.skillListener(questRuntime::skillUsed);
                craftingRuntime.completionListener(questRuntime::craftCompleted);
                getServer().getPluginManager().registerEvents(questRuntime, this);
                getServer().getScheduler().runTaskTimer(
                        this, questRuntime::tick, 1L, 1L);
                encounterRuntime.completionListener(state -> {
                    var definition = contentService.snapshot().encounters()
                            .get(state.definitionId());
                    if (definition == null) return;
                    java.util.Set<UUID> eligible = state.participantSnapshot().stream()
                            .filter(playerId -> state.contributions()
                                    .getOrDefault(playerId, java.util.Map.of()).values()
                                    .stream().mapToDouble(Double::doubleValue).sum()
                                    >= definition.minimumContribution())
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
                    eligible.forEach(playerId -> questRuntime.bossDefeated(
                            playerId, state.definitionId(), eligible));
                });
                telemetryRuntime = new PaperTelemetryRuntime(telemetryService, scheduler);
                getServer().getPluginManager().registerEvents(telemetryRuntime, this);
                getServer().getScheduler().runTaskTimer(
                        this, telemetryRuntime::poll, 20L, 20L);
                startAutosaveTask();
                getServer().getServicesManager().register(
                        LifeSkillQuery.class, sessionService, this, ServicePriority.Normal);
                getServer().getServicesManager().register(
                        LifeSkillMutationService.class, lifeSkillProgression,
                        this, ServicePriority.Normal);
                getServer().getServicesManager().register(
                        CombatMasteryService.class, combatMasteryService,
                        this, ServicePriority.Normal);
                getServer().getServicesManager().register(
                        LoadoutService.class, loadoutService, this, ServicePriority.Normal);
                getServer().getServicesManager().register(
                        InventoryService.class, inventoryService, this, ServicePriority.Normal);
                getServer().getServicesManager().register(
                        LootService.class, lootService, this, ServicePriority.Normal);
                getServer().getServicesManager().register(
                        EquipmentService.class, equipmentService, this, ServicePriority.Normal);
                getServer().getServicesManager().register(
                        GatheringService.class, gatheringService, this, ServicePriority.Normal);
                getServer().getServicesManager().register(
                        CraftingService.class, craftingService, this, ServicePriority.Normal);
                getServer().getServicesManager().register(
                        EconomyPaymentPort.class, economyPayment, this, ServicePriority.Normal);
                getServer().getServicesManager().register(
                        AdminCurrencyPort.class, adminCurrency, this, ServicePriority.Normal);
                getServer().getServicesManager().register(
                        EncounterService.class, encounterService, this, ServicePriority.Normal);
                getServer().getServicesManager().register(
                        PartyService.class, partyService, this, ServicePriority.Normal);
                getServer().getServicesManager().register(
                        TradeService.class, tradeService, this, ServicePriority.Normal);
                getServer().getServicesManager().register(
                        TelemetryService.class, telemetryService, this, ServicePriority.Normal);
                getServer().getServicesManager().register(
                        QuestService.class, questRuntime.quests(), this, ServicePriority.Normal);
                getServer().getServicesManager().register(
                        QuestContentService.class, questRuntime.content(),
                        this, ServicePriority.Normal);
            }
            getServer().getServicesManager().register(
                    ContentService.class, contentService, this, ServicePriority.Normal);
            Objects.requireNonNull(getCommand("branz"), "branz command").setExecutor(new AdminCommand());
            getLogger().info("Branz MMORPG enabled with " + initialLoad.definitionCount()
                    + " content definitions (revision " + initialLoad.revision() + ").");
        } catch (Exception exception) {
            getLogger().severe("Branz MMORPG startup failed: " + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /** Awards progression only from damage already committed by CombatEngine. */
    private void rewardCombatDamage(
            com.branz.mmorpg.core.combat.CombatEvents.DamageDealt event) {
        if (event.attackerId() == null
                || getServer().getPlayer(event.attackerId()) == null
                || getServer().getPlayer(event.targetId()) != null) return;
        double effectiveDamage = event.result().applied() + event.result().absorbed();
        if (!Double.isFinite(effectiveDamage) || effectiveDamage <= 0.0) return;
        PlayerSession session = sessionService.session(event.attackerId()).orElse(null);
        if (session == null || !session.playable() || session.profile().classId().isEmpty()) return;
        var weapon = itemRuntime.activeWeapon(event.attackerId())
                .or(() -> loadoutService.current(event.attackerId())).orElse(null);
        if (weapon == null) return;
        long baseXp = Math.max(1L, Math.round(effectiveDamage));
        var token = session.token();
        ContentId classId = session.profile().classId().orElseThrow();
        String eventKey = event.eventId().toString();
        scheduler.async(() -> {
            if (!sessionService.isLive(token)) return;
            characterClassProgression.grantXp(event.attackerId(), baseXp,
                    OperationId.of("classxp", classId.value(), event.attackerId(), eventKey));
            long familyXp = Math.round(baseXp * weapon.familyXpShare());
            long typeXp = Math.round(baseXp * weapon.typeXpShare());
            if (familyXp > 0) {
                combatMasteryService.grantContribution(event.attackerId(),
                        weapon.familyMasteryId(), familyXp, 1.0,
                        OperationId.of("mastery", weapon.familyMasteryId().value(),
                                event.attackerId(), eventKey + "_family"));
            }
            if (typeXp > 0) {
                combatMasteryService.grantContribution(event.attackerId(),
                        weapon.typeMasteryId(), typeXp, 1.0,
                        OperationId.of("mastery", weapon.typeMasteryId().value(),
                                event.attackerId(), eventKey + "_type"));
            }
        }).exceptionally(failure -> {
            getLogger().log(Level.WARNING,
                    "Combat progression reward failed for " + event.attackerId(), failure);
            return null;
        });
    }

    /** Loads SQL-backed progression before exposing the in-memory combat stat block. */
    private void activateCombatProfile(UUID playerId) {
        characterClassProgression.activate(playerId);
        combatMasteryService.activate(playerId);
        if (sessionService.session(playerId).filter(PlayerSession::playable).isEmpty()) {
            characterClassProgression.forget(playerId);
            combatMasteryService.forget(playerId);
            return;
        }
        attributeService.activate(playerId);
        characterClassProgression.reconcileCached(playerId);
    }

    @Override
    public void onDisable() {
        if (questRuntime != null) {
            questRuntime.shutdown();
        }
        getServer().getServicesManager().unregisterAll(this);
        getServer().getScheduler().cancelTasks(this);
        Duration shutdownBudget = Duration.ofMillis(Math.max(
                1L, getConfig().getLong("player-session.shutdown-timeout-millis", 10_000L)));
        if (scheduler != null && !scheduler.drainAndShutdown(shutdownBudget)) {
            getLogger().warning("Scheduler did not drain within " + shutdownBudget.toMillis()
                    + "ms; remaining work was abandoned.");
        }
        if (serviceContainer != null) {
            // Storage closes last: draining work above may still need it.
            serviceContainer.stopAll();
            serviceContainer = null;
            scheduler = null;
            sessionService = null;
            profileRepository = null;
            lifeSkillProgression = null;
            combatMasteryService = null;
            loadoutService = null;
            characterBuildService = null;
            inventoryService = null;
            lootService = null;
            inventoryRepository = null;
            equipmentService = null;
            gatheringRepository = null;
            gatheringService = null;
            craftingRepository = null;
            craftingService = null;
            economyPayment = null;
            adminCurrency = null;
            mobRepository = null;
            encounterRepository = null;
            encounterService = null;
            partyRepository = null;
            partyService = null;
            tradeRepository = null;
            tradeService = null;
            combatRuntime = null;
            skillRuntime = null;
            statusRuntime = null;
            itemRuntime = null;
            gatheringRuntime = null;
            craftingRuntime = null;
            mobRuntime = null;
            encounterRuntime = null;
            socialRuntime = null;
            hudRuntime = null;
            telemetryRuntime = null;
            questRuntime = null;
        }
        transactionRunner = null;
        telemetryService = null;
        if (databaseManager != null) {
            databaseManager.close();
            databaseManager = null;
        }
        getLogger().info("Branz MMORPG disabled.");
    }

    private DuplicateLoginPolicy duplicateLoginPolicy() {
        String configured = getConfig()
                .getString("player.duplicate-login", DuplicateLoginPolicy.CLOSE_PREVIOUS.name())
                .toUpperCase(Locale.ROOT);
        try {
            return DuplicateLoginPolicy.valueOf(configured);
        } catch (IllegalArgumentException unknown) {
            getLogger().warning("Unknown player.duplicate-login '" + configured
                    + "'; falling back to " + DuplicateLoginPolicy.CLOSE_PREVIOUS);
            return DuplicateLoginPolicy.CLOSE_PREVIOUS;
        }
    }

    private CombatPolicy combatPolicy() {
        return new CombatPolicy(
                getConfig().getBoolean("combat.pvp-enabled", false),
                getConfig().getBoolean("combat.friendly-fire", false),
                getConfig().getDouble("combat.pvp-coefficient", 0.5),
                getConfig().getDouble("combat.mitigation-constant", 100.0),
                getConfig().getDouble("combat.maximum-reduction", 0.85),
                getConfig().getDouble("combat.minimum-damage-fraction", 0.10),
                getConfig().getLong("combat.timeout-millis", 6000L));
    }

    private void startAutosaveTask() {
        long intervalTicks = Math.max(20L * 30, getConfig().getLong("player.autosave-seconds", 300) * 20L);
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                sessionService.flushAll();
                sessionService.retryPendingSaves();
            } catch (RuntimeException failure) {
                getLogger().warning("Autosave pass failed: " + failure.getMessage());
            }
        }, intervalTicks, intervalTicks);
    }

    private void saveBundledContent() {
        for (String resource : java.util.List.of(
                "content/materials/aether_ore.yml",
                "content/materials/aether_ingot.yml",
                "content/materials/stone_chunk.yml",
                "content/materials/iron_ore.yml",
                "content/skills/basic_strike.yml",
                "content/skills/heavy_slash.yml",
                "content/skills/precise_shot.yml",
                "content/skills/fire_burst.yml",
                "content/skills/rallying_shout.yml",
                "content/skills/warbreaker.yml",
                "content/skills/mana_shield.yml",
                "content/skills/meteor.yml",
                "content/skills/smoke_veil.yml",
                        "content/skills/shadow_step.yml",
                        "content/skills/shield_bash.yml",
                        "content/skills/fire_bolt.yml",
                        "content/skills/flame_dash.yml",
                        "content/skills/dagger_strike.yml",
                        "content/skills/dagger_flurry.yml",
                        "content/skills/throwing_knife.yml",
                        "content/skills/eviscerate.yml",
                "content/classes/warrior.yml",
                "content/classes/mage.yml",
                "content/classes/rogue.yml",
                "content/class_trees/warrior_root.yml",
                "content/class_trees/warrior_rally.yml",
                "content/class_trees/warrior_momentum.yml",
                "content/class_trees/warrior_ultimate.yml",
                "content/class_trees/mage_root.yml",
                "content/class_trees/mage_shield.yml",
                "content/class_trees/mage_efficiency.yml",
                "content/class_trees/mage_ultimate.yml",
                "content/class_trees/rogue_root.yml",
                "content/class_trees/rogue_smoke.yml",
                "content/class_trees/rogue_combo.yml",
                "content/class_trees/rogue_ultimate.yml",
                "content/inputs/default.yml",
                "content/combos/broadsword_heavy_strike.yml",
                "content/life_skills/mining.yml",
                "content/life_skills/mining_stoneworker.yml",
                "content/life_skills/mining_efficient_swing.yml",
                "content/life_skills/mining_ore_sense.yml",
                "content/life_skills/mining_prospector.yml",
                "content/life_skills/mining_deep_delver.yml",
                "content/life_skills/mining_geologist.yml",
                "content/masteries/sword.yml",
                "content/masteries/broadsword.yml",
                "content/masteries/bow.yml",
                "content/masteries/longbow.yml",
                "content/masteries/fire_staff.yml",
                        "content/masteries/pyromancer_staff.yml",
                        "content/masteries/dagger.yml",
                        "content/masteries/dual_daggers.yml",
                "content/mastery_trees/sword_edge.yml",
                "content/mastery_trees/broadsword_guard.yml",
                "content/mastery_trees/bow_precision.yml",
                "content/mastery_trees/longbow_draw.yml",
                "content/mastery_trees/staff_focus.yml",
                        "content/mastery_trees/pyromancer_burn.yml",
                        "content/mastery_trees/dagger_agility.yml",
                        "content/mastery_trees/dual_daggers_combo.yml",
                "content/weapons/broadsword.yml",
                "content/weapons/longbow.yml",
                        "content/weapons/fire_staff.yml",
                        "content/weapons/daggers.yml",
                "content/loot/aether_cache.yml",
                "content/gathering/aether_deposit.yml",
                "content/gathering/stone_deposit.yml",
                "content/gathering/iron_vein.yml",
                "content/professions/blacksmithing.yml",
                "content/recipes/aether_ingot.yml",
                "content/mobs/seal_guardian.yml",
                "content/encounters/seal_guardian.yml",
                "content/statuses/burn.yml",
                "content/statuses/bleed.yml",
                "content/statuses/poison.yml",
                "content/statuses/slow.yml",
                "content/statuses/root.yml",
                "content/statuses/stun.yml",
                "content/statuses/silence.yml",
                "content/statuses/shield.yml",
                "content/statuses/regeneration.yml",
                "content/statuses/vulnerability.yml")) {
            if (!getDataFolder().toPath().resolve(resource).toFile().exists()) {
                saveResource(resource, false);
            }
        }
        for (String resource : java.util.List.of(
                "quest-content/quests/the_old_seal.yml",
                "quest-content/dialogues/keeper_warning.yml",
                "quest-content/cutscenes/seal_opening.yml",
                "quest-content/lang/en_us.yml")) {
            if (!getDataFolder().toPath().resolve(resource).toFile().exists()) {
                saveResource(resource, false);
            }
        }
    }

    private DatabaseConfig readDatabaseConfig() {
        return new DatabaseConfig(
                getConfig().getString("database.host", "localhost"),
                getConfig().getInt("database.port", 3306),
                getConfig().getString("database.name", "branz_mmorpg"),
                getConfig().getString("database.username", "branz"),
                getConfig().getString("database.password", ""),
                getConfig().getInt("database.pool-size", 10),
                getConfig().getLong("database.connection-timeout-millis", 5000));
    }

    private byte[] readItemTokenSecret() {
        String configured = getConfig().getString("items.token-secret", "");
        if (configured != null && !configured.isBlank()) {
            try {
                byte[] decoded = java.util.Base64.getDecoder().decode(configured);
                if (decoded.length >= 32) return decoded;
            } catch (IllegalArgumentException invalid) {
                getLogger().warning("items.token-secret is invalid; generating a replacement.");
            }
        }
        byte[] generated = new byte[32];
        new java.security.SecureRandom().nextBytes(generated);
        getConfig().set("items.token-secret",
                java.util.Base64.getEncoder().encodeToString(generated));
        saveConfig();
        getLogger().warning("Generated a new signed-item token secret in config.yml.");
        return generated;
    }

    private void logDiagnostics(String heading, ContentReloadResult result) {
        getLogger().severe(heading + "; active revision remains " + result.revision() + '.');
        result.diagnostics().forEach(line -> getLogger().severe(" - " + line));
    }

    private boolean playerCommand(CommandSender sender, String label, String[] args) {
        if (sessionService == null) {
            sender.sendMessage("Player sessions are offline; the database is disabled.");
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage("Usage: /" + label + " player <inspect|save> <player>");
            return true;
        }
        var target = getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("Player " + args[2] + " is not online.");
            return true;
        }
        var session = sessionService.session(target.getUniqueId());
        if (session.isEmpty()) {
            sender.sendMessage("No MMO session for " + args[2] + ".");
            return true;
        }
        PlayerSession live = session.get();
        if (args[1].equalsIgnoreCase("inspect")) {
            sender.sendMessage("Session " + live.token() + ": " + live.state()
                    + ", content revision " + live.contentRevision());
            if (live.state().playable()) {
                sender.sendMessage("Profile: schema " + live.profile().schemaVersion()
                        + ", created " + live.profile().createdAt()
                        + ", trained skills " + live.lifeSkills().trainedSkills().size());
            } else {
                sender.sendMessage("Profile is not loaded; MMO features are disabled for this player.");
            }
            int pending = sessionService.pendingSaves().size();
            if (pending > 0) {
                sender.sendMessage("Pending saves awaiting recovery: " + pending);
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("save")) {
            sender.sendMessage("Session save started.");
            scheduler.async(sessionService::flushAll).whenComplete((saved, failure) ->
                    scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Session save failed; check server logs.");
                            getLogger().log(java.util.logging.Level.WARNING,
                                    "Manual session save failed", failure);
                        } else {
                            sender.sendMessage("Flushed " + saved + " dirty session(s).");
                        }
                    }));
            return true;
        }
        sender.sendMessage("Usage: /" + label + " player <inspect|save> <player>");
        return true;
    }

    private boolean lifeCommand(CommandSender sender, String label, String[] args) {
        if (lifeSkillProgression == null || sessionService == null) {
            sender.sendMessage("Life Skill services are offline.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /" + label
                    + " life <inspect|tree|grant-xp> <player> [skill] [amount] [operation]");
            return true;
        }
        var target = getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("Player " + args[2] + " is not online.");
            return true;
        }
        if (args[1].equalsIgnoreCase("inspect")) {
            var profile = sessionService.profile(target.getUniqueId());
            if (args.length == 3) {
                sender.sendMessage("Life Skills for " + target.getName() + ": "
                        + profile.trainedSkills());
            } else {
                ContentId skillId = ContentId.parse(args[3]);
                var skill = profile.skill(skillId);
                sender.sendMessage(skillId + ": level " + skill.level() + ", XP "
                        + skill.totalXp() + ", points " + skill.unspentPoints());
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("tree") && args.length == 4) {
            ContentId skillId = ContentId.parse(args[3]);
            sender.sendMessage(skillId + " ranks: "
                    + sessionService.profile(target.getUniqueId()).skill(skillId).nodeRanks());
            return true;
        }
        if (args[1].equalsIgnoreCase("grant-xp") && args.length == 6) {
            ContentId skillId = ContentId.parse(args[3]);
            long amount;
            try {
                amount = Long.parseLong(args[4]);
            } catch (NumberFormatException invalid) {
                sender.sendMessage("XP amount must be an integer.");
                return true;
            }
            OperationId operationId = OperationId.of("mastery", skillId.toString(),
                    target.getUniqueId(), args[5]);
            sender.sendMessage("Life Skill XP mutation started.");
            scheduler.async(() -> lifeSkillProgression.grantXp(
                    target.getUniqueId(), skillId, amount, operationId))
                    .whenComplete((result, failure) -> scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Life Skill XP mutation failed: "
                                    + failure.getMessage());
                        } else {
                            sender.sendMessage((result.applied() ? "Granted" : "Already applied")
                                    + "; level " + result.after().level() + ", XP "
                                    + result.after().totalXp());
                        }
                    }));
            return true;
        }
        sender.sendMessage("Usage: /" + label
                + " life <inspect|tree|grant-xp> <player> [skill] [amount] [operation]");
        return true;
    }

    private boolean masteryCommand(CommandSender sender, String label, String[] args) {
        if (combatMasteryService == null || scheduler == null) {
            sender.sendMessage("Combat Mastery services are offline.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /" + label
                    + " mastery <inspect|grant-xp> <player> [mastery] [amount] [operation]");
            return true;
        }
        var target = getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("Player " + args[2] + " is not online.");
            return true;
        }
        if (args[1].equalsIgnoreCase("inspect") && (args.length == 3 || args.length == 4)) {
            sender.sendMessage("Combat Mastery query started.");
            scheduler.async(() -> combatMasteryService.profile(target.getUniqueId()))
                    .whenComplete((profile, failure) -> scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Combat Mastery query failed: "
                                    + failure.getMessage());
                        } else if (args.length == 4) {
                            ContentId id = ContentId.parse(args[3]);
                            var mastery = profile.get(id);
                            sender.sendMessage(mastery == null
                                    ? id + ": untrained"
                                    : id + ": level " + mastery.level() + ", XP "
                                            + mastery.totalXp());
                        } else {
                            sender.sendMessage("Combat Masteries for " + target.getName()
                                    + ": " + profile.values());
                        }
                    }));
            return true;
        }
        if (args[1].equalsIgnoreCase("grant-xp") && args.length == 6) {
            ContentId masteryId;
            long amount;
            try {
                masteryId = ContentId.parse(args[3]);
                amount = Long.parseLong(args[4]);
            } catch (RuntimeException invalid) {
                sender.sendMessage("Mastery ID or XP amount is invalid.");
                return true;
            }
            OperationId operationId = OperationId.of("combat_mastery",
                    masteryId.toString(), target.getUniqueId(), args[5]);
            sender.sendMessage("Combat Mastery XP mutation started.");
            scheduler.async(() -> combatMasteryService.grantContribution(
                    target.getUniqueId(), masteryId, amount, 1.0, operationId))
                    .whenComplete((result, failure) -> scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Combat Mastery XP mutation failed: "
                                    + failure.getMessage());
                        } else {
                            sender.sendMessage((result.applied() ? "Granted" : "Already applied")
                                    + "; level " + result.after().level() + ", XP "
                                    + result.after().totalXp());
                        }
                    }));
            return true;
        }
        sender.sendMessage("Usage: /" + label
                + " mastery <inspect|grant-xp> <player> [mastery] [amount] [operation]");
        return true;
    }

    private boolean loadoutCommand(CommandSender sender, String label, String[] args) {
        if (loadoutService == null) {
            sender.sendMessage("Loadout services are offline.");
            return true;
        }
        if (args.length != 3 && args.length != 4) {
            sender.sendMessage("Usage: /" + label + " loadout <inspect|equip> <player> [weapon]");
            return true;
        }
        var target = getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("Player " + args[2] + " is not online.");
            return true;
        }
        try {
            if (args[1].equalsIgnoreCase("inspect") && args.length == 3) {
                sender.sendMessage("Active weapon: " + loadoutService.current(target.getUniqueId())
                        .map(weapon -> weapon.id().toString()).orElse("none"));
                return true;
            }
            if (args[1].equalsIgnoreCase("equip") && args.length == 4) {
                var result = loadoutService.equip(
                        target.getUniqueId(), ContentId.parse(args[3]));
                sender.sendMessage(result.equipped()
                        ? "Equipped " + result.weapon().displayName() + "."
                        : "Equip rejected: " + result.rejection());
                return true;
            }
        } catch (RuntimeException invalid) {
            sender.sendMessage("Loadout operation failed: " + invalid.getMessage());
            return true;
        }
        sender.sendMessage("Usage: /" + label + " loadout <inspect|equip> <player> [weapon]");
        return true;
    }

    private boolean inventoryCommand(CommandSender sender, String label, String[] args) {
        if (inventoryService == null || scheduler == null) {
            sender.sendMessage("Inventory services are offline.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /" + label
                    + " inventory <inspect|grant|claim-material|claim-item> <player> "
                    + "[item] [quantity] [operation]");
            return true;
        }
        var target = getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("Player " + args[2] + " is not online.");
            return true;
        }
        if (args[1].equalsIgnoreCase("inspect") && args.length == 3) {
            sender.sendMessage("Inventory query started.");
            scheduler.async(() -> inventoryService.inventory(target.getUniqueId()))
                    .whenComplete((inventory, failure) -> scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Inventory query failed: " + failure.getMessage());
                        } else {
                            sender.sendMessage("Materials: " + inventory.materials());
                            sender.sendMessage("Unique items: " + inventory.items().size()
                                    + ", pending materials: " + inventory.pendingMaterials()
                                    + ", pending items: " + inventory.pendingItems().size());
                        }
                    }));
            return true;
        }
        if (args[1].equalsIgnoreCase("grant") && args.length == 6) {
            ContentId materialId;
            long quantity;
            try {
                materialId = ContentId.parse(args[3]);
                quantity = Long.parseLong(args[4]);
            } catch (RuntimeException invalid) {
                sender.sendMessage("Material ID or quantity is invalid.");
                return true;
            }
            OperationId operationId = OperationId.of(
                    "inventory", materialId.toString(), target.getUniqueId(), args[5]);
            sender.sendMessage("Inventory grant started.");
            scheduler.async(() -> inventoryService.grantMaterial(
                    target.getUniqueId(), materialId, quantity, operationId))
                    .whenComplete((result, failure) -> scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Inventory grant failed: " + failure.getMessage());
                        } else {
                            sender.sendMessage((result.applied() ? "Granted" : "Already applied")
                                    + "; delivered " + result.delivered()
                                    + ", pending " + result.overflowed());
                            if (itemRuntime != null) itemRuntime.reconcile(target);
                        }
                    }));
            return true;
        }
        if (args[1].equalsIgnoreCase("claim-material") && args.length == 6) {
            ContentId materialId;
            long quantity;
            try {
                materialId = ContentId.parse(args[3]);
                quantity = Long.parseLong(args[4]);
            } catch (RuntimeException invalid) {
                sender.sendMessage("Material ID or quantity is invalid.");
                return true;
            }
            OperationId operationId = OperationId.of(
                    "claim", materialId.toString(), target.getUniqueId(), args[5]);
            sender.sendMessage("Pending material claim started.");
            scheduler.async(() -> inventoryService.claimMaterial(
                    target.getUniqueId(), materialId, quantity, operationId))
                    .whenComplete((result, failure) -> scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Claim failed: " + failure.getMessage());
                        } else {
                            sender.sendMessage((result.applied() ? "Claimed" : "Already claimed")
                                    + " " + result.delivered() + ".");
                            if (itemRuntime != null) itemRuntime.reconcile(target);
                        }
                    }));
            return true;
        }
        if (args[1].equalsIgnoreCase("claim-item") && args.length == 5) {
            UUID itemId;
            try {
                itemId = UUID.fromString(args[3]);
            } catch (IllegalArgumentException invalid) {
                sender.sendMessage("Item instance UUID is invalid.");
                return true;
            }
            OperationId operationId = OperationId.of(
                    "claim", itemId.toString(), target.getUniqueId(), args[4]);
            sender.sendMessage("Pending item claim started.");
            scheduler.async(() -> inventoryService.claimUnique(
                    target.getUniqueId(), itemId, operationId))
                    .whenComplete((result, failure) -> scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Claim failed: " + failure.getMessage());
                        } else {
                            sender.sendMessage(result.applied()
                                    ? "Item claimed." : "Item was already claimed.");
                            if (itemRuntime != null) itemRuntime.reconcile(target);
                        }
                    }));
            return true;
        }
        if (args[1].equalsIgnoreCase("revoke") && args.length == 6) {
            ContentId materialId;
            long quantity;
            try {
                materialId = ContentId.parse(args[3]);
                quantity = Long.parseLong(args[4]);
            } catch (RuntimeException invalid) {
                sender.sendMessage("Material ID or quantity is invalid.");
                return true;
            }
            OperationId operation = OperationId.of(
                    "admin_revoke", materialId.toString(), target.getUniqueId(), args[5]);
            asyncMessage(sender, () -> inventoryService.revokeMaterial(
                            target.getUniqueId(), materialId, quantity, operation),
                    result -> result.applied() ? "Material revoked and audited."
                            : "Revoke operation was already applied.");
            return true;
        }
        if (args[1].equalsIgnoreCase("revoke-item") && args.length == 5) {
            UUID itemId;
            try {
                itemId = UUID.fromString(args[3]);
            } catch (RuntimeException invalid) {
                sender.sendMessage("Item UUID is invalid.");
                return true;
            }
            OperationId operation = OperationId.of(
                    "admin_revoke", itemId.toString(), target.getUniqueId(), args[4]);
            asyncMessage(sender, () -> inventoryService.revokeUnique(
                            target.getUniqueId(), itemId, operation),
                    result -> result.applied() ? "Item revoked and audited."
                            : "Revoke operation was already applied.");
            return true;
        }
        sender.sendMessage("Usage: /" + label
                + " inventory <inspect|grant|revoke|revoke-item|claim-material|claim-item> <player> "
                + "[item] [quantity] [operation]");
        return true;
    }

    private boolean lootCommand(CommandSender sender, String label, String[] args) {
        if (lootService == null || scheduler == null) {
            sender.sendMessage("Loot services are offline.");
            return true;
        }
        if (args.length != 4) {
            sender.sendMessage("Usage: /" + label + " loot <player> <table> <durable-roll-id>");
            return true;
        }
        var target = getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("Player " + args[1] + " is not online.");
            return true;
        }
        ContentId tableId;
        try {
            tableId = ContentId.parse(args[2]);
        } catch (RuntimeException invalid) {
            sender.sendMessage("Loot table ID is invalid.");
            return true;
        }
        sender.sendMessage("Loot resolution started.");
        scheduler.async(() -> lootService.resolvePersonal(
                target.getUniqueId(), tableId, args[3], true, java.util.Set.of(), java.util.Map.of()))
                .whenComplete((result, failure) -> scheduler.sync(() -> {
                    if (failure != null) {
                        sender.sendMessage("Loot resolution failed: " + failure.getMessage());
                    } else {
                        sender.sendMessage((result.newlyApplied() ? "Resolved" : "Already resolved")
                                + " " + result.awards() + "; delivered " + result.delivered()
                                + ", pending " + result.overflowed());
                        if (itemRuntime != null) itemRuntime.reconcile(target);
                    }
                }));
        return true;
    }

    private boolean equipmentCommand(CommandSender sender, String label, String[] args) {
        if (equipmentService == null || scheduler == null) {
            sender.sendMessage("Equipment services are offline.");
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage("Usage: /" + label
                    + " equipment <equip|unequip> <player> <item|slot> [slot] <operation>");
            return true;
        }
        var target = getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("Player " + args[2] + " is not online.");
            return true;
        }
        if (args[1].equalsIgnoreCase("equip") && args.length == 6) {
            UUID itemId;
            EquipmentSlot slot;
            try {
                itemId = UUID.fromString(args[3]);
                slot = EquipmentSlot.valueOf(args[4].toUpperCase(Locale.ROOT));
            } catch (RuntimeException invalid) {
                sender.sendMessage("Item UUID or equipment slot is invalid.");
                return true;
            }
            OperationId operation = OperationId.of(
                    "equipment", itemId.toString(), target.getUniqueId(), args[5]);
            sender.sendMessage("Equipment transaction started.");
            scheduler.async(() -> equipmentService.equip(
                    target.getUniqueId(), itemId, slot, operation))
                    .whenComplete((result, failure) -> scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Equip failed: " + failure.getMessage());
                        } else {
                            sender.sendMessage(result.applied()
                                    ? "Equipment updated." : "Equipment operation already applied.");
                            if (itemRuntime != null) itemRuntime.reconcile(target);
                        }
                    }));
            return true;
        }
        if (args[1].equalsIgnoreCase("unequip") && args.length == 5) {
            EquipmentSlot slot;
            try {
                slot = EquipmentSlot.valueOf(args[3].toUpperCase(Locale.ROOT));
            } catch (RuntimeException invalid) {
                sender.sendMessage("Equipment slot is invalid.");
                return true;
            }
            OperationId operation = OperationId.of(
                    "equipment", slot.name(), target.getUniqueId(), args[4]);
            sender.sendMessage("Equipment transaction started.");
            scheduler.async(() -> equipmentService.unequip(
                    target.getUniqueId(), slot, operation))
                    .whenComplete((result, failure) -> scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Unequip failed: " + failure.getMessage());
                        } else {
                            sender.sendMessage(result.applied()
                                    ? "Equipment updated." : "Equipment operation already applied.");
                            if (itemRuntime != null) itemRuntime.reconcile(target);
                        }
                    }));
            return true;
        }
        sender.sendMessage("Usage: /" + label
                + " equipment <equip|unequip> <player> <item|slot> [slot] <operation>");
        return true;
    }

    private boolean nodeCommand(CommandSender sender, String label, String[] args) {
        if (gatheringService == null || gatheringRuntime == null || scheduler == null) {
            sender.sendMessage("Gathering node services are offline.");
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            String filter = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
            sender.sendMessage("Gathering node query started.");
            scheduler.async(gatheringService::nodes)
                    .whenComplete((nodes, failure) -> scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Node query failed: " + failure.getMessage());
                        } else {
                            nodes.stream()
                                    .filter(node -> filter.isEmpty()
                                            || node.definitionId().toString().contains(filter)
                                            || node.state().name().toLowerCase(Locale.ROOT)
                                                    .contains(filter))
                                    .forEach(node -> sender.sendMessage(node.instanceId() + " "
                                            + node.definitionId() + " " + node.state() + " "
                                            + node.position()));
                        }
                    }));
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("broken")) {
            sender.sendMessage("Gathering node query started.");
            scheduler.async(gatheringService::nodes)
                    .whenComplete((nodes, failure) -> scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Node query failed: " + failure.getMessage());
                        } else {
                            nodes.stream()
                                    .filter(node -> node.state() == GatheringNodeState.BROKEN)
                                    .forEach(node -> sender.sendMessage(node.instanceId() + " "
                                            + node.definitionId() + " " + node.position()));
                        }
                    }));
            return true;
        }
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("This node command requires a player target cursor.");
            return true;
        }
        org.bukkit.block.Block target = player.getTargetBlockExact(8);
        if (target == null) {
            sender.sendMessage("No target block within 8 blocks.");
            return true;
        }
        WorldBlockPosition position = new WorldBlockPosition(target.getWorld().getUID(),
                target.getX(), target.getY(), target.getZ());
        if (args.length >= 2 && args[1].equalsIgnoreCase("inspect")) {
            var node = gatheringRuntime.cachedAt(position);
            sender.sendMessage(node == null ? "No registered node at target."
                    : node.instanceId() + " " + node.definitionId() + " " + node.state()
                            + ", respawn " + node.respawnAt().map(Object::toString).orElse("-")
                            + ", last harvester "
                            + node.lastHarvestedBy().map(Object::toString).orElse("-"));
            return true;
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("place")) {
            ContentId definition;
            try {
                definition = ContentId.parse(args[2]);
            } catch (RuntimeException invalid) {
                sender.sendMessage("Gathering definition ID is invalid.");
                return true;
            }
            sender.sendMessage("Node placement started; reason: " + args[3]);
            scheduler.async(() -> gatheringService.place(
                    definition, position, player.getUniqueId()))
                    .whenComplete((node, failure) -> scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Node placement failed: " + failure.getMessage());
                        } else {
                            sender.sendMessage("Placed node " + node.instanceId() + ".");
                            gatheringRuntime.present(node);
                            gatheringRuntime.refresh();
                        }
                    }));
            return true;
        }
        GatheringNodeInstance targetNode = gatheringRuntime.cachedAt(position);
        if (targetNode == null) {
            sender.sendMessage("No registered node at target.");
            return true;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
            sender.sendMessage("Node removal started; reason: " + args[2]);
            scheduler.async(() -> gatheringService.remove(targetNode.instanceId()))
                    .whenComplete((removed, failure) -> scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Node removal failed: " + failure.getMessage());
                        } else {
                            sender.sendMessage(removed ? "Node removed." : "Node no longer exists.");
                            gatheringRuntime.refresh();
                        }
                    }));
            return true;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("respawn")) {
            sender.sendMessage("Node respawn started; reason: " + args[2]);
            scheduler.async(() -> gatheringService.setState(
                    targetNode.instanceId(), GatheringNodeState.AVAILABLE,
                    new SystemGameClock().now()))
                    .whenComplete((node, failure) -> scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Node respawn failed: " + failure.getMessage());
                        } else {
                            sender.sendMessage("Node respawned.");
                            gatheringRuntime.refresh();
                        }
                    }));
            return true;
        }
        sender.sendMessage("Usage: /" + label
                + " node <place ID reason|remove reason|inspect|list [filter]|respawn reason|broken>");
        return true;
    }

    private boolean craftCommand(CommandSender sender, String label, String[] args) {
        if (craftingService == null || scheduler == null) {
            sender.sendMessage("Crafting services are offline.");
            return true;
        }
        if (args.length == 5 && args[1].equalsIgnoreCase("start")) {
            var target = getServer().getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("Player " + args[2] + " is not online.");
                return true;
            }
            ContentId recipeId;
            try {
                recipeId = ContentId.parse(args[3]);
            } catch (RuntimeException invalid) {
                sender.sendMessage("Recipe ID is invalid.");
                return true;
            }
            OperationId operation = OperationId.of(
                    "craft", recipeId.toString(), target.getUniqueId(), args[4]);
            sender.sendMessage("Craft preparation started.");
            scheduler.async(() -> craftingService.begin(
                    target.getUniqueId(), recipeId, java.util.Set.of("branz:forge"),
                    java.util.Set.of(), operation))
                    .whenComplete((result, failure) -> scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Craft start failed: " + failure.getMessage());
                        } else {
                            sender.sendMessage("Craft " + result.job().status() + ": "
                                    + result.detail() + " operation=" + operation);
                            if (itemRuntime != null) itemRuntime.reconcile(target);
                        }
                    }));
            return true;
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("complete")
                || args[1].equalsIgnoreCase("resume"))) {
            OperationId operation;
            try {
                operation = OperationId.parse(args[2]);
            } catch (RuntimeException invalid) {
                sender.sendMessage("Operation ID is invalid.");
                return true;
            }
            boolean complete = args[1].equalsIgnoreCase("complete");
            sender.sendMessage("Craft operation started.");
            scheduler.async(() -> complete
                            ? craftingService.complete(operation)
                            : craftingService.resumePayment(operation))
                    .whenComplete((result, failure) -> scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Craft operation failed: " + failure.getMessage());
                        } else {
                            sender.sendMessage("Craft " + result.job().status() + ": "
                                    + result.detail());
                            var player = getServer().getPlayer(operation.playerUuid());
                            if (player != null && itemRuntime != null) {
                                itemRuntime.reconcile(player);
                            }
                        }
                    }));
            return true;
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("profession")) {
            var target = getServer().getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("Player " + args[2] + " is not online.");
                return true;
            }
            ContentId professionId;
            try {
                professionId = ContentId.parse(args[3]);
            } catch (RuntimeException invalid) {
                sender.sendMessage("Profession ID is invalid.");
                return true;
            }
            scheduler.async(() -> craftingService.profession(
                    target.getUniqueId(), professionId))
                    .whenComplete((profession, failure) -> scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Profession query failed: " + failure.getMessage());
                        } else {
                            sender.sendMessage(profession.professionId() + ": level "
                                    + profession.level() + ", XP " + profession.totalXp());
                        }
                    }));
            return true;
        }
        sender.sendMessage("Usage: /" + label
                + " craft <start player recipe operation|resume operation-id|"
                + "complete operation-id|profession player id>");
        return true;
    }

    private boolean mobCommand(CommandSender sender, String label, String[] args) {
        if (mobRuntime == null) {
            sender.sendMessage("Mob services are offline.");
            return true;
        }
        try {
            if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
                var states = mobRuntime.states();
                sender.sendMessage("Placed mobs: " + states.size());
                states.forEach(state -> sender.sendMessage(" - " + state.instanceId()
                        + " " + state.definitionId() + " L" + state.level()
                        + " " + state.state() + " HP " + state.health()
                        + "/" + state.maximumHealth()));
                return true;
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("inspect")) {
                UUID id = UUID.fromString(args[2]);
                sender.sendMessage(mobRuntime.state(id).map(Object::toString)
                        .orElse("Unknown mob " + id));
                return true;
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("spawn")
                    && sender instanceof org.bukkit.entity.Player player) {
                int level = Integer.parseInt(args[3]);
                mobRuntime.spawn(ContentId.parse(args[2]), level,
                        player.getLocation(), sender::sendMessage);
                sender.sendMessage("Mob placement started.");
                return true;
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
                mobRuntime.remove(UUID.fromString(args[2]), sender::sendMessage);
                sender.sendMessage("Mob removal started.");
                return true;
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("reset")) {
                UUID id = UUID.fromString(args[2]);
                sender.sendMessage("Mob reset started; reason: " + args[3]);
                mobRuntime.reset(id, sender::sendMessage);
                return true;
            }
        } catch (RuntimeException invalid) {
            sender.sendMessage("Mob operation failed: " + invalid.getMessage());
            return true;
        }
        sender.sendMessage("Usage: /" + label
                + " mob <list|inspect id|spawn definition level|remove id|reset id reason>");
        return true;
    }

    private boolean encounterCommand(CommandSender sender, String label, String[] args) {
        if (encounterRuntime == null) {
            sender.sendMessage("Encounter services are offline.");
            return true;
        }
        try {
            if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
                var states = encounterRuntime.states();
                sender.sendMessage("Encounters: " + states.size());
                states.forEach(state -> sender.sendMessage(" - " + state.instanceId()
                        + " " + state.definitionId() + " " + state.state()
                        + " phase " + (state.phaseIndex() + 1)
                        + " participants " + state.participantSnapshot().size()));
                return true;
            }
            if (args.length >= 3 && args[1].equalsIgnoreCase("start")
                    && sender instanceof org.bukkit.entity.Player player) {
                java.util.HashSet<UUID> participants = new java.util.HashSet<>();
                participants.add(player.getUniqueId());
                for (int index = 3; index < args.length; index++) {
                    var target = getServer().getPlayerExact(args[index]);
                    if (target == null) {
                        sender.sendMessage("Player " + args[index] + " is not online.");
                        return true;
                    }
                    participants.add(target.getUniqueId());
                }
                encounterRuntime.start(ContentId.parse(args[2]), participants,
                        player.getLocation(), sender::sendMessage);
                return true;
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("abandon")) {
                encounterRuntime.abandon(UUID.fromString(args[2]), sender::sendMessage);
                return true;
            }
        } catch (RuntimeException invalid) {
            sender.sendMessage("Encounter operation failed: " + invalid.getMessage());
            return true;
        }
        sender.sendMessage("Usage: /" + label
                + " encounter <list|start definition [players...]|abandon instance-uuid>");
        return true;
    }

    private boolean partyCommand(CommandSender sender, String label, String[] args) {
        if (partyService == null || scheduler == null
                || !(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("Party services are offline or require a player.");
            return true;
        }
        try {
            if (args.length == 2 && args[1].equalsIgnoreCase("create")) {
                asyncMessage(sender, () -> partyService.create(player.getUniqueId()),
                        party -> "Created party " + party.partyId());
                return true;
            }
            if (args.length == 2 && args[1].equalsIgnoreCase("inspect")) {
                asyncMessage(sender, () -> partyService.party(player.getUniqueId()),
                        party -> party.map(value -> "Party " + value.partyId()
                                + " leader " + value.leaderId() + " members " + value.members()
                                + " reward range " + value.rewardRange()
                                + (value.rewardsRequireSameWorld() ? " same-world" : " cross-world"))
                                .orElse("Not in a party."));
                return true;
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("invite")) {
                var target = getServer().getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage("Player is not online.");
                    return true;
                }
                asyncMessage(sender, () -> partyService.invite(
                                player.getUniqueId(), target.getUniqueId()),
                        party -> "Invited " + target.getName() + " to " + party.partyId());
                return true;
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("accept")) {
                asyncMessage(sender, () -> partyService.accept(
                                player.getUniqueId(), UUID.fromString(args[2])),
                        party -> "Joined party " + party.partyId());
                return true;
            }
            if (args.length == 2 && args[1].equalsIgnoreCase("leave")) {
                asyncMessage(sender, () -> partyService.leave(player.getUniqueId()),
                        party -> party.map(value -> "Left; party remains " + value.partyId())
                                .orElse("Left; party disbanded."));
                return true;
            }
            if (args.length == 3 && (args[1].equalsIgnoreCase("kick")
                    || args[1].equalsIgnoreCase("leader"))) {
                var target = getServer().getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage("Target must be online.");
                    return true;
                }
                if (args[1].equalsIgnoreCase("kick")) {
                    asyncMessage(sender, () -> partyService.kick(
                                    player.getUniqueId(), target.getUniqueId()),
                            party -> "Removed " + target.getName());
                } else {
                    asyncMessage(sender, () -> partyService.transferLeadership(
                                    player.getUniqueId(), target.getUniqueId()),
                            party -> "New leader: " + target.getName());
                }
                return true;
            }
        } catch (RuntimeException invalid) {
            sender.sendMessage("Party operation failed: " + invalid.getMessage());
            return true;
        }
        sender.sendMessage("Usage: /" + label
                + " party <create|inspect|invite player|accept id|leave|kick player|leader player>");
        return true;
    }

    private boolean tradeCommand(CommandSender sender, String label, String[] args) {
        if (tradeService == null || scheduler == null
                || !(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("Trade services are offline or require a player.");
            return true;
        }
        try {
            if (args.length == 3 && args[1].equalsIgnoreCase("request")) {
                var target = getServer().getPlayerExact(args[2]);
                if (target == null || !target.getWorld().equals(player.getWorld())
                        || target.getLocation().distanceSquared(player.getLocation()) > 64
                        || !player.hasLineOfSight(target)) {
                    sender.sendMessage("Trade target must be online, visible, and within 8 blocks.");
                    return true;
                }
                asyncMessage(sender, () -> tradeService.request(
                                player.getUniqueId(), target.getUniqueId()),
                        trade -> "Trade requested: " + trade.tradeId());
                return true;
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("accept")) {
                asyncMessage(sender, () -> tradeService.accept(
                                UUID.fromString(args[2]), player.getUniqueId()),
                        trade -> "Trade open: " + trade.tradeId());
                return true;
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("inspect")) {
                asyncMessage(sender, () -> tradeService.trade(UUID.fromString(args[2])),
                        trade -> trade.map(value -> value.tradeId() + " " + value.state()
                                + " offers " + value.offers()
                                + " confirmed " + value.confirmedPlayers())
                                .orElse("Unknown trade."));
                return true;
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("clear")) {
                asyncMessage(sender, () -> tradeService.offer(UUID.fromString(args[2]),
                                player.getUniqueId(),
                                com.branz.mmorpg.api.social.TradeOffer.empty()),
                        trade -> "Offer cleared; confirmations reset.");
                return true;
            }
            if (args.length == 5 && args[1].equalsIgnoreCase("offer-material")) {
                UUID tradeId = UUID.fromString(args[2]);
                ContentId material = ContentId.parse(args[3]);
                long quantity = Long.parseLong(args[4]);
                asyncMessage(sender, () -> {
                    var trade = tradeService.trade(tradeId).orElseThrow();
                    var current = trade.offers().getOrDefault(
                            player.getUniqueId(),
                            com.branz.mmorpg.api.social.TradeOffer.empty());
                    var materials = new java.util.HashMap<>(current.materials());
                    materials.put(material, quantity);
                    return tradeService.offer(tradeId, player.getUniqueId(),
                            new com.branz.mmorpg.api.social.TradeOffer(
                                    materials, current.itemIds()));
                }, trade -> "Material escrowed; confirmations reset.");
                return true;
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("offer-item")) {
                UUID tradeId = UUID.fromString(args[2]);
                UUID itemId = UUID.fromString(args[3]);
                asyncMessage(sender, () -> {
                    var trade = tradeService.trade(tradeId).orElseThrow();
                    var current = trade.offers().getOrDefault(
                            player.getUniqueId(),
                            com.branz.mmorpg.api.social.TradeOffer.empty());
                    var items = new java.util.HashSet<>(current.itemIds());
                    items.add(itemId);
                    return tradeService.offer(tradeId, player.getUniqueId(),
                            new com.branz.mmorpg.api.social.TradeOffer(
                                    current.materials(), items));
                }, trade -> "Item escrowed; confirmations reset.");
                return true;
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("confirm")) {
                asyncMessage(sender, () -> tradeService.confirm(
                                UUID.fromString(args[2]), player.getUniqueId()),
                        trade -> "Trade state: " + trade.state());
                return true;
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("cancel")) {
                asyncMessage(sender, () -> tradeService.cancel(
                                UUID.fromString(args[2]), player.getUniqueId()),
                        trade -> "Trade cancelled; escrow returned to mailbox.");
                return true;
            }
        } catch (RuntimeException invalid) {
            sender.sendMessage("Trade operation failed: " + invalid.getMessage());
            return true;
        }
        sender.sendMessage("Usage: /" + label
                + " trade <request player|accept id|inspect id|clear id|"
                + "offer-material id material qty|offer-item id item|confirm id|cancel id>");
        return true;
    }

    private boolean telemetryCommand(CommandSender sender, String label, String[] args) {
        if (telemetryService == null || args.length != 2
                || !args[1].equalsIgnoreCase("inspect")) {
            sender.sendMessage("Usage: /" + label + " telemetry inspect");
            return true;
        }
        var snapshot = telemetryService.snapshot();
        sender.sendMessage("Telemetry counters: " + snapshot.counters());
        sender.sendMessage("Telemetry maximum observations: " + snapshot.observations());
        return true;
    }

    private boolean currencyCommand(CommandSender sender, String label, String[] args) {
        if (adminCurrency == null || scheduler == null || args.length != 6
                || !args[1].equalsIgnoreCase("adjust")) {
            sender.sendMessage("Usage: /" + label
                    + " currency adjust <player> <amount> <operation-id> <reason>");
            return true;
        }
        var target = getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("Target must be online.");
            return true;
        }
        try {
            long amount = Long.parseLong(args[3]);
            asyncMessage(sender, () -> adminCurrency.adjustCredits(
                            target.getUniqueId(), amount, args[4], args[5]),
                    applied -> applied
                            ? "Wallet credit mutation applied and ledger-audited."
                            : "Wallet operation already applied or rejected.");
        } catch (RuntimeException invalid) {
            sender.sendMessage("Currency operation failed: " + invalid.getMessage());
        }
        return true;
    }

    private boolean questCommand(CommandSender sender, String label, String[] args) {
        if (questRuntime == null) {
            sender.sendMessage("Quest services are offline.");
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("journal")
                && sender instanceof org.bukkit.entity.Player player) {
            questRuntime.journal(player.getUniqueId());
            return true;
        }
        if (args.length >= 2 && (args[1].equalsIgnoreCase("reload")
                || args[1].equalsIgnoreCase("validate"))) {
            scheduler.async(questRuntime::reload).whenComplete((result, failure) ->
                    scheduler.sync(() -> {
                        if (failure != null) {
                            sender.sendMessage("Quest content check failed: "
                                    + failure.getMessage());
                            return;
                        }
                        sender.sendMessage((result.successful() ? "Quest content active"
                                : "Quest content rejected") + ": revision "
                                + result.catalog().revision() + ", quests "
                                + result.catalog().quests().size() + ", dialogues "
                                + result.catalog().dialogues().size() + ", cutscenes "
                                + result.catalog().cutscenes().size());
                        result.diagnostics().forEach(diagnostic -> sender.sendMessage(
                                diagnostic.severity() + " " + diagnostic.code() + " "
                                        + diagnostic.source() + ":" + diagnostic.line()
                                        + " [" + diagnostic.fieldPath() + "] "
                                        + diagnostic.resolution()));
                    }));
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("bootstrap")
                && sender instanceof org.bukkit.entity.Player player) {
            sender.sendMessage(questRuntime.bootstrapReference(player.getLocation()));
            return true;
        }
        if (args.length >= 2 && (args[1].equalsIgnoreCase("graph")
                || args[1].equalsIgnoreCase("paths"))) {
            var catalog = questRuntime.content().catalog();
            if (args.length == 3) {
                try {
                    var definition = catalog.find(ContentId.parse(args[2])).orElse(null);
                    if (definition == null) {
                        sender.sendMessage("Unknown quest " + args[2]);
                    } else {
                        sender.sendMessage(definition.id() + " start=" + definition.startStage());
                        definition.stages().values().forEach(stage -> sender.sendMessage(
                                " - " + stage.id() + " -> "
                                        + stage.nextStage().orElse("<complete>")
                                        + stage.failureStage().map(value ->
                                        " fail->" + value).orElse("")
                                        + " objectives=" + stage.objectives().size()));
                    }
                } catch (IllegalArgumentException invalid) {
                    sender.sendMessage(invalid.getMessage());
                }
            } else {
                catalog.quests().values().forEach(definition -> sender.sendMessage(
                        definition.id() + " stages=" + definition.stages().size()
                                + " start=" + definition.startStage()));
            }
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /" + label
                    + " quest <start|turnin|abandon|inspect|reset|migrate|stage|objective"
                    + "|journal|history|retry"
                    + "|validate|reload|graph> <player> [quest] [reason]");
            return true;
        }
        var target = getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("Player " + args[2] + " is not online.");
            return true;
        }
        try {
            if (args[1].equalsIgnoreCase("journal")) {
                questRuntime.journal(target.getUniqueId());
                sender.sendMessage("Opened quest journal for " + target.getName() + ".");
                return true;
            }
            if (args[1].equalsIgnoreCase("retry")) {
                questRuntime.retryPending(sender::sendMessage);
                return true;
            }
            UUID actor = sender instanceof org.bukkit.entity.Player player
                    ? player.getUniqueId() : new UUID(0L, 0L);
            if (args[1].equalsIgnoreCase("stage") && args.length >= 6) {
                questRuntime.setStage(target.getUniqueId(), ContentId.parse(args[3]),
                        args[4], actor,
                        String.join(" ", java.util.Arrays.copyOfRange(args, 5, args.length)),
                        sender::sendMessage);
                return true;
            }
            if (args[1].equalsIgnoreCase("objective") && args.length >= 7) {
                questRuntime.setObjective(target.getUniqueId(), ContentId.parse(args[3]),
                        args[4], Long.parseLong(args[5]), actor,
                        String.join(" ", java.util.Arrays.copyOfRange(args, 6, args.length)),
                        sender::sendMessage);
                return true;
            }
            Optional<ContentId> questId = args.length >= 4
                    ? Optional.of(ContentId.parse(args[3])) : Optional.empty();
            if (args[1].equalsIgnoreCase("inspect")) {
                questRuntime.inspect(target.getUniqueId(), questId, sender::sendMessage);
                return true;
            }
            if (args[1].equalsIgnoreCase("history")) {
                if (questId.isEmpty()) throw new IllegalArgumentException(
                        "history requires a dialogue content ID");
                questRuntime.history(target.getUniqueId(), questId.orElseThrow(),
                        sender::sendMessage);
                return true;
            }
            if (questId.isEmpty()) {
                throw new IllegalArgumentException("A quest content ID is required.");
            }
            if (args[1].equalsIgnoreCase("start")) {
                questRuntime.startQuest(target.getUniqueId(), questId.orElseThrow(),
                        sender::sendMessage);
            } else if (args[1].equalsIgnoreCase("turnin")) {
                questRuntime.turnIn(target.getUniqueId(), questId.orElseThrow(),
                        sender::sendMessage);
            } else if (args[1].equalsIgnoreCase("abandon")) {
                questRuntime.abandon(target.getUniqueId(), questId.orElseThrow(),
                        sender::sendMessage);
            } else if (args[1].equalsIgnoreCase("reset")) {
                String reason = args.length >= 5
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length))
                        : "manual admin reset";
                questRuntime.reset(target.getUniqueId(), questId.orElseThrow(),
                        sender instanceof org.bukkit.entity.Player player
                                ? player.getUniqueId() : new UUID(0L, 0L),
                        reason, sender::sendMessage);
            } else if (args[1].equalsIgnoreCase("migrate")) {
                String reason = args.length >= 5
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length))
                        : "manual admin migration";
                questRuntime.migrate(target.getUniqueId(), questId.orElseThrow(),
                        sender instanceof org.bukkit.entity.Player player
                                ? player.getUniqueId() : new UUID(0L, 0L),
                        reason, sender::sendMessage);
            } else {
                throw new IllegalArgumentException("Unknown quest operation " + args[1]);
            }
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage("Quest command failed: " + invalid.getMessage());
        }
        return true;
    }

    private boolean dialogueCommand(CommandSender sender, String label, String[] args) {
        if (questRuntime == null || args.length < 3) {
            sender.sendMessage("Usage: /" + label
                    + " dialogue <start|choose|status|history> <player> [arguments]");
            return true;
        }
        if (args[1].equalsIgnoreCase("choose")
                && sender instanceof org.bukkit.entity.Player player
                && args.length == 5) {
            try {
                questRuntime.advanceDialogue(player.getUniqueId(),
                        UUID.fromString(args[2]), Long.parseLong(args[3]),
                        Optional.of(args[4]), sender::sendMessage);
            } catch (IllegalArgumentException invalid) {
                sender.sendMessage("Dialogue choice failed: " + invalid.getMessage());
            }
            return true;
        }
        var target = getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("Player " + args[2] + " is not online.");
            return true;
        }
        try {
            if (args[1].equalsIgnoreCase("start") && args.length == 4) {
                questRuntime.startDialogueCommand(target.getUniqueId(),
                        ContentId.parse(args[3]), sender::sendMessage);
            } else if (args[1].equalsIgnoreCase("choose") && args.length >= 6) {
                questRuntime.advanceDialogue(target.getUniqueId(), UUID.fromString(args[3]),
                        Long.parseLong(args[4]), Optional.of(args[5]), sender::sendMessage);
            } else if (args[1].equalsIgnoreCase("status")) {
                questRuntime.dialogueSessions().stream()
                        .filter(value -> value.playerId().equals(target.getUniqueId()))
                        .forEach(value -> sender.sendMessage(value.sessionId() + " "
                                + value.dialogueId() + " node=" + value.currentNode()
                                + " sequence=" + value.sequence() + " state=" + value.state()));
            } else if (args[1].equalsIgnoreCase("history") && args.length == 4) {
                questRuntime.history(target.getUniqueId(), ContentId.parse(args[3]),
                        sender::sendMessage);
            } else if (args[1].equalsIgnoreCase("settings") && args.length == 6) {
                questRuntime.accessibility(target.getUniqueId(),
                        com.branz.mmorpg.quest.api.AccessibilitySettings.DialogueMode
                                .valueOf(args[3].toUpperCase(Locale.ROOT)),
                        Double.parseDouble(args[4]), Boolean.parseBoolean(args[5]),
                        sender::sendMessage);
            } else {
                sender.sendMessage("Usage: /" + label
                        + " dialogue <start|choose|status|history|settings> <player> "
                        + "[id|session seq choice|mode speed sound-alternatives]");
            }
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage("Dialogue command failed: " + invalid.getMessage());
        }
        return true;
    }

    private boolean cutsceneCommand(CommandSender sender, String label, String[] args) {
        if (questRuntime != null && args.length == 3
                && args[1].equalsIgnoreCase("skip")
                && sender instanceof org.bukkit.entity.Player player) {
            try {
                questRuntime.skipCutscene(player.getUniqueId(),
                        UUID.fromString(args[2]), sender::sendMessage);
            } catch (IllegalArgumentException invalid) {
                sender.sendMessage("Cutscene skip failed: " + invalid.getMessage());
            }
            return true;
        }
        if (questRuntime == null || args.length < 3) {
            sender.sendMessage("Usage: /" + label
                    + " cutscene <play|skip|status> <player> [id|session]");
            return true;
        }
        var target = getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("Player " + args[2] + " is not online.");
            return true;
        }
        try {
            if (args[1].equalsIgnoreCase("play") && args.length == 4) {
                questRuntime.playCutscene(target.getUniqueId(),
                        ContentId.parse(args[3]), sender::sendMessage);
            } else if (args[1].equalsIgnoreCase("skip") && args.length == 4) {
                questRuntime.skipCutscene(target.getUniqueId(), UUID.fromString(args[3]),
                        sender::sendMessage);
            } else if (args[1].equalsIgnoreCase("status")) {
                questRuntime.cutsceneSessions().stream()
                        .filter(value -> value.participantSnapshot()
                                .contains(target.getUniqueId()))
                        .forEach(value -> sender.sendMessage(value.sessionId() + " "
                                + value.cutsceneId() + " elapsed=" + value.elapsedMillis()
                                + "ms state=" + value.state()));
            } else {
                sender.sendMessage("Usage: /" + label
                        + " cutscene <play|skip|status> <player> [id|session]");
            }
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage("Cutscene command failed: " + invalid.getMessage());
        }
        return true;
    }

    private boolean npcCommand(CommandSender sender, String label, String[] args) {
        if (questRuntime == null || !(sender instanceof org.bukkit.entity.Player player)
                || args.length != 4 || !args[1].equalsIgnoreCase("create")) {
            sender.sendMessage("Usage (in game): /" + label
                    + " npc create <npc-id> <dialogue-id>");
            return true;
        }
        try {
            UUID entityId = questRuntime.createNpc(player.getLocation(),
                    ContentId.parse(args[2]), ContentId.parse(args[3]));
            sender.sendMessage("Created persistent quest NPC " + entityId + ".");
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage("NPC creation failed: " + invalid.getMessage());
        }
        return true;
    }

    private boolean locationCommand(CommandSender sender, String label, String[] args) {
        if (questRuntime == null || args.length < 2) {
            sender.sendMessage("Usage: /" + label
                    + " location <set|show|list|delete|bind> [id]");
            return true;
        }
        if (args[1].equalsIgnoreCase("list")) {
            questRuntime.locations().forEach((id, location) -> sender.sendMessage(
                    id + " " + location.getWorld().getName() + " "
                            + location.getBlockX() + "," + location.getBlockY()
                            + "," + location.getBlockZ()));
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage("Usage: /" + label
                    + " location <set|show|delete|bind> <id>");
            return true;
        }
        if (args[1].equalsIgnoreCase("delete")) {
            sender.sendMessage(questRuntime.deleteLocation(args[2])
                    ? "Location deleted." : "Unknown location.");
            return true;
        }
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("This location operation must be run in game.");
            return true;
        }
        if (args[1].equalsIgnoreCase("set")) {
            questRuntime.captureLocation(args[2], player.getLocation());
            sender.sendMessage("Captured location " + args[2] + ".");
        } else if (args[1].equalsIgnoreCase("show")) {
            org.bukkit.Location location = questRuntime.locations().get(args[2]);
            sender.sendMessage(location == null ? "Unknown location." : args[2] + " "
                    + location.getWorld().getName() + " " + location.getX() + ","
                    + location.getY() + "," + location.getZ());
        } else if (args[1].equalsIgnoreCase("bind")) {
            questRuntime.bindWorldObject(player.getTargetBlockExact(6) == null
                            ? player.getLocation()
                            : player.getTargetBlockExact(6).getLocation(),
                    ContentId.parse(args[2]));
            sender.sendMessage("World object bound.");
        } else {
            sender.sendMessage("Unknown location operation " + args[1]);
        }
        return true;
    }

    private <T> void asyncMessage(
            CommandSender sender, java.util.function.Supplier<T> work,
            java.util.function.Function<T, String> success) {
        scheduler.async(work).whenComplete((result, failure) -> scheduler.sync(() -> {
            if (failure != null) {
                Throwable root = failure;
                while (root.getCause() != null) root = root.getCause();
                sender.sendMessage("Operation failed: " + root.getMessage());
            } else {
                sender.sendMessage(success.apply(result));
            }
        }));
    }

    private final class AdminCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length == 0) {
                sender.sendMessage("Usage: /" + label + " <reload|status|player>");
                return true;
            }
            boolean playerDialogueChoice = args[0].equalsIgnoreCase("dialogue")
                    && args.length == 5 && args[1].equalsIgnoreCase("choose")
                    && sender instanceof org.bukkit.entity.Player;
            boolean playerJournal = args[0].equalsIgnoreCase("quest")
                    && args.length == 2 && args[1].equalsIgnoreCase("journal")
                    && sender instanceof org.bukkit.entity.Player;
            boolean playerCutsceneSkip = args[0].equalsIgnoreCase("cutscene")
                    && args.length == 3 && args[1].equalsIgnoreCase("skip")
                    && sender instanceof org.bukkit.entity.Player;
            if (!sender.hasPermission("branz.admin") && !playerDialogueChoice
                    && !playerJournal && !playerCutsceneSkip) {
                sender.sendMessage("You do not have permission to use this command.");
                return true;
            }
            if (args[0].equalsIgnoreCase("player")) {
                return playerCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("life")) {
                return lifeCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("mastery")) {
                return masteryCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("loadout")) {
                return loadoutCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("inventory")) {
                return inventoryCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("loot")) {
                return lootCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("equipment")) {
                return equipmentCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("node")) {
                return nodeCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("craft")) {
                return craftCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("mob")) {
                return mobCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("encounter")) {
                return encounterCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("party")) {
                return partyCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("trade")) {
                return tradeCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("telemetry")) {
                return telemetryCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("currency")) {
                return currencyCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("quest")) {
                return questCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("dialogue")) {
                return dialogueCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("cutscene")) {
                return cutsceneCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("npc")) {
                return npcCommand(sender, label, args);
            }
            if (args[0].equalsIgnoreCase("location")) {
                return locationCommand(sender, label, args);
            }
            if (args.length != 1) {
                sender.sendMessage("Usage: /" + label + " <reload|status|player>");
                return true;
            }
            if (args[0].equalsIgnoreCase("status")) {
                var snapshot = contentService.snapshot();
                sender.sendMessage("Branz MMORPG: content revision " + snapshot.revision()
                        + ", definitions " + snapshot.definitions().size()
                        + ", database " + (databaseManager == null ? "disabled" : "connected"));
                var health = serviceContainer == null ? null : serviceContainer.health();
                sender.sendMessage("Core: " + (health == null
                        ? "not started"
                        : (health.ready() ? "ready" : "DEGRADED")));
                if (health != null) {
                    for (ServiceStatus status : health.services()) {
                        sender.sendMessage(" - " + status.name() + ": " + status.state()
                                + (status.detail() == null ? "" : " (" + status.detail() + ")"));
                    }
                }
                return true;
            }
            if (args[0].equalsIgnoreCase("reload")) {
                sender.sendMessage("Content reload started.");
                scheduler.async(() -> contentService.reload(contentDirectory))
                        .whenComplete((result, failure) -> scheduler.sync(() -> {
                            if (failure != null) {
                                sender.sendMessage("Content reload failed; check server logs.");
                                getLogger().log(java.util.logging.Level.SEVERE,
                                        "Content reload failed", failure);
                            } else if (result.successful()) {
                                sender.sendMessage("Content reloaded: revision " + result.revision()
                                        + ", definitions " + result.definitionCount());
                            } else {
                                sender.sendMessage("Content reload rejected; revision "
                                        + result.revision()
                                        + " remains active. Check server logs.");
                                logDiagnostics("Content reload rejected", result);
                            }
                        }));
                return true;
            }
            sender.sendMessage("Usage: /" + label + " <reload|status>");
            return true;
        }
    }
}
