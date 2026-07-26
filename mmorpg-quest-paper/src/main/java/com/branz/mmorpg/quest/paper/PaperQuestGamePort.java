package com.branz.mmorpg.quest.paper;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.economy.AdminCurrencyPort;
import com.branz.mmorpg.api.item.InventoryService;
import com.branz.mmorpg.api.mastery.CombatMasteryService;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.social.PartyService;
import com.branz.mmorpg.quest.api.ActionDefinition;
import com.branz.mmorpg.quest.api.PendingQuestOperation;
import com.branz.mmorpg.quest.api.QuestGamePort;
import com.branz.mmorpg.quest.api.QuestUnlockStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperQuestGamePort implements QuestGamePort {
    public interface Presentation {
        ActionResult execute(PendingQuestOperation operation);
    }
    private final JavaPlugin plugin;
    private final InventoryService inventory;
    private final CombatMasteryService mastery;
    private final PartyService parties;
    private final AdminCurrencyPort currency;
    private final QuestUnlockStore unlocks;
    private final Map<UUID, Set<String>> permissions = new ConcurrentHashMap<>();
    private final Map<UUID, ContentId> regions = new ConcurrentHashMap<>();
    private volatile Presentation presentation = operation ->
            new ActionResult(ActionResult.Status.UNAVAILABLE,
                    "quest presentation runtime is not installed");

    public PaperQuestGamePort(JavaPlugin plugin, InventoryService inventory,
                              CombatMasteryService mastery, PartyService parties,
                              AdminCurrencyPort currency, QuestUnlockStore unlocks) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.inventory = java.util.Objects.requireNonNull(inventory, "inventory");
        this.mastery = java.util.Objects.requireNonNull(mastery, "mastery");
        this.parties = java.util.Objects.requireNonNull(parties, "parties");
        this.currency = java.util.Objects.requireNonNull(currency, "currency");
        this.unlocks = java.util.Objects.requireNonNull(unlocks, "unlocks");
    }

    public void presentation(Presentation value) {
        presentation = java.util.Objects.requireNonNull(value, "presentation");
    }

    public void capturePermissions(Player player) {
        permissions.put(player.getUniqueId(), player.getEffectivePermissions().stream()
                .filter(org.bukkit.permissions.PermissionAttachmentInfo::getValue)
                .map(org.bukkit.permissions.PermissionAttachmentInfo::getPermission)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    public void forget(UUID playerId) {
        permissions.remove(playerId);
        regions.remove(playerId);
    }
    public void region(UUID playerId, ContentId regionId) { regions.put(playerId, regionId); }

    @Override public long itemQuantity(UUID playerId, ContentId itemId) {
        var snapshot = inventory.inventory(playerId);
        return snapshot.materials().getOrDefault(itemId, 0L)
                + snapshot.pendingMaterials().getOrDefault(itemId, 0L)
                + snapshot.items().values().stream()
                .filter(item -> item.definitionId().equals(itemId)).count()
                + snapshot.pendingItems().values().stream()
                .filter(item -> item.definitionId().equals(itemId)).count();
    }

    @Override public int masteryLevel(UUID playerId, ContentId masteryId) {
        var value = mastery.profile(playerId).get(masteryId);
        return value == null ? 0 : value.level();
    }

    @Override public int partySize(UUID playerId) {
        return parties.party(playerId).map(value -> value.members().size()).orElse(1);
    }

    @Override public boolean hasPermission(UUID playerId, String permission) {
        return permissions.getOrDefault(playerId, Set.of()).contains(permission);
    }

    @Override public boolean contentUnlocked(UUID playerId, ContentId contentId) {
        return unlocks.unlocked(playerId, contentId);
    }
    @Override public boolean inRegion(UUID playerId, ContentId regionId) {
        return regionId.equals(regions.get(playerId));
    }

    @Override public ActionResult execute(PendingQuestOperation operation) {
        try {
            OperationId operationId = OperationId.of("quest",
                    operation.questId().toString(), operation.playerId(),
                    digest(operation.operationId()).substring(0, 24));
            return switch (operation.operationType()) {
                case GRANT_ITEM -> {
                    ContentId item = ContentId.parse(required(operation, "id"));
                    long amount = amount(operation);
                    var result = inventory.grantMaterial(
                            operation.playerId(), item, amount, operationId);
                    yield applied(result.applied());
                }
                case TAKE_ITEM -> {
                    ContentId item = ContentId.parse(required(operation, "id"));
                    var result = inventory.revokeMaterial(
                            operation.playerId(), item, amount(operation), operationId);
                    yield applied(result.applied());
                }
                case GRANT_CURRENCY -> applied(currency.adjustCredits(
                        operation.playerId(), amount(operation),
                        operationId.value(), "quest reward " + operation.questId()));
                case GRANT_MASTERY_XP -> {
                    ContentId id = ContentId.parse(required(operation, "id"));
                    var result = mastery.grantContribution(
                            operation.playerId(), id, amount(operation), 1, operationId);
                    yield applied(result.applied());
                }
                case UNLOCK_CONTENT -> applied(unlocks.unlock(
                        operation.playerId(),
                        ContentId.parse(required(operation, "id")),
                        operationId.value()));
                case SET_FLAG, REMOVE_FLAG -> new ActionResult(
                        ActionResult.Status.ALREADY_APPLIED, "committed with quest progress");
                default -> presentation.execute(operation);
            };
        } catch (IllegalStateException unavailable) {
            return new ActionResult(ActionResult.Status.UNAVAILABLE, unavailable.getMessage());
        } catch (RuntimeException rejected) {
            return new ActionResult(ActionResult.Status.REJECTED, rejected.getMessage());
        }
    }

    private static ActionResult applied(boolean applied) {
        return new ActionResult(applied
                ? ActionResult.Status.APPLIED : ActionResult.Status.ALREADY_APPLIED, "");
    }
    private static String required(PendingQuestOperation operation, String key) {
        String value = operation.payload().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing action payload " + key);
        }
        return value;
    }
    private static long amount(PendingQuestOperation operation) {
        return Long.parseLong(operation.payload().getOrDefault("amount", "1"));
    }
    private static String digest(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
