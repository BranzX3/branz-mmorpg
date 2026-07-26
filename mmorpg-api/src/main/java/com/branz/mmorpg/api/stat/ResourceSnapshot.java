package com.branz.mmorpg.api.stat;

import com.branz.mmorpg.api.skill.ResourceType;
import java.util.Objects;

public record ResourceSnapshot(ResourceType resource, double current, double maximum) {
    public ResourceSnapshot {
        Objects.requireNonNull(resource, "resource");
        if (!Double.isFinite(current) || !Double.isFinite(maximum)
                || maximum < 0 || current < 0 || current > maximum) {
            throw new IllegalArgumentException("invalid resource snapshot");
        }
    }

    public boolean depleted() { return current == 0.0; }
    public double ratio() { return maximum == 0.0 ? 0.0 : current / maximum; }
}
