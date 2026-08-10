package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.ItemPayloadUpdate;
import com.branz.mmorpg.persistence.transaction.JdbcValueTransactionService;
import com.branz.mmorpg.persistence.transaction.TransactionErrorCode;
import com.branz.mmorpg.persistence.transaction.TransactionExecution;
import com.branz.mmorpg.persistence.transaction.TransactionRequest;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.util.Objects;
import java.util.UUID;

/** Durable shield-wear commit boundary for one blocked non-PvP impact. */
final class ShieldDurabilityService {
    private static final int DURABILITY_PER_BLOCKED_IMPACT = 1;

    private final DatabaseRuntime database;
    private final CharacterSessionService sessions;

    ShieldDurabilityService(DatabaseRuntime database, CharacterSessionService sessions) {
        this.database = Objects.requireNonNull(database, "database");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> commitBlockedImpact(
            LoadedCharacterSession session,
            ItemId shieldItemId,
            DefinitionId expectedDefinitionId,
            int baseMaximumDurability,
            UUID operationId,
            String contentVersion) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(shieldItemId, "shieldItemId");
        Objects.requireNonNull(expectedDefinitionId, "expectedDefinitionId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        if (baseMaximumDurability < 1) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Shield durability profile must be positive.");
        }

        ItemId equipped = session.snapshot().equipment().item(EquipmentSlot.OFF_HAND).orElse(null);
        ItemLocationRecord shield =
                session.snapshot().itemRecords().stream()
                        .filter(record -> record.itemId().equals(shieldItemId))
                        .findFirst()
                        .orElse(null);
        ValueLocation expectedLocation =
                ValueLocation.nativeEquipped(EquipmentSlot.OFF_HAND.name());
        if (!shieldItemId.equals(equipped)
                || shield == null
                || !shield.definitionId().equals(expectedDefinitionId)
                || !shield.location().equals(expectedLocation)) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Shield wear requires the same authoritative equipped off-hand item.");
        }

        ItemDurability next;
        String replacement;
        try {
            ItemDurability current =
                    ItemDurabilityPayloadCodec.decode(shield.payloadJson(), baseMaximumDurability);
            next = current.spend(DURABILITY_PER_BLOCKED_IMPACT);
            replacement = ItemDurabilityPayloadCodec.encode(shield.payloadJson(), next);
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID, exception.getMessage());
        }

        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(operationId),
                        "shield-block:" + operationId,
                        session.characterId(),
                        session.sessionId(),
                        JdbcValueTransactionService.ITEM_PAYLOAD_UPDATE,
                        "{\"itemId\":\""
                                + shield.itemId().value()
                                + "\",\"itemVersion\":"
                                + shield.version()
                                + ",\"equipmentSlot\":\"OFF_HAND\"}",
                        "{\"currentDurability\":" + next.current() + "}",
                        contentVersion);
        Result<TransactionExecution, TransactionErrorCode> updated =
                database.values()
                        .updateItemPayload(
                                request,
                                new ItemPayloadUpdate(
                                        shield.itemId(),
                                        shield.version(),
                                        shield.ownerCharacterId(),
                                        shield.location(),
                                        shield.payloadJson(),
                                        replacement));
        if (updated instanceof Result.Failure<TransactionExecution, TransactionErrorCode> failure) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    failure.error().code() + ": " + failure.detail());
        }
        return sessions.reload(session);
    }

    ItemDurability authoritativeState(
            LoadedCharacterSession session,
            ItemId shieldItemId,
            DefinitionId expectedDefinitionId,
            int baseMaximumDurability) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(shieldItemId, "shieldItemId");
        Objects.requireNonNull(expectedDefinitionId, "expectedDefinitionId");
        ItemLocationRecord shield =
                session.snapshot().itemRecords().stream()
                        .filter(record -> record.itemId().equals(shieldItemId))
                        .filter(record -> record.definitionId().equals(expectedDefinitionId))
                        .findFirst()
                        .orElseThrow(
                                () -> new IllegalArgumentException("shield item is unavailable"));
        return ItemDurabilityPayloadCodec.decode(shield.payloadJson(), baseMaximumDurability);
    }
}
