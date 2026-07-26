package com.branz.mmorpg.core.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.combat.WorldPoint;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.input.CombatComboDefinition;
import com.branz.mmorpg.api.input.CombatInputIntent;
import com.branz.mmorpg.api.input.CombatInputKey;
import com.branz.mmorpg.api.input.CombatInputProfileDefinition;
import com.branz.mmorpg.api.input.InputResolution;
import com.branz.mmorpg.api.input.SkillSlot;
import com.branz.mmorpg.api.player.SessionToken;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatInputEngineTest {
    private static final UUID PLAYER = UUID.fromString("3055446d-f871-49cc-a701-f7fcf74c1988");
    private static final UUID WORLD = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final SessionToken TOKEN = SessionToken.first(PLAYER);
    private final CombatComboResolver resolver = new CombatComboResolver();
    private final CombatInputEngine engine = new CombatInputEngine(resolver);

    @Test
    void nonConsumingPrefixStillRoutesBasicAttackThenResolvesFinisher() {
        InputResolution first = engine.accept(intent(CombatInputKey.LMB, 1_000_000_000L, 7),
                profile(), List.of(combo(false)), context(7, Set.of("sword")));
        InputResolution second = engine.accept(intent(CombatInputKey.RMB, 1_200_000_000L, 7),
                profile(), List.of(combo(false)), context(7, Set.of("sword")));

        assertEquals(InputResolution.Outcome.SLOT, first.outcome());
        assertEquals(SkillSlot.BASIC_ATTACK, first.slot().orElseThrow());
        assertEquals(InputResolution.Outcome.COMBO_RESOLVED, second.outcome());
        assertEquals(ContentId.parse("branz:heavy_slash"), second.skillId().orElseThrow());
        assertTrue(resolver.state(PLAYER).isEmpty());
    }

    @Test
    void consumingPrefixProducesOnlyComboAdvance() {
        InputResolution result = engine.accept(intent(CombatInputKey.LMB, 1_000_000_000L, 1),
                profile(), List.of(combo(true)), context(1, Set.of("sword")));

        assertEquals(InputResolution.Outcome.COMBO_ADVANCED, result.outcome());
        assertTrue(result.slot().isEmpty());
    }

    @Test
    void timeoutAndLoadoutRevisionResetPendingCombo() {
        engine.accept(intent(CombatInputKey.LMB, 1_000_000_000L, 4), profile(),
                List.of(combo(false)), context(4, Set.of("sword")));
        InputResolution stale = engine.accept(intent(CombatInputKey.RMB, 1_200_000_000L, 4),
                profile(), List.of(combo(false)), context(5, Set.of("sword")));
        assertEquals(InputResolution.Outcome.REJECTED, stale.outcome());
        assertTrue(resolver.state(PLAYER).isEmpty());

        engine.accept(intent(CombatInputKey.LMB, 2_000_000_000L, 5), profile(),
                List.of(combo(false)), context(5, Set.of("sword")));
        InputResolution late = engine.accept(intent(CombatInputKey.RMB, 2_700_000_000L, 5),
                profile(), List.of(combo(false)), context(5, Set.of("sword")));
        assertEquals(InputResolution.Outcome.SLOT, late.outcome(),
                "expired combo falls back to the RMB binding");
    }

    @Test
    void staleSessionAndMissingTagsFailWithoutAdvancingCombo() {
        CombatInputEngine.Context relogged = new CombatInputEngine.Context(TOKEN.next(), true,
                9, 1, Set.of("sword"), null);
        assertEquals(InputResolution.Outcome.REJECTED, engine.accept(
                intent(CombatInputKey.LMB, 1_000_000_000L, 1), profile(),
                List.of(combo(false)), relogged).outcome());
        assertTrue(resolver.state(PLAYER).isEmpty());

        InputResolution noTag = engine.accept(intent(CombatInputKey.LMB, 1_000_000_000L, 1),
                profile(), List.of(combo(false)), context(1, Set.of("staff")));
        assertEquals(InputResolution.Outcome.SLOT, noTag.outcome());
        assertTrue(resolver.state(PLAYER).isEmpty());
    }

    private static CombatInputIntent intent(CombatInputKey key, long nanos, long loadoutRevision) {
        return new CombatInputIntent(UUID.randomUUID(), PLAYER, TOKEN, key, nanos / 50_000_000L,
                nanos, Optional.empty(), Optional.empty(), new WorldPoint(WORLD, 0, 0, 0),
                1, 9, loadoutRevision);
    }

    private static CombatInputEngine.Context context(long loadoutRevision, Set<String> tags) {
        return new CombatInputEngine.Context(TOKEN, true, 9, loadoutRevision, tags, null);
    }

    private static CombatInputProfileDefinition profile() {
        EnumMap<CombatInputKey, SkillSlot> bindings = new EnumMap<>(CombatInputKey.class);
        bindings.put(CombatInputKey.LMB, SkillSlot.BASIC_ATTACK);
        bindings.put(CombatInputKey.RMB, SkillSlot.WEAPON_SKILL_1);
        bindings.put(CombatInputKey.F, SkillSlot.WEAPON_SKILL_2);
        bindings.put(CombatInputKey.SHIFT_LMB, SkillSlot.CLASS_SKILL_1);
        bindings.put(CombatInputKey.SHIFT_RMB, SkillSlot.CLASS_SKILL_2);
        bindings.put(CombatInputKey.SHIFT_F, SkillSlot.ULTIMATE);
        return new CombatInputProfileDefinition(ContentId.parse("branz:controls"), 1,
                bindings, 450, 150);
    }

    private static CombatComboDefinition combo(boolean consumes) {
        return new CombatComboDefinition(ContentId.parse("branz:heavy_combo"), Set.of("sword"),
                List.of(new CombatComboDefinition.Step(CombatInputKey.LMB, 0, 0),
                        new CombatComboDefinition.Step(CombatInputKey.RMB, 20, 450)),
                600, 100, consumes, ContentId.parse("branz:heavy_slash"));
    }
}
