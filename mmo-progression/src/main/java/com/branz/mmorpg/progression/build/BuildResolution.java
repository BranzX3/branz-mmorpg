package com.branz.mmorpg.progression.build;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record BuildResolution(
        CharacterBuild build,
        Map<MovesetBranch, DefinitionId> resolvedMoves,
        Optional<FormDefinition> form,
        int attunementLoad,
        Set<String> activeTags) {
    public BuildResolution {
        Objects.requireNonNull(build, "build");
        resolvedMoves = Map.copyOf(Objects.requireNonNull(resolvedMoves, "resolvedMoves"));
        Objects.requireNonNull(form, "form");
        if (attunementLoad < 0 || attunementLoad > build.attunementCapacity()) {
            throw new IllegalArgumentException("attunementLoad must fit capacity");
        }
        activeTags = Set.copyOf(Objects.requireNonNull(activeTags, "activeTags"));
    }

    public int scaleStaminaCost(int baseCost) {
        return scale(baseCost, form.map(FormDefinition::staminaCostMultiplier).orElse(1.0));
    }

    public int scaleManaCost(int baseCost) {
        return scale(baseCost, form.map(FormDefinition::manaCostMultiplier).orElse(1.0));
    }

    private static int scale(int baseCost, double multiplier) {
        if (baseCost < 0) {
            throw new IllegalArgumentException("baseCost must not be negative");
        }
        return (int) Math.ceil(baseCost * multiplier);
    }
}
