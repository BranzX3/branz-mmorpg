package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

final class ResourcePackGate implements Listener {
    private static final Component PROMPT =
            Component.text(
                    "Branz MMO requires its resource pack for gameplay.", NamedTextColor.GOLD);

    private final JavaPlugin plugin;
    private final boolean enabled;
    private final boolean required;
    private final String url;
    private final String configuredSha256;
    private final String activeSha256;
    private Consumer<Player> readyHandler = player -> {};
    private final Map<UUID, PackAdmissionState> states = new ConcurrentHashMap<>();

    ResourcePackGate(JavaPlugin plugin, ContentSnapshot snapshot) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(snapshot, "snapshot");
        enabled = plugin.getConfig().getBoolean("resource-pack.enabled", false);
        required = plugin.getConfig().getBoolean("resource-pack.required", true);
        url = plugin.getConfig().getString("resource-pack.url", "").trim();
        configuredSha256 =
                plugin.getConfig()
                        .getString("resource-pack.sha256", "")
                        .trim()
                        .toLowerCase(Locale.ROOT);
        activeSha256 = snapshot.manifest().resourcePackSha256().toLowerCase(Locale.ROOT);
    }

    void setReadyHandler(Consumer<Player> readyHandler) {
        this.readyHandler = Objects.requireNonNull(readyHandler, "readyHandler");
    }

    boolean configurationValid() {
        if (!enabled) {
            return true;
        }
        return (url.startsWith("https://") || url.startsWith("http://"))
                && configuredSha256.matches("[0-9a-f]{64}")
                && configuredSha256.equals(activeSha256);
    }

    boolean ready(Player player) {
        return state(player.getUniqueId()) == PackAdmissionState.READY
                || state(player.getUniqueId()) == PackAdmissionState.DISABLED;
    }

    PackAdmissionState state(UUID playerId) {
        return states.getOrDefault(
                Objects.requireNonNull(playerId, "playerId"),
                enabled ? PackAdmissionState.PENDING : PackAdmissionState.DISABLED);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!enabled) {
            states.put(player.getUniqueId(), PackAdmissionState.DISABLED);
            readyHandler.accept(player);
            return;
        }
        if (!configurationValid()) {
            states.put(player.getUniqueId(), PackAdmissionState.STALE);
            player.sendMessage(
                    Component.text(
                            "Resource-pack configuration does not match active content.",
                            NamedTextColor.RED));
            return;
        }
        states.put(player.getUniqueId(), PackAdmissionState.PENDING);
        player.setResourcePack(url, configuredSha256, required, PROMPT);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                states.put(player.getUniqueId(), PackAdmissionState.READY);
                readyHandler.accept(player);
            }
            case DECLINED -> reject(player, PackAdmissionState.DECLINED, "Resource pack declined.");
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED ->
                    reject(player, PackAdmissionState.FAILED, "Resource pack failed to load.");
            case ACCEPTED, DOWNLOADED ->
                    states.put(player.getUniqueId(), PackAdmissionState.PENDING);
            default -> reject(player, PackAdmissionState.FAILED, "Unknown resource-pack status.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    void clear() {
        states.clear();
    }

    private void reject(Player player, PackAdmissionState state, String message) {
        states.put(player.getUniqueId(), state);
        player.sendMessage(
                Component.text(message + " MMO gameplay remains locked.", NamedTextColor.RED));
        if (required) {
            plugin.getLogger()
                    .warning("Resource pack " + state + " for player " + player.getUniqueId());
        }
    }
}
