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

/** Durable weapon-wear commit boundary for one successful non-PvP attack. */
final class WeaponDurabilityService {
    private final DatabaseRuntime database;
    private final CharacterSessionService sessions;

    WeaponDurabilityService(DatabaseRuntime database, CharacterSessionService sessions) {
        this.database = Objects.requireNonNull(database, "database");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> commitSuccessfulAttack(
            LoadedCharacterSession session,
            ItemId weaponItemId,
            DefinitionId expectedDefinitionId,
            int baseMaximumDurability,
            int durabilityCost,
            DefinitionId sourceMoveId,
            UUID operationId,
            String contentVersion) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(weaponItemId, "weaponItemId");
        Objects.requireNonNull(expectedDefinitionId, "expectedDefinitionId");
        Objects.requireNonNull(sourceMoveId, "sourceMoveId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        if (baseMaximumDurability < 1 || durabilityCost < 1) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Weapon durability profile must be positive.");
        }

        ItemId equipped =
                session.snapshot().equipment().item(EquipmentSlot.MAIN_HAND).orElse(null);
        ItemLocationRecord weapon =
                session.snapshot().itemRecords().stream()
                        .filter(record -> record.itemId().equals(weaponItemId))
                        .findFirst()
                        .orElse(null);
        ValueLocation expectedLocation =
                ValueLocation.nativeEquipped(EquipmentSlot.MAIN_HAND.name());
        if (!weaponItemId.equals(equipped)
                || weapon == null
                || !weapon.definitionId().equals(expectedDefinitionId)
                || !weapon.location().equals(expectedLocation)) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Weapon wear requires the same authoritative equipped main-hand item.");
        }

        WeaponDurability next;
        String replacement;
        try {
            WeaponDurability current =
                    WeaponPayloadCodec.decode(weapon.payloadJson(), baseMaximumDurability);
            next = current.spend(durabilityCost);
            replacement = WeaponPayloadCodec.encode(weapon.payloadJson(), next);
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID, exception.getMessage());
        }

        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(operationId),
                        "weapon-hit:" + operationId,
                        session.characterId(),
                        session.sessionId(),
                        JdbcValueTransactionService.ITEM_PAYLOAD_UPDATE,
                        "{\"itemId\":\""
                                + weapon.itemId().value()
                                + "\",\"itemVersion\":"
                                + weapon.version()
                                + ",\"sourceMoveId\":\""
                                + sourceMoveId.value()
                                + "\"}",
                        "{\"currentDurability\":" + next.current() + "}",
                        contentVersion);
        Result<TransactionExecution, TransactionErrorCode> updated =
                database.values()
                        .updateItemPayload(
                                request,
                                new ItemPayloadUpdate(
                                        weapon.itemId(),
                                        weapon.version(),
                                        weapon.ownerCharacterId(),
                                        weapon.location(),
                                        weapon.payloadJson(),
                                        replacement));
        if (updated instanceof Result.Failure<TransactionExecution, TransactionErrorCode> failure) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    failure.error().code() + ": " + failure.detail());
        }
        return sessions.reload(session);
    }

    WeaponDurability authoritativeState(
            LoadedCharacterSession session,
            ItemId weaponItemId,
            DefinitionId expectedDefinitionId,
            int baseMaximumDurability) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(weaponItemId, "weaponItemId");
        Objects.requireNonNull(expectedDefinitionId, "expectedDefinitionId");
        ItemLocationRecord weapon =
                session.snapshot().itemRecords().stream()
                        .filter(record -> record.itemId().equals(weaponItemId))
                        .filter(record -> record.definitionId().equals(expectedDefinitionId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("weapon item is unavailable"));
        return WeaponPayloadCodec.decode(weapon.payloadJson(), baseMaximumDurability);
    }
}
