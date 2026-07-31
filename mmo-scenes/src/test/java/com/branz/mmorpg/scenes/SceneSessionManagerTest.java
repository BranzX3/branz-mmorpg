package com.branz.mmorpg.scenes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.equipment.EquipmentLoadout;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.items.quiver.QuiverPreparation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SceneSessionManagerTest {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-31T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void closeDiscardsPreviewWithoutCallingCommitter() {
        SceneSessionManager manager = new SceneSessionManager(CLOCK);
        UUID playerId = UUID.randomUUID();
        SceneSession opened = success(manager.open(playerId, EquipmentLoadout.empty()));
        ItemId previewItem = new ItemId(UUID.randomUUID());
        SceneSession previewed =
                success(
                        manager.previewEquipment(
                                playerId,
                                opened.sessionId(),
                                EquipmentSlot.COSMETIC_HEAD,
                                Optional.of(previewItem)));
        assertTrue(previewed.hasUncommittedPreview());

        ClosedSceneSession closed =
                success(manager.close(playerId, opened.sessionId(), SceneCloseReason.DISCONNECT));

        assertTrue(closed.session().hasUncommittedPreview());
        assertEquals(SceneCloseReason.DISCONNECT, closed.reason());
        assertTrue(manager.find(playerId).isEmpty());
    }

    @Test
    void failedCommitLeavesCommittedStateUntouchedAndSuccessfulCommitAdvancesIt() {
        SceneSessionManager manager = new SceneSessionManager(CLOCK);
        UUID playerId = UUID.randomUUID();
        SceneSession opened = success(manager.open(playerId, EquipmentLoadout.empty()));
        ItemId itemId = new ItemId(UUID.randomUUID());
        success(
                manager.previewEquipment(
                        playerId, opened.sessionId(), EquipmentSlot.RING_ONE, Optional.of(itemId)));

        Result<SceneSession, SceneErrorCode> rejected =
                manager.confirm(
                        playerId,
                        opened.sessionId(),
                        ignored ->
                                Result.failure(
                                        SceneErrorCode.SCENE_COMMIT_REJECTED, "ownership changed"));
        assertFalse(rejected.isSuccess());
        SceneSession afterFailure = manager.find(playerId).orElseThrow();
        assertTrue(afterFailure.hasUncommittedPreview());
        assertTrue(afterFailure.committedState().equipment().equipped().isEmpty());

        SceneSession committed =
                success(
                        manager.confirm(
                                playerId,
                                opened.sessionId(),
                                session -> Result.success(session.previewState())));
        assertFalse(committed.hasUncommittedPreview());
        assertEquals(
                itemId,
                committed.committedState().equipment().item(EquipmentSlot.RING_ONE).orElseThrow());
    }

    @Test
    void staleCallbacksCannotMutateANewerSession() {
        SceneSessionManager manager = new SceneSessionManager(CLOCK);
        UUID playerId = UUID.randomUUID();
        SceneSession first = success(manager.open(playerId, EquipmentLoadout.empty()));
        success(manager.close(playerId, first.sessionId(), SceneCloseReason.EXIT));
        SceneSession second = success(manager.open(playerId, EquipmentLoadout.empty()));

        Result<SceneSession, SceneErrorCode> stale =
                manager.changeMode(playerId, first.sessionId(), SceneMode.EQUIPMENT);

        assertFalse(stale.isSuccess());
        assertEquals(
                SceneErrorCode.SCENE_STALE_SESSION,
                ((Result.Failure<SceneSession, SceneErrorCode>) stale).error());
        assertEquals(SceneMode.HUB, manager.find(playerId).orElseThrow().mode());
        assertFalse(first.sessionId().equals(second.sessionId()));
    }

    @Test
    void quiverPreparationParticipatesInPreviewDiscardAndCommit() {
        SceneSessionManager manager = new SceneSessionManager(CLOCK);
        UUID playerId = UUID.randomUUID();
        SceneSession opened = success(manager.open(playerId, EquipmentLoadout.empty()));
        QuiverPreparation prepared =
                QuiverPreparation.empty()
                        .toggle(
                                com.branz.mmorpg.api.identity.DefinitionId.of("ammo.test.arrow"),
                                4);

        SceneSession previewed =
                success(manager.previewQuiverPreparation(playerId, opened.sessionId(), prepared));
        assertTrue(previewed.hasUncommittedPreview());
        assertEquals(
                QuiverPreparation.empty(),
                success(manager.back(playerId, opened.sessionId()))
                        .previewState()
                        .quiverPreparation());

        success(manager.changeMode(playerId, opened.sessionId(), SceneMode.EQUIPMENT));
        success(manager.previewQuiverPreparation(playerId, opened.sessionId(), prepared));
        SceneSession committed =
                success(
                        manager.confirm(
                                playerId,
                                opened.sessionId(),
                                session -> Result.success(session.previewState())));
        assertEquals(prepared, committed.committedState().quiverPreparation());
    }

    @Test
    void quiverLotTransferRemainsPreviewOnlyUntilCommitterReturnsDatabaseTruth() {
        SceneSessionManager manager = new SceneSessionManager(CLOCK);
        UUID playerId = UUID.randomUUID();
        SceneSession opened = success(manager.open(playerId, EquipmentLoadout.empty()));
        QuiverAmmoTransferPreview transfer =
                new QuiverAmmoTransferPreview(UUID.randomUUID(), 64, QuiverTransferDirection.STORE);

        SceneSession previewed =
                success(manager.previewQuiverTransfer(playerId, opened.sessionId(), transfer));
        assertTrue(previewed.hasUncommittedPreview());
        assertEquals(transfer, previewed.previewState().quiverTransfer().orElseThrow());
        assertTrue(previewed.committedState().quiverTransfer().isEmpty());

        SceneSession committed =
                success(
                        manager.confirm(
                                playerId,
                                opened.sessionId(),
                                session ->
                                        Result.success(
                                                new ScenePreviewState(
                                                        session.committedState().equipment(),
                                                        session.committedState()
                                                                .quiverPreparation()))));
        assertFalse(committed.hasUncommittedPreview());
        assertTrue(committed.committedState().quiverTransfer().isEmpty());
    }

    private static <T> T success(Result<T, SceneErrorCode> result) {
        assertTrue(
                result.isSuccess(),
                () -> {
                    if (result instanceof Result.Failure<T, SceneErrorCode> failure) {
                        return failure.error() + ": " + failure.detail();
                    }
                    return "";
                });
        return ((Result.Success<T, SceneErrorCode>) result).value();
    }
}
