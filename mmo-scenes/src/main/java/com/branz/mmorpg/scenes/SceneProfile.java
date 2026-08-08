package com.branz.mmorpg.scenes;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable composition and policy contract for a world-backed Scene. */
public record SceneProfile(
        DefinitionId id,
        SceneTopology topology,
        SceneMode entryMode,
        Map<SceneMode, SceneModeProfile> modes,
        boolean locksNormalMovement) {
    public SceneProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(entryMode, "entryMode");
        Objects.requireNonNull(modes, "modes");
        EnumMap<SceneMode, SceneModeProfile> copy = new EnumMap<>(SceneMode.class);
        modes.forEach(
                (mode, profile) -> {
                    if (mode != profile.mode()) {
                        throw new IllegalArgumentException(
                                "Scene mode profile key does not match mode");
                    }
                    copy.put(mode, profile);
                });
        if (!copy.containsKey(entryMode)) {
            throw new IllegalArgumentException("Scene entry mode is not present in profile");
        }
        modes = Map.copyOf(copy);
    }

    public Optional<SceneModeProfile> mode(SceneMode mode) {
        return Optional.ofNullable(modes.get(Objects.requireNonNull(mode, "mode")));
    }

    public SceneModeProfile requireMode(SceneMode mode) {
        return mode(mode)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Scene mode is not in profile: " + mode));
    }
}
