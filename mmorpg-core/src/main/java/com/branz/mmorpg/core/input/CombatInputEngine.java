package com.branz.mmorpg.core.input;

import com.branz.mmorpg.api.input.CombatComboDefinition;
import com.branz.mmorpg.api.input.CombatInputIntent;
import com.branz.mmorpg.api.input.CombatInputProfileDefinition;
import com.branz.mmorpg.api.input.InputResolution;
import com.branz.mmorpg.api.input.SkillSlot;
import com.branz.mmorpg.api.player.SessionToken;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Validates immutable intent revisions before resolving one slot or combo action. */
public final class CombatInputEngine {
    private final CombatComboResolver combos;

    public CombatInputEngine(CombatComboResolver combos) {
        this.combos = Objects.requireNonNull(combos, "combos");
    }

    public InputResolution accept(CombatInputIntent intent,
                                  CombatInputProfileDefinition profile,
                                  Collection<CombatComboDefinition> definitions,
                                  Context context) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(context, "context");
        if (!context.playable()) return rejected("session is not ACTIVE");
        if (!intent.playerId().equals(context.sessionToken().playerId())
                || !intent.sessionToken().equals(context.sessionToken())) {
            return rejected("stale session token");
        }
        if (intent.contentRevision() != context.contentRevision()) {
            return rejected("stale content revision");
        }
        if (intent.inputProfileRevision() != profile.revision()) {
            return rejected("stale input profile revision");
        }
        if (intent.loadoutRevision() != context.loadoutRevision()) {
            combos.reset(intent.playerId());
            return rejected("stale loadout revision");
        }
        if (context.lockedReason() != null) return rejected(context.lockedReason());

        CombatComboResolver.Result combo = combos.accept(intent, context.loadoutTags(),
                context.loadoutRevision(), definitions);
        if (combo.outcome() == CombatComboResolver.Result.Outcome.RESOLVED) {
            return new InputResolution(InputResolution.Outcome.COMBO_RESOLVED, Optional.empty(),
                    Optional.of(combo.definition().id()),
                    Optional.of(combo.definition().resultSkillId()), null);
        }
        if (combo.outcome() == CombatComboResolver.Result.Outcome.ADVANCED
                && combo.definition().consumesInput()) {
            return new InputResolution(InputResolution.Outcome.COMBO_ADVANCED, Optional.empty(),
                    Optional.of(combo.definition().id()), Optional.empty(), null);
        }
        SkillSlot slot = profile.bindings().get(intent.input());
        return slot == null ? rejected("input is unbound")
                : new InputResolution(InputResolution.Outcome.SLOT, Optional.of(slot),
                        combo.definition() == null ? Optional.empty()
                                : Optional.of(combo.definition().id()),
                        Optional.empty(), null);
    }

    private static InputResolution rejected(String reason) {
        return new InputResolution(InputResolution.Outcome.REJECTED, Optional.empty(),
                Optional.empty(), Optional.empty(), reason);
    }

    public record Context(SessionToken sessionToken, boolean playable,
                          long contentRevision, long loadoutRevision,
                          Set<String> loadoutTags, String lockedReason) {
        public Context {
            Objects.requireNonNull(sessionToken, "sessionToken");
            loadoutTags = Set.copyOf(Objects.requireNonNull(loadoutTags, "loadoutTags"));
            if (contentRevision < 1 || loadoutRevision < 0) {
                throw new IllegalArgumentException("invalid input context revision");
            }
        }
    }
}
