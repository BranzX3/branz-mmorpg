package com.branz.mmorpg.api.gathering;

import java.util.Objects;
import java.util.UUID;

public record WorldBlockPosition(UUID worldId, int x, int y, int z) {
    public WorldBlockPosition {
        Objects.requireNonNull(worldId, "worldId");
    }
}
