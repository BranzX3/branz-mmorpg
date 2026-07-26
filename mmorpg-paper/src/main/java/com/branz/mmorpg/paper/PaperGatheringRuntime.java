package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.gathering.GatheringNodeDefinition;
import com.branz.mmorpg.api.gathering.GatheringNodeInstance;
import com.branz.mmorpg.api.gathering.GatheringNodeState;
import com.branz.mmorpg.api.gathering.GatheringReservation;
import com.branz.mmorpg.api.gathering.GatheringService;
import com.branz.mmorpg.api.gathering.WorldBlockPosition;
import com.branz.mmorpg.api.runtime.Scheduler;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper interaction/channel adapter for registered gathering nodes. */
public final class PaperGatheringRuntime implements Listener {
    private static final double LEASH_SQUARED = 9.0;
    private final JavaPlugin plugin;
    private final GatheringService gathering;
    private final com.branz.mmorpg.api.content.ContentService content;
    private final Scheduler scheduler;
    private final PaperItemRuntime items;
    private final Map<WorldBlockPosition, GatheringNodeInstance> nodes =
            new ConcurrentHashMap<>();
    private final Map<UUID, ActiveHarvest> active = new ConcurrentHashMap<>();
    private final Set<UUID> starting = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean refreshInFlight = new AtomicBoolean();

    public PaperGatheringRuntime(
            JavaPlugin plugin, GatheringService gathering,
            com.branz.mmorpg.api.content.ContentService content,
            Scheduler scheduler, PaperItemRuntime items) {
        this.plugin = plugin;
        this.gathering = gathering;
        this.content = content;
        this.scheduler = scheduler;
        this.items = items;
        refresh();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null) return;
        GatheringNodeInstance cached = nodes.get(position(event.getClickedBlock()));
        if (cached == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!starting.add(player.getUniqueId()) || active.containsKey(player.getUniqueId())) {
            player.sendActionBar(Component.text("Already harvesting.", NamedTextColor.YELLOW));
            return;
        }
        Set<String> toolTags = toolTags(player.getInventory().getItemInMainHand().getType());
        boolean matches = presentationMatches(event.getClickedBlock(), cached);
        Material held = player.getInventory().getItemInMainHand().getType();
        scheduler.async(() -> gathering.begin(player.getUniqueId(), cached.position(),
                        toolTags, true, matches))
                .whenComplete((reservation, failure) -> scheduler.sync(() -> {
                    starting.remove(player.getUniqueId());
                    if (!player.isOnline()) {
                        if (reservation != null) interruptAsync(reservation, "logout");
                        return;
                    }
                    if (failure != null) {
                        player.sendActionBar(Component.text(
                                rootMessage(failure), NamedTextColor.RED));
                        refresh();
                        return;
                    }
                    active.put(player.getUniqueId(),
                            new ActiveHarvest(reservation, player.getLocation().clone(), held));
                    player.sendActionBar(Component.text(
                            "Harvesting " + reservation.definitionId() + "...",
                            NamedTextColor.GREEN));
                }));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (nodes.containsKey(position(event.getBlock()))) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text(
                    "This MMO node must be harvested, not broken.", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) interrupt(player, "damage");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeldChange(PlayerItemHeldEvent event) {
        interrupt(event.getPlayer(), "tool_swap");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        starting.remove(event.getPlayer().getUniqueId());
        interrupt(event.getPlayer(), "logout");
    }

    /** Paper tick: validates channels and commits completed harvests off-thread. */
    public void tick() {
        Instant now = Instant.now();
        for (var entry : active.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            ActiveHarvest harvest = entry.getValue();
            if (player == null || !player.isOnline()
                    || !player.getWorld().equals(harvest.origin().getWorld())
                    || player.getLocation().distanceSquared(harvest.origin()) > LEASH_SQUARED
                    || player.getInventory().getItemInMainHand().getType()
                            != harvest.heldMaterial()) {
                if (active.remove(entry.getKey(), harvest)) {
                    interruptAsync(harvest.reservation(), "interrupted");
                }
                continue;
            }
            if (!now.isBefore(harvest.reservation().completesAt())
                    && active.remove(entry.getKey(), harvest)) {
                scheduler.async(() -> gathering.complete(harvest.reservation()))
                        .whenComplete((result, failure) -> scheduler.sync(() -> {
                            if (!player.isOnline()) return;
                            if (failure != null) {
                                player.sendActionBar(Component.text(
                                        rootMessage(failure), NamedTextColor.RED));
                            } else {
                                player.sendActionBar(Component.text(
                                        "Harvested +" + result.awardedXp() + " XP "
                                                + result.yields(),
                                        NamedTextColor.GREEN));
                                items.reconcile(player);
                            }
                            refresh();
                        }));
            }
        }
    }

    /** Async refresh; also persists timestamp-based respawns. */
    public void refresh() {
        if (!refreshInFlight.compareAndSet(false, true)) return;
        scheduler.async(gathering::nodes).whenComplete((loaded, failure) -> {
            refreshInFlight.set(false);
            if (failure != null) {
                plugin.getLogger().warning("Gathering node refresh failed: "
                        + rootMessage(failure));
                return;
            }
            scheduler.sync(() -> applyRefresh(loaded));
        });
    }

    public GatheringNodeInstance cachedAt(WorldBlockPosition position) {
        return nodes.get(position);
    }

    private void applyRefresh(Collection<GatheringNodeInstance> loaded) {
        Map<WorldBlockPosition, GatheringNodeInstance> previous = Map.copyOf(nodes);
        nodes.clear();
        Instant now = Instant.now();
        for (GatheringNodeInstance node : loaded) {
            nodes.put(node.position(), node);
            GatheringNodeDefinition definition =
                    content.snapshot().gatheringNodes().get(node.definitionId());
            if (definition == null) {
                if (node.state() != GatheringNodeState.BROKEN) {
                    plugin.getLogger().warning("Gathering node " + node.instanceId()
                            + " is orphaned from " + node.definitionId() + "; marking BROKEN.");
                    scheduler.async(() -> gathering.setState(
                            node.instanceId(), GatheringNodeState.BROKEN, now));
                }
                continue;
            }
            if (node.state() == GatheringNodeState.DEPLETED
                    && !node.respawnAt().orElseThrow().isAfter(now)) {
                scheduler.async(() -> gathering.setState(
                        node.instanceId(), GatheringNodeState.AVAILABLE, now))
                        .whenComplete((ignored, failure) -> {
                            if (failure == null) refresh();
                        });
            } else if (node.state() != GatheringNodeState.BROKEN) {
                GatheringNodeInstance old = previous.get(node.position());
                if (old != null && old.state() != node.state()) {
                    render(node);
                } else if (!storedPresentationMatches(node, definition)) {
                    plugin.getLogger().warning("Gathering node " + node.instanceId()
                            + " no longer matches its world block; marking BROKEN.");
                    scheduler.async(() -> gathering.setState(
                            node.instanceId(), GatheringNodeState.BROKEN, now));
                }
            }
        }
    }

    public void present(GatheringNodeInstance node) {
        render(node);
    }

    private void render(GatheringNodeInstance node) {
        var world = plugin.getServer().getWorld(node.position().worldId());
        if (world == null) return;
        GatheringNodeDefinition definition =
                content.snapshot().gatheringNodes().get(node.definitionId());
        if (definition == null) return;
        Material material = material(node.state() == GatheringNodeState.DEPLETED
                ? definition.presentation().depletedBlock()
                : definition.presentation().availableBlock());
        if (material != null) {
            world.getBlockAt(node.position().x(), node.position().y(), node.position().z())
                    .setType(material, false);
        }
    }

    private boolean presentationMatches(Block block, GatheringNodeInstance node) {
        GatheringNodeDefinition definition =
                content.snapshot().gatheringNodes().get(node.definitionId());
        Material expected = definition == null ? null
                : material(definition.presentation().availableBlock());
        return expected != null && block.getType() == expected;
    }

    private boolean storedPresentationMatches(
            GatheringNodeInstance node, GatheringNodeDefinition definition) {
        var world = plugin.getServer().getWorld(node.position().worldId());
        if (world == null || !world.isChunkLoaded(
                node.position().x() >> 4, node.position().z() >> 4)) return true;
        Material expected = material(node.state() == GatheringNodeState.DEPLETED
                ? definition.presentation().depletedBlock()
                : definition.presentation().availableBlock());
        return expected != null && world.getBlockAt(
                node.position().x(), node.position().y(), node.position().z()).getType() == expected;
    }

    private void interrupt(Player player, String reason) {
        ActiveHarvest harvest = active.remove(player.getUniqueId());
        if (harvest != null) {
            interruptAsync(harvest.reservation(), reason);
            player.sendActionBar(Component.text("Harvest interrupted.", NamedTextColor.RED));
        }
    }

    private void interruptAsync(GatheringReservation reservation, String reason) {
        scheduler.async(() -> gathering.interrupt(reservation, reason))
                .exceptionally(failure -> {
                    plugin.getLogger().warning("Failed to release gathering reservation: "
                            + rootMessage(failure));
                    return null;
                });
    }

    private static Set<String> toolTags(Material material) {
        String name = material.name().toLowerCase(Locale.ROOT);
        if (name.endsWith("_pickaxe")) return Set.of("branz:pickaxe");
        if (name.endsWith("_axe")) return Set.of("branz:axe");
        if (name.endsWith("_shovel")) return Set.of("branz:shovel");
        if (name.endsWith("_hoe")) return Set.of("branz:hoe");
        return Set.of();
    }

    private static Material material(String key) {
        String raw = key.startsWith("minecraft:") ? key.substring("minecraft:".length()) : key;
        return Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
    }

    private static WorldBlockPosition position(Block block) {
        return new WorldBlockPosition(block.getWorld().getUID(),
                block.getX(), block.getY(), block.getZ());
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private record ActiveHarvest(
            GatheringReservation reservation,
            org.bukkit.Location origin,
            Material heldMaterial) {}
}
