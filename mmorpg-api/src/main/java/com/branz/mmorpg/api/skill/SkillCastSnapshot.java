package com.branz.mmorpg.api.skill;

import com.branz.mmorpg.api.content.ContentId;
import java.util.UUID;

/** Immutable view of a cast, safe for UI and event consumers. */
public record SkillCastSnapshot(
        UUID castId,
        UUID casterId,
        UUID targetId,
        ContentId skillId,
        long contentRevision,
        SkillState state,
        long phaseStartedNanos,
        String interruptionReason) {
}
