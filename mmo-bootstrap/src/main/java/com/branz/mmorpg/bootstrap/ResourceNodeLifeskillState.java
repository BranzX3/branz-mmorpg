package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.lifeskills.progression.LifeFocusRuntime;
import com.branz.mmorpg.lifeskills.progression.LifeskillRankRuntime;
import java.time.Instant;
import java.util.Objects;

record ResourceNodeLifeskillState(LifeskillRankRuntime rank, LifeFocusRuntime focus) {
    ResourceNodeLifeskillState {
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(focus, "focus");
    }

    static ResourceNodeLifeskillState initial(
            com.branz.mmorpg.lifeskills.progression.LifeskillDiscipline discipline, Instant now) {
        return new ResourceNodeLifeskillState(
                LifeskillRankRuntime.initial(discipline), LifeFocusRuntime.full(now));
    }
}
