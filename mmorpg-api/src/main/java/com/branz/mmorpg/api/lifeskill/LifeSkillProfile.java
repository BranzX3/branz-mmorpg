package com.branz.mmorpg.api.lifeskill;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Every Life Skill of one player, as an immutable point-in-time value.
 *
 * <p>A session holds one of these and replaces it wholesale when progress
 * changes, rather than mutating counters in place. That is what lets a snapshot
 * be read off the main thread, handed to the UI, or compared against a later
 * one without locking.
 *
 * @param playerId player this profile belongs to; UUID is the identity, never a name
 * @param skills   snapshots by skill ID; a skill absent from the map is untrained
 * @param loadedAt when this profile was read from storage
 */
public record LifeSkillProfile(UUID playerId, Map<ContentId, LifeSkillSnapshot> skills, Instant loadedAt) {

    public LifeSkillProfile {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(skills, "skills");
        Objects.requireNonNull(loadedAt, "loadedAt");
        skills = Map.copyOf(skills);
    }

    /** Profile of a player with no recorded Life Skill progress at all. */
    public static LifeSkillProfile empty(UUID playerId, Instant loadedAt) {
        return new LifeSkillProfile(playerId, Map.of(), loadedAt);
    }

    /**
     * Snapshot of {@code skillId}, or an untrained snapshot when the player has
     * never trained it. Never null, never empty — see
     * {@link LifeSkillProgress#untrained}.
     */
    public LifeSkillSnapshot skill(ContentId skillId) {
        Objects.requireNonNull(skillId, "skillId");
        LifeSkillSnapshot snapshot = skills.get(skillId);
        return snapshot != null ? snapshot : LifeSkillSnapshot.untrained(skillId, loadedAt);
    }

    /** Skills with recorded progress. Untrained skills are not listed. */
    public Set<ContentId> trainedSkills() {
        return skills.keySet();
    }

    public int level(ContentId skillId) {
        return skill(skillId).level();
    }

    public boolean hasNode(ContentId skillId, ContentId nodeId, int minimumRank) {
        return skill(skillId).hasNode(nodeId, minimumRank);
    }

    /** Returns a copy with {@code snapshot} replacing the entry for its skill. */
    public LifeSkillProfile with(LifeSkillSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        var updated = new java.util.HashMap<>(skills);
        updated.put(snapshot.skillId(), snapshot);
        return new LifeSkillProfile(playerId, updated, loadedAt);
    }
}
