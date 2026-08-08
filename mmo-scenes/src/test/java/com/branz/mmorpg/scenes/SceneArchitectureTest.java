package com.branz.mmorpg.scenes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.equipment.EquipmentLoadout;
import com.branz.mmorpg.scenes.actor.PreviewActorHandle;
import com.branz.mmorpg.scenes.actor.PreviewActorProvider;
import com.branz.mmorpg.scenes.environment.SceneEnvironmentHandle;
import com.branz.mmorpg.scenes.environment.SceneEnvironmentProvider;
import com.branz.mmorpg.scenes.overlay.MenuOverlay;
import com.branz.mmorpg.scenes.overlay.MenuOverlayHandle;
import com.branz.mmorpg.scenes.viewpoint.SceneViewpointHandle;
import com.branz.mmorpg.scenes.viewpoint.SceneViewpointProvider;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SceneArchitectureTest {
    @Test
    void canonicalProfilesDeclareV1TopologyAndModeAuthority() {
        SceneProfile local = SceneProfiles.localCharacterHub();
        assertEquals(SceneTopology.LOCAL_CHARACTER, local.topology());
        assertTrue(local.locksNormalMovement());
        assertEquals(
                SceneInteractionModel.READ_ONLY,
                local.requireMode(SceneMode.CHARACTER_INFORMATION).interactionModel());
        assertFalse(local.requireMode(SceneMode.COMBAT_ARTS).restContextRequiredForConfirm());
        assertTrue(local.requireMode(SceneMode.FORMS).restContextRequiredForConfirm());
        assertTrue(local.requireMode(SceneMode.MAGIC_ATTUNEMENT).restContextRequiredForConfirm());
        assertEquals(
                SceneTopology.FIXED_PRIVATE,
                SceneProfiles.fixedPrivateCharacterCreation().topology());
        assertEquals(
                SceneInteractionModel.PREVIEW_COMMIT,
                SceneProfiles.fixedPrivateCharacterCreation()
                        .requireMode(SceneMode.CHARACTER_CREATION)
                        .interactionModel());
        assertEquals(
                SceneInteractionModel.DIALOGUE,
                SceneProfiles.narrativeDialogue()
                        .requireMode(SceneMode.IMPORTANT_DIALOGUE)
                        .interactionModel());
        assertEquals(
                SceneInteractionModel.CINEMATIC,
                SceneProfiles.narrativeDialogue()
                        .requireMode(SceneMode.CUTSCENE)
                        .interactionModel());
    }

    @Test
    void readOnlyModeRejectsPreviewMutation() {
        SceneSessionManager manager = new SceneSessionManager(Clock.systemUTC());
        UUID playerId = UUID.randomUUID();
        SceneSession session = success(manager.open(playerId, EquipmentLoadout.empty()));

        Result<SceneSession, SceneErrorCode> rejected =
                manager.previewBuild(
                        playerId,
                        session.sessionId(),
                        com.branz.mmorpg.progression.build.CharacterBuild.initial());

        assertFalse(rejected.isSuccess());
        assertEquals(
                SceneErrorCode.SCENE_INTERACTION_MODEL_MISMATCH,
                ((Result.Failure<SceneSession, SceneErrorCode>) rejected).error());
    }

    @Test
    void engineAcquiresAndRecoversPresentationInDeterministicOrder() {
        List<String> events = new ArrayList<>();
        UUID worldId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        SceneSessionManager manager = new SceneSessionManager(Clock.systemUTC());
        SceneSession session = success(manager.open(UUID.randomUUID(), EquipmentLoadout.empty()));
        SceneEngine engine =
                new SceneEngine(
                        environment(events, worldId),
                        actor(events, actorId),
                        viewpoint(events, worldId),
                        overlay(events),
                        new SceneRecovery());

        assertTrue(engine.open(session).isSuccess());
        assertEquals(
                List.of("environment.open", "actor.open", "viewpoint.open", "overlay.open"),
                events);

        assertTrue(engine.close(session.sessionId()));
        assertFalse(engine.close(session.sessionId()));
        assertEquals(
                List.of(
                        "environment.open",
                        "actor.open",
                        "viewpoint.open",
                        "overlay.open",
                        "overlay.close",
                        "viewpoint.close",
                        "actor.close",
                        "environment.close"),
                events);
    }

    @Test
    void engineRecoversAcquiredProvidersWhenOpeningFailsPartWay() {
        List<String> events = new ArrayList<>();
        UUID worldId = UUID.randomUUID();
        SceneSession session =
                success(
                        new SceneSessionManager(Clock.systemUTC())
                                .open(UUID.randomUUID(), EquipmentLoadout.empty()));
        SceneViewpointProvider unavailableViewpoint =
                new SceneViewpointProvider() {
                    @Override
                    public Result<SceneViewpointHandle, SceneErrorCode> open(
                            SceneSession ignored, PreviewActorHandle actor) {
                        events.add("viewpoint.open");
                        return Result.failure(
                                SceneErrorCode.SCENE_PROVIDER_FAILURE, "viewpoint unavailable");
                    }

                    @Override
                    public void close(SceneViewpointHandle handle) {
                        events.add("viewpoint.close");
                    }
                };
        SceneEngine engine =
                new SceneEngine(
                        environment(events, worldId),
                        actor(events, UUID.randomUUID()),
                        unavailableViewpoint,
                        overlay(events),
                        new SceneRecovery());

        assertFalse(engine.open(session).isSuccess());
        assertTrue(engine.find(session.sessionId()).isEmpty());
        assertEquals(
                List.of(
                        "environment.open",
                        "actor.open",
                        "viewpoint.open",
                        "actor.close",
                        "environment.close"),
                events);
    }

    private static SceneEnvironmentProvider environment(List<String> events, UUID worldId) {
        return new SceneEnvironmentProvider() {
            @Override
            public Result<SceneEnvironmentHandle, SceneErrorCode> open(SceneSession session) {
                events.add("environment.open");
                return Result.success(
                        new SceneEnvironmentHandle(
                                session.sessionId(), session.playerId(), worldId, "test"));
            }

            @Override
            public void close(SceneEnvironmentHandle handle) {
                events.add("environment.close");
            }
        };
    }

    private static PreviewActorProvider actor(List<String> events, UUID actorId) {
        return new PreviewActorProvider() {
            @Override
            public Result<PreviewActorHandle, SceneErrorCode> open(SceneSession session) {
                events.add("actor.open");
                return Result.success(
                        new PreviewActorHandle(session.sessionId(), session.playerId(), actorId));
            }

            @Override
            public Result<PreviewActorHandle, SceneErrorCode> update(
                    PreviewActorHandle handle, SceneSession session) {
                return Result.success(handle);
            }

            @Override
            public void close(PreviewActorHandle handle) {
                events.add("actor.close");
            }
        };
    }

    private static SceneViewpointProvider viewpoint(List<String> events, UUID worldId) {
        return new SceneViewpointProvider() {
            @Override
            public Result<SceneViewpointHandle, SceneErrorCode> open(
                    SceneSession session, PreviewActorHandle actor) {
                events.add("viewpoint.open");
                return Result.success(
                        new SceneViewpointHandle(
                                session.sessionId(),
                                session.playerId(),
                                actor.actorId(),
                                worldId,
                                0,
                                0));
            }

            @Override
            public void close(SceneViewpointHandle handle) {
                events.add("viewpoint.close");
            }
        };
    }

    private static MenuOverlay overlay(List<String> events) {
        return new MenuOverlay() {
            @Override
            public Result<MenuOverlayHandle, SceneErrorCode> open(SceneSession session) {
                events.add("overlay.open");
                return Result.success(
                        new MenuOverlayHandle(session.sessionId(), session.playerId(), "test"));
            }

            @Override
            public Result<MenuOverlayHandle, SceneErrorCode> update(
                    MenuOverlayHandle handle, SceneSession session) {
                return Result.success(handle);
            }

            @Override
            public void close(MenuOverlayHandle handle) {
                events.add("overlay.close");
            }
        };
    }

    private static <T> T success(Result<T, SceneErrorCode> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<T, SceneErrorCode>) result).value();
    }
}
