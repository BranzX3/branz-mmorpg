package com.branz.mmorpg.paper;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.branz.mmorpg.api.skill.ResourceType;

/** Coalesced, dirty-driven action-bar HUD with text-only accessibility. */
public final class PaperHudRuntime implements Listener {
    private final JavaPlugin plugin;
    private final PaperSkillRuntime skills;
    private final PaperStatusRuntime statuses;
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    public PaperHudRuntime(JavaPlugin plugin, PaperSkillRuntime skills,
                           PaperStatusRuntime statuses) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.skills = java.util.Objects.requireNonNull(skills, "skills");
        this.statuses = java.util.Objects.requireNonNull(statuses, "statuses");
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { mark(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        dirty.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) mark(player);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player) mark(player);
    }
    @EventHandler public void onHeld(PlayerItemHeldEvent event) { mark(event.getPlayer()); }

    public void mark(Player player) { dirty.add(player.getUniqueId()); }

    /** Low-frequency refresh marks changing resources/statuses; flush stays coalesced. */
    public void markOnlineDirty() {
        plugin.getServer().getOnlinePlayers().forEach(this::mark);
    }

    public void flush() {
        for (UUID playerId : Set.copyOf(dirty)) {
            if (!dirty.remove(playerId)) continue;
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;
            PaperSkillRuntime.ResourceView resource = skills.resources(playerId);
            String active = statuses.active(playerId).stream()
                    .map(value -> value.definitionId().value())
                    .distinct().limit(3)
                    .collect(java.util.stream.Collectors.joining(","));
            Component line = Component.text("HP ", NamedTextColor.RED)
                    .append(Component.text(round(player.getHealth()) + "/"
                            + round(player.getMaxHealth()), NamedTextColor.WHITE))
                    .append(Component.text("  " + resourceLabel(resource.primaryResource()) + " ",
                            resourceColor(resource.primaryResource())))
                    .append(Component.text(round(resource.primary()) + "/"
                            + round(resource.maximumPrimary()), NamedTextColor.WHITE));
            if (!active.isEmpty()) {
                line = line.append(Component.text("  [" + active + "]",
                        NamedTextColor.LIGHT_PURPLE));
            }
            player.sendActionBar(line);
        }
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }

    private static String resourceLabel(ResourceType resource) {
        return switch (resource) {
            case MANA -> "MP";
            case STAMINA -> "ST";
            case RAGE -> "RG";
            case ENERGY -> "EN";
            case HEALTH -> "HP";
        };
    }

    private static NamedTextColor resourceColor(ResourceType resource) {
        return switch (resource) {
            case MANA -> NamedTextColor.AQUA;
            case STAMINA -> NamedTextColor.YELLOW;
            case RAGE -> NamedTextColor.RED;
            case ENERGY -> NamedTextColor.GREEN;
            case HEALTH -> NamedTextColor.DARK_RED;
        };
    }
}
