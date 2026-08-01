package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.resource.ExpeditionFlaskEngine;
import com.branz.mmorpg.combat.resource.FlaskConsumption;
import com.branz.mmorpg.combat.resource.FlaskDose;
import com.branz.mmorpg.combat.resource.FlaskErrorCode;
import com.branz.mmorpg.items.consumable.ConsumableUseProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper adapter for the character-owned, non-transferable Expedition Flask representation. */
final class FlaskHotbarController implements Listener {
    private static final int FIRST_GAMEPLAY_SLOT = 0;
    private static final int LAST_GAMEPLAY_SLOT = 7;
    private static final float WALK_SPEED_MULTIPLIER = 0.60F;

    private final JavaPlugin plugin;
    private final CharacterSessionController characters;
    private final CombatSessionController combat;
    private final String contentVersion;
    private final NamespacedKey markerKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey doseKey;
    private final DurableFlaskUseEngine uses = new DurableFlaskUseEngine();
    private final ExpeditionFlaskEngine flasks = new ExpeditionFlaskEngine();
    private final Map<UUID, ActiveUse> active = new HashMap<>();
    private final Map<UUID, FlaskDose> selectedDoses = new HashMap<>();
    private final Map<UUID, Integer> previousCombatSlots = new HashMap<>();
    private int tickTaskId = -1;

    FlaskHotbarController(
            JavaPlugin plugin,
            CharacterSessionController characters,
            CombatSessionController combat,
            String contentVersion) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        markerKey = new NamespacedKey(plugin, "expedition_flask");
        ownerKey = new NamespacedKey(plugin, "expedition_flask_owner");
        doseKey = new NamespacedKey(plugin, "expedition_flask_dose");
    }

    void start() {
        tickTaskId =
                plugin.getServer()
                        .getScheduler()
                        .scheduleSyncRepeatingTask(plugin, this::tickAll, 1L, 1L);
    }

    void shutdown() {
        if (tickTaskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(tickTaskId);
            tickTaskId = -1;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ActiveUse use = active.remove(player.getUniqueId());
            if (use != null) {
                combat.endFlaskUse(player, use.state.operationId());
                restoreWalkSpeed(player, use);
            }
            removeRepresentations(player);
        }
        selectedDoses.clear();
        previousCombatSlots.clear();
    }

    void onCharacterReady(Player player) {
        reconcile(player);
    }

    void interruptFromCombat(Player player, String reason) {
        interrupt(player, reason);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                || !event.getAction().isRightClick()
                || !isOwnedFlask(event.getItem(), event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            if (active.containsKey(player.getUniqueId())) {
                player.sendActionBar(
                        Component.text("Flask selection is action-locked.", NamedTextColor.RED));
                return;
            }
            cycleDose(player);
            return;
        }
        beginUse(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (isOwnedFlask(event.getItem(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldSlot(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack previous = player.getInventory().getItem(event.getPreviousSlot());
        ItemStack next = player.getInventory().getItem(event.getNewSlot());
        if (isOwnedFlask(next, player) && !isOwnedFlask(previous, player)) {
            previousCombatSlots.put(player.getUniqueId(), event.getPreviousSlot());
        }
        ActiveUse use = active.get(player.getUniqueId());
        if (use != null && event.getNewSlot() != use.flaskSlot) {
            use.userSelectedAnotherSlot = true;
            interrupt(player, "HOTBAR_SELECTION");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (!event.isSprinting() || !active.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        interrupt(event.getPlayer(), "SPRINT");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent
                || !active.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getTo().getY() > event.getFrom().getY() + 0.01
                && event.getPlayer().getVelocity().getY() > 0.1) {
            interrupt(event.getPlayer(), "JUMP");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        interrupt(event.getPlayer(), "TELEPORT");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isFlask);
        interrupt(event.getEntity(), "DEATH");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> reconcile(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ActiveUse use = active.remove(player.getUniqueId());
        if (use != null) {
            combat.endFlaskUse(player, use.state.operationId());
            restoreWalkSpeed(player, use);
        }
        selectedDoses.remove(player.getUniqueId());
        previousCombatSlots.remove(player.getUniqueId());
        removeRepresentations(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack hotbar =
                event.getHotbarButton() >= 0
                        ? player.getInventory().getItem(event.getHotbarButton())
                        : null;
        boolean representationInvolved =
                isFlask(event.getCurrentItem()) || isFlask(event.getCursor()) || isFlask(hotbar);
        if (!representationInvolved) {
            return;
        }
        event.setCancelled(true);
        scheduleReconcile(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isFlask(event.getOldCursor())) {
            return;
        }
        event.setCancelled(true);
        scheduleReconcile(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isFlask(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isFlask(event.getMainHandItem()) || isFlask(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    private void beginUse(Player player) {
        if (active.containsKey(player.getUniqueId())) {
            player.sendActionBar(
                    Component.text("Flask use is already active.", NamedTextColor.RED));
            return;
        }
        LoadedCharacterSession character = characters.active(player).orElse(null);
        if (character == null || !characters.ready(player)) {
            player.sendActionBar(
                    Component.text("Character session is not ready.", NamedTextColor.RED));
            return;
        }
        FlaskDose dose = selectedDose(player, character.snapshot().expeditionState());
        if (!flasks.consume(character.snapshot().expeditionState().flaskState(), dose)
                .isSuccess()) {
            player.sendActionBar(
                    Component.text(dose + " Flask charge is empty.", NamedTextColor.RED));
            return;
        }
        UUID operationId = UUID.randomUUID();
        if (!combat.beginFlaskUse(player, operationId)) {
            player.sendActionBar(
                    Component.text(
                            "Wait for the weapon to sheathe and finish the active action.",
                            NamedTextColor.RED));
            return;
        }
        int flaskSlot = player.getInventory().getHeldItemSlot();
        int previousSlot = previousCombatSlots.getOrDefault(player.getUniqueId(), flaskSlot);
        ActiveUse use =
                new ActiveUse(
                        uses.start(operationId, dose, plugin.getServer().getCurrentTick()),
                        flaskSlot,
                        previousSlot,
                        player.getWalkSpeed());
        active.put(player.getUniqueId(), use);
        player.setSprinting(false);
        player.setWalkSpeed(clampWalkSpeed(use.originalWalkSpeed * WALK_SPEED_MULTIPLIER));
        player.sendActionBar(
                Component.text(
                        "FLASK "
                                + dose
                                + " WINDUP "
                                + ConsumableUseProfile.expeditionFlask().commitTick()
                                + "t",
                        NamedTextColor.AQUA));
    }

    private void tickAll() {
        for (Map.Entry<UUID, ActiveUse> entry : List.copyOf(active.entrySet())) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            ActiveUse use = entry.getValue();
            DurableFlaskUseTransition transition =
                    uses.tick(use.state, plugin.getServer().getCurrentTick());
            use.state = transition.state();
            if (transition.commitNow()) {
                beginDurableCommit(player, use);
            } else if (use.state.phase().terminal()) {
                finish(player, use);
            }
        }
    }

    private void beginDurableCommit(Player player, ActiveUse use) {
        combat.markFlaskCommitting(player, use.state.operationId());
        LoadedCharacterSession character = characters.active(player).orElse(null);
        if (character == null) {
            use.state = uses.commitFailed(use.state);
            finish(player, use);
            return;
        }
        PersistentExpeditionState current = character.snapshot().expeditionState();
        Result<FlaskConsumption, FlaskErrorCode> consumed =
                flasks.consume(current.flaskState(), use.state.dose());
        if (consumed instanceof Result.Failure<FlaskConsumption, FlaskErrorCode>) {
            use.state = uses.commitFailed(use.state);
            finish(player, use);
            return;
        }
        FlaskConsumption consumption =
                ((Result.Success<FlaskConsumption, FlaskErrorCode>) consumed).value();
        PersistentExpeditionState desired =
                new PersistentExpeditionState(
                        consumption.state(),
                        current.consumableEffects(),
                        current.ailments(),
                        current.preparedFlaskSnapshot());
        characters.commitExpeditionState(
                player,
                desired,
                use.state.operationId(),
                contentVersion,
                result -> completeDurableCommit(player, use, consumption, result));
    }

    private void completeDurableCommit(
            Player player,
            ActiveUse expected,
            FlaskConsumption consumption,
            Result<LoadedCharacterSession, CharacterSessionErrorCode> result) {
        ActiveUse current = active.get(player.getUniqueId());
        if (current != expected || current.state.phase() != DurableFlaskUsePhase.COMMITTING) {
            return;
        }
        if (result
                instanceof
                Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode> failure) {
            current.state = uses.commitFailed(current.state);
            player.sendActionBar(
                    Component.text(
                            "FLASK COMMIT FAILED " + failure.error().code(), NamedTextColor.RED));
            finish(player, current);
            return;
        }
        boolean applied =
                combat.applyFlaskRestoration(
                        player, current.state.operationId(), consumption.restoration());
        current.state = uses.commitSucceeded(current.state, plugin.getServer().getCurrentTick());
        reconcile(player);
        if (!applied || current.state.phase().terminal()) {
            finish(player, current);
            return;
        }
        player.sendActionBar(
                Component.text(
                        "FLASK "
                                + current.state.dose()
                                + " COMMITTED | recovery="
                                + current.state.timeline().profile().recoveryTicks()
                                + "t",
                        NamedTextColor.GREEN));
    }

    private void interrupt(Player player, String reason) {
        ActiveUse use = active.get(player.getUniqueId());
        if (use == null || use.state.phase().terminal()) {
            return;
        }
        DurableFlaskUseTransition transition =
                uses.interrupt(use.state, plugin.getServer().getCurrentTick());
        use.state = transition.state();
        if (transition.commitNow()) {
            beginDurableCommit(player, use);
        }
        if (use.state.phase().terminal()) {
            player.sendActionBar(
                    Component.text(
                            "FLASK " + use.state.phase() + " " + reason, NamedTextColor.RED));
            finish(player, use);
        }
    }

    private void finish(Player player, ActiveUse expected) {
        if (!active.remove(player.getUniqueId(), expected)) {
            return;
        }
        combat.endFlaskUse(player, expected.state.operationId());
        restoreWalkSpeed(player, expected);
        if (!expected.userSelectedAnotherSlot
                && player.isOnline()
                && player.getInventory().getHeldItemSlot() == expected.flaskSlot
                && expected.previousCombatSlot >= FIRST_GAMEPLAY_SLOT
                && expected.previousCombatSlot <= LAST_GAMEPLAY_SLOT) {
            player.getInventory().setHeldItemSlot(expected.previousCombatSlot);
        }
        if (player.isOnline()) {
            reconcile(player);
            if (expected.state.phase() == DurableFlaskUsePhase.COMPLETE) {
                player.sendActionBar(
                        Component.text("FLASK RECOVERY COMPLETE", NamedTextColor.GREEN));
            }
        }
    }

    private void cycleDose(Player player) {
        PersistentExpeditionState state =
                characters
                        .active(player)
                        .map(session -> session.snapshot().expeditionState())
                        .orElse(null);
        if (state == null) {
            return;
        }
        FlaskDose current = selectedDose(player, state);
        FlaskDose[] order = FlaskDose.values();
        for (int offset = 1; offset <= order.length; offset++) {
            FlaskDose candidate = order[(current.ordinal() + offset) % order.length];
            if (state.flaskState().allocation().maximum(candidate) > 0) {
                selectedDoses.put(player.getUniqueId(), candidate);
                reconcile(player);
                player.sendActionBar(
                        Component.text(
                                "Flask selected "
                                        + candidate
                                        + " | charges="
                                        + state.flaskState().charge(candidate),
                                NamedTextColor.GOLD));
                return;
            }
        }
    }

    private FlaskDose selectedDose(Player player, PersistentExpeditionState state) {
        FlaskDose selected = selectedDoses.get(player.getUniqueId());
        if (selected != null && state.flaskState().allocation().maximum(selected) > 0) {
            return selected;
        }
        FlaskDose fallback =
                java.util.Arrays.stream(FlaskDose.values())
                        .filter(dose -> state.flaskState().allocation().maximum(dose) > 0)
                        .findFirst()
                        .orElse(FlaskDose.HEALING);
        selectedDoses.put(player.getUniqueId(), fallback);
        return fallback;
    }

    private void reconcile(Player player) {
        LoadedCharacterSession character = characters.active(player).orElse(null);
        if (character == null || !characters.ready(player)) {
            return;
        }
        ItemStack[] storage = player.getInventory().getStorageContents();
        List<Integer> owned = new ArrayList<>();
        for (int slot = 0; slot < storage.length; slot++) {
            if (!isFlask(storage[slot])) {
                continue;
            }
            if (!isOwnedFlask(storage[slot], player) || !owned.isEmpty()) {
                storage[slot] = null;
                continue;
            }
            owned.add(slot);
            FlaskDose encoded = encodedDose(storage[slot]);
            if (encoded != null) {
                selectedDoses.putIfAbsent(player.getUniqueId(), encoded);
            }
        }
        int currentSlot = owned.isEmpty() ? -1 : owned.getFirst();
        int target = currentSlot;
        if (currentSlot < FIRST_GAMEPLAY_SLOT || currentSlot > LAST_GAMEPLAY_SLOT) {
            target = firstFreeGameplaySlot(storage);
            if (currentSlot >= 0) {
                storage[currentSlot] = null;
            }
        }
        if (target < 0) {
            target = firstFreeGameplaySlot(storage);
        }
        if (target < 0) {
            player.getInventory().setStorageContents(storage);
            player.sendActionBar(
                    Component.text(
                            "Free a gameplay hotbar slot for the Expedition Flask.",
                            NamedTextColor.YELLOW));
            return;
        }
        FlaskDose selected = selectedDose(player, character.snapshot().expeditionState());
        storage[target] = render(player, character.snapshot().expeditionState(), selected);
        player.getInventory().setStorageContents(storage);
    }

    private ItemStack render(
            Player player, PersistentExpeditionState state, FlaskDose selectedDose) {
        ItemStack item = new ItemStack(Material.HONEY_BOTTLE);
        item.editMeta(
                meta -> {
                    meta.displayName(
                            Component.text(
                                    "Expedition Flask - " + selectedDose, NamedTextColor.GOLD));
                    List<Component> lore = new ArrayList<>();
                    for (FlaskDose dose : FlaskDose.values()) {
                        lore.add(
                                Component.text(
                                        (dose == selectedDose ? "> " : "  ")
                                                + dose
                                                + " "
                                                + state.flaskState().charge(dose)
                                                + "/"
                                                + state.flaskState().allocation().maximum(dose),
                                        dose == selectedDose
                                                ? NamedTextColor.YELLOW
                                                : NamedTextColor.GRAY));
                    }
                    lore.add(
                            Component.text(
                                    "Sneak + right-click: select dose", NamedTextColor.DARK_GRAY));
                    lore.add(
                            Component.text(
                                    "Right-click: use | character-bound",
                                    NamedTextColor.DARK_GREEN));
                    meta.lore(lore);
                    PersistentDataContainer data = meta.getPersistentDataContainer();
                    data.set(markerKey, PersistentDataType.BYTE, (byte) 1);
                    data.set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
                    data.set(doseKey, PersistentDataType.STRING, selectedDose.name());
                });
        return item;
    }

    private FlaskDose encodedDose(ItemStack item) {
        if (!isFlask(item)) {
            return null;
        }
        String value =
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(doseKey, PersistentDataType.STRING);
        try {
            return value == null ? null : FlaskDose.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isOwnedFlask(ItemStack item, Player player) {
        if (!isFlask(item)) {
            return false;
        }
        String owner =
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(ownerKey, PersistentDataType.STRING);
        return player.getUniqueId().toString().equals(owner);
    }

    private boolean isFlask(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        Byte marker =
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(markerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void removeRepresentations(Player player) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            if (isFlask(storage[slot])) {
                storage[slot] = null;
            }
        }
        player.getInventory().setStorageContents(storage);
        if (isFlask(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        }
    }

    private void scheduleReconcile(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> reconcile(player));
    }

    private static int firstFreeGameplaySlot(ItemStack[] storage) {
        for (int slot = FIRST_GAMEPLAY_SLOT; slot <= LAST_GAMEPLAY_SLOT; slot++) {
            ItemStack item = storage[slot];
            if (item == null || item.getType().isAir()) {
                return slot;
            }
        }
        return -1;
    }

    private static float clampWalkSpeed(float value) {
        return Math.max(-1.0F, Math.min(1.0F, value));
    }

    private static void restoreWalkSpeed(Player player, ActiveUse use) {
        player.setWalkSpeed(use.originalWalkSpeed);
    }

    private static final class ActiveUse {
        private DurableFlaskUseState state;
        private final int flaskSlot;
        private final int previousCombatSlot;
        private final float originalWalkSpeed;
        private boolean userSelectedAnotherSlot;

        private ActiveUse(
                DurableFlaskUseState state,
                int flaskSlot,
                int previousCombatSlot,
                float originalWalkSpeed) {
            this.state = Objects.requireNonNull(state, "state");
            this.flaskSlot = flaskSlot;
            this.previousCombatSlot = previousCombatSlot;
            this.originalWalkSpeed = originalWalkSpeed;
        }
    }
}
