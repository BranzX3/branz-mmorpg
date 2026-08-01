package com.branz.mmorpg.lifeskills.progression;

import com.branz.mmorpg.api.result.Result;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

/** Wall-clock Life Focus recovery and exact committed-work spending. */
public final class LifeFocusEngine {
    public static final int MAXIMUM_FOCUS = 100;
    public static final int MAXIMUM_FOCUSED_COST = 5;
    public static final Duration RECOVERY_INTERVAL = Duration.ofMinutes(10);

    public Result<LifeFocusRuntime, LifeFocusErrorCode> recover(
            LifeFocusRuntime runtime, Instant now) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(now, "now");
        if (now.isBefore(runtime.lastRecoveryAt())) {
            return Result.failure(
                    LifeFocusErrorCode.CLOCK_MOVED_BACKWARD,
                    "Life Focus recovery clock cannot move backward.");
        }
        if (runtime.focus() == MAXIMUM_FOCUS) {
            return Result.success(
                    now.equals(runtime.lastRecoveryAt())
                            ? runtime
                            : new LifeFocusRuntime(
                                    runtime.focus(), now, runtime.processedWorkOperations()));
        }
        Duration elapsed = Duration.between(runtime.lastRecoveryAt(), now);
        long intervals = elapsed.dividedBy(RECOVERY_INTERVAL);
        if (intervals == 0) {
            return Result.success(runtime);
        }
        int recovered = (int) Math.min(MAXIMUM_FOCUS - runtime.focus(), intervals);
        int focus = runtime.focus() + recovered;
        Instant recoveryAnchor =
                focus == MAXIMUM_FOCUS
                        ? now
                        : runtime.lastRecoveryAt().plus(RECOVERY_INTERVAL.multipliedBy(intervals));
        return Result.success(
                new LifeFocusRuntime(focus, recoveryAnchor, runtime.processedWorkOperations()));
    }

    public Result<LifeFocusDecision, LifeFocusErrorCode> commitWork(
            LifeFocusRuntime runtime, int focusCost, UUID operationId, Instant now) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(now, "now");
        if (focusCost < 0 || focusCost > MAXIMUM_FOCUSED_COST) {
            return Result.failure(
                    LifeFocusErrorCode.COST_INVALID,
                    "Normal work costs zero Focus; focused work costs one to five.");
        }
        Integer priorCost = runtime.processedWorkOperations().get(operationId);
        if (priorCost != null && priorCost != focusCost) {
            return Result.failure(
                    LifeFocusErrorCode.OPERATION_ID_REUSED,
                    "Life Focus operation was reused with a different cost.");
        }
        Result<LifeFocusRuntime, LifeFocusErrorCode> recoveredResult = recover(runtime, now);
        if (recoveredResult
                instanceof Result.Failure<LifeFocusRuntime, LifeFocusErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        LifeFocusRuntime recovered =
                ((Result.Success<LifeFocusRuntime, LifeFocusErrorCode>) recoveredResult).value();
        int recoveredAmount = recovered.focus() - runtime.focus();
        if (priorCost != null) {
            return Result.success(
                    new LifeFocusDecision(recovered, recoveredAmount, 0, focusCost > 0, true));
        }
        if (recovered.focus() < focusCost) {
            return Result.failure(
                    LifeFocusErrorCode.FOCUS_INSUFFICIENT,
                    "Focused work requires more Life Focus than is available.");
        }
        HashMap<UUID, Integer> operations = new HashMap<>(recovered.processedWorkOperations());
        operations.put(operationId, focusCost);
        LifeFocusRuntime next =
                new LifeFocusRuntime(
                        recovered.focus() - focusCost, recovered.lastRecoveryAt(), operations);
        return Result.success(
                new LifeFocusDecision(next, recoveredAmount, focusCost, focusCost > 0, false));
    }
}
