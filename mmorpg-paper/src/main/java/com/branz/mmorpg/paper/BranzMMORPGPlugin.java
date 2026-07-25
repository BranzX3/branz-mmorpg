package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.content.ContentReloadResult;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.runtime.Scheduler;
import com.branz.mmorpg.api.runtime.TransactionRunner;
import com.branz.mmorpg.api.service.ServiceStatus;
import com.branz.mmorpg.content.AtomicContentService;
import com.branz.mmorpg.core.runtime.ExecutorScheduler;
import com.branz.mmorpg.core.service.ServiceContainer;
import com.branz.mmorpg.storage.DatabaseConfig;
import com.branz.mmorpg.storage.DatabaseManager;
import com.branz.mmorpg.storage.JdbcTransactionRunner;
import java.time.Duration;
import java.nio.file.Path;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class BranzMMORPGPlugin extends JavaPlugin {
    private AtomicContentService contentService;
    private DatabaseManager databaseManager;
    private ServiceContainer serviceContainer;
    private Scheduler scheduler;
    private TransactionRunner transactionRunner;
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
            serviceContainer.startAll();

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
        if (scheduler != null && !scheduler.drainAndShutdown(Duration.ofSeconds(10))) {
            getLogger().warning("Scheduler did not drain within 10s; remaining work was abandoned.");
        }
        if (serviceContainer != null) {
            // Storage closes last: draining work above may still need it.
            serviceContainer.stopAll();
            serviceContainer = null;
            scheduler = null;
        }
        transactionRunner = null;
        if (databaseManager != null) {
            databaseManager.close();
            databaseManager = null;
        }
        getLogger().info("Branz MMORPG disabled.");
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
