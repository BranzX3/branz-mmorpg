package com.branz.mmorpg.core.mastery;

import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.mastery.MasterySnapshot;
import java.time.Instant;
import java.util.Objects;

/** Pure combat-mastery curve, bounded bonus, and anti-farm award calculation. */
public final class CombatMasteryEngine {

    private final MasteryDefinition definition;

    public CombatMasteryEngine(MasteryDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "definition");
    }

    public long requiredXp(int level) {
        if (level < 1 || level > definition.maximumLevel()) {
            throw new IllegalArgumentException("level outside mastery curve");
        }
        return Math.max(1L, Math.round(
                definition.curveBase() * Math.pow(level, definition.curveExponent())));
    }

    public int levelFor(long totalXp) {
        if (totalXp < 0) {
            throw new IllegalArgumentException("totalXp must not be negative");
        }
        int level = 1;
        long remaining = totalXp;
        while (level < definition.maximumLevel() && remaining >= requiredXp(level)) {
            remaining -= requiredXp(level);
            level++;
        }
        return level;
    }

    public long awardAmount(long baseXp, double antiFarmMultiplier) {
        if (baseXp < 0 || !Double.isFinite(antiFarmMultiplier)
                || antiFarmMultiplier < 0.0 || antiFarmMultiplier > 1.0) {
            throw new IllegalArgumentException("invalid mastery award");
        }
        return (long) Math.floor(baseXp * antiFarmMultiplier);
    }

    public MasterySnapshot award(MasterySnapshot current, long awardedXp, Instant now) {
        if (!current.masteryId().equals(definition.id())) {
            throw new IllegalArgumentException("snapshot belongs to another mastery");
        }
        long total = Math.addExact(current.totalXp(), awardedXp);
        return new MasterySnapshot(definition.id(), levelFor(total), total, now);
    }

    public double powerBonus(MasterySnapshot snapshot) {
        double progress = definition.maximumLevel() <= 1 ? 1.0
                : (snapshot.level() - 1.0) / (definition.maximumLevel() - 1.0);
        return Math.min(definition.maximumPowerBonus(),
                Math.max(0.0, progress * definition.maximumPowerBonus()));
    }
}
