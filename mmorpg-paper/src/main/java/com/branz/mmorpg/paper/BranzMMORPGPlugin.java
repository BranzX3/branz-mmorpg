package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.content.ContentReloadResult;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.lifecycle.HealthService;
import com.branz.mmorpg.api.player.PlayerSessionService;
import com.branz.mmorpg.api.player.PlayerSessionState;
import com.branz.mmorpg.content.AtomicContentService;
import com.branz.mmorpg.core.lifecycle.CoreRuntime;
import com.branz.mmorpg.core.player.PlayerSessionManager;
import com.branz.mmorpg.core.service.ContentManagedService;
import com.branz.mmorpg.core.service.DatabaseManagedService;
import com.branz.mmorpg.storage.DatabaseConfig;
import com.branz.mmorpg.storage.player.MySqlPlayerProfileStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class BranzMMORPGPlugin extends JavaPlugin implements Listener {
    private AtomicContentService contentService;
    private ContentManagedService contentManagedService;
    private DatabaseManagedService databaseManagedService;
    private CoreRuntime coreRuntime;
    private ExecutorService playerStorageExecutor;
    private PlayerSessionManager playerSessionManager;
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
            contentManagedService = new ContentManagedService(contentService, contentDirectory);
            boolean databaseEnabled = getConfig().getBoolean("database.enabled", false);
            databaseManagedService = new DatabaseManagedService(databaseEnabled, readDatabaseConfig());
            coreRuntime = new CoreRuntime(List.of(contentManagedService, databaseManagedService));
            coreRuntime.start();

            ContentReloadResult initialLoad = contentManagedService.lastResult();
            if (databaseManagedService.connected()) {
                getLogger().info("Database connected and migrations applied.");
                startPlayerSessions();
            } else {
                getLogger().warning("Database is disabled; persistent gameplay services must remain offline.");
            }

            getServer().getServicesManager().register(
                    ContentService.class, contentService, this, ServicePriority.Normal);
            getServer().getServicesManager().register(
                    HealthService.class, coreRuntime, this, ServicePriority.Normal);
            if (playerSessionManager != null) {
                getServer().getServicesManager().register(
                        PlayerSessionService.class,
                        playerSessionManager,
                        this,
                        ServicePriority.Normal);
            }
            getServer().getPluginManager().registerEvents(this, this);
            Objects.requireNonNull(getCommand("branz"), "branz command").setExecutor(new AdminCommand());
            getLogger().info("Branz MMORPG Core " + coreRuntime.health().state()
                    + " with " + initialLoad.definitionCount()
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
        closeOnlinePlayerSessions();
        stopPlayerStorageExecutor();
        if (coreRuntime != null) {
            try {
                coreRuntime.close();
            } catch (RuntimeException exception) {
                getLogger().severe("Core shutdown failed: " + exception.getMessage());
            }
            coreRuntime = null;
        }
        getLogger().info("Branz MMORPG disabled.");
    }

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

    private void startPlayerSessions() {
        int workerThreads = Math.max(1, getConfig().getInt("player-session.worker-threads", 2));
        playerStorageExecutor = Executors.newFixedThreadPool(
                workerThreads,
                Thread.ofPlatform().name("branz-player-storage-", 0).daemon(true).factory());
        playerSessionManager = new PlayerSessionManager(new MySqlPlayerProfileStore(
                databaseManagedService.manager().orElseThrow(), playerStorageExecutor));
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

    private void logDiagnostics(String heading, ContentReloadResult result) {
        getLogger().severe(heading + "; active revision remains " + result.revision() + '.');
        result.diagnostics().forEach(line -> getLogger().severe(" - " + line));
    }

    private final class AdminCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length != 1) {
                sender.sendMessage("Usage: /" + label + " <reload|status>");
                return true;
            }
            if (args[0].equalsIgnoreCase("status")) {
                var snapshot = contentService.snapshot();
                sender.sendMessage("Branz MMORPG: content revision " + snapshot.revision()
                        + ", definitions " + snapshot.definitions().size()
                        + ", core " + coreRuntime.health().state()
                        + ", database " + databaseManagedService.detail()
                        + ", active sessions "
                        + (playerSessionManager == null ? "offline" : playerSessionManager.activeSessionCount()));
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
