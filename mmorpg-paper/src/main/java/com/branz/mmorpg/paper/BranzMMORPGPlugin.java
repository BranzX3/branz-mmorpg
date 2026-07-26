package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.content.ContentReloadResult;
import com.branz.mmorpg.api.content.ContentService;
<<<<<<< HEAD
import com.branz.mmorpg.api.lifeskill.LifeSkillQuery;
import com.branz.mmorpg.api.player.DuplicateLoginPolicy;
import com.branz.mmorpg.api.player.PlayerSession;
import com.branz.mmorpg.api.runtime.Scheduler;
import com.branz.mmorpg.api.runtime.TransactionRunner;
import com.branz.mmorpg.api.service.ServiceStatus;
import com.branz.mmorpg.content.AtomicContentService;
import com.branz.mmorpg.core.player.PlayerSessionService;
import com.branz.mmorpg.core.runtime.ExecutorScheduler;
import com.branz.mmorpg.core.runtime.SystemGameClock;
import com.branz.mmorpg.core.service.ServiceContainer;
import com.branz.mmorpg.storage.DatabaseConfig;
import com.branz.mmorpg.storage.DatabaseManager;
import com.branz.mmorpg.storage.JdbcPlayerProfileRepository;
import com.branz.mmorpg.storage.JdbcTransactionRunner;
import java.time.Duration;
import java.util.Locale;
<<<<<<< HEAD
import java.util.Optional;
=======
>>>>>>> parent of 14f4881 (complete mmo task)
=======
import com.branz.mmorpg.api.lifecycle.HealthService;
import com.branz.mmorpg.api.player.PlayerSessionService;
import com.branz.mmorpg.api.player.PlayerSessionState;
import com.branz.mmorpg.content.AtomicContentService;
import com.branz.mmorpg.core.lifecycle.CoreRuntime;
import com.branz.mmorpg.core.player.PlayerSessionManager;
import com.branz.mmorpg.core.player.PlayerSessionSavePolicy;
import com.branz.mmorpg.core.service.ContentManagedService;
import com.branz.mmorpg.core.service.DatabaseManagedService;
import com.branz.mmorpg.storage.DatabaseConfig;
import com.branz.mmorpg.storage.player.FilePlayerProfileRecoveryStore;
import com.branz.mmorpg.storage.player.MySqlPlayerProfileStore;
>>>>>>> parent of 3846639 (74)
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
<<<<<<< HEAD
    private DatabaseManager databaseManager;
    private ServiceContainer serviceContainer;
    private Scheduler scheduler;
    private TransactionRunner transactionRunner;
    private PlayerSessionService sessionService;
=======
    private ContentManagedService contentManagedService;
    private DatabaseManagedService databaseManagedService;
    private CoreRuntime coreRuntime;
    private ExecutorService playerStorageExecutor;
    private PlayerSessionManager playerSessionManager;
    private BukkitTask playerAutosaveTask;
>>>>>>> parent of 3846639 (74)
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
<<<<<<< HEAD
            ContentReloadResult initialLoad = contentService.reload(contentDirectory);
            if (!initialLoad.successful()) {
                logDiagnostics("Initial content load failed", initialLoad);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            if (getConfig().getBoolean("database.enabled", false)) {
                databaseManager = DatabaseManager.connect(readDatabaseConfig());
                transactionRunner = new JdbcTransactionRunner(databaseManager);
=======
            contentManagedService = new ContentManagedService(contentService, contentDirectory);
            boolean databaseEnabled = getConfig().getBoolean("database.enabled", false);
            databaseManagedService = new DatabaseManagedService(databaseEnabled, readDatabaseConfig());
            coreRuntime = new CoreRuntime(List.of(contentManagedService, databaseManagedService));
            coreRuntime.start();

            ContentReloadResult initialLoad = contentManagedService.lastResult();
            if (databaseManagedService.connected()) {
>>>>>>> parent of 3846639 (74)
                getLogger().info("Database connected and migrations applied.");
            } else {
                getLogger().warning("Database is disabled; persistent gameplay services must remain offline.");
            }

<<<<<<< HEAD
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
                sessionService = serviceContainer.register(new PlayerSessionService(
                        new JdbcPlayerProfileRepository(databaseManager),
                        scheduler,
                        new SystemGameClock(),
                        () -> contentService.snapshot().revision(),
<<<<<<< HEAD
                        duplicateLoginPolicy(),
                        new FilePendingSessionSaveStore(
                                getDataFolder().toPath().resolve(getConfig().getString(
                                        "player-session.recovery-directory",
                                        "recovery/player-profiles"))),
                        Math.max(1, getConfig().getInt("player-session.save-max-attempts", 3))));
                lifeSkillProgression = new LifeSkillProgressionService(
                        profileRepository, sessionService, new SystemGameClock(),
                        contentService::snapshot);
                combatMasteryService = new DefaultCombatMasteryService(
                        new JdbcCombatMasteryRepository(databaseManager),
                        contentService::snapshot, new SystemGameClock());
                inventoryRepository = new JdbcInventoryRepository(databaseManager);
                inventoryService = new DefaultInventoryService(
                        inventoryRepository,
                        contentService::snapshot, new SystemGameClock());
                lootService = new DefaultLootService(
                        inventoryService, contentService::snapshot, new SystemGameClock());
                gatheringRepository = new JdbcGatheringNodeRepository(databaseManager);
                gatheringService = new DefaultGatheringService(
                        gatheringRepository, sessionService,
                        contentService::snapshot, new SystemGameClock());
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
=======
                        duplicateLoginPolicy()));
>>>>>>> parent of 14f4881 (complete mmo task)
            }
            serviceContainer.startAll();

            if (sessionService != null) {
                getServer().getPluginManager()
                        .registerEvents(new PlayerSessionListener(this, sessionService), this);
                startAutosaveTask();
                getServer().getServicesManager().register(
                        LifeSkillQuery.class, sessionService, this, ServicePriority.Normal);
            }
=======
>>>>>>> parent of 3846639 (74)
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

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
<<<<<<< HEAD
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
        }
        transactionRunner = null;
        if (databaseManager != null) {
            databaseManager.close();
            databaseManager = null;
=======
        stopPlayerAutosave();
        closeOnlinePlayerSessions();
        stopPlayerStorageExecutor();
        if (coreRuntime != null) {
            try {
                coreRuntime.close();
            } catch (RuntimeException exception) {
                getLogger().severe("Core shutdown failed: " + exception.getMessage());
            }
            coreRuntime = null;
>>>>>>> parent of 3846639 (74)
        }
        getLogger().info("Branz MMORPG disabled.");
    }

<<<<<<< HEAD
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
=======
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (playerSessionManager == null) {
            return;
        }
        UUID playerId = event.getPlayer().getUniqueId();
        String playerName = event.getPlayer().getName();
        long contentRevision = contentService.snapshot().revision();
        playerSessionManager.open(playerId, playerName, contentRevision).whenComplete((snapshot, failure) -> {
            if (!isEnabled()) {
                return;
            }
            getServer().getScheduler().runTask(this, () -> {
                Player player = getServer().getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    return;
                }
                if (failure != null) {
                    getLogger().log(Level.SEVERE, "Player session callback failed for " + playerId, failure);
                    player.sendMessage("MMO profile unavailable; gameplay mutations are disabled.");
                    return;
                }
                if (snapshot.state() == PlayerSessionState.LOAD_FAILED
                        || snapshot.state() == PlayerSessionState.CONFLICTED) {
                    getLogger().warning("Player session rejected for " + playerId + ": " + snapshot.detail());
                    player.sendMessage("MMO profile unavailable: " + snapshot.detail());
                }
            });
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (playerSessionManager == null) {
            return;
        }
        UUID playerId = event.getPlayer().getUniqueId();
        playerSessionManager.snapshot(playerId).ifPresent(snapshot ->
                playerSessionManager.close(playerId, snapshot.token()).whenComplete((closed, failure) -> {
                    if (failure != null) {
                        getLogger().log(Level.SEVERE, "Player session close failed for " + playerId, failure);
                    } else if (closed.state() == PlayerSessionState.SAVE_RETRY_PENDING) {
                        getLogger().severe("Player profile save requires retry for " + playerId + ": " + closed.detail());
                    }
                }));
>>>>>>> parent of 3846639 (74)
    }

    private void saveBundledContent() {
        if (!getDataFolder().toPath().resolve("content/materials/aether_ore.yml").toFile().exists()) {
            saveResource("content/materials/aether_ore.yml", false);
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

<<<<<<< HEAD
=======
    private void startPlayerSessions() {
        int workerThreads = Math.max(1, getConfig().getInt("player-session.worker-threads", 2));
        playerStorageExecutor = Executors.newFixedThreadPool(
                workerThreads,
                Thread.ofPlatform().name("branz-player-storage-", 0).daemon(true).factory());
        Path recoveryDirectory = getDataFolder().toPath()
                .resolve(getConfig().getString(
                        "player-session.recovery-directory", "recovery/player-profiles"))
                .normalize();
        playerSessionManager = new PlayerSessionManager(
                new MySqlPlayerProfileStore(
                        databaseManagedService.manager().orElseThrow(), playerStorageExecutor),
                new PlayerSessionSavePolicy(Math.max(
                        1, getConfig().getInt("player-session.save-max-attempts", 3))),
                new FilePlayerProfileRecoveryStore(recoveryDirectory, playerStorageExecutor));
        long autosaveInterval = Math.max(
                20L, getConfig().getLong("player-session.autosave-interval-ticks", 6000L));
        playerAutosaveTask = getServer().getScheduler().runTaskTimer(
                this, this::saveDirtyPlayerSessions, autosaveInterval, autosaveInterval);
    }

    private void saveDirtyPlayerSessions() {
        if (playerSessionManager == null) {
            return;
        }
        for (Player player : getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            playerSessionManager.snapshot(playerId)
                    .filter(snapshot -> snapshot.state() == PlayerSessionState.ACTIVE)
                    .filter(snapshot -> !snapshot.dirtyComponents().isEmpty())
                    .ifPresent(snapshot -> playerSessionManager.save(playerId, snapshot.token())
                            .whenComplete((saved, failure) -> {
                                if (failure != null) {
                                    getLogger().log(Level.SEVERE, "Player autosave failed for " + playerId, failure);
                                } else if (saved.state() == PlayerSessionState.SAVE_RETRY_PENDING) {
                                    getLogger().severe("Player autosave requires retry for "
                                            + playerId + ": " + saved.detail());
                                }
                            }));
        }
    }

    private void stopPlayerAutosave() {
        if (playerAutosaveTask != null) {
            playerAutosaveTask.cancel();
            playerAutosaveTask = null;
        }
    }

    private void closeOnlinePlayerSessions() {
        if (playerSessionManager == null) {
            return;
        }
        List<CompletableFuture<?>> pending = new ArrayList<>();
        for (Player player : getServer().getOnlinePlayers()) {
            playerSessionManager.snapshot(player.getUniqueId()).ifPresent(snapshot -> pending.add(
                    playerSessionManager.close(player.getUniqueId(), snapshot.token()).toCompletableFuture()));
        }
        if (pending.isEmpty()) {
            return;
        }
        long timeoutMillis = Math.max(
                1, getConfig().getLong("player-session.shutdown-timeout-millis", 5000));
        try {
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                    .get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Timed out while flushing player sessions", exception);
        }
    }

    private void stopPlayerStorageExecutor() {
        if (playerStorageExecutor == null) {
            return;
        }
        playerStorageExecutor.shutdown();
        long timeoutMillis = Math.max(
                1, getConfig().getLong("player-session.shutdown-timeout-millis", 5000));
        try {
            if (!playerStorageExecutor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                getLogger().severe("Player storage executor did not stop within the shutdown budget.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            getLogger().warning("Interrupted while stopping player storage executor.");
        } finally {
            playerStorageExecutor = null;
            playerSessionManager = null;
        }
    }

>>>>>>> parent of 3846639 (74)
    private void logDiagnostics(String heading, ContentReloadResult result) {
        getLogger().severe(heading + "; active revision remains " + result.revision() + '.');
        result.diagnostics().forEach(line -> getLogger().severe(" - " + line));
    }

<<<<<<< HEAD
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
            int saved = sessionService.flushAll();
            sender.sendMessage("Flushed " + saved + " dirty session(s).");
            return true;
        }
        sender.sendMessage("Usage: /" + label + " player <inspect|save> <player>");
        return true;
    }

    private final class AdminCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length == 0) {
                sender.sendMessage("Usage: /" + label + " <reload|status|player>");
                return true;
            }
            if (args[0].equalsIgnoreCase("player")) {
                return playerCommand(sender, label, args);
            }
=======
    private final class AdminCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
>>>>>>> parent of 3846639 (74)
            if (args.length != 1) {
                sender.sendMessage("Usage: /" + label + " <reload|status>");
                return true;
            }
            if (args[0].equalsIgnoreCase("status")) {
                var snapshot = contentService.snapshot();
                sender.sendMessage("Branz MMORPG: content revision " + snapshot.revision()
                        + ", definitions " + snapshot.definitions().size()
<<<<<<< HEAD
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
=======
                        + ", core " + coreRuntime.health().state()
                        + ", database " + databaseManagedService.detail()
                        + ", active sessions "
                        + (playerSessionManager == null ? "offline" : playerSessionManager.activeSessionCount())
                        + ", dirty sessions "
                        + (playerSessionManager == null ? "offline" : playerSessionManager.dirtySessionCount()));
>>>>>>> parent of 3846639 (74)
                return true;
            }
            if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                ContentReloadResult result = contentService.reload(contentDirectory);
                if (result.successful()) {
                    sender.sendMessage("Content reloaded: revision " + result.revision()
                            + ", definitions " + result.definitionCount());
                } else {
                    sender.sendMessage("Content reload rejected; revision " + result.revision()
                            + " remains active. Check server logs.");
                    logDiagnostics("Content reload rejected", result);
                }
                return true;
            }
            sender.sendMessage("Usage: /" + label + " <reload|status>");
            return true;
        }
    }
}
