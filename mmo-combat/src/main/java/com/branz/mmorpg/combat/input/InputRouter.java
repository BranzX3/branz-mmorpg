package com.branz.mmorpg.combat.input;

import com.branz.mmorpg.api.result.Result;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Per-character-session semantic input deduplication, priority and one-slot attack buffer. */
public final class InputRouter {
    public static final int DEDUPLICATION_TICKS = 2;
    public static final int BUFFER_EXPIRY_TICKS = 12;

    private static final Comparator<CombatInputRequest> PRIORITY =
            Comparator.comparingInt(CombatInputRequest::priority)
                    .reversed()
                    .thenComparingLong(CombatInputRequest::sequence);

    private final Map<InputDeduplicationKey, Long> lastObservationTick = new HashMap<>();
    private long nextSequence = 1;
    private long lastResolvedSequence;
    private CombatInputRequest buffered;

    public Result<CombatInputRequest, InputRejectionCode> observe(InputObservation observation) {
        Objects.requireNonNull(observation, "observation");
        Long previous = lastObservationTick.get(observation.deduplicationKey());
        if (previous != null
                && observation.tick() >= previous
                && observation.tick() - previous <= DEDUPLICATION_TICKS) {
            return Result.failure(
                    InputRejectionCode.DUPLICATE_OBSERVATION,
                    "Equivalent input observation was already accepted within two ticks.");
        }
        lastObservationTick.put(observation.deduplicationKey(), observation.tick());
        pruneDeduplicationKeys(observation.tick());
        CombatInputRequest request =
                new CombatInputRequest(
                        nextSequence++,
                        observation.tick(),
                        observation.input(),
                        observation.direction(),
                        observation.branchFamily());
        return Result.success(request);
    }

    public Result<InputRouteOutcome, InputRejectionCode> routeFrame(
            List<CombatInputRequest> requests, InputRoutingContext context) {
        Objects.requireNonNull(requests, "requests");
        Objects.requireNonNull(context, "context");
        ArrayList<CombatInputRequest> fresh = new ArrayList<>();
        for (CombatInputRequest request : requests) {
            Objects.requireNonNull(request, "request");
            if (request.sequence() > lastResolvedSequence) {
                fresh.add(request);
            }
        }
        if (fresh.isEmpty()) {
            return Result.failure(
                    InputRejectionCode.STALE_SEQUENCE,
                    "Input frame contains no unresolved sequence.");
        }
        fresh.sort(PRIORITY);
        lastResolvedSequence =
                fresh.stream().mapToLong(CombatInputRequest::sequence).max().orElseThrow();

        for (CombatInputRequest request : fresh) {
            if (context.legalNow().contains(request.input())) {
                if (request.input() == SemanticInput.DODGE) {
                    clearBuffer(InputBufferClearReason.DODGE);
                }
                return Result.success(new InputRouteOutcome(InputRouteDecision.EXECUTED, request));
            }
        }
        if (context.bufferWindowOpen()) {
            for (CombatInputRequest request : fresh) {
                if (request.input().bufferable()) {
                    return buffer(request);
                }
            }
        }
        return Result.failure(
                InputRejectionCode.ACTION_LOCKED,
                "No input in the frame is legal in the current combat state.");
    }

    public Result<InputRouteOutcome, InputRejectionCode> pollBuffered(
            long currentTick, InputRoutingContext context) {
        if (currentTick < 0) {
            throw new IllegalArgumentException("currentTick must not be negative");
        }
        Objects.requireNonNull(context, "context");
        if (buffered == null) {
            return Result.failure(
                    InputRejectionCode.ACTION_LOCKED, "No input request is buffered.");
        }
        if (currentTick - buffered.observedTick() > BUFFER_EXPIRY_TICKS) {
            buffered = null;
            return Result.failure(
                    InputRejectionCode.BUFFER_EXPIRED, "Buffered input exceeded twelve ticks.");
        }
        if (!context.legalNow().contains(buffered.input())) {
            return Result.failure(
                    InputRejectionCode.ACTION_LOCKED,
                    "Buffered input is not legal in the current combat state.");
        }
        CombatInputRequest request = buffered;
        buffered = null;
        return Result.success(new InputRouteOutcome(InputRouteDecision.EXECUTED, request));
    }

    public Optional<CombatInputRequest> buffered() {
        return Optional.ofNullable(buffered);
    }

    public void clearBuffer(InputBufferClearReason reason) {
        Objects.requireNonNull(reason, "reason");
        buffered = null;
    }

    private Result<InputRouteOutcome, InputRejectionCode> buffer(CombatInputRequest request) {
        if (buffered == null || request.priority() > buffered.priority()) {
            buffered = request;
            return Result.success(new InputRouteOutcome(InputRouteDecision.BUFFERED, request));
        }
        if (buffered.branchFamily().equals(request.branchFamily())) {
            buffered = request;
            return Result.success(
                    new InputRouteOutcome(InputRouteDecision.BUFFER_REFRESHED, request));
        }
        return Result.failure(
                InputRejectionCode.BUFFER_OCCUPIED,
                "An equal or higher-priority branch is already buffered.");
    }

    private void pruneDeduplicationKeys(long currentTick) {
        lastObservationTick
                .entrySet()
                .removeIf(entry -> currentTick - entry.getValue() > DEDUPLICATION_TICKS);
    }
}
