package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.character.CharacterClassId;
import com.branz.mmorpg.api.character.CharacterClassSelectionRequest;
import com.branz.mmorpg.api.character.ClassSkillNodeDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.item.PendingSlotItem;
import com.branz.mmorpg.api.item.PendingSlotItemRepository;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.player.PlayerSession;
import com.branz.mmorpg.api.player.SessionToken;
import com.branz.mmorpg.api.runtime.Scheduler;
import com.branz.mmorpg.core.character.CharacterClassProgressionService;
import com.branz.mmorpg.core.character.PermanentCharacterClassService;
import com.branz.mmorpg.core.item.ItemTokenSigner;
import com.branz.mmorpg.core.item.StarterKitDeliveryService;
import com.branz.mmorpg.core.player.PlayerSessionService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Protected slot-9 class selection and immutable class-tree inventory UI. */
public final class PaperClassCompassRuntime implements Listener {
    public static final int RESERVED_SLOT = 8;
    private static final long TOKEN_VERSION = 1L;
    private static final ContentId SELECTION_TOKEN = ContentId.parse("branz:class_selection_compass");
    private static final ContentId TREE_TOKEN = ContentId.parse("branz:class_tree_compass");

    private final JavaPlugin plugin;
    private final PlayerSessionService sessions;
    private final ContentService content;
    private final PermanentCharacterClassService classes;
    private final CharacterClassProgressionService progression;
    private final StarterKitDeliveryService starterDelivery;
    private final PendingSlotItemRepository pendingSlots;
    private final PaperItemRuntime items;
    private final Scheduler scheduler;
    private final ItemTokenSigner signer;
    private final ReservedSlotPolicy slotPolicy = new ReservedSlotPolicy();
    private final org.bukkit.NamespacedKey tokenTypeKey;
    private final org.bukkit.NamespacedKey tokenVersionKey;
    private final org.bukkit.NamespacedKey ownerKey;
    private final org.bukkit.NamespacedKey sessionKey;
    private final org.bukkit.NamespacedKey signatureKey;
    private final Set<UUID> relocations = ConcurrentHashMap.newKeySet();
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    public PaperClassCompassRuntime(JavaPlugin plugin, PlayerSessionService sessions,
                                    ContentService content,
                                    PermanentCharacterClassService classes,
                                    CharacterClassProgressionService progression,
                                    StarterKitDeliveryService starterDelivery,
                                    PendingSlotItemRepository pendingSlots,
                                    PaperItemRuntime items, Scheduler scheduler,
                                    byte[] tokenSecret) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.content = Objects.requireNonNull(content, "content");
        this.classes = Objects.requireNonNull(classes, "classes");
        this.progression = Objects.requireNonNull(progression, "progression");
        this.starterDelivery = Objects.requireNonNull(starterDelivery, "starterDelivery");
        this.pendingSlots = Objects.requireNonNull(pendingSlots, "pendingSlots");
        this.items = Objects.requireNonNull(items, "items");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        signer = new ItemTokenSigner(Objects.requireNonNull(tokenSecret, "tokenSecret"));
        tokenTypeKey = new org.bukkit.NamespacedKey(plugin, "class_compass_type");
        tokenVersionKey = new org.bukkit.NamespacedKey(plugin, "class_compass_version");
        ownerKey = new org.bukkit.NamespacedKey(plugin, "class_compass_owner");
        sessionKey = new org.bukkit.NamespacedKey(plugin, "class_compass_session");
        signatureKey = new org.bukkit.NamespacedKey(plugin, "class_compass_signature");
    }

    /** Called after the exact session has loaded; inventory work returns to Paper sync. */
    public void sessionReady(UUID playerId) {
        PlayerSession session = sessions.session(playerId).orElse(null);
        if (session == null || !session.playable()) return;
        SessionToken token = session.token();
        if (session.profile().classId().isEmpty()) {
            scheduler.sync(() -> reconcileLive(playerId, token));
            return;
        }
        scheduler.async(() -> starterDelivery.deliver(playerId)).whenComplete((ignored, failure) ->
                scheduler.sync(() -> {
                    if (failure != null) {
                        plugin.getLogger().log(java.util.logging.Level.WARNING,
                                "Starter kit delivery failed for " + playerId, failure);
                    }
                    reconcileLive(playerId, token);
                }));
    }

    private void reconcileLive(UUID playerId, SessionToken token) {
        if (!sessions.isLive(token)) return;
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) return;
        items.reconcile(player);
        reconcile(player);
    }

    public boolean isHeldCompass(Player player) {
        if (player.getInventory().getHeldItemSlot() != RESERVED_SLOT) return false;
        return validToken(player, player.getInventory().getItem(RESERVED_SLOT));
    }

    public void reconcile(Player player) {
        PlayerSession session = sessions.session(player.getUniqueId()).orElse(null);
        if (session == null || !session.playable() || relocations.contains(player.getUniqueId())) return;
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            if (slot != RESERVED_SLOT && hasToken(storage[slot])) {
                player.getInventory().setItem(slot, null);
            }
        }
        ItemStack reserved = player.getInventory().getItem(RESERVED_SLOT);
        int free = freeStorageSlot(player);
        ReservedSlotPolicy.Decision decision = slotPolicy.decide(
                reserved == null || reserved.getType().isAir(), hasToken(reserved),
                validToken(player, reserved), free);
        switch (decision.action()) {
            case REFRESH_VALID_TOKEN, REPLACE_INVALID_TOKEN, PLACE_TOKEN ->
                    player.getInventory().setItem(RESERVED_SLOT, compass(player, session));
            case RELOCATE_NORMAL_ITEM -> {
                player.getInventory().setItem(decision.destinationSlot(), reserved);
                player.getInventory().setItem(RESERVED_SLOT, compass(player, session));
            }
            case PERSIST_NORMAL_ITEM -> persistDisplaced(
                    player, session.token(), reserved.clone());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!isHeldCompass(event.getPlayer())) return;
        event.setCancelled(true);
        if (event.getAction().isRightClick()) open(event.getPlayer());
        else event.getPlayer().sendActionBar(Component.text(
                "Right-click to open class progression.", NamedTextColor.YELLOW));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (hasToken(event.getMainHandItem()) || hasToken(event.getOffHandItem())) {
            event.setCancelled(true);
            reconcile(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (!hasToken(event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        reconcile(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !hasToken(event.getItem().getItemStack())) return;
        event.setCancelled(true);
        event.getItem().remove();
        reconcile(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder holder) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                handleMenuClick(player, holder, event.getRawSlot());
            }
            return;
        }
        boolean reserved = event.getClickedInventory() == player.getInventory()
                && event.getSlot() == RESERVED_SLOT;
        if (reserved || event.getHotbarButton() == RESERVED_SLOT
                || hasToken(event.getCurrentItem()) || hasToken(event.getCursor())
                || relocations.contains(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> reconcile(player));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder
                || hasToken(event.getOldCursor()) || relocations.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        for (int raw : event.getRawSlots()) {
            Inventory inventory = event.getView().getInventory(raw);
            if (inventory == player.getInventory()
                    && event.getView().convertSlot(raw) == RESERVED_SLOT) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::hasToken);
        if (event.getEntity().getOpenInventory().getTopInventory()
                .getHolder() instanceof MenuHolder) {
            event.getEntity().closeInventory();
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> reconcile(event.getPlayer()));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory()
                .getHolder() instanceof MenuHolder) {
            event.getPlayer().closeInventory();
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> reconcile(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        relocations.remove(event.getPlayer().getUniqueId());
        processing.remove(event.getPlayer().getUniqueId());
    }

    private void open(Player player) {
        PlayerSession session = requireLive(player, null);
        if (session == null) return;
        if (session.profile().classId().isEmpty()) openSelection(player, session);
        else openTree(player, session);
    }

    private void openSelection(Player player, PlayerSession session) {
        MenuHolder holder = new MenuHolder(MenuType.SELECTION, UUID.randomUUID(),
                session.token(), session.profile().revision(), content.snapshot().revision(),
                null, 0, Map.of(), Map.of());
        Inventory menu = create(holder, 27, "Choose Your Permanent Class");
        Map<Integer, ContentId> classesBySlot = new HashMap<>();
        putClass(menu, 11, CharacterClassId.WARRIOR.value(), Material.IRON_SWORD, classesBySlot);
        putClass(menu, 13, CharacterClassId.MAGE.value(), Material.BLAZE_ROD, classesBySlot);
        putClass(menu, 15, CharacterClassId.ROGUE.value(), Material.IRON_SWORD, classesBySlot);
        holder.classBySlot = Map.copyOf(classesBySlot);
        player.openInventory(menu);
    }

    private void openConfirm(Player player, MenuHolder previous, ContentId classId) {
        CharacterClassDefinition definition = content.snapshot().characterClasses().get(classId);
        if (definition == null) return;
        MenuHolder holder = new MenuHolder(MenuType.CONFIRM, previous.nonce,
                previous.token, previous.profileRevision, previous.contentRevision,
                classId, definition.treeRevision(), Map.of(), Map.of());
        Inventory menu = create(holder, 27, "Confirm " + definition.displayName());
        menu.setItem(13, icon(Material.LIME_CONCRETE, "Confirm permanent selection",
                NamedTextColor.GREEN, List.of("This choice cannot be changed normally.",
                        "Starter: " + definition.starterGrantPlan().weaponId())));
        menu.setItem(18, icon(Material.BARRIER, "Cancel", NamedTextColor.RED,
                List.of("No changes will be saved.")));
        player.openInventory(menu);
    }

    private void openTree(Player player, PlayerSession session) {
        ContentId classId = session.profile().classId().orElseThrow();
        CharacterClassDefinition definition = content.snapshot().characterClasses().get(classId);
        var progress = progression.progress(player.getUniqueId());
        MenuHolder holder = new MenuHolder(MenuType.TREE, UUID.randomUUID(), session.token(),
                session.profile().revision(), content.snapshot().revision(), classId,
                progress.treeRevision(), Map.of(), Map.of());
        Inventory menu = create(holder, 54, definition.displayName() + " Skill Tree");
        menu.setItem(4, icon(Material.NETHER_STAR,
                definition.displayName() + " - Level " + progress.level(),
                NamedTextColor.GOLD, List.of("Class XP: " + progress.totalXp(),
                        "Skill Points: " + progress.unspentSkillPoints())));
        List<ClassSkillNodeDefinition> nodes = content.snapshot().classSkillNodes().values()
                .stream().filter(node -> node.classId().equals(classId))
                .sorted(java.util.Comparator.comparing(node -> node.id().toString())).toList();
        Map<Integer, ContentId> bySlot = new HashMap<>();
        int[] slots = {19, 21, 23, 25, 28, 30, 32, 34};
        for (int index = 0; index < nodes.size() && index < slots.length; index++) {
            ClassSkillNodeDefinition node = nodes.get(index);
            int rank = progress.rank(node.id());
            boolean available = progress.level() >= node.requiredClassLevel()
                    && progress.unspentSkillPoints() >= node.pointCostPerRank();
            Material material = rank >= node.maximumRank() ? Material.EMERALD_BLOCK
                    : available ? Material.LIME_DYE : Material.GRAY_DYE;
            menu.setItem(slots[index], icon(material, node.id().value(),
                    rank > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                    List.of("Rank: " + rank + "/" + node.maximumRank(),
                            "Cost: " + node.pointCostPerRank(),
                            "Required level: " + node.requiredClassLevel(),
                            node.unlockedSkillId().map(id -> "Unlocks: " + id)
                                    .orElse("Branch: " + node.branchId()))));
            bySlot.put(slots[index], node.id());
        }
        holder.nodeBySlot = Map.copyOf(bySlot);
        player.openInventory(menu);
    }

    private void handleMenuClick(Player player, MenuHolder holder, int slot) {
        PlayerSession session = requireLive(player, holder.token);
        if (session == null || !holder.ownerId.equals(player.getUniqueId())
                || holder.openedAt.plusSeconds(300).isBefore(Instant.now())
                || holder.contentRevision != content.snapshot().revision()) {
            player.closeInventory();
            return;
        }
        if (holder.type == MenuType.SELECTION) {
            ContentId selected = holder.classBySlot.get(slot);
            if (selected != null) openConfirm(player, holder, selected);
            return;
        }
        if (holder.type == MenuType.CONFIRM) {
            if (slot == 18) {
                player.closeInventory();
                return;
            }
            if (slot == 13) confirmSelection(player, holder, session);
            return;
        }
        ContentId nodeId = holder.nodeBySlot.get(slot);
        if (holder.type == MenuType.TREE && nodeId != null) purchaseNode(player, holder, nodeId);
    }

    private void confirmSelection(Player player, MenuHolder holder, PlayerSession session) {
        UUID playerId = player.getUniqueId();
        if (!processing.add(playerId)) return;
        player.closeInventory();
        CharacterClassSelectionRequest request = new CharacterClassSelectionRequest(
                OperationId.of("class", "selection", playerId, holder.nonce.toString()),
                playerId, holder.token, CharacterClassId.parse(holder.classId.toString()),
                holder.profileRevision, holder.contentRevision, true);
        scheduler.async(() -> {
            var result = classes.select(request);
            StarterKitDeliveryService.Result delivery = starterDelivery.deliver(playerId);
            return new SelectionCompletion(result, delivery);
        }).whenComplete((completed, failure) -> scheduler.sync(() -> {
            processing.remove(playerId);
            if (!sessions.isLive(holder.token)) return;
            Player live = plugin.getServer().getPlayer(playerId);
            if (live == null) return;
            if (failure != null) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Permanent class selection failed for " + playerId, failure);
                live.sendMessage(Component.text("Class selection failed; please retry.",
                        NamedTextColor.RED));
                reconcile(live);
                return;
            }
            reconcile(live);
            items.reconcile(live);
            live.sendMessage(Component.text("Selected "
                    + completed.result.snapshot().classId().orElseThrow().value()
                    + " permanently. Starter kit: " + completed.delivery.status(),
                    NamedTextColor.GREEN));
        }));
    }

    private void purchaseNode(Player player, MenuHolder holder, ContentId nodeId) {
        UUID playerId = player.getUniqueId();
        if (!processing.add(playerId)) return;
        scheduler.async(() -> {
            if (progression.progress(playerId).treeRevision() != holder.treeRevision) {
                throw new IllegalStateException("stale class tree revision");
            }
            return progression.purchase(playerId, nodeId, OperationId.of(
                    "class_tree", nodeId.value(), playerId, holder.nonce.toString()));
        }).whenComplete((commit, failure) -> scheduler.sync(() -> {
            processing.remove(playerId);
            if (!sessions.isLive(holder.token)) return;
            Player live = plugin.getServer().getPlayer(playerId);
            if (live == null) return;
            if (failure != null) {
                live.sendActionBar(Component.text("Cannot purchase node: "
                        + rootMessage(failure), NamedTextColor.RED));
            }
            openTree(live, sessions.requirePlayable(playerId));
            reconcile(live);
        }));
    }

    private void persistDisplaced(Player player, SessionToken token, ItemStack displaced) {
        UUID playerId = player.getUniqueId();
        if (!relocations.add(playerId)) return;
        byte[] payload = displaced.serializeAsBytes();
        String hash = sha256(payload);
        UUID deliveryId = UUID.nameUUIDFromBytes(
                (playerId + "|slot9|" + hash).getBytes(StandardCharsets.UTF_8));
        PendingSlotItem pending = new PendingSlotItem(
                playerId, deliveryId, payload, hash, Instant.now());
        scheduler.async(() -> pendingSlots.store(pending)).whenComplete((stored, failure) ->
                scheduler.sync(() -> {
                    relocations.remove(playerId);
                    if (!sessions.isLive(token)) return;
                    Player live = plugin.getServer().getPlayer(playerId);
                    if (live == null) return;
                    if (failure != null) {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                "Cannot preserve occupied class-compass slot for " + playerId,
                                failure);
                        live.sendMessage(Component.text(
                                "Slot 9 is locked: no safe item relocation is available.",
                                NamedTextColor.RED));
                        return;
                    }
                    ItemStack current = live.getInventory().getItem(RESERVED_SLOT);
                    if (current != null && MessageDigest.isEqual(
                            current.serializeAsBytes(), payload)) {
                        live.getInventory().setItem(RESERVED_SLOT, null);
                        reconcile(live);
                        live.sendMessage(Component.text(
                                "Your previous slot-9 item was moved to pending delivery.",
                                NamedTextColor.YELLOW));
                    }
                }));
    }

    private ItemStack compass(Player player, PlayerSession session) {
        boolean selected = session.profile().classId().isPresent();
        ContentId type = selected ? TREE_TOKEN : SELECTION_TOKEN;
        UUID tokenId = tokenId(player.getUniqueId(), session.token(), type);
        ItemStack stack = new ItemStack(Material.COMPASS);
        var meta = stack.getItemMeta();
        if (selected) {
            CharacterClassDefinition definition = content.snapshot().characterClasses()
                    .get(session.profile().classId().orElseThrow());
            var progress = progression.progress(player.getUniqueId());
            meta.displayName(Component.text(definition.displayName() + " Skill Tree",
                    NamedTextColor.GOLD));
            meta.lore(List.of(Component.text("Class Level: " + progress.level(), NamedTextColor.GRAY),
                    Component.text("Skill Points: " + progress.unspentSkillPoints(), NamedTextColor.GRAY),
                    Component.text("Right-click to open", NamedTextColor.YELLOW)));
        } else {
            meta.displayName(Component.text("Choose Your Class", NamedTextColor.GOLD));
            meta.lore(List.of(Component.text("Select Warrior, Mage, or Rogue", NamedTextColor.GRAY),
                    Component.text("Your choice is permanent", NamedTextColor.RED),
                    Component.text("Right-click to open", NamedTextColor.YELLOW)));
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(tokenTypeKey, PersistentDataType.STRING, type.toString());
        pdc.set(tokenVersionKey, PersistentDataType.LONG, TOKEN_VERSION);
        pdc.set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        pdc.set(sessionKey, PersistentDataType.LONG, session.token().sequence());
        pdc.set(signatureKey, PersistentDataType.STRING,
                signer.sign(tokenId, type, player.getUniqueId()));
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean validToken(Player player, ItemStack stack) {
        if (!hasToken(stack)) return false;
        try {
            PlayerSession session = sessions.requirePlayable(player.getUniqueId());
            PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
            ContentId type = ContentId.parse(pdc.get(tokenTypeKey, PersistentDataType.STRING));
            ContentId expected = session.profile().classId().isPresent() ? TREE_TOKEN : SELECTION_TOKEN;
            UUID owner = UUID.fromString(pdc.get(ownerKey, PersistentDataType.STRING));
            Long version = pdc.get(tokenVersionKey, PersistentDataType.LONG);
            Long sequence = pdc.get(sessionKey, PersistentDataType.LONG);
            String signature = pdc.get(signatureKey, PersistentDataType.STRING);
            return owner.equals(player.getUniqueId()) && type.equals(expected)
                    && version != null && version == TOKEN_VERSION
                    && sequence != null && sequence == session.token().sequence()
                    && signer.verify(tokenId(owner, session.token(), type), type, owner, signature);
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private boolean hasToken(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(tokenTypeKey);
    }

    private PlayerSession requireLive(Player player, SessionToken expected) {
        PlayerSession session = sessions.session(player.getUniqueId()).orElse(null);
        if (session == null || !session.playable()
                || (expected != null && !session.token().equals(expected))) {
            player.closeInventory();
            player.sendActionBar(Component.text("This class menu is stale.", NamedTextColor.RED));
            return null;
        }
        return session;
    }

    private void putClass(Inventory menu, int slot, ContentId classId, Material material,
                          Map<Integer, ContentId> bySlot) {
        CharacterClassDefinition definition = content.snapshot().characterClasses().get(classId);
        menu.setItem(slot, icon(material, definition.displayName(), NamedTextColor.GOLD,
                List.of("Roles: " + definition.roles(),
                        "Resource: " + definition.primaryResource(),
                        "Starter: " + definition.starterGrantPlan().weaponId(),
                        "Skills: " + definition.classSkillIds(),
                        "Ultimate: " + definition.ultimateSkillId(),
                        "Click to preview; choice is permanent.")));
        bySlot.put(slot, classId);
    }

    private Inventory create(MenuHolder holder, int size, String title) {
        Inventory inventory = plugin.getServer().createInventory(
                holder, size, Component.text(title));
        holder.inventory = inventory;
        return inventory;
    }

    private static ItemStack icon(Material material, String name, NamedTextColor color,
                                  List<String> lore) {
        ItemStack stack = new ItemStack(material);
        var meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color));
        List<Component> lines = new ArrayList<>();
        lore.forEach(line -> lines.add(Component.text(line, NamedTextColor.GRAY)));
        meta.lore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    private static int freeStorageSlot(Player player) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            if (slot == RESERVED_SLOT) continue;
            ItemStack item = storage[slot];
            if (item == null || item.getType().isAir()) return slot;
        }
        return -1;
    }

    private static UUID tokenId(UUID playerId, SessionToken token, ContentId type) {
        return UUID.nameUUIDFromBytes((playerId + "|" + token.sequence() + "|" + type)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private enum MenuType { SELECTION, CONFIRM, TREE }

    private static final class MenuHolder implements InventoryHolder {
        private final MenuType type;
        private final UUID nonce;
        private final UUID ownerId;
        private final SessionToken token;
        private final Instant openedAt;
        private final long profileRevision;
        private final long contentRevision;
        private final ContentId classId;
        private final int treeRevision;
        private Map<Integer, ContentId> classBySlot;
        private Map<Integer, ContentId> nodeBySlot;
        private Inventory inventory;

        private MenuHolder(MenuType type, UUID nonce, SessionToken token,
                           long profileRevision, long contentRevision,
                           ContentId classId, int treeRevision,
                           Map<Integer, ContentId> classBySlot,
                           Map<Integer, ContentId> nodeBySlot) {
            this.type = type;
            this.nonce = nonce;
            this.token = token;
            this.ownerId = token.playerId();
            this.openedAt = Instant.now();
            this.profileRevision = profileRevision;
            this.contentRevision = contentRevision;
            this.classId = classId;
            this.treeRevision = treeRevision;
            this.classBySlot = classBySlot;
            this.nodeBySlot = nodeBySlot;
        }

        @Override public Inventory getInventory() { return inventory; }
    }

    private record SelectionCompletion(
            com.branz.mmorpg.api.character.CharacterClassSelectionResult result,
            StarterKitDeliveryService.Result delivery) {}
}
