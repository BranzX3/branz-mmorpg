package com.branz.mmorpg.magic.cast;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.action.ActionTimelineErrorCode;
import com.branz.mmorpg.combat.action.CombatResources;
import com.branz.mmorpg.magic.definition.SpellCastType;
import com.branz.mmorpg.magic.definition.SpellDefinition;
import java.util.Objects;
import java.util.Set;

/** Deterministic V1 instant, windup, charge and channel runtime. */
public final class SpellCastEngine {
    public Result<SpellCastRuntime, SpellCastErrorCode> start(
            SpellDefinition spell,
            CombatResources resources,
            Set<String> catalystTags,
            int attunement,
            long currentTick) {
        Objects.requireNonNull(spell, "spell");
        Objects.requireNonNull(resources, "resources");
        catalystTags = Set.copyOf(Objects.requireNonNull(catalystTags, "catalystTags"));
        if (currentTick < 0) {
            throw new IllegalArgumentException("currentTick must not be negative");
        }
        if (spell.castType() == SpellCastType.SUSTAIN || spell.castType() == SpellCastType.RITUAL) {
            return Result.failure(
                    SpellCastErrorCode.CAST_TYPE_UNSUPPORTED,
                    "Normal combat runtime does not support SUSTAIN or RITUAL casts.");
        }
        if (!catalystTags.containsAll(spell.requirements().catalystTags())) {
            return Result.failure(
                    SpellCastErrorCode.CATALYST_INCOMPATIBLE,
                    "The equipped catalyst lacks a required spell tag.");
        }
        if (attunement < spell.requirements().attunement()) {
            return Result.failure(
                    SpellCastErrorCode.ATTUNEMENT_INSUFFICIENT,
                    "The active build lacks required attunement capacity.");
        }
        Result<CombatResources, ActionTimelineErrorCode> reserved =
                resources.reserveMana(spell.manaCost());
        if (reserved instanceof Result.Failure<CombatResources, ActionTimelineErrorCode>) {
            return Result.failure(
                    SpellCastErrorCode.NO_MANA, "Spell mana cost cannot be reserved.");
        }
        CombatResources next =
                ((Result.Success<CombatResources, ActionTimelineErrorCode>) reserved).value();
        SpellCastPhase phase;
        if (spell.phases().windupTicks() > 0) {
            phase = SpellCastPhase.WINDUP;
        } else if (spell.castType() == SpellCastType.CHARGE) {
            phase = SpellCastPhase.CHARGING;
        } else {
            phase = SpellCastPhase.READY;
        }
        return Result.success(
                new SpellCastRuntime(
                        spell,
                        phase,
                        currentTick,
                        java.util.OptionalLong.empty(),
                        false,
                        0,
                        -1,
                        next));
    }

    public SpellCastRuntime advance(SpellCastRuntime runtime, long currentTick) {
        Objects.requireNonNull(runtime, "runtime");
        if (currentTick < runtime.startedAtTick()) {
            throw new IllegalArgumentException("currentTick precedes cast start");
        }
        if (runtime.phase().terminal()) {
            return runtime;
        }
        if (runtime.phase() == SpellCastPhase.WINDUP
                && currentTick - runtime.startedAtTick()
                        >= runtime.spell().phases().windupTicks()) {
            SpellCastPhase nextPhase =
                    runtime.spell().castType() == SpellCastType.CHARGE
                            ? SpellCastPhase.CHARGING
                            : SpellCastPhase.READY;
            return new SpellCastRuntime(
                    runtime.spell(),
                    nextPhase,
                    runtime.startedAtTick(),
                    runtime.releasedAtTick(),
                    runtime.manaCommitted(),
                    runtime.pulsesCompleted(),
                    -1,
                    runtime.resources());
        }
        if (runtime.phase() == SpellCastPhase.RECOVERY
                && currentTick - runtime.releasedAtTick().orElseThrow()
                        >= runtime.spell().phases().recoveryTicks()) {
            return new SpellCastRuntime(
                    runtime.spell(),
                    SpellCastPhase.COMPLETE,
                    runtime.startedAtTick(),
                    runtime.releasedAtTick(),
                    true,
                    runtime.pulsesCompleted(),
                    -1,
                    runtime.resources());
        }
        return runtime;
    }

    public boolean canRelease(SpellCastRuntime runtime, long currentTick) {
        Objects.requireNonNull(runtime, "runtime");
        if (runtime.phase() == SpellCastPhase.READY) {
            return true;
        }
        return runtime.phase() == SpellCastPhase.CHARGING
                && runtime.chargeTicks(currentTick)
                        >= runtime.spell().phases().minimumChargeTicks();
    }

    public boolean maximumChargeReached(SpellCastRuntime runtime, long currentTick) {
        Objects.requireNonNull(runtime, "runtime");
        return runtime.phase() == SpellCastPhase.CHARGING
                && runtime.chargeTicks(currentTick)
                        >= runtime.spell().phases().maximumChargeTicks();
    }

    public Result<SpellCastRuntime, SpellCastErrorCode> release(
            SpellCastRuntime runtime, long currentTick) {
        Objects.requireNonNull(runtime, "runtime");
        if (runtime.phase().terminal()) {
            return Result.failure(
                    SpellCastErrorCode.CAST_ALREADY_FINISHED, "Terminal cast cannot release.");
        }
        if (runtime.manaCommitted()) {
            return Result.failure(
                    SpellCastErrorCode.CAST_ALREADY_COMMITTED, "Cast mana is already committed.");
        }
        if (!canRelease(runtime, currentTick)) {
            return Result.failure(
                    SpellCastErrorCode.RELEASE_TOO_EARLY,
                    "Charge has not reached the minimum release tick.");
        }
        boolean channel = runtime.spell().castType() == SpellCastType.CHANNEL;
        return Result.success(
                new SpellCastRuntime(
                        runtime.spell(),
                        channel ? SpellCastPhase.CHANNELING : SpellCastPhase.RECOVERY,
                        runtime.startedAtTick(),
                        java.util.OptionalLong.of(currentTick),
                        true,
                        0,
                        channel ? currentTick : -1,
                        runtime.resources().commitReservedMana(runtime.spell().manaCost())));
    }

    public ChannelPulseResolution pulse(SpellCastRuntime runtime, long currentTick) {
        Objects.requireNonNull(runtime, "runtime");
        if (runtime.phase() != SpellCastPhase.CHANNELING) {
            throw new IllegalArgumentException("only an active channel may pulse");
        }
        SpellDefinition.Channel channel = runtime.spell().channel().orElseThrow();
        if (currentTick < runtime.nextPulseAtTick()) {
            return new ChannelPulseResolution(runtime, false, false);
        }
        if (runtime.pulsesCompleted() >= channel.maximumPulses()) {
            return new ChannelPulseResolution(stopChannel(runtime, currentTick), false, false);
        }
        java.util.Optional<CombatResources> paid =
                runtime.resources().spendMana(channel.manaPerPulse());
        if (paid.isEmpty()) {
            return new ChannelPulseResolution(stopChannel(runtime, currentTick), false, true);
        }
        int pulses = runtime.pulsesCompleted() + 1;
        SpellCastRuntime next =
                new SpellCastRuntime(
                        runtime.spell(),
                        SpellCastPhase.CHANNELING,
                        runtime.startedAtTick(),
                        runtime.releasedAtTick(),
                        true,
                        pulses,
                        currentTick + channel.pulseIntervalTicks(),
                        paid.orElseThrow());
        if (pulses >= channel.maximumPulses()) {
            next = stopChannel(next, currentTick);
        }
        return new ChannelPulseResolution(next, true, false);
    }

    public SpellCastRuntime stopChannel(SpellCastRuntime runtime, long currentTick) {
        Objects.requireNonNull(runtime, "runtime");
        if (runtime.phase() != SpellCastPhase.CHANNELING) {
            throw new IllegalArgumentException("only an active channel may stop");
        }
        return new SpellCastRuntime(
                runtime.spell(),
                SpellCastPhase.RECOVERY,
                runtime.startedAtTick(),
                java.util.OptionalLong.of(currentTick),
                true,
                runtime.pulsesCompleted(),
                -1,
                runtime.resources());
    }

    public Result<SpellCastRuntime, SpellCastErrorCode> cancel(SpellCastRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        if (runtime.phase().terminal()) {
            return Result.failure(
                    SpellCastErrorCode.CAST_ALREADY_FINISHED, "Terminal cast cannot cancel.");
        }
        CombatResources resources =
                runtime.manaCommitted()
                        ? runtime.resources()
                        : runtime.resources().releaseReservedMana(runtime.spell().manaCost());
        return Result.success(
                new SpellCastRuntime(
                        runtime.spell(),
                        SpellCastPhase.CANCELLED,
                        runtime.startedAtTick(),
                        runtime.releasedAtTick(),
                        runtime.manaCommitted(),
                        runtime.pulsesCompleted(),
                        -1,
                        resources));
    }
}
