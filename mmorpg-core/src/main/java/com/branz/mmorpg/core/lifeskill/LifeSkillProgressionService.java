package com.branz.mmorpg.core.lifeskill;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.lifeskill.LifeSkillMutationCommit;
import com.branz.mmorpg.api.lifeskill.LifeSkillMutationService;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.player.PlayerProfileRepository;
import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.core.player.PlayerSessionService;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Transactional facade for XP, mastery-node purchases, and respecs. */
public final class LifeSkillProgressionService implements LifeSkillMutationService {

    private final PlayerProfileRepository repository;
    private final PlayerSessionService sessions;
    private final GameClock clock;
    private final Supplier<ContentSnapshot> content;

    public LifeSkillProgressionService(PlayerProfileRepository repository,
                                       PlayerSessionService sessions,
                                       GameClock clock,
                                       Supplier<ContentSnapshot> content) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override
    public LifeSkillMutationCommit grantXp(UUID playerId, ContentId skillId, long amount,
                                           OperationId operationId) {
        sessions.requirePlayable(playerId);
        ContentSnapshot snapshot = content.get();
        LifeSkillProgressionEngine engine = engine(snapshot, skillId);
        LifeSkillMutationCommit committed = repository.mutateLifeSkill(
                playerId, skillId, operationId,
                current -> engine.award(current, amount, snapshot.revision(), clock.now()).after());
        sessions.requirePlayable(playerId).acceptPersistedLifeSkill(committed.after());
        return committed;
    }

    @Override
    public LifeSkillMutationCommit purchase(UUID playerId, ContentId skillId, ContentId nodeId,
                                            OperationId operationId) {
        sessions.requirePlayable(playerId);
        LifeSkillProgressionEngine engine = engine(content.get(), skillId);
        LifeSkillMutationCommit committed = repository.mutateLifeSkill(
                playerId, skillId, operationId,
                current -> engine.purchase(current, nodeId, clock.now()).after());
        sessions.requirePlayable(playerId).acceptPersistedLifeSkill(committed.after());
        return committed;
    }

    @Override
    public LifeSkillMutationCommit respec(UUID playerId, ContentId skillId,
                                          OperationId operationId) {
        sessions.requirePlayable(playerId);
        LifeSkillProgressionEngine engine = engine(content.get(), skillId);
        LifeSkillMutationCommit committed = repository.mutateLifeSkill(
                playerId, skillId, operationId,
                current -> engine.respec(current, clock.now()).after());
        sessions.requirePlayable(playerId).acceptPersistedLifeSkill(committed.after());
        return committed;
    }

    private static LifeSkillProgressionEngine engine(ContentSnapshot snapshot, ContentId skillId) {
        var skill = snapshot.lifeSkills().get(skillId);
        if (skill == null) {
            throw new IllegalArgumentException("unknown Life Skill " + skillId);
        }
        Map<ContentId, LifeSkillNodeDefinition> nodes = snapshot.lifeSkillNodes().entrySet().stream()
                .filter(entry -> entry.getValue().skillId().equals(skillId))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return new LifeSkillProgressionEngine(skill, nodes);
    }
}
