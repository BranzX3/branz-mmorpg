package com.branz.mmorpg.core.mob;

import com.branz.mmorpg.api.mob.MobAbilityDefinition;
import com.branz.mmorpg.api.mob.MobAiState;
import com.branz.mmorpg.api.mob.MobDecision;
import com.branz.mmorpg.api.mob.MobDefinition;
import com.branz.mmorpg.api.mob.MobRuntimeSnapshot;
import com.branz.mmorpg.api.mob.MobTargetCandidate;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

/** Pure bounded-cadence AI. Presentation and navigation execution live outside this class. */
public final class MobAiEngine {
    private static final double TARGET_SWITCH_MARGIN = 1.20;

    public MobDecision decide(MobDefinition definition, MobRuntimeSnapshot mob,
                              Collection<MobTargetCandidate> candidates, Instant now) {
        java.util.Objects.requireNonNull(definition, "definition");
        java.util.Objects.requireNonNull(mob, "mob");
        java.util.Objects.requireNonNull(candidates, "candidates");
        java.util.Objects.requireNonNull(now, "now");
        if (mob.state() == MobAiState.DEAD) return none(mob);
        if (mob.health() <= 0) {
            return new MobDecision(copy(mob, mob.position(), MobAiState.DEAD,
                    Optional.empty(), 0, now, now, now,
                    mob.decisionSequence() + 1, mob.rewardSequence() + 1),
                    MobDecision.Action.DIE, Optional.empty(), Optional.empty(), false);
        }
        if (now.isBefore(mob.nextDecisionAt())) return none(mob);

        Instant nextDecision = now.plusMillis(definition.navigation().decisionIntervalMillis());
        if (mob.state() == MobAiState.RESET) {
            if (now.isBefore(mob.stateSince().plusMillis(definition.resetMillis()))) {
                return new MobDecision(copy(mob, mob.position(), MobAiState.RESET,
                        Optional.empty(), mob.health(), mob.stateSince(), nextDecision,
                        mob.nextPathRequestAt(), mob.decisionSequence() + 1,
                        mob.rewardSequence()), MobDecision.Action.RESET,
                        Optional.empty(), Optional.empty(), false);
            }
            MobRuntimeSnapshot canonical = copy(mob, mob.home(), MobAiState.IDLE,
                    Optional.empty(), mob.maximumHealth(), now, nextDecision, now,
                    mob.decisionSequence() + 1, mob.rewardSequence());
            return new MobDecision(canonical, MobDecision.Action.RESET,
                    Optional.empty(), Optional.empty(), false);
        }
        if (mob.home().distanceSquared(mob.position())
                > definition.leashRange() * definition.leashRange()) {
            return reset(mob, now, nextDecision, true);
        }

        List<MobTargetCandidate> valid = candidates.stream()
                .filter(MobTargetCandidate::alive)
                .filter(MobTargetCandidate::targetable)
                .filter(target -> mob.position().distanceSquared(target.position())
                        <= definition.aggroRange() * definition.aggroRange())
                .sorted(Comparator.comparingDouble(
                        (MobTargetCandidate target) -> score(mob, target)).reversed()
                        .thenComparing(MobTargetCandidate::entityId))
                .toList();
        Optional<MobTargetCandidate> target = selectTarget(mob.targetId(), valid, mob);
        if (target.isEmpty()) {
            if (mob.targetId().isPresent()) return reset(mob, now, nextDecision, false);
            MobRuntimeSnapshot idle = copy(mob, mob.position(), MobAiState.IDLE,
                    Optional.empty(), mob.health(), now, nextDecision,
                    mob.nextPathRequestAt(), mob.decisionSequence() + 1,
                    mob.rewardSequence());
            return none(idle);
        }

        MobTargetCandidate selected = target.orElseThrow();
        double distance = Math.sqrt(mob.position().distanceSquared(selected.position()));
        Optional<MobAbilityDefinition> ability = chooseAbility(
                definition.abilities(), selected, mob, distance);
        if (ability.isPresent()) {
            MobRuntimeSnapshot cast = copy(mob, mob.position(), MobAiState.CAST,
                    Optional.of(selected.entityId()), mob.health(), now, nextDecision,
                    mob.nextPathRequestAt(), mob.decisionSequence() + 1,
                    mob.rewardSequence());
            return new MobDecision(cast, MobDecision.Action.CAST,
                    cast.targetId(), Optional.of(ability.orElseThrow().skillId()), false);
        }
        boolean path = !now.isBefore(mob.nextPathRequestAt());
        Instant nextPath = path
                ? now.plusMillis(definition.navigation().pathRequestIntervalMillis())
                : mob.nextPathRequestAt();
        MobRuntimeSnapshot pursue = copy(mob, mob.position(), MobAiState.PURSUE,
                Optional.of(selected.entityId()), mob.health(), now, nextDecision, nextPath,
                mob.decisionSequence() + 1, mob.rewardSequence());
        MobDecision.Action action = mob.targetId().isPresent()
                ? MobDecision.Action.MOVE : MobDecision.Action.ACQUIRE;
        return new MobDecision(pursue, action, pursue.targetId(), Optional.empty(), path);
    }

    public MobRuntimeSnapshot withPosition(MobRuntimeSnapshot mob,
                                           com.branz.mmorpg.api.mob.SpatialPosition position) {
        return copy(mob, position, mob.state(), mob.targetId(), mob.health(),
                mob.stateSince(), mob.nextDecisionAt(), mob.nextPathRequestAt(),
                mob.decisionSequence(), mob.rewardSequence());
    }

    public MobRuntimeSnapshot withHealth(MobRuntimeSnapshot mob, double health) {
        double bounded = Math.max(0, Math.min(mob.maximumHealth(), health));
        return copy(mob, mob.position(), mob.state(), mob.targetId(), bounded,
                mob.stateSince(), mob.nextDecisionAt(), mob.nextPathRequestAt(),
                mob.decisionSequence(), mob.rewardSequence());
    }

    private static MobDecision reset(MobRuntimeSnapshot mob, Instant now,
                                     Instant nextDecision, boolean path) {
        MobRuntimeSnapshot reset = copy(mob, mob.position(), MobAiState.RESET,
                Optional.empty(), mob.health(), now, nextDecision, path ? now : mob.nextPathRequestAt(),
                mob.decisionSequence() + 1, mob.rewardSequence());
        return new MobDecision(reset, MobDecision.Action.RESET,
                Optional.empty(), Optional.empty(), path);
    }

    private static Optional<MobTargetCandidate> selectTarget(
            Optional<UUID> currentId, List<MobTargetCandidate> valid, MobRuntimeSnapshot mob) {
        if (valid.isEmpty()) return Optional.empty();
        MobTargetCandidate best = valid.getFirst();
        if (currentId.isEmpty()) return Optional.of(best);
        Optional<MobTargetCandidate> current = valid.stream()
                .filter(value -> value.entityId().equals(currentId.orElseThrow())).findFirst();
        if (current.isEmpty()) return Optional.of(best);
        return score(mob, best) > score(mob, current.orElseThrow()) * TARGET_SWITCH_MARGIN
                ? Optional.of(best) : current;
    }

    private static double score(MobRuntimeSnapshot mob, MobTargetCandidate target) {
        return target.threat() * 1000
                - mob.position().distanceSquared(target.position());
    }

    private static Optional<MobAbilityDefinition> chooseAbility(
            List<MobAbilityDefinition> abilities, MobTargetCandidate target,
            MobRuntimeSnapshot mob, double distance) {
        double healthFraction = mob.health() / mob.maximumHealth();
        List<MobAbilityDefinition> eligible = abilities.stream()
                .filter(value -> distance >= value.minimumRange()
                        && distance <= value.maximumRange())
                .filter(value -> healthFraction <= value.maximumHealthFraction())
                .filter(value -> target.tags().containsAll(value.requiredTargetTags()))
                .toList();
        if (eligible.isEmpty()) return Optional.empty();
        double total = eligible.stream().mapToDouble(MobAbilityDefinition::weight).sum();
        long seed = mob.instanceId().getMostSignificantBits()
                ^ mob.instanceId().getLeastSignificantBits() ^ mob.decisionSequence();
        double roll = new SplittableRandom(seed).nextDouble(total);
        for (MobAbilityDefinition ability : eligible) {
            roll -= ability.weight();
            if (roll < 0) return Optional.of(ability);
        }
        return Optional.of(eligible.getLast());
    }

    private static MobDecision none(MobRuntimeSnapshot mob) {
        return new MobDecision(mob, MobDecision.Action.NONE,
                mob.targetId(), Optional.empty(), false);
    }

    private static MobRuntimeSnapshot copy(
            MobRuntimeSnapshot source, com.branz.mmorpg.api.mob.SpatialPosition position,
            MobAiState state, Optional<UUID> target, double health,
            Instant stateSince, Instant nextDecision, Instant nextPath,
            long decisionSequence, long rewardSequence) {
        return new MobRuntimeSnapshot(source.instanceId(), source.definitionId(), source.level(),
                source.home(), position, state, target, health, source.maximumHealth(),
                stateSince, nextDecision, nextPath, decisionSequence, rewardSequence);
    }
}
