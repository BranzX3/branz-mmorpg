package com.branz.mmorpg.lifeskills.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.lifeskills.progression.LifeskillDiscipline;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResourceNodeEngineTest {
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
    private final ResourceNodeEngine engine = new ResourceNodeEngine();
    private final CharacterId alpha = character();
    private final CharacterId beta = character();

    @Test
    void personalCommonNodeAllowsIndependentReservations() {
        ResourceNodeDefinition definition = common();
        ResourceNodeRuntime initial = runtime(definition);
        ResourceNodeTransition alphaReserved = reserve(definition, initial, alpha, 0, NOW);
        ResourceNodeTransition betaReserved =
                reserve(definition, alphaReserved.runtime(), beta, 0, NOW);

        assertEquals(
                ResourceNodePhase.RESERVED,
                engine.slotFor(definition, betaReserved.runtime(), alpha).phase());
        assertEquals(
                ResourceNodePhase.RESERVED,
                engine.slotFor(definition, betaReserved.runtime(), beta).phase());
        assertEquals(2, betaReserved.runtime().slots().size());
    }

    @Test
    void reservationReplayIsExactAndChangedReuseFailsClosed() {
        ResourceNodeDefinition definition = common();
        ResourceNodeRuntime initial = runtime(definition);
        ResourceNodeReservationRequest request = request(alpha, 2, NOW, 0);
        ResourceNodeTransition reserved = success(engine.reserve(definition, initial, request));

        ResourceNodeTransition replay =
                success(engine.reserve(definition, reserved.runtime(), request));
        assertFalse(replay.changed());
        ResourceNodeReservationRequest changed =
                new ResourceNodeReservationRequest(
                        request.actor(),
                        request.toolItemId(),
                        request.toolTags(),
                        request.availableToolDurability(),
                        request.regionEligible(),
                        request.actionAvailable(),
                        3,
                        request.reservationId(),
                        request.operationId(),
                        request.currentTick(),
                        request.now());
        assertEquals(
                ResourceNodeErrorCode.OPERATION_ID_REUSED,
                failure(engine.reserve(definition, reserved.runtime(), changed)));
    }

    @Test
    void sharedRareNodeReservesToFirstActorAndReleasesOnTimeout() {
        ResourceNodeDefinition definition = rare();
        ResourceNodeRuntime initial = runtime(definition);
        ResourceNodeTransition reserved = reserve(definition, initial, alpha, 0, NOW);

        assertEquals(
                ResourceNodeErrorCode.NODE_UNAVAILABLE,
                failure(engine.reserve(definition, reserved.runtime(), request(beta, 0, NOW, 0))));
        ResourceNodeTransition loadedChunk =
                engine.reconcile(definition, reserved.runtime(), NOW.plusSeconds(2), false);
        assertFalse(loadedChunk.changed());
        ResourceNodeTransition timedOut =
                engine.reconcile(definition, reserved.runtime(), NOW.plusSeconds(9), false);
        assertTrue(timedOut.changed());
        assertEquals(1, timedOut.releasedReservations().size());
        assertEquals(
                ResourceNodePhase.AVAILABLE,
                engine.slotFor(definition, timedOut.runtime(), beta).phase());
        assertTrue(reserve(definition, timedOut.runtime(), beta, 0, NOW.plusSeconds(9)).changed());
    }

    @Test
    void admissionToolAndDurabilityFailuresAreStable() {
        ResourceNodeDefinition definition = common();
        ResourceNodeRuntime runtime = runtime(definition);
        ResourceNodeReservationRequest base = request(alpha, 0, NOW, 0);

        assertEquals(
                ResourceNodeErrorCode.ADMISSION_REJECTED,
                failure(
                        engine.reserve(
                                definition,
                                runtime,
                                copy(base, Set.of("tool.pickaxe"), 100, false, true))));
        assertEquals(
                ResourceNodeErrorCode.TOOL_INVALID,
                failure(
                        engine.reserve(
                                definition,
                                runtime,
                                copy(base, Set.of("tool.axe"), 100, true, true))));
        assertEquals(
                ResourceNodeErrorCode.TOOL_DURABILITY_INSUFFICIENT,
                failure(
                        engine.reserve(
                                definition,
                                runtime,
                                copy(base, Set.of("tool.pickaxe"), 1, true, true))));
    }

    @Test
    void commitOccursOnceAtAuthoredPointWithStableYieldSeed() {
        ResourceNodeDefinition definition = rare();
        ResourceNodeTransition reserved = reserve(definition, runtime(definition), alpha, 5, NOW);
        ResourceNodeReservation reservation = reserved.newReservation().orElseThrow();
        UUID commitOperation = UUID.randomUUID();

        assertEquals(
                ResourceNodeErrorCode.COMMIT_TOO_EARLY,
                failure(
                        engine.commit(
                                definition,
                                reserved.runtime(),
                                alpha,
                                reservation.reservationId(),
                                commitOperation,
                                39,
                                NOW.plusSeconds(1))));
        ResourceNodeTransition committed =
                success(
                        engine.commit(
                                definition,
                                reserved.runtime(),
                                alpha,
                                reservation.reservationId(),
                                commitOperation,
                                40,
                                NOW.plusSeconds(2)));
        ResourceNodeHarvestCommit harvest = committed.harvestCommit().orElseThrow();
        assertEquals(reservation.yieldSeed(), harvest.yieldSeed());
        assertEquals(5, harvest.focusCost());
        assertEquals(ResourceNodePhase.DEPLETED, sharedSlot(committed).phase());

        ResourceNodeTransition replay =
                success(
                        engine.commit(
                                definition,
                                committed.runtime(),
                                alpha,
                                reservation.reservationId(),
                                commitOperation,
                                40,
                                NOW.plusSeconds(2)));
        assertFalse(replay.changed());
        assertTrue(replay.harvestCommit().isEmpty());
        assertEquals(
                ResourceNodeErrorCode.OPERATION_ID_REUSED,
                failure(
                        engine.cancel(
                                definition,
                                committed.runtime(),
                                alpha,
                                reservation.reservationId(),
                                commitOperation)));
    }

    @Test
    void restartBeforeCommitReleasesButAfterCommitPreservesDepletionAndRecovery() {
        ResourceNodeDefinition definition = rare();
        ResourceNodeTransition reserved = reserve(definition, runtime(definition), alpha, 0, NOW);
        ResourceNodeTransition restarted =
                engine.reconcile(definition, reserved.runtime(), NOW.plusSeconds(1), true);
        assertEquals(ResourceNodePhase.AVAILABLE, sharedSlot(restarted).phase());

        ResourceNodeTransition second =
                reserve(definition, restarted.runtime(), alpha, 0, NOW.plusSeconds(1));
        ResourceNodeReservation reservation = second.newReservation().orElseThrow();
        ResourceNodeTransition committed =
                success(
                        engine.commit(
                                definition,
                                second.runtime(),
                                alpha,
                                reservation.reservationId(),
                                UUID.randomUUID(),
                                40,
                                NOW.plusSeconds(3)));
        ResourceNodeTransition recoveredStartup =
                engine.reconcile(definition, committed.runtime(), NOW.plusSeconds(4), true);
        assertEquals(ResourceNodePhase.RECOVERING, sharedSlot(recoveredStartup).phase());
        assertEquals(0, sharedSlot(recoveredStartup).remainingCharges());
        ResourceNodeTransition recovered =
                engine.reconcile(
                        definition, recoveredStartup.runtime(), NOW.plusSeconds(34), false);
        assertEquals(ResourceNodePhase.AVAILABLE, sharedSlot(recovered).phase());
        assertEquals(definition.maximumCharges(), sharedSlot(recovered).remainingCharges());
    }

    @Test
    void cancellationReturnsTheReservedChargeWithoutHarvest() {
        ResourceNodeDefinition definition = common();
        ResourceNodeTransition reserved = reserve(definition, runtime(definition), alpha, 0, NOW);
        ResourceNodeReservation reservation = reserved.newReservation().orElseThrow();
        ResourceNodeTransition cancelled =
                success(
                        engine.cancel(
                                definition,
                                reserved.runtime(),
                                alpha,
                                reservation.reservationId(),
                                UUID.randomUUID()));
        assertEquals(ResourceNodePhase.AVAILABLE, personalSlot(cancelled, alpha).phase());
        assertEquals(
                definition.maximumCharges(), personalSlot(cancelled, alpha).remainingCharges());
        assertTrue(cancelled.harvestCommit().isEmpty());
    }

    @Test
    void nodeTypesEnforceTheirAuthoredSharingContract() {
        assertThrows(
                IllegalArgumentException.class,
                () -> definition(ResourceNodeType.COMMON, ResourceNodeSharing.SHARED, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> definition(ResourceNodeType.RARE, ResourceNodeSharing.PERSONAL, 1));
    }

    private ResourceNodeTransition reserve(
            ResourceNodeDefinition definition,
            ResourceNodeRuntime runtime,
            CharacterId actor,
            int focusCost,
            Instant now) {
        return success(engine.reserve(definition, runtime, request(actor, focusCost, now, 0)));
    }

    private static ResourceNodeReservationRequest request(
            CharacterId actor, int focusCost, Instant now, long tick) {
        return new ResourceNodeReservationRequest(
                actor,
                UUID.randomUUID(),
                Set.of("tool.pickaxe"),
                100,
                true,
                true,
                focusCost,
                UUID.randomUUID(),
                UUID.randomUUID(),
                tick,
                now);
    }

    private static ResourceNodeReservationRequest copy(
            ResourceNodeReservationRequest source,
            Set<String> tags,
            int durability,
            boolean regionEligible,
            boolean actionAvailable) {
        return new ResourceNodeReservationRequest(
                source.actor(),
                source.toolItemId(),
                tags,
                durability,
                regionEligible,
                actionAvailable,
                source.focusCost(),
                source.reservationId(),
                source.operationId(),
                source.currentTick(),
                source.now());
    }

    private static ResourceNodeRuntime runtime(ResourceNodeDefinition definition) {
        return ResourceNodeRuntime.initial(new ResourceNodeId(UUID.randomUUID()), definition);
    }

    private static ResourceNodeDefinition common() {
        return definition(ResourceNodeType.COMMON, ResourceNodeSharing.PERSONAL, 2);
    }

    private static ResourceNodeDefinition rare() {
        return definition(ResourceNodeType.RARE, ResourceNodeSharing.SHARED, 1);
    }

    private static ResourceNodeDefinition definition(
            ResourceNodeType type, ResourceNodeSharing sharing, int charges) {
        return new ResourceNodeDefinition(
                DefinitionId.of("node.mining.test_ore"),
                LifeskillDiscipline.of("mining"),
                type,
                sharing,
                charges,
                40,
                Duration.ofSeconds(8),
                Duration.ofSeconds(30),
                2,
                Set.of("tool.pickaxe"));
    }

    private static ResourceNodeSlot sharedSlot(ResourceNodeTransition transition) {
        return transition.runtime().slots().get(ResourceNodeAccessKey.shared());
    }

    private static ResourceNodeSlot personalSlot(
            ResourceNodeTransition transition, CharacterId characterId) {
        return transition.runtime().slots().get(ResourceNodeAccessKey.personal(characterId));
    }

    private static CharacterId character() {
        return new CharacterId(UUID.randomUUID());
    }

    private static <T, E extends com.branz.mmorpg.api.result.ErrorCode> T success(
            Result<T, E> result) {
        return ((Result.Success<T, E>) result).value();
    }

    private static <T, E extends com.branz.mmorpg.api.result.ErrorCode> E failure(
            Result<T, E> result) {
        return ((Result.Failure<T, E>) result).error();
    }
}
