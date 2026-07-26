package com.branz.mmorpg.core.crafting;

import com.branz.mmorpg.api.crafting.ProfessionDefinition;
import com.branz.mmorpg.api.crafting.ProfessionSnapshot;
import java.time.Instant;
import java.util.Objects;

public final class ProfessionEngine {
    private final ProfessionDefinition definition;

    public ProfessionEngine(ProfessionDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "definition");
    }

    public long requiredXp(int level) {
        if (level < 1 || level > definition.maximumLevel()) {
            throw new IllegalArgumentException("level outside profession curve");
        }
        return Math.max(1, Math.round(
                definition.curveBase() * Math.pow(level, definition.curveExponent())));
    }

    public int levelFor(long totalXp) {
        if (totalXp < 0) throw new IllegalArgumentException("negative profession XP");
        int level = 1;
        long remaining = totalXp;
        while (level < definition.maximumLevel() && remaining >= requiredXp(level)) {
            remaining -= requiredXp(level++);
        }
        return level;
    }

    public long diminishedAward(long baseXp, int currentLevel, int trivialAfterLevel) {
        if (baseXp < 0) throw new IllegalArgumentException("negative profession XP");
        int excess = Math.max(0, currentLevel - trivialAfterLevel);
        double multiplier = Math.max(0.10, 1.0 / (1.0 + excess));
        return (long) Math.floor(baseXp * multiplier);
    }

    public ProfessionSnapshot award(
            ProfessionSnapshot before, long xp, Instant now) {
        if (!before.professionId().equals(definition.id())) {
            throw new IllegalArgumentException("snapshot belongs to another profession");
        }
        long total = Math.addExact(before.totalXp(), xp);
        return new ProfessionSnapshot(definition.id(), levelFor(total), total, now);
    }
}
