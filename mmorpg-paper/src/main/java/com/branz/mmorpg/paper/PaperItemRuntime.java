package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.item.InventoryService;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.item.ItemInstance;
import com.branz.mmorpg.core.item.ItemTokenSigner;
import com.branz.mmorpg.core.player.PlayerSessionService;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Reconciles untrusted ItemStack tokens against authoritative unique ownership. */
public final class PaperItemRuntime implements Listener {
    private final JavaPlugin plugin;
    private final PlayerSessionService sessions;
    private final InventoryService inventory;
    private final ContentService content;
    private final ItemTokenSigner signer;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey definitionKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey signatureKey;
    private final Map<UUID, InventorySnapshot> snapshots = new ConcurrentHashMap<>();

    public PaperItemRuntime(JavaPlugin plugin, PlayerSessionService sessions,
                            InventoryService inventory, ContentService content,
                            byte[] tokenSecret) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.content = Objects.requireNonNull(content, "content");
        signer = new ItemTokenSigner(tokenSecret);
        itemIdKey = new NamespacedKey(plugin, "item_instance");
        definitionKey = new NamespacedKey(plugin, "definition_id");
        ownerKey = new NamespacedKey(plugin, "owner_id");
        signatureKey = new NamespacedKey(plugin, "token_signature");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        snapshots.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::hasToken);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> reconcile(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!hasToken(event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar(Component.text(
                "MMO equipment cannot be dropped. Use trade or storage.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !hasToken(event.getItem().getItemStack())) return;
        event.setCancelled(true);
        event.getItem().remove();
        plugin.getLogger().warning("Removed public MMO item token near " + player.getUniqueId());
        reconcile(player);
    }

    public void reconcile(Player player) {
        UUID playerId = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                InventorySnapshot authoritative = inventory.inventory(playerId);
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> applyReconciliation(player, authoritative));
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING,
                        "Item reconciliation failed for " + playerId, failure);
            }
        });
    }

    public Optional<com.branz.mmorpg.api.item.WeaponDefinition> activeWeapon(UUID playerId) {
        InventorySnapshot snapshot = snapshots.get(playerId);
        if (snapshot == null) return Optional.empty();
        UUID instanceId = snapshot.equipped().get(
                com.branz.mmorpg.api.item.EquipmentSlot.MAIN_HAND);
        ItemInstance item = instanceId == null ? null : snapshot.items().get(instanceId);
        return item == null ? Optional.empty()
                : Optional.ofNullable(content.snapshot().weapons().get(item.definitionId()));
    }

    private void applyReconciliation(Player player, InventorySnapshot authoritative) {
        if (!player.isOnline()) return;
        snapshots.put(player.getUniqueId(), authoritative);
        Set<UUID> seen = new HashSet<>();
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (!hasToken(stack)) continue;
            Token token = read(stack);
            ItemInstance owned = token == null ? null : authoritative.items().get(token.itemId());
            boolean valid = token != null
                    && token.ownerId().equals(player.getUniqueId())
                    && signer.verify(token.itemId(), token.definitionId(),
                            token.ownerId(), token.signature())
                    && owned != null
                    && owned.definitionId().equals(token.definitionId())
                    && seen.add(token.itemId());
            if (!valid) {
                player.getInventory().setItem(slot, quarantined());
                plugin.getLogger().warning("Quarantined invalid or duplicate MMO token for "
                        + player.getUniqueId() + " in slot " + slot);
            }
        }
        for (ItemInstance item : authoritative.items().values()) {
            if (seen.contains(item.instanceId())) continue;
            ItemStack token = token(item, player.getUniqueId());
            var leftovers = player.getInventory().addItem(token);
            if (!leftovers.isEmpty()) {
                player.sendActionBar(Component.text(
                        "MMO inventory is full; an equipment token is pending display.",
                        NamedTextColor.YELLOW));
                break;
            }
        }
    }

    private ItemStack token(ItemInstance item, UUID ownerId) {
        Material material = presentation(item.definitionId());
        ItemStack stack = new ItemStack(material);
        var meta = stack.getItemMeta();
        String name = content.snapshot().find(item.definitionId())
                .map(definition -> {
                    if (definition instanceof com.branz.mmorpg.api.item.WeaponDefinition weapon) {
                        return weapon.displayName();
                    }
                    return definition.id().toString();
                }).orElse(item.definitionId().toString());
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.setUnbreakable(true);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(itemIdKey, PersistentDataType.STRING, item.instanceId().toString());
        pdc.set(definitionKey, PersistentDataType.STRING, item.definitionId().toString());
        pdc.set(ownerKey, PersistentDataType.STRING, ownerId.toString());
        pdc.set(signatureKey, PersistentDataType.STRING,
                signer.sign(item.instanceId(), item.definitionId(), ownerId));
        stack.setItemMeta(meta);
        return stack;
    }

    private Token read(ItemStack stack) {
        try {
            PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
            return new Token(
                    UUID.fromString(pdc.get(itemIdKey, PersistentDataType.STRING)),
                    ContentId.parse(pdc.get(definitionKey, PersistentDataType.STRING)),
                    UUID.fromString(pdc.get(ownerKey, PersistentDataType.STRING)),
                    pdc.get(signatureKey, PersistentDataType.STRING));
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private boolean hasToken(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(itemIdKey);
    }

    private static ItemStack quarantined() {
        ItemStack stack = new ItemStack(Material.BARRIER);
        var meta = stack.getItemMeta();
        meta.displayName(Component.text("Quarantined MMO Item", NamedTextColor.RED));
        meta.lore(java.util.List.of(Component.text(
                "Invalid, duplicated, or unowned token", NamedTextColor.GRAY)));
        stack.setItemMeta(meta);
        return stack;
    }

    private static Material presentation(ContentId definitionId) {
        String value = definitionId.value();
        if (value.contains("bow")) return Material.BOW;
        if (value.contains("staff")) return Material.BLAZE_ROD;
        return Material.IRON_SWORD;
    }

    private record Token(UUID itemId, ContentId definitionId, UUID ownerId, String signature) {}
}
