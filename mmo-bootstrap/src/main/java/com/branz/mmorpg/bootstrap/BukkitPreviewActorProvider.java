package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.scenes.SceneErrorCode;
import com.branz.mmorpg.scenes.SceneSession;
import com.branz.mmorpg.scenes.actor.PreviewActorHandle;
import com.branz.mmorpg.scenes.actor.PreviewActorProvider;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Paper 26.2 owner-only Mannequin actor for the Local Character Scene. */
final class BukkitPreviewActorProvider implements PreviewActorProvider {
    static final String ACTOR_TAG = "branzmmo.scene.preview_actor";
    private static final List<Double> PLACEMENT_DISTANCES = List.of(2.75, 2.35, 1.95);
    private static final List<Double> PLACEMENT_YAW_OFFSETS =
            List.of(0.0, 35.0, -35.0, 70.0, -70.0, 180.0);

    private final JavaPlugin plugin;
    private final BiConsumer<Mannequin, SceneSession> appearanceUpdater;
    private final Map<UUID, UUID> viewersByActor = new HashMap<>();

    BukkitPreviewActorProvider(
            JavaPlugin plugin, BiConsumer<Mannequin, SceneSession> appearanceUpdater) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.appearanceUpdater = Objects.requireNonNull(appearanceUpdater, "appearanceUpdater");
    }

    @Override
    public Result<PreviewActorHandle, SceneErrorCode> open(SceneSession session) {
        Player player = plugin.getServer().getPlayer(session.playerId());
        if (player == null || !player.isOnline()) {
            return Result.failure(
                    SceneErrorCode.SCENE_PREVIEW_UNAVAILABLE,
                    "Preview actor requires a live player.");
        }
        Location placement = placement(player).orElse(null);
        if (placement == null) {
            return Result.failure(
                    SceneErrorCode.SCENE_PREVIEW_UNAVAILABLE,
                    "No safe visible position is available for the character preview actor.");
        }
        Mannequin actor =
                player.getWorld()
                        .spawn(
                                placement,
                                Mannequin.class,
                                mannequin -> {
                                    mannequin.setProfile(
                                            ResolvableProfile.resolvableProfile(
                                                    player.getPlayerProfile()));
                                    mannequin.setImmovable(true);
                                    mannequin.setInvulnerable(true);
                                    mannequin.setGravity(false);
                                    mannequin.setCollidable(false);
                                    mannequin.setSilent(true);
                                    mannequin.setPersistent(false);
                                    mannequin.addScoreboardTag(ACTOR_TAG);
                                    appearanceUpdater.accept(mannequin, session);
                                });
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                viewer.hideEntity(plugin, actor);
            }
        }
        viewersByActor.put(actor.getUniqueId(), player.getUniqueId());
        return Result.success(
                new PreviewActorHandle(
                        session.sessionId(), session.playerId(), actor.getUniqueId()));
    }

    @Override
    public Result<PreviewActorHandle, SceneErrorCode> update(
            PreviewActorHandle handle, SceneSession session) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(session, "session");
        if (!handle.sessionId().equals(session.sessionId())
                || !handle.viewerId().equals(session.playerId())) {
            return Result.failure(
                    SceneErrorCode.SCENE_STALE_SESSION,
                    "Preview actor belongs to another Scene session.");
        }
        Entity entity = plugin.getServer().getEntity(handle.actorId());
        if (!(entity instanceof Mannequin actor) || !actor.isValid()) {
            return Result.failure(
                    SceneErrorCode.SCENE_PREVIEW_UNAVAILABLE, "Preview actor is no longer valid.");
        }
        appearanceUpdater.accept(actor, session);
        return Result.success(handle);
    }

    @Override
    public void close(PreviewActorHandle handle) {
        Objects.requireNonNull(handle, "handle");
        viewersByActor.remove(handle.actorId());
        Entity actor = plugin.getServer().getEntity(handle.actorId());
        if (actor != null) {
            actor.remove();
        }
    }

    void hideActiveActorsFrom(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        plugin.getServer().getWorlds().stream()
                .flatMap(world -> world.getEntitiesByClass(Mannequin.class).stream())
                .filter(actor -> actor.getScoreboardTags().contains(ACTOR_TAG))
                .forEach(actor -> viewer.hideEntity(plugin, actor));
    }

    Optional<UUID> viewerForActor(UUID actorId) {
        return Optional.ofNullable(viewersByActor.get(Objects.requireNonNull(actorId, "actorId")));
    }

    private static Optional<Location> placement(Player player) {
        Location origin = player.getLocation();
        Vector forward = origin.getDirection().setY(0);
        if (forward.lengthSquared() < 0.0001) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        for (double yawOffset : PLACEMENT_YAW_OFFSETS) {
            Vector direction = forward.clone().rotateAroundY(Math.toRadians(-yawOffset));
            for (double distance : PLACEMENT_DISTANCES) {
                Location candidate = origin.clone().add(direction.clone().multiply(distance));
                Vector towardPlayer = origin.toVector().subtract(candidate.toVector()).setY(0);
                candidate.setDirection(towardPlayer);
                Location focus = candidate.clone().add(0, 1.35, 0);
                Vector sight = focus.toVector().subtract(player.getEyeLocation().toVector());
                boolean visible =
                        player.getWorld()
                                        .rayTraceBlocks(
                                                player.getEyeLocation(),
                                                sight.clone().normalize(),
                                                Math.max(0, sight.length() - 0.15),
                                                FluidCollisionMode.NEVER,
                                                true)
                                == null;
                if (visible
                        && candidate.getBlock().isPassable()
                        && candidate.clone().add(0, 1, 0).getBlock().isPassable()
                        && !candidate.clone().subtract(0, 0.05, 0).getBlock().isPassable()) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }
}
