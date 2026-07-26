package com.branz.mmorpg.quest.api;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

public record CutsceneAction(
        String id,
        long atMillis,
        int trackPriority,
        Track track,
        Type type,
        Map<String, String> values,
        Map<String, Double> numbers) {
    public enum Track { CAMERA, ACTOR, WORLD, PLAYER }
    public enum Type {
        CAMERA_CUT, CAMERA_MOVE, CAMERA_LOOK_AT, CAMERA_FOLLOW, CAMERA_ORBIT,
        CAMERA_SHAKE, CAMERA_FADE, CAMERA_RESTORE,
        ACTOR_SPAWN, ACTOR_DESPAWN, ACTOR_MOVE, ACTOR_TELEPORT, ACTOR_LOOK_AT,
        ACTOR_EQUIP, ACTOR_ANIMATE, ACTOR_VISIBLE, ACTOR_SPEAK,
        SOUND, PARTICLE, DISPLAY, BLOCK_CHANGE, DOOR_LIGHT_HOOK,
        FREEZE_INPUT, INVULNERABILITY, PARTICIPANT_VISIBILITY, PLAYER_TELEPORT,
        DIALOGUE, OBJECTIVE_SIGNAL
    }
    public static final Comparator<CutsceneAction> ORDER =
            Comparator.comparingLong(CutsceneAction::atMillis)
                    .thenComparingInt(CutsceneAction::trackPriority)
                    .thenComparing(CutsceneAction::id);
    public CutsceneAction {
        id = Objects.requireNonNull(id, "id").trim();
        Objects.requireNonNull(track, "track");
        Objects.requireNonNull(type, "type");
        values = Map.copyOf(values);
        numbers = Map.copyOf(numbers);
        if (id.isEmpty() || atMillis < 0) {
            throw new IllegalArgumentException("invalid cutscene action");
        }
    }
}
