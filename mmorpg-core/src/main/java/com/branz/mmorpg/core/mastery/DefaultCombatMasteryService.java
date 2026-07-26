package com.branz.mmorpg.core.mastery;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.mastery.CombatMasteryRepository;
import com.branz.mmorpg.api.mastery.CombatMasteryService;
import com.branz.mmorpg.api.mastery.MasteryMutationCommit;
import com.branz.mmorpg.api.mastery.MasterySnapshot;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.runtime.GameClock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Consumer;

public final class DefaultCombatMasteryService implements CombatMasteryService {

    private final CombatMasteryRepository repository;
    private final Supplier<ContentSnapshot> content;
    private final GameClock clock;
    private volatile Consumer<MasteryChanged> listener = ignored -> {};

    public record MasteryChanged(
            UUID playerId, ContentId masteryId, MasteryMutationCommit commit,
            OperationId operationId) {
    }

    public void mutationListener(Consumer<MasteryChanged> listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public DefaultCombatMasteryService(CombatMasteryRepository repository,
                                       Supplier<ContentSnapshot> content,
                                       GameClock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.content = Objects.requireNonNull(content, "content");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Map<ContentId, MasterySnapshot> profile(UUID playerId) {
        return repository.load(playerId);
    }

    @Override
    public MasteryMutationCommit grantContribution(
            UUID playerId, ContentId masteryId, long baseXp,
            double antiFarmMultiplier, OperationId operationId) {
        var definition = content.get().masteries().get(masteryId);
        if (definition == null) {
            throw new IllegalArgumentException("unknown combat mastery " + masteryId);
        }
        CombatMasteryEngine engine = new CombatMasteryEngine(definition);
        long awarded = engine.awardAmount(baseXp, antiFarmMultiplier);
        MasteryMutationCommit commit = repository.mutate(playerId, masteryId, operationId, awarded,
                current -> engine.award(current, awarded, clock.now()));
        if (commit.applied()) {
            try {
                listener.accept(new MasteryChanged(
                        playerId, masteryId, commit, operationId));
            } catch (RuntimeException ignored) {
                // Mastery commit remains authoritative if an observer is offline.
            }
        }
        return commit;
    }
}
