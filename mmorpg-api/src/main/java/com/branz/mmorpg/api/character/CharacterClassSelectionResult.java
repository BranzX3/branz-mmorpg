package com.branz.mmorpg.api.character;

import java.util.Objects;

public record CharacterClassSelectionResult(
        Status status,
        CharacterClassSnapshot snapshot,
        StarterGrantPlan starterGrantPlan,
        long contentRevision) {
    public enum Status { APPLIED, REPLAYED }

    public CharacterClassSelectionResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(starterGrantPlan, "starterGrantPlan");
        if (contentRevision < 1) throw new IllegalArgumentException("content revision must be positive");
    }

    public boolean applied() { return status == Status.APPLIED; }
}
