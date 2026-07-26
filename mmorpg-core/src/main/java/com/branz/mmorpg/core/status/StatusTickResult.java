package com.branz.mmorpg.core.status;

import com.branz.mmorpg.api.status.StatusInstance;
import java.util.List;
import java.util.Objects;

/**
 * What one advance produced for one target.
 *
 * @param ticked  instances whose periodic tick came due
 * @param expired instances that lapsed and were removed
 */
public record StatusTickResult(List<StatusInstance> ticked, List<StatusInstance> expired) {

    public StatusTickResult {
        ticked = List.copyOf(Objects.requireNonNull(ticked, "ticked"));
        expired = List.copyOf(Objects.requireNonNull(expired, "expired"));
    }

    public static StatusTickResult empty() {
        return new StatusTickResult(List.of(), List.of());
    }

    public boolean isEmpty() {
        return ticked.isEmpty() && expired.isEmpty();
    }
}
