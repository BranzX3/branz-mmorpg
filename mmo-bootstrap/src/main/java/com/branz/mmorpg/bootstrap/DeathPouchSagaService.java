package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.transaction.CarriedWalletAdjustment;
import com.branz.mmorpg.persistence.transaction.CarriedWalletAdjustmentExecution;
import com.branz.mmorpg.persistence.transaction.CarriedWalletBalance;
import com.branz.mmorpg.persistence.transaction.CarriedWalletOperation;
import com.branz.mmorpg.persistence.transaction.CarriedWalletOperationKind;
import com.branz.mmorpg.persistence.transaction.CarriedWalletService;
import com.branz.mmorpg.persistence.transaction.DeathPouchRecord;
import com.branz.mmorpg.persistence.transaction.DeathPouchState;
import com.branz.mmorpg.persistence.transaction.JdbcCarriedWalletService;
import com.branz.mmorpg.persistence.transaction.TransactionErrorCode;
import com.branz.mmorpg.persistence.transaction.TransactionRequest;
import com.branz.mmorpg.worldloop.death.DeathPouchDraft;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Synchronous durable Death Pouch saga composition; callers own async scheduling. */
final class DeathPouchSagaService {
    private final DurableDeathPouchStore store;
    private final CarriedWalletService wallet;
    private final String contentVersion;

    DeathPouchSagaService(
            DurableDeathPouchStore store, CarriedWalletService wallet, String contentVersion) {
        this.store = Objects.requireNonNull(store, "store");
        this.wallet = Objects.requireNonNull(wallet, "wallet");
        this.contentVersion = requireText(contentVersion, "contentVersion");
    }

    Result<DeathPouchRecord, TransactionErrorCode> activate(DeathPouchDraft draft) {
        Result<DeathPouchRecord, TransactionErrorCode> pendingResult = store.create(draft);
        if (pendingResult
                instanceof Result.Failure<DeathPouchRecord, TransactionErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        DeathPouchRecord pending = success(pendingResult);
        if (pending.state() != DeathPouchState.PENDING_DEBIT) {
            return Result.success(pending);
        }
        Result<CarriedWalletBalance, TransactionErrorCode> debit =
                adjust(
                        pending.walletDebitOperationId(),
                        pending.ownerCharacterId(),
                        CarriedWalletOperationKind.DEBIT,
                        pending.amount());
        if (debit instanceof Result.Failure<CarriedWalletBalance, TransactionErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        return store.transition(pending, DeathPouchState.ACTIVE);
    }

    Result<DeathPouchRecord, TransactionErrorCode> recover(DeathPouchRecord active) {
        Objects.requireNonNull(active, "active");
        Result<DeathPouchRecord, TransactionErrorCode> recovering =
                store.transition(active, DeathPouchState.RECOVERING);
        if (recovering instanceof Result.Failure<DeathPouchRecord, TransactionErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        DeathPouchRecord checkpoint = success(recovering);
        return checkpoint.state() == DeathPouchState.RECOVERED
                ? Result.success(checkpoint)
                : resumeRecovering(checkpoint);
    }

    Result<DeathPouchRecord, TransactionErrorCode> resume(DeathPouchRecord record, Instant now) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(now, "now");
        return switch (record.state()) {
            case PENDING_DEBIT -> resumePending(record, now);
            case RECOVERING -> resumeRecovering(record);
            default ->
                    Result.failure(
                            TransactionErrorCode.TRANSACTION_INVALID_STATE,
                            "Death Pouch is not in a recoverable saga state.");
        };
    }

    Result<DeathPouchRecord, TransactionErrorCode> expire(DeathPouchRecord record, Instant now) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(now, "now");
        if (record.expiresAt().isAfter(now)) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "Death Pouch has not reached its expiry time.");
        }
        return record.state() == DeathPouchState.PENDING_DEBIT
                ? resumePending(record, now)
                : store.transition(record, DeathPouchState.EXPIRED);
    }

    Result<CarriedWalletBalance, TransactionErrorCode> adjust(
            UUID operationId,
            CharacterId characterId,
            CarriedWalletOperationKind kind,
            long amount) {
        CarriedWalletAdjustment adjustment =
                new CarriedWalletAdjustment(operationId, characterId, kind, amount);
        TransactionRequest request =
                TransactionRequest.system(
                        new TransactionId(operationId),
                        "carried-wallet:" + operationId,
                        JdbcCarriedWalletService.CARRIED_WALLET_ADJUST,
                        "{\"characterId\":\""
                                + characterId.value()
                                + "\",\"kind\":\""
                                + kind
                                + "\",\"amount\":"
                                + amount
                                + "}",
                        "{\"operationId\":\"" + operationId + "\"}",
                        contentVersion);
        Result<CarriedWalletAdjustmentExecution, TransactionErrorCode> adjusted =
                wallet.adjust(request, adjustment);
        if (adjusted
                instanceof
                Result.Failure<CarriedWalletAdjustmentExecution, TransactionErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        return Result.success(success(adjusted).balance());
    }

    private Result<DeathPouchRecord, TransactionErrorCode> resumePending(
            DeathPouchRecord pending, Instant now) {
        if (!pending.expiresAt().isAfter(now)) {
            Result<Optional<CarriedWalletOperation>, TransactionErrorCode> found =
                    wallet.findOperation(pending.walletDebitOperationId());
            if (found
                    instanceof
                    Result.Failure<Optional<CarriedWalletOperation>, TransactionErrorCode>
                            failure) {
                return Result.failure(failure.error(), failure.detail());
            }
            Optional<CarriedWalletOperation> operation = success(found);
            if (operation.isEmpty()) {
                return store.transition(pending, DeathPouchState.EXPIRED);
            }
            CarriedWalletAdjustment debit =
                    new CarriedWalletAdjustment(
                            pending.walletDebitOperationId(),
                            pending.ownerCharacterId(),
                            CarriedWalletOperationKind.DEBIT,
                            pending.amount());
            if (!operation.orElseThrow().matches(debit)) {
                return Result.failure(
                        TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                        "Stored Death Pouch debit differs from its durable intention.");
            }
            Result<DeathPouchRecord, TransactionErrorCode> active =
                    store.transition(pending, DeathPouchState.ACTIVE);
            if (active instanceof Result.Failure<DeathPouchRecord, TransactionErrorCode>) {
                return active;
            }
            DeathPouchRecord checkpoint = success(active);
            return checkpoint.state() == DeathPouchState.EXPIRED
                    ? Result.success(checkpoint)
                    : store.transition(checkpoint, DeathPouchState.EXPIRED);
        }
        Result<CarriedWalletBalance, TransactionErrorCode> debit =
                adjust(
                        pending.walletDebitOperationId(),
                        pending.ownerCharacterId(),
                        CarriedWalletOperationKind.DEBIT,
                        pending.amount());
        if (debit instanceof Result.Failure<CarriedWalletBalance, TransactionErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        return store.transition(pending, DeathPouchState.ACTIVE);
    }

    private Result<DeathPouchRecord, TransactionErrorCode> resumeRecovering(
            DeathPouchRecord recovering) {
        if (recovering.state() == DeathPouchState.RECOVERED) {
            return Result.success(recovering);
        }
        if (recovering.state() != DeathPouchState.RECOVERING) {
            return Result.failure(
                    TransactionErrorCode.TRANSACTION_INVALID_STATE,
                    "Death Pouch is not awaiting recovery credit.");
        }
        Result<CarriedWalletBalance, TransactionErrorCode> credit =
                adjust(
                        recovering.walletCreditOperationId(),
                        recovering.ownerCharacterId(),
                        CarriedWalletOperationKind.CREDIT,
                        recovering.amount());
        if (credit instanceof Result.Failure<CarriedWalletBalance, TransactionErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        return store.transition(recovering, DeathPouchState.RECOVERED);
    }

    private static <T> T success(Result<T, TransactionErrorCode> result) {
        return ((Result.Success<T, TransactionErrorCode>) result).value();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
