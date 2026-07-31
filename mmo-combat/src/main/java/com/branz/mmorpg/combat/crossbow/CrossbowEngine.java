package com.branz.mmorpg.combat.crossbow;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import java.util.Objects;
import java.util.Optional;

/** Deterministic Crossbow reload state machine; persistence commits are explicit caller actions. */
public final class CrossbowEngine {
    private final CrossbowReloadProfile profile;

    public CrossbowEngine(CrossbowReloadProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public CrossbowReloadProfile profile() {
        return profile;
    }

    public Result<CrossbowRuntime, CrossbowErrorCode> beginOrResume(
            CrossbowRuntime runtime, long tick) {
        Objects.requireNonNull(runtime, "runtime");
        if (tick < runtime.phaseStartedTick()) {
            return checkpointMismatch("Crossbow tick moved backwards.");
        }
        return switch (runtime.phase()) {
            case UNLOADED ->
                    Result.success(
                            new CrossbowRuntime(CrossbowPhase.COCKING, tick, Optional.empty()));
            case BOLT_PLACED ->
                    Result.success(
                            new CrossbowRuntime(CrossbowPhase.LOCKING, tick, runtime.boundAmmo()));
            case COCKING, LOCKING, LOADED, FIRED ->
                    Result.failure(
                            CrossbowErrorCode.CROSSBOW_ACTION_LOCKED,
                            "Crossbow reload/fire state is already active.");
        };
    }

    public CrossbowTickResolution tick(CrossbowRuntime runtime, long tick) {
        Objects.requireNonNull(runtime, "runtime");
        if (tick < runtime.phaseStartedTick()) {
            throw new IllegalArgumentException("Crossbow tick moved backwards");
        }
        long elapsed = tick - runtime.phaseStartedTick();
        CrossbowTickOutcome outcome =
                switch (runtime.phase()) {
                    case COCKING ->
                            elapsed >= profile.boltPlacementTicks()
                                    ? CrossbowTickOutcome.BOLT_BIND_REQUIRED
                                    : CrossbowTickOutcome.WAITING;
                    case LOCKING ->
                            elapsed >= profile.lockingTicks()
                                    ? CrossbowTickOutcome.LOADED_CHECKPOINT_REQUIRED
                                    : CrossbowTickOutcome.WAITING;
                    case UNLOADED, BOLT_PLACED, LOADED, FIRED -> CrossbowTickOutcome.WAITING;
                };
        return new CrossbowTickResolution(runtime, outcome);
    }

    public Result<CrossbowRuntime, CrossbowErrorCode> boltPlaced(
            CrossbowRuntime runtime, long tick, DefinitionId ammoDefinitionId) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(ammoDefinitionId, "ammoDefinitionId");
        if (runtime.phase() != CrossbowPhase.COCKING
                || tick - runtime.phaseStartedTick() < profile.boltPlacementTicks()) {
            return checkpointMismatch("Bolt cannot bind before the authored placement checkpoint.");
        }
        return Result.success(
                new CrossbowRuntime(
                        CrossbowPhase.BOLT_PLACED, tick, Optional.of(ammoDefinitionId)));
    }

    public Result<CrossbowRuntime, CrossbowErrorCode> loaded(CrossbowRuntime runtime, long tick) {
        Objects.requireNonNull(runtime, "runtime");
        if (runtime.phase() != CrossbowPhase.LOCKING
                || tick - runtime.phaseStartedTick() < profile.lockingTicks()) {
            return checkpointMismatch("Crossbow cannot become loaded before lock completion.");
        }
        return Result.success(new CrossbowRuntime(CrossbowPhase.LOADED, tick, runtime.boundAmmo()));
    }

    public Result<CrossbowFireResolution, CrossbowErrorCode> fire(
            CrossbowRuntime runtime, long tick) {
        Objects.requireNonNull(runtime, "runtime");
        if (runtime.phase() != CrossbowPhase.LOADED) {
            return Result.failure(
                    CrossbowErrorCode.CROSSBOW_ACTION_LOCKED,
                    "Crossbow must be loaded before firing.");
        }
        return Result.success(
                new CrossbowFireResolution(
                        new CrossbowRuntime(CrossbowPhase.FIRED, tick, Optional.empty()),
                        runtime.boundAmmo().orElseThrow()));
    }

    public Result<CrossbowRuntime, CrossbowErrorCode> completeFire(
            CrossbowRuntime runtime, long tick) {
        Objects.requireNonNull(runtime, "runtime");
        if (runtime.phase() != CrossbowPhase.FIRED || tick < runtime.phaseStartedTick()) {
            return checkpointMismatch("Only a fired Crossbow can settle to UNLOADED.");
        }
        return Result.success(new CrossbowRuntime(CrossbowPhase.UNLOADED, tick, Optional.empty()));
    }

    public CrossbowRuntime interrupt(CrossbowRuntime runtime, long tick) {
        Objects.requireNonNull(runtime, "runtime");
        return switch (runtime.phase()) {
            case COCKING -> new CrossbowRuntime(CrossbowPhase.UNLOADED, tick, Optional.empty());
            case LOCKING ->
                    new CrossbowRuntime(CrossbowPhase.BOLT_PLACED, tick, runtime.boundAmmo());
            case FIRED -> new CrossbowRuntime(CrossbowPhase.UNLOADED, tick, Optional.empty());
            case UNLOADED, BOLT_PLACED, LOADED -> runtime;
        };
    }

    private static <T> Result<T, CrossbowErrorCode> checkpointMismatch(String detail) {
        return Result.failure(CrossbowErrorCode.CROSSBOW_CHECKPOINT_MISMATCH, detail);
    }
}
