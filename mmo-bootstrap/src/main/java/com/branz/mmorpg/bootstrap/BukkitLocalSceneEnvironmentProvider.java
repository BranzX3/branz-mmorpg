package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.scenes.SceneErrorCode;
import com.branz.mmorpg.scenes.SceneSession;
import com.branz.mmorpg.scenes.environment.SceneEnvironmentHandle;
import com.branz.mmorpg.scenes.environment.SceneEnvironmentProvider;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Local Scene environment: lease the player's current world without teleporting them. */
final class BukkitLocalSceneEnvironmentProvider implements SceneEnvironmentProvider {
    private final JavaPlugin plugin;

    BukkitLocalSceneEnvironmentProvider(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public Result<SceneEnvironmentHandle, SceneErrorCode> open(SceneSession session) {
        Player player = plugin.getServer().getPlayer(session.playerId());
        if (player == null || !player.isOnline() || player.isDead()) {
            return Result.failure(
                    SceneErrorCode.SCENE_PROVIDER_FAILURE,
                    "Local Scene environment requires a live player.");
        }
        return Result.success(
                new SceneEnvironmentHandle(
                        session.sessionId(),
                        session.playerId(),
                        player.getWorld().getUID(),
                        "bukkit-local-world"));
    }

    @Override
    public void close(SceneEnvironmentHandle handle) {
        Objects.requireNonNull(handle, "handle");
    }
}
