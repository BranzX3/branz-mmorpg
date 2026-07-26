package com.branz.mmorpg.core.fixture;

import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.lifeskill.LifeSkillProfile;
import com.branz.mmorpg.api.lifeskill.LifeSkillMutationCommit;
import com.branz.mmorpg.api.lifeskill.LifeSkillSnapshot;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.api.player.PlayerProfileRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;
import java.util.function.UnaryOperator;

/** In-memory repository fixture with switchable failure modes. */
public final class FakePlayerProfileRepository implements PlayerProfileRepository {

    private final Map<UUID, PlayerProfile> stored = new ConcurrentHashMap<>();
    private final Map<UUID, LifeSkillProfile> storedLifeSkills = new ConcurrentHashMap<>();
    private final AtomicInteger saveCount = new AtomicInteger();
    private final AtomicInteger loadCount = new AtomicInteger();
    private final Set<OperationId> processedOperations = ConcurrentHashMap.newKeySet();

    private volatile boolean failLoads;
    private volatile int failSavesRemaining;

    public void failLoads(boolean fail) {
        this.failLoads = fail;
    }

    /** Fails the next {@code count} saves, then succeeds. */
    public void failNextSaves(int count) {
        this.failSavesRemaining = count;
    }

    public int saveCount() {
        return saveCount.get();
    }

    public int loadCount() {
        return loadCount.get();
    }

    public PlayerProfile stored(UUID playerId) {
        return stored.get(playerId);
    }

    public void preload(PlayerProfile profile) {
        stored.put(profile.playerId(), profile);
    }

    public LifeSkillProfile storedLifeSkills(UUID playerId) {
        return storedLifeSkills.get(playerId);
    }

    @Override
    public PlayerProfile loadOrCreate(UUID playerId, String currentName) {
        loadCount.incrementAndGet();
        if (failLoads) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE, "load failed on purpose");
        }
        return stored.computeIfAbsent(playerId,
                id -> PlayerProfile.createNew(id, currentName, Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Override
    public LifeSkillProfile loadLifeSkills(UUID playerId) {
        if (failLoads) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE, "load failed on purpose");
        }
        return storedLifeSkills.getOrDefault(playerId,
                LifeSkillProfile.empty(playerId, Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Override
    public void saveProfile(PlayerProfile profile) {
        saveCount.incrementAndGet();
        if (failSavesRemaining > 0) {
            failSavesRemaining--;
            throw new MMOException(ErrorCode.STORAGE_FAILURE, "save failed on purpose");
        }
        stored.put(profile.playerId(), profile);
    }

    @Override
    public void saveLifeSkills(LifeSkillProfile lifeSkills) {
        storedLifeSkills.put(lifeSkills.playerId(), lifeSkills);
    }

    @Override
    public void saveSession(PlayerProfile profile, LifeSkillProfile lifeSkills) {
        saveProfile(profile);
        saveLifeSkills(lifeSkills);
    }

    @Override
    public synchronized LifeSkillMutationCommit mutateLifeSkill(
            UUID playerId, ContentId skillId, OperationId operationId,
            UnaryOperator<LifeSkillSnapshot> mutation) {
        LifeSkillProfile profile = loadLifeSkills(playerId);
        LifeSkillSnapshot before = profile.skill(skillId);
        if (!processedOperations.add(operationId)) {
            return new LifeSkillMutationCommit(false, before, before);
        }
        LifeSkillSnapshot after = mutation.apply(before);
        storedLifeSkills.put(playerId, profile.with(after));
        return new LifeSkillMutationCommit(true, before, after);
    }
}
