package com.branz.mmorpg.combat.status;

import java.util.Objects;

/** Deterministic resistance, buildup, decay, active reapplication and cleanse authority. */
public final class AilmentEngine {
    public AilmentApplication applyBuildup(
            AilmentDefinition definition,
            AilmentState current,
            double baseBuildup,
            double resistance,
            long currentTick) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(current, "current");
        if (!Double.isFinite(baseBuildup) || baseBuildup < 0 || !Double.isFinite(resistance)) {
            throw new IllegalArgumentException("invalid buildup application");
        }
        AilmentState advanced = advance(definition, current, currentTick);
        double applied = baseBuildup * clamp(0.40, 1 - resistance, 1.30);
        double buildup = Math.min(definition.buildupMaximum(), advanced.buildup() + applied);
        if (buildup < definition.buildupMaximum()) {
            return new AilmentApplication(
                    new AilmentState(
                            buildup,
                            currentTick,
                            currentTick,
                            advanced.activeUntilTick(),
                            advanced.tier()),
                    applied,
                    false,
                    false);
        }
        boolean active = advanced.activeAt(currentTick);
        int tier = active ? advanced.tier() : 1;
        long activeUntil = active ? advanced.activeUntilTick() : 0;
        boolean changed = !active;
        if (!active || definition.reapplication() == AilmentReapplication.REFRESH) {
            activeUntil = currentTick + definition.activeDurationTicks();
            changed = true;
        } else if (definition.reapplication() == AilmentReapplication.INTENSIFY) {
            tier = Math.min(definition.maximumTier(), tier + 1);
            activeUntil = currentTick + definition.activeDurationTicks();
            changed = true;
        }
        return new AilmentApplication(
                new AilmentState(0, currentTick, currentTick, activeUntil, tier),
                applied,
                true,
                changed);
    }

    public AilmentState advance(
            AilmentDefinition definition, AilmentState current, long currentTick) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(current, "current");
        if (currentTick < current.evaluatedTick()) {
            throw new IllegalArgumentException("currentTick must be monotonic");
        }
        long decayStart = current.lastBuildupTick() + definition.buildupDecayDelayTicks();
        long decayTicks = Math.max(0, currentTick - Math.max(current.evaluatedTick(), decayStart));
        double buildup =
                Math.max(0, current.buildup() - decayTicks * definition.buildupDecayPerTick());
        boolean active = current.activeAt(currentTick);
        return new AilmentState(
                buildup,
                current.lastBuildupTick(),
                currentTick,
                active ? current.activeUntilTick() : 0,
                active ? current.tier() : 0);
    }

    public AilmentState cleanse(
            AilmentDefinition definition,
            AilmentState current,
            String cleanseTag,
            long currentTick) {
        Objects.requireNonNull(cleanseTag, "cleanseTag");
        AilmentState advanced = advance(definition, current, currentTick);
        return definition.cleanseTags().contains(cleanseTag)
                ? AilmentState.empty(currentTick)
                : advanced;
    }

    public AilmentState onDeath(
            AilmentDefinition definition, AilmentState current, long currentTick) {
        AilmentState advanced = advance(definition, current, currentTick);
        return definition.persistence() == AilmentPersistence.CLEAR_ON_DEATH
                ? AilmentState.empty(currentTick)
                : advanced;
    }

    private static double clamp(double minimum, double value, double maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
