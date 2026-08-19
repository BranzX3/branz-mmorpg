package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.items.projection.ObservedProjection;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Owns F-key physical shield equip/unequip while DB OFF_HAND remains authoritative. */
final class PhysicalOffHandInteractionController implements Listener {
    private static final int OFF_HAND_LOGICAL_SLOT = 101;

    private final JavaPlugin plugin;
    private final CharacterSessionController characters;
    private final BukkitItemProjectionCodec codec;
    private final ItemEngine items;
    private final PhysicalOffHandItemMoveService moves;
    private final String contentVersion;

    PhysicalOffHandInteractionController(
            JavaPlugin plugin,
            CharacterSessionController characters,
            BukkitItemProjectionCodec codec,
            ItemEngine items,
            PhysicalOffHandItemMoveService moves,
            String contentVersion) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.items = Objects.requireNonNull(items, "items");
        this.moves = Objects.requireNonNull(moves, "moves");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPhysicalShieldUseTrace(PlayerInteractEvent event) {
        if (!Boolean.getBoolean("mmo.physical-shield-d13-acceptance")
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                        && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        plugin.getLogger()
                .info(
                        "PHYSICAL_AUTHORITY_SHIELD_D46_USE_SERVER player="
                                + event.getPlayer().getName()
                                + " hand="
                                + event.getHand()
                                + " action="
                                + event.getAction()
                                + " material="
                                + event.getMaterial());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysicalStatus(PlayerCommandPreprocessEvent event) {
        if (!event.getMessage().trim().equalsIgnoreCase("/mmo physical status")) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!devToolsAllowed(player)) {
            player.sendMessage(
                    Component.text(
                            "Physical authority inspection is disabled for this environment/account.",
                            NamedTextColor.RED));
            return;
        }
        LoadedCharacterSession active = characters.active(player).orElse(null);
        if (active == null || !characters.ready(player)) {
            player.sendMessage(
                    Component.text("Character session is not ready.", NamedTextColor.RED));
            return;
        }
        if (characters.valueMutationInFlight(player)) {
            player.sendMessage(
                    Component.text(
                            "Authoritative value transaction is still in progress; retry after commit.",
                            NamedTextColor.YELLOW));
            return;
        }
        PersistentCharacterSnapshot snapshot = active.snapshot();
        player.sendMessage(
                Component.text(
                        "Physical Authority: character="
                                + active.characterId().value()
                                + " | items="
                                + snapshot.itemRecords().size()
                                + " | lots="
                                + snapshot.lotRecords().size(),
                        NamedTextColor.GOLD));
        snapshot.itemRecords().stream()
                .sorted(
                        java.util.Comparator.comparing(
                                record -> record.itemId().value().toString()))
                .forEach(
                        record -> {
                            java.util.OptionalInt baseMaximumDurability =
                                    items.find(record.definitionId())
                                            .map(ItemDefinition::baseMaxDurability)
                                            .orElse(java.util.OptionalInt.empty());
                            player.sendMessage(
                                    Component.text(
                                            PhysicalAuthorityInspectionFormatter.item(
                                                    record, baseMaximumDurability),
                                            NamedTextColor.AQUA));
                        });
        snapshot.lotRecords().stream()
                .sorted(java.util.Comparator.comparing(record -> record.lotId().value().toString()))
                .forEach(
                        record ->
                                player.sendMessage(
                                        Component.text(
                                                PhysicalAuthorityInspectionFormatter.lot(record),
                                                NamedTextColor.LIGHT_PURPLE)));
        if (snapshot.itemRecords().isEmpty() && snapshot.lotRecords().isEmpty()) {
            player.sendMessage(
                    Component.text("No persisted item or lot records.", NamedTextColor.GRAY));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!characters.ready(player)) {
            return;
        }
        ResolvedPhysicalItem selected = characters.selectedPhysicalItem(player).orElse(null);
        if (selected != null
                && selected.definition().weaponProfile().stream()
                        .anyMatch(profile -> profile.family().equals("STAFF"))) {
            return;
        }
        LoadedCharacterSession active = characters.active(player).orElse(null);
        if (active == null) {
            return;
        }
        ItemId committedOffHandId =
                active.snapshot().equipment().item(EquipmentSlot.OFF_HAND).orElse(null);
        boolean touchesMmo =
                selected != null
                        || committedOffHandId != null
                        || codec.hasProjectionMarker(player.getInventory().getItemInOffHand());
        if (!touchesMmo) {
            return;
        }
        event.setCancelled(true);

        String validationFailure =
                validatePhysicalState(player, active, selected, committedOffHandId);
        if (validationFailure != null) {
            reconcile(player, validationFailure);
            return;
        }
        Optional<ItemId> selectedShield =
                selected == null ? Optional.empty() : Optional.of(selected.record().itemId());
        LoadedCharacterSession session = characters.beginExternalValueMutation(player).orElse(null);
        if (session == null) {
            player.sendActionBar(
                    Component.text(
                            "Another durable character transaction is still in progress.",
                            NamedTextColor.RED));
            return;
        }
        int selectedSlot = player.getInventory().getHeldItemSlot();
        UUID operationId = UUID.randomUUID();
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<LoadedCharacterSession, CharacterSessionErrorCode> result =
                                    moves.swap(
                                            session,
                                            selectedSlot,
                                            selectedShield,
                                            operationId,
                                            contentVersion);
                            String originalFailure = null;
                            if (result
                                    instanceof
                                    Result.Failure<
                                                    LoadedCharacterSession,
                                                    CharacterSessionErrorCode>
                                            failure) {
                                originalFailure = failure.error().code() + ": " + failure.detail();
                                result = characters.reloadExternalValueMutation(session);
                            }
                            Result<LoadedCharacterSession, CharacterSessionErrorCode> completed =
                                    result;
                            String failureMessage = originalFailure;
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    finish(
                                                            player,
                                                            session,
                                                            completed,
                                                            failureMessage));
                        });
    }

    private String validatePhysicalState(
            Player player,
            LoadedCharacterSession session,
            ResolvedPhysicalItem selected,
            ItemId committedOffHandId) {
        ItemStack selectedStack =
                player.getInventory().getItem(player.getInventory().getHeldItemSlot());
        if (selected == null && selectedStack != null && !selectedStack.getType().isAir()) {
            return "Selected hotbar slot is not an eligible MMO shield.";
        }
        if (selected != null && selected.definition().shieldProfile().isEmpty()) {
            return "Only an authored shield may enter the physical off-hand slot.";
        }

        ItemStack actualOffHand = player.getInventory().getItemInOffHand();
        if (committedOffHandId == null) {
            return actualOffHand == null || actualOffHand.getType().isAir()
                    ? null
                    : "Physical off-hand contains an unmanaged or stale item.";
        }
        ItemLocationRecord record =
                session.snapshot().itemRecords().stream()
                        .filter(candidate -> candidate.itemId().equals(committedOffHandId))
                        .findFirst()
                        .orElse(null);
        if (record == null
                || record.ownerCharacterId().filter(session.characterId()::equals).isEmpty()
                || !record.location()
                        .equals(ValueLocation.nativeEquipped(EquipmentSlot.OFF_HAND.name()))) {
            return "Committed OFF_HAND item does not match authoritative item truth.";
        }
        ItemDefinition definition = items.find(record.definitionId()).orElse(null);
        if (definition == null || definition.shieldProfile().isEmpty()) {
            return "Committed OFF_HAND item is not an authored shield.";
        }
        if (!codec.hasProjectionMarker(actualOffHand)) {
            return "Physical off-hand projection is missing.";
        }
        ObservedProjection observed =
                codec.decode(actualOffHand, OFF_HAND_LOGICAL_SLOT).orElse(null);
        if (observed == null
                || !observed.valueId().equals(record.itemId().value())
                || !observed.definitionId().equals(record.definitionId())
                || observed.authorityVersion() != record.version()
                || !observed.contentVersion().equals(record.contentVersion())) {
            return "Physical off-hand projection is stale or invalid.";
        }
        return null;
    }

    private void finish(
            Player player,
            LoadedCharacterSession session,
            Result<LoadedCharacterSession, CharacterSessionErrorCode> result,
            String originalFailure) {
        characters.completeExternalValueMutation(
                session,
                result,
                completed -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (completed
                            instanceof
                            Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>
                                    failure) {
                        player.kick(
                                Component.text(
                                        "Off-hand authority could not be reloaded: "
                                                + failure.detail()
                                                + ". Reconnect after database recovery.",
                                        NamedTextColor.RED));
                        return;
                    }
                    if (originalFailure != null) {
                        player.sendActionBar(
                                Component.text(
                                        "Off-hand swap rejected and reconciled: " + originalFailure,
                                        NamedTextColor.RED));
                    }
                });
    }

    private void reconcile(Player player, String detail) {
        LoadedCharacterSession session = characters.beginExternalValueMutation(player).orElse(null);
        if (session == null) {
            player.sendActionBar(Component.text(detail, NamedTextColor.RED));
            return;
        }
        characters.completeExternalValueMutation(
                session,
                Result.success(session),
                completed -> {
                    if (player.isOnline()) {
                        player.sendActionBar(Component.text(detail, NamedTextColor.RED));
                    }
                });
    }

    private boolean devToolsAllowed(Player player) {
        String environment =
                plugin.getConfig()
                        .getString("environment", "LOCAL")
                        .trim()
                        .toUpperCase(java.util.Locale.ROOT);
        boolean environmentAllowed =
                environment.equals("LOCAL")
                        || environment.equals("CONTENT_DEV")
                        || environment.equals("INTEGRATION")
                        || environment.equals("STAGING");
        return environmentAllowed
                && plugin.getConfig().getBoolean("dev-tools.enabled", false)
                && player.hasPermission("branzmmo.dev");
    }
}
