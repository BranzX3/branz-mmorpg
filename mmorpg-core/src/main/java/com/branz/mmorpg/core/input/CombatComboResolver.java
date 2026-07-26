package com.branz.mmorpg.core.input;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.input.CombatComboDefinition;
import com.branz.mmorpg.api.input.CombatInputIntent;
import com.branz.mmorpg.api.input.ComboStateSnapshot;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** One-candidate-per-player deterministic combo finite-state machine. */
public final class CombatComboResolver {
    private static final Comparator<CombatComboDefinition> ORDER = Comparator
            .comparingInt(CombatComboDefinition::priority).reversed()
            .thenComparing(value -> value.id().toString());
    private final Map<UUID, RuntimeState> states = new HashMap<>();

    public synchronized Result accept(CombatInputIntent intent, Set<String> loadoutTags,
                                      long currentLoadoutRevision,
                                      Collection<CombatComboDefinition> definitions) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(loadoutTags, "loadoutTags");
        Objects.requireNonNull(definitions, "definitions");
        RuntimeState current = states.get(intent.playerId());
        if (current != null && (current.loadoutRevision != currentLoadoutRevision
                || intent.monotonicNanos() > current.expiresAtNanos)) {
            states.remove(intent.playerId());
            current = null;
        }
        if (current != null) {
            ContentId currentComboId = current.comboId;
            CombatComboDefinition definition = definitions.stream()
                    .filter(value -> value.id().equals(currentComboId)).findFirst().orElse(null);
            if (definition == null || !loadoutTags.containsAll(definition.requiredTags())) {
                states.remove(intent.playerId());
            } else {
                CombatComboDefinition.Step expected = definition.steps().get(current.nextStep);
                long delayMillis = Math.max(0L,
                        (intent.monotonicNanos() - current.lastInputNanos) / 1_000_000L);
                if (intent.input() == expected.input()
                        && delayMillis >= expected.minimumDelayMillis()
                        && delayMillis <= expected.maximumDelayMillis()) {
                    int accepted = current.nextStep + 1;
                    if (accepted == definition.steps().size()) {
                        states.remove(intent.playerId());
                        return Result.resolved(definition);
                    }
                    RuntimeState advanced = new RuntimeState(definition.id(), accepted,
                            intent.monotonicNanos(), addMillis(intent.monotonicNanos(),
                                    definition.resetTimeoutMillis()), currentLoadoutRevision);
                    states.put(intent.playerId(), advanced);
                    return Result.advanced(definition, snapshot(intent.playerId(), advanced));
                }
                states.remove(intent.playerId());
            }
        }

        CombatComboDefinition started = definitions.stream()
                .filter(combo -> loadoutTags.containsAll(combo.requiredTags()))
                .filter(combo -> combo.steps().get(0).input() == intent.input())
                .sorted(ORDER).findFirst().orElse(null);
        if (started == null) return Result.noMatch();
        RuntimeState state = new RuntimeState(started.id(), 1, intent.monotonicNanos(),
                addMillis(intent.monotonicNanos(), started.resetTimeoutMillis()),
                currentLoadoutRevision);
        states.put(intent.playerId(), state);
        return Result.advanced(started, snapshot(intent.playerId(), state));
    }

    public synchronized Optional<ComboStateSnapshot> state(UUID playerId) {
        RuntimeState state = states.get(playerId);
        return state == null ? Optional.empty() : Optional.of(snapshot(playerId, state));
    }

    public synchronized void reset(UUID playerId) { states.remove(playerId); }
    public synchronized int trackedPlayers() { return states.size(); }

    private static long addMillis(long nanos, long millis) {
        return Math.addExact(nanos, Math.multiplyExact(millis, 1_000_000L));
    }

    private static ComboStateSnapshot snapshot(UUID playerId, RuntimeState state) {
        return new ComboStateSnapshot(playerId, state.comboId, state.nextStep,
                state.lastInputNanos, state.expiresAtNanos, state.loadoutRevision);
    }

    private record RuntimeState(ContentId comboId, int nextStep, long lastInputNanos,
                                long expiresAtNanos, long loadoutRevision) {
    }

    public record Result(Outcome outcome, CombatComboDefinition definition,
                         ComboStateSnapshot state) {
        public enum Outcome { NO_MATCH, ADVANCED, RESOLVED }
        static Result noMatch() { return new Result(Outcome.NO_MATCH, null, null); }
        static Result advanced(CombatComboDefinition definition, ComboStateSnapshot state) {
            return new Result(Outcome.ADVANCED, definition, state);
        }
        static Result resolved(CombatComboDefinition definition) {
            return new Result(Outcome.RESOLVED, definition, null);
        }
    }
}
