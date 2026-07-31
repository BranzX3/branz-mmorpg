package com.branz.mmorpg.magic.cast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.action.CombatResources;
import com.branz.mmorpg.magic.definition.ArcaneSchool;
import com.branz.mmorpg.magic.definition.SpellCastType;
import com.branz.mmorpg.magic.definition.SpellDefinition;
import com.branz.mmorpg.magic.definition.SpellDeliveryType;
import com.branz.mmorpg.magic.definition.SpellTargetType;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpellCastEngineTest {
    private final SpellCastEngine engine = new SpellCastEngine();

    @Test
    void reservesAtStartCommitsAtReleaseAndCompletesRecovery() {
        SpellCastRuntime runtime =
                success(engine.start(spell(), resources(), Set.of("STAFF"), 2, 100));

        assertEquals(100, runtime.resources().mana());
        assertEquals(18, runtime.resources().reservedMana());
        runtime = engine.advance(runtime, 108);
        assertEquals(SpellCastPhase.CHARGING, runtime.phase());
        assertEquals(
                SpellCastErrorCode.RELEASE_TOO_EARLY,
                ((Result.Failure<SpellCastRuntime, SpellCastErrorCode>)
                                engine.release(runtime, 115))
                        .error());

        runtime = success(engine.release(runtime, 116));
        assertEquals(82, runtime.resources().mana());
        assertEquals(0, runtime.resources().reservedMana());
        assertTrue(runtime.manaCommitted());
        assertEquals(SpellCastPhase.RECOVERY, runtime.phase());
        assertEquals(SpellCastPhase.COMPLETE, engine.advance(runtime, 128).phase());
    }

    @Test
    void preCommitCancelRefundsAndRequirementsRejectWithoutReservation() {
        SpellCastRuntime runtime =
                success(engine.start(spell(), resources(), Set.of("STAFF"), 2, 0));
        SpellCastRuntime cancelled = success(engine.cancel(runtime));

        assertEquals(100, cancelled.resources().mana());
        assertEquals(0, cancelled.resources().reservedMana());
        assertEquals(SpellCastPhase.CANCELLED, cancelled.phase());
        assertEquals(
                SpellCastErrorCode.CATALYST_INCOMPATIBLE,
                failure(engine.start(spell(), resources(), Set.of("FOCUS"), 2, 0)));
        assertEquals(
                SpellCastErrorCode.ATTUNEMENT_INSUFFICIENT,
                failure(engine.start(spell(), resources(), Set.of("STAFF"), 1, 0)));
        CombatResources lowMana = new CombatResources(1000, 1000, 100, 100, 100, 17, 0, 0, 0);
        assertEquals(
                SpellCastErrorCode.NO_MANA,
                failure(engine.start(spell(), lowMana, Set.of("STAFF"), 2, 0)));
    }

    private static SpellDefinition spell() {
        return new SpellDefinition(
                DefinitionId.of("spell.ember.fire_lance"),
                DefinitionId.of("magic.ember"),
                SpellCastType.CHARGE,
                SpellTargetType.CROSSHAIR_POINT,
                SpellDeliveryType.PROJECTILE,
                new SpellDefinition.Requirements(Set.of("STAFF"), 2),
                18,
                new SpellDefinition.Phases(8, 8, 30, 12),
                new SpellDefinition.Interruption(false, false, true, true, true, true),
                Optional.of(new SpellDefinition.Projectile(2.2, 0.01, 0.995, 0.22, 70, 0, "FIRE")),
                new SpellDefinition.Output(ArcaneSchool.FIRE, 0.9, 16, 14),
                "EMBER_FIRE_LANCE",
                new SpellDefinition.CombatProfiles(1, 0.65));
    }

    private static CombatResources resources() {
        return CombatResources.full(1000, 100, 100);
    }

    private static SpellCastRuntime success(Result<SpellCastRuntime, SpellCastErrorCode> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<SpellCastRuntime, SpellCastErrorCode>) result).value();
    }

    private static SpellCastErrorCode failure(Result<SpellCastRuntime, SpellCastErrorCode> result) {
        return ((Result.Failure<SpellCastRuntime, SpellCastErrorCode>) result).error();
    }
}
