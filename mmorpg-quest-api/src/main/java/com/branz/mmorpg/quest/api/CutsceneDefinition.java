package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CutsceneDefinition(
        ContentId id,
        int version,
        Scope scope,
        SkipPolicy skipPolicy,
        List<CutsceneAction> setup,
        List<CutsceneAction> timeline,
        List<Long> checkpointsMillis,
        List<CutsceneAction> finalState,
        List<CutsceneAction> skipState,
        List<CutsceneAction> cleanup,
        DisconnectRecovery disconnectRecovery) {
    public enum Scope { PRIVATE, PARTY, PUBLIC, INSTANCE }
    public enum SkipPolicy { NEVER, ALWAYS, PREVIOUSLY_COMPLETED }
    public enum DisconnectRecovery { PAUSE_RESUME, APPLY_SKIP_STATE, APPLY_FINAL_STATE, FAIL }
    public CutsceneDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(skipPolicy, "skipPolicy");
        setup = List.copyOf(setup);
        timeline = timeline.stream().sorted(CutsceneAction.ORDER).toList();
        checkpointsMillis = checkpointsMillis.stream().sorted().toList();
        finalState = List.copyOf(finalState);
        skipState = List.copyOf(skipState);
        cleanup = List.copyOf(cleanup);
        Objects.requireNonNull(disconnectRecovery, "disconnectRecovery");
        if (version < 1 || finalState.isEmpty() || skipState.isEmpty()
                || cleanup.isEmpty() || checkpointsMillis.stream().anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException("invalid cutscene definition");
        }
    }
}
