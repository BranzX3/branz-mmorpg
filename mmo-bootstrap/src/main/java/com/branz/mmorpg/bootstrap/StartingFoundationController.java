package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.transaction.CharacterOnboardingStateRecord;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Normal-player, fail-closed first-session foundation selection and starter provisioning. */
final class StartingFoundationController implements Listener {
    private static final int INVENTORY_SIZE = 27;
    private static final Map<StartingFoundation, Integer> SLOTS = slots();

    private final JavaPlugin plugin;
    private final CharacterSessionController characters;
    private final String contentVersion;
    private final NamespacedKey foundationKey;
    private final Set<UUID> locked = new HashSet<>();
    private final Set<UUID> choiceRequired = new HashSet<>();
    private final Set<UUID> provisioning = new HashSet<>();
    private final Map<UUID, Inventory> choiceInventories = new HashMap<>();
    private BiConsumer<Player, StartingFoundation> foundationReadyObserver =
            (player, foundation) -> {};

    StartingFoundationController(
            JavaPlugin plugin, CharacterSessionController characters, String contentVersion) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        foundationKey = new NamespacedKey(plugin, "starting_foundation");
    }

    void setFoundationReadyObserver(BiConsumer<Player, StartingFoundation> observer) {
        foundationReadyObserver = Objects.requireNonNull(observer, "observer");
    }

    void onCharacterReady(Player player) {
        Objects.requireNonNull(player, "player");
        if (Boolean.getBoolean(PhysicalClientIngressAcceptanceProbe.ENABLE_PROPERTY)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        locked.add(playerId);
        characters.startingFoundationState(player, result -> handleState(player, result));
    }

    void shutdown() {
        locked.clear();
        choiceRequired.clear();
        provisioning.clear();
        choiceInventories.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    void onMove(PlayerMoveEvent event) {
        if (!locked.contains(event.getPlayer().getUniqueId()) || event.getTo() == null) {
            return;
        }
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    void onInteract(PlayerInteractEvent event) {
        if (locked.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    void onDrop(PlayerDropItemEvent event) {
        if (locked.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    void onSwap(PlayerSwapHandItemsEvent event) {
        if (locked.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && locked.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && locked.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!locked.contains(playerId)) {
            return;
        }
        event.setCancelled(true);
        Inventory expected = choiceInventories.get(playerId);
        if (expected == null
                || event.getView().getTopInventory() != expected
                || event.getClickedInventory() != expected
                || provisioning.contains(playerId)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir() || !current.hasItemMeta()) {
            return;
        }
        String raw =
                current.getItemMeta()
                        .getPersistentDataContainer()
                        .get(foundationKey, PersistentDataType.STRING);
        if (raw == null) {
            return;
        }
        StartingFoundation foundation;
        try {
            foundation = StartingFoundation.fromPersistentId(raw);
        } catch (IllegalArgumentException exception) {
            return;
        }
        choiceRequired.remove(playerId);
        provisioning.add(playerId);
        choiceInventories.remove(playerId);
        player.closeInventory();
        player.sendActionBar(
                Component.text(
                        "Preparing " + foundation.displayName() + " starter kit...",
                        NamedTextColor.GOLD));
        provision(player, foundation);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Inventory expected = choiceInventories.get(playerId);
        if (expected == null
                || event.getInventory() != expected
                || !choiceRequired.contains(playerId)
                || provisioning.contains(playerId)) {
            return;
        }
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (player.isOnline() && choiceRequired.contains(playerId)) {
                                openChoice(player);
                            }
                        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        locked.remove(playerId);
        choiceRequired.remove(playerId);
        provisioning.remove(playerId);
        choiceInventories.remove(playerId);
    }

    private void handleState(
            Player player,
            Result<Optional<CharacterOnboardingStateRecord>, CharacterSessionErrorCode> result) {
        if (!player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (result
                instanceof
                Result.Failure<Optional<CharacterOnboardingStateRecord>, CharacterSessionErrorCode>
                        failure) {
            failClosed(player, "Onboarding state unavailable: " + failure.detail());
            return;
        }
        Optional<CharacterOnboardingStateRecord> state =
                ((Result.Success<
                                        Optional<CharacterOnboardingStateRecord>,
                                        CharacterSessionErrorCode>)
                                result)
                        .value();
        if (state.isEmpty()) {
            boolean fresh =
                    characters
                            .active(player)
                            .map(
                                    session ->
                                            session.snapshot().itemRecords().isEmpty()
                                                    && session.snapshot().lotRecords().isEmpty())
                            .orElse(false);
            if (!fresh) {
                locked.remove(playerId);
                return;
            }
            choiceRequired.add(playerId);
            openChoice(player);
            return;
        }

        CharacterOnboardingStateRecord record = state.orElseThrow();
        StartingFoundation foundation;
        try {
            foundation = StartingFoundation.fromPersistentId(record.foundationId());
        } catch (IllegalArgumentException exception) {
            failClosed(player, "Stored starting foundation is invalid.");
            return;
        }
        if (record.kitReady()) {
            locked.remove(playerId);
            foundationReadyObserver.accept(player, foundation);
            return;
        }
        provisioning.add(playerId);
        player.sendActionBar(Component.text("Restoring your starter kit...", NamedTextColor.GOLD));
        provision(player, foundation);
    }

    private void provision(Player player, StartingFoundation foundation) {
        characters.chooseStartingFoundation(
                player,
                foundation,
                contentVersion,
                result -> {
                    UUID playerId = player.getUniqueId();
                    provisioning.remove(playerId);
                    if (!player.isOnline()) {
                        return;
                    }
                    if (result
                            instanceof
                            Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>
                                    failure) {
                        failClosed(player, "Starter provisioning failed: " + failure.detail());
                        return;
                    }
                    locked.remove(playerId);
                    choiceRequired.remove(playerId);
                    choiceInventories.remove(playerId);
                    player.sendMessage(
                            Component.text("Foundation ready: ", NamedTextColor.GREEN)
                                    .append(
                                            Component.text(
                                                    foundation.displayName(),
                                                    NamedTextColor.GOLD)));
                    player.sendMessage(
                            Component.text(
                                    "Your Chronicle is in slot 9. Draw your weapon and try LMB when ready.",
                                    NamedTextColor.GRAY));
                    foundationReadyObserver.accept(player, foundation);
                });
    }

    private void openChoice(Player player) {
        UUID playerId = player.getUniqueId();
        if (!player.isOnline() || !choiceRequired.contains(playerId)) {
            return;
        }
        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        INVENTORY_SIZE,
                        Component.text("Choose a starting foundation", NamedTextColor.DARK_GRAY));
        for (StartingFoundation foundation : StartingFoundation.values()) {
            inventory.setItem(SLOTS.get(foundation), choiceItem(foundation));
        }
        choiceInventories.put(playerId, inventory);
        player.openInventory(inventory);
        player.sendMessage(
                Component.text(
                        "Choose a starting foundation. This is a starter kit, not a permanent class.",
                        NamedTextColor.YELLOW));
    }

    private ItemStack choiceItem(StartingFoundation foundation) {
        Material material =
                switch (foundation) {
                    case GREATSWORD -> Material.IRON_SWORD;
                    case SWORD_AND_SHIELD -> Material.SHIELD;
                    case BOW -> Material.BOW;
                    case STAFF_EMBER -> Material.BLAZE_ROD;
                };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(foundation.displayName(), NamedTextColor.GOLD));
        meta.lore(
                List.of(
                        Component.text(foundation.description(), NamedTextColor.GRAY),
                        Component.text("Click to choose", NamedTextColor.GREEN)));
        meta.getPersistentDataContainer()
                .set(foundationKey, PersistentDataType.STRING, foundation.name());
        item.setItemMeta(meta);
        return item;
    }

    private void failClosed(Player player, String detail) {
        UUID playerId = player.getUniqueId();
        choiceRequired.remove(playerId);
        provisioning.remove(playerId);
        choiceInventories.remove(playerId);
        locked.add(playerId);
        plugin.getLogger().severe("Starting foundation failure for " + playerId + ": " + detail);
        player.kick(
                Component.text(
                        "Starter setup could not complete safely. Reconnect to resume.",
                        NamedTextColor.RED));
    }

    private static Map<StartingFoundation, Integer> slots() {
        EnumMap<StartingFoundation, Integer> slots = new EnumMap<>(StartingFoundation.class);
        slots.put(StartingFoundation.GREATSWORD, 10);
        slots.put(StartingFoundation.SWORD_AND_SHIELD, 12);
        slots.put(StartingFoundation.BOW, 14);
        slots.put(StartingFoundation.STAFF_EMBER, 16);
        return Map.copyOf(slots);
    }
}
