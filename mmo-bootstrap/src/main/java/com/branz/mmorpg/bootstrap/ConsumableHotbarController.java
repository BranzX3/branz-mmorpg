package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.state.EngagementState;
import com.branz.mmorpg.items.consumable.ConsumableCategory;
import com.branz.mmorpg.items.consumable.ConsumableDefinitionProfile;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.items.projection.ObservedProjection;
import com.branz.mmorpg.items.projection.ProjectionValueType;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Live signed-lot adapter for authored category consumables. */
final class ConsumableHotbarController implements Listener {
    private static final int EFFECT_CHECKPOINT_INTERVAL_TICKS = 100;
    private final JavaPlugin plugin;
    private final CharacterSessionController characters;
    private final CombatSessionController combat;
    private final ItemEngine items;
    private final BukkitItemProjectionCodec projections;
    private final String contentVersion;
    private final DurableConsumableUseEngine uses = new DurableConsumableUseEngine();
    private final Map<UUID, ActiveUse> active = new HashMap<>();
    private final Map<UUID, Map<ConsumableCategory, RuntimeEffect>> effectDeadlines =
            new HashMap<>();
    private final Set<UUID> effectCheckpointInFlight = new HashSet<>();
    private int tickTaskId = -1;

    ConsumableHotbarController(
            JavaPlugin plugin,
            CharacterSessionController characters,
            CombatSessionController combat,
            ItemEngine items,
            BukkitItemProjectionCodec projections,
            String contentVersion) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.items = Objects.requireNonNull(items, "items");
        this.projections = Objects.requireNonNull(projections, "projections");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
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
        for (Map.Entry<UUID, ActiveUse> entry : List.copyOf(active.entrySet())) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                combat.endConsumableUse(player, entry.getValue().state.operationId());
            }
        }
        active.clear();
        effectDeadlines.clear();
        effectCheckpointInFlight.clear();
    }

    void onCharacterReady(Player player) {
        characters
                .active(player)
                .ifPresent(
                        session ->
                                resetEffectDeadlines(
                                        player.getUniqueId(),
                                        session.snapshot().expeditionState().consumableEffects()));
    }

    void interruptFromCombat(Player player, String reason) {
        interrupt(player, reason);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void observeUseIngress(PlayerInteractEvent event) {
        if (!physicalConsumableUseAcceptanceDebug()
                || event.getHand() != EquipmentSlot.HAND
                || !event.getAction().isRightClick()) {
            return;
        }
        Player player = event.getPlayer();
        plugin.getLogger()
                .info(
                        "PHYSICAL_AUTHORITY_CONSUMABLE_USE_INTERACT_SERVER player="
                                + player.getName()
                                + " action="
                                + event.getAction()
                                + " slot="
                                + player.getInventory().getHeldItemSlot());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        Player player = event.getPlayer();
        if (event.useItemInHand() == org.bukkit.event.Event.Result.DENY) {
            debugUse(player, "REJECTED_SERVER reason=item-use-denied");
            return;
        }
        debugUse(player, "ROUTED_SERVER slot=" + player.getInventory().getHeldItemSlot());
        SelectedConsumable selected = selected(player);
        if (selected == null) {
            debugUse(player, "REJECTED_SERVER reason=selected-null");
            return;
        }
        debugUse(
                player,
                "SELECTED_SERVER lot="
                        + selected.lotId
                        + " definition="
                        + selected.definitionId
                        + " category="
                        + selected.profile.category());
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        begin(player, selected);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVanillaConsume(PlayerItemConsumeEvent event) {
        int slot = event.getPlayer().getInventory().getHeldItemSlot();
        if (decode(event.getPlayer(), event.getItem(), slot) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeldSlot(PlayerItemHeldEvent event) {
        if (active.containsKey(event.getPlayer().getUniqueId())) {
            interrupt(event.getPlayer(), "HOTBAR_SELECTION");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && active.containsKey(player.getUniqueId())) {
            interrupt(player, "INVENTORY_CHANGE");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!(event instanceof PlayerTeleportEvent)
                && event.getTo().getY() > event.getFrom().getY() + 0.05) {
            interrupt(event.getPlayer(), "JUMP");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (event.isSprinting()) {
            interrupt(event.getPlayer(), "SPRINT");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        interrupt(event.getPlayer(), "TELEPORT");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        interrupt(event.getEntity(), "DEATH");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        ActiveUse use = active.remove(event.getPlayer().getUniqueId());
        if (use != null) {
            combat.endConsumableUse(event.getPlayer(), use.state.operationId());
        }
        effectDeadlines.remove(event.getPlayer().getUniqueId());
        effectCheckpointInFlight.remove(event.getPlayer().getUniqueId());
    }

    private void begin(Player player, SelectedConsumable selected) {
        debugUse(player, "BEGIN_ENTER_SERVER lot=" + selected.lotId);
        if (active.containsKey(player.getUniqueId())) {
            debugUse(player, "REJECTED_SERVER reason=active-use");
            player.sendActionBar(
                    Component.text("Consumable use is already active.", NamedTextColor.RED));
            return;
        }
        if (selected.profile.category() == com.branz.mmorpg.items.consumable.ConsumableCategory.MEAL
                && combat.status(player)
                        .map(status -> status.engagementState() == EngagementState.ENGAGED)
                        .orElse(true)) {
            debugUse(player, "REJECTED_SERVER reason=meal-engaged");
            player.sendActionBar(
                    Component.text("Meals require exploration state.", NamedTextColor.RED));
            return;
        }
        boolean replacesRare =
                characters
                        .active(player)
                        .map(
                                session ->
                                        session
                                                .snapshot()
                                                .expeditionState()
                                                .consumableEffects()
                                                .stream()
                                                .anyMatch(
                                                        effect ->
                                                                effect.category()
                                                                                == selected.profile
                                                                                        .category()
                                                                        && effect.rare()))
                        .orElse(false);
        if (replacesRare && !player.isSneaking()) {
            debugUse(player, "REJECTED_SERVER reason=rare-replace-confirmation");
            player.sendActionBar(
                    Component.text(
                            "Sneak + right-click to confirm replacing the active rare effect.",
                            NamedTextColor.YELLOW));
            return;
        }
        UUID operationId = UUID.randomUUID();
        if (!combat.beginConsumableUse(player, operationId)) {
            debugUse(player, "REJECTED_SERVER reason=combat-busy");
            player.sendActionBar(
                    Component.text(
                            "Wait for the weapon to sheathe and finish the active action.",
                            NamedTextColor.RED));
            return;
        }
        DurableConsumableUseState state =
                uses.start(
                        operationId,
                        selected.definitionId,
                        selected.profile.useProfile(),
                        plugin.getServer().getCurrentTick());
        active.put(
                player.getUniqueId(),
                new ActiveUse(
                        state,
                        selected.lotId,
                        selected.profile,
                        replacesRare && player.isSneaking()));
        player.setSprinting(false);
        debugUse(
                player,
                "BEGIN_SERVER lot="
                        + selected.lotId
                        + " operation="
                        + operationId
                        + " category="
                        + selected.profile.category());
        sendTimelineActionBar(
                player,
                Component.text(
                        "CONSUMABLE "
                                + selected.profile.category()
                                + " WINDUP "
                                + selected.profile.useProfile().commitTick()
                                + "t",
                        NamedTextColor.AQUA));
    }

    private void tickAll() {
        long currentTick = plugin.getServer().getCurrentTick();
        for (Map.Entry<UUID, ActiveUse> entry : List.copyOf(active.entrySet())) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            ActiveUse use = entry.getValue();
            DurableConsumableUseTransition transition = uses.tick(use.state, currentTick);
            use.state = transition.state();
            if (transition.commitNow()) {
                beginCommit(player, use);
            } else if (use.state.phase().terminal()) {
                finish(player, use);
            }
        }
        if (currentTick % EFFECT_CHECKPOINT_INTERVAL_TICKS == 0) {
            checkpointEffects(currentTick);
        }
    }

    private void beginCommit(Player player, ActiveUse use) {
        combat.markConsumableCommitting(player, use.state.operationId());
        characters.consumeAndApplyEffect(
                player,
                use.lotId,
                use.state.definitionId(),
                use.profile,
                use.replacementConfirmed,
                use.state.operationId(),
                contentVersion,
                result -> completeCommit(player, use, result));
    }

    private void completeCommit(
            Player player,
            ActiveUse expected,
            Result<ConsumableUseCommitResult, CharacterSessionErrorCode> result) {
        ActiveUse current = active.get(player.getUniqueId());
        if (current != expected || current.state.phase() != DurableFlaskUsePhase.COMMITTING) {
            return;
        }
        if (result
                instanceof
                Result.Failure<ConsumableUseCommitResult, CharacterSessionErrorCode> failure) {
            current.state = uses.commitFailed(current.state);
            player.sendActionBar(
                    Component.text(
                            "CONSUMABLE COMMIT FAILED " + failure.error().code(),
                            NamedTextColor.RED));
            finish(player, current);
            return;
        }
        ConsumableUseCommitResult committed =
                ((Result.Success<ConsumableUseCommitResult, CharacterSessionErrorCode>) result)
                        .value();
        setEffectDeadline(player.getUniqueId(), committed.effect());
        current.state = uses.commitSucceeded(current.state, plugin.getServer().getCurrentTick());
        if (current.state.phase().terminal()) {
            finish(player, current);
            return;
        }
        sendTimelineActionBar(
                player,
                Component.text(
                        "CONSUMABLE "
                                + committed.effect().category()
                                + " COMMITTED | effect="
                                + committed.effect().remainingTicks()
                                + "t",
                        NamedTextColor.GREEN));
    }

    private void interrupt(Player player, String reason) {
        ActiveUse use = active.get(player.getUniqueId());
        if (use == null || use.state.phase().terminal()) {
            return;
        }
        DurableConsumableUseTransition transition =
                uses.interrupt(use.state, plugin.getServer().getCurrentTick());
        use.state = transition.state();
        if (transition.commitNow()) {
            beginCommit(player, use);
        }
        if (use.state.phase().terminal()) {
            player.sendActionBar(
                    Component.text(
                            "CONSUMABLE " + use.state.phase() + " " + reason, NamedTextColor.RED));
            finish(player, use);
        }
    }

    private void finish(Player player, ActiveUse expected) {
        if (!active.remove(player.getUniqueId(), expected)) {
            return;
        }
        combat.endConsumableUse(player, expected.state.operationId());
        if (expected.state.phase() == DurableFlaskUsePhase.COMPLETE) {
            sendTimelineActionBar(
                    player, Component.text("CONSUMABLE RECOVERY COMPLETE", NamedTextColor.GREEN));
        }
    }

    private SelectedConsumable selected(Player player) {
        int slot = player.getInventory().getHeldItemSlot();
        return decode(player, player.getInventory().getItem(slot), slot);
    }

    private void checkpointEffects(long currentTick) {
        for (Map.Entry<UUID, Map<ConsumableCategory, RuntimeEffect>> entry :
                List.copyOf(effectDeadlines.entrySet())) {
            UUID playerId = entry.getKey();
            Player player = plugin.getServer().getPlayer(playerId);
            LoadedCharacterSession character =
                    player == null ? null : characters.active(player).orElse(null);
            if (player == null
                    || !player.isOnline()
                    || character == null
                    || active.containsKey(playerId)
                    || !effectCheckpointInFlight.add(playerId)) {
                continue;
            }
            PersistentExpeditionState current = character.snapshot().expeditionState();
            entry.getValue()
                    .entrySet()
                    .removeIf(
                            runtime ->
                                    current.consumableEffects().stream()
                                            .noneMatch(
                                                    effect ->
                                                            effect.category() == runtime.getKey()
                                                                    && effect.definitionId()
                                                                            .equals(
                                                                                    runtime
                                                                                            .getValue()
                                                                                            .definitionId)));
            for (PersistentConsumableEffect effect : current.consumableEffects()) {
                entry.getValue()
                        .computeIfAbsent(
                                effect.category(),
                                ignored ->
                                        new RuntimeEffect(
                                                effect.definitionId(),
                                                effect.category(),
                                                Math.addExact(currentTick, effect.remainingTicks()),
                                                effect.rare()));
            }
            List<PersistentConsumableEffect> remaining =
                    entry.getValue().values().stream()
                            .map(
                                    effect -> {
                                        long ticks = effect.deadlineTick - currentTick;
                                        return ticks < 1
                                                ? null
                                                : new PersistentConsumableEffect(
                                                        effect.definitionId,
                                                        effect.category,
                                                        Math.toIntExact(ticks),
                                                        effect.rare);
                                    })
                            .filter(Objects::nonNull)
                            .sorted(
                                    java.util.Comparator.comparing(
                                            effect -> effect.category().name()))
                            .toList();
            PersistentExpeditionState desired =
                    new PersistentExpeditionState(
                            current.flaskState(),
                            remaining,
                            current.ailments(),
                            current.preparedFlaskSnapshot());
            characters.commitExpeditionState(
                    player,
                    desired,
                    UUID.randomUUID(),
                    contentVersion,
                    result -> {
                        effectCheckpointInFlight.remove(playerId);
                        if (!(result
                                instanceof
                                Result.Success<
                                        LoadedCharacterSession, CharacterSessionErrorCode>)) {
                            return;
                        }
                        entry.getValue()
                                .entrySet()
                                .removeIf(value -> value.getValue().deadlineTick <= currentTick);
                        if (entry.getValue().isEmpty()) {
                            effectDeadlines.remove(playerId);
                            player.sendActionBar(
                                    Component.text(
                                            "Consumable effects expired.", NamedTextColor.GRAY));
                        }
                    });
        }
    }

    private void resetEffectDeadlines(UUID playerId, List<PersistentConsumableEffect> effects) {
        long currentTick = plugin.getServer().getCurrentTick();
        EnumMap<ConsumableCategory, RuntimeEffect> deadlines =
                new EnumMap<>(ConsumableCategory.class);
        for (PersistentConsumableEffect effect : effects) {
            deadlines.put(
                    effect.category(),
                    new RuntimeEffect(
                            effect.definitionId(),
                            effect.category(),
                            Math.addExact(currentTick, effect.remainingTicks()),
                            effect.rare()));
        }
        if (deadlines.isEmpty()) {
            effectDeadlines.remove(playerId);
        } else {
            effectDeadlines.put(playerId, deadlines);
        }
    }

    private void setEffectDeadline(UUID playerId, PersistentConsumableEffect effect) {
        effectDeadlines
                .computeIfAbsent(playerId, ignored -> new EnumMap<>(ConsumableCategory.class))
                .put(
                        effect.category(),
                        new RuntimeEffect(
                                effect.definitionId(),
                                effect.category(),
                                Math.addExact(
                                        plugin.getServer().getCurrentTick(),
                                        effect.remainingTicks()),
                                effect.rare()));
    }

    private SelectedConsumable decode(Player player, ItemStack item, int slot) {
        ObservedProjection observed = projections.decode(item, slot).orElse(null);
        LoadedCharacterSession session = characters.active(player).orElse(null);
        if (observed == null
                || session == null
                || observed.valueType() != ProjectionValueType.STACKABLE_LOT) {
            return null;
        }
        Result<
                        com.branz.mmorpg.persistence.transaction.LotLocationRecord,
                        PhysicalLotResolutionErrorCode>
                authoritative =
                        PhysicalLotAuthority.resolve(
                                session.characterId(), slot, observed, session.snapshot());
        if (!(authoritative
                instanceof
                Result.Success<
                                com.branz.mmorpg.persistence.transaction.LotLocationRecord,
                                PhysicalLotResolutionErrorCode>
                        success)) {
            return null;
        }
        com.branz.mmorpg.persistence.transaction.LotLocationRecord lot = success.value();
        ItemDefinition definition = items.find(lot.definitionId()).orElse(null);
        ConsumableDefinitionProfile profile =
                definition == null ? null : definition.consumableProfile().orElse(null);
        if (profile == null) {
            return null;
        }
        return new SelectedConsumable(lot.lotId(), lot.definitionId(), profile);
    }

    private static boolean physicalConsumableUseAcceptanceDebug() {
        return Boolean.getBoolean("mmo.physical-consumable-lot-acceptance")
                || Boolean.getBoolean("mmo.physical-consumable-use-acceptance");
    }

    private void sendTimelineActionBar(Player player, Component message) {
        player.sendActionBar(message);
        if (physicalConsumableUseAcceptanceDebug()) {
            player.sendMessage(message);
        }
    }

    private void debugUse(Player player, String detail) {
        if (physicalConsumableUseAcceptanceDebug()) {
            plugin.getLogger()
                    .info(
                            "PHYSICAL_AUTHORITY_CONSUMABLE_USE_"
                                    + detail
                                    + " player="
                                    + player.getName());
        }
    }

    private record SelectedConsumable(
            LotId lotId, DefinitionId definitionId, ConsumableDefinitionProfile profile) {}

    private record RuntimeEffect(
            DefinitionId definitionId,
            ConsumableCategory category,
            long deadlineTick,
            boolean rare) {}

    private static final class ActiveUse {
        private DurableConsumableUseState state;
        private final LotId lotId;
        private final ConsumableDefinitionProfile profile;
        private final boolean replacementConfirmed;

        private ActiveUse(
                DurableConsumableUseState state,
                LotId lotId,
                ConsumableDefinitionProfile profile,
                boolean replacementConfirmed) {
            this.state = Objects.requireNonNull(state, "state");
            this.lotId = Objects.requireNonNull(lotId, "lotId");
            this.profile = Objects.requireNonNull(profile, "profile");
            this.replacementConfirmed = replacementConfirmed;
        }
    }
}
