package com.branz.mmorpg.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

public final class BranzMmoPlugin extends JavaPlugin {
    private final BootstrapLifecycle lifecycle = new BootstrapLifecycle();

    @Override
    public void onEnable() {
        lifecycle.enable();
        getLogger().info("Branz MMO platform enabled; gameplay modules are not active yet.");
        if (Boolean.getBoolean("mmo.bootstrap.smoke-test")) {
            getLogger().info("Bootstrap smoke test requested; scheduling a clean shutdown.");
            getServer().getScheduler().runTaskLater(this, getServer()::shutdown, 1L);
        }
    }

    @Override
    public void onDisable() {
        lifecycle.disable();
        getLogger().info("Branz MMO platform disabled cleanly.");
    }
}
