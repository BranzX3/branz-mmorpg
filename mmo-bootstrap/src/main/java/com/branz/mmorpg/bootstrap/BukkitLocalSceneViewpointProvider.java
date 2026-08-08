package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.scenes.SceneErrorCode;
import com.branz.mmorpg.scenes.SceneSession;
import com.branz.mmorpg.scenes.actor.PreviewActorHandle;
import com.branz.mmorpg.scenes.viewpoint.SceneViewpointHandle;
import com.branz.mmorpg.scenes.viewpoint.SceneViewpointProvider;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Frames the Local Scene actor while retaining the player's authoritative world position. */
final class BukkitLocalSceneViewpointProvider implements SceneViewpointProvider {
    private final JavaPlugin plugin;

    BukkitLocalSceneViewpointProvider(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public Result<SceneViewpointHandle, SceneErrorCode> open(
            SceneSession session, PreviewActorHandle actorHandle) {
        Player player = plugin.getServer().getPlayer(session.playerId());
        Entity actor = plugin.getServer().getEntity(actorHandle.actorId());
        if (player == null || actor == null || player.getWorld() != actor.getWorld()) {
            return Result.failure(
                    SceneErrorCode.SCENE_PROVIDER_FAILURE,
                    "Local Scene viewpoint cannot resolve its player and actor.");
        }
        Location original = player.getLocation();
        Location eye = player.getEyeLocation();
        Location focus = actor.getLocation().add(0, 1.35, 0);
        Location framed = eye.clone().setDirection(focus.toVector().subtract(eye.toVector()));
        player.setRotation(framed.getYaw(), framed.getPitch());
        return Result.success(
                new SceneViewpointHandle(
                        session.sessionId(),
                        session.playerId(),
                        actorHandle.actorId(),
                        player.getWorld().getUID(),
                        original.getYaw(),
                        original.getPitch()));
    }

    @Override
    public void close(SceneViewpointHandle handle) {
        Objects.requireNonNull(handle, "handle");
        Player player = plugin.getServer().getPlayer(handle.viewerId());
        if (player != null
                && player.isOnline()
                && player.getWorld().getUID().equals(handle.worldId())) {
            player.setRotation(handle.originalYaw(), handle.originalPitch());
        }
    }
}
