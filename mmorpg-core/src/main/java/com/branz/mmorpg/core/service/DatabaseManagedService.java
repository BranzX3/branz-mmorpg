package com.branz.mmorpg.core.service;

import com.branz.mmorpg.core.lifecycle.ManagedService;
import com.branz.mmorpg.storage.DatabaseConfig;
import com.branz.mmorpg.storage.DatabaseManager;
import java.util.Objects;
import java.util.Optional;

public final class DatabaseManagedService implements ManagedService {
    private final boolean enabled;
    private final DatabaseConfig config;
    private DatabaseManager manager;

    public DatabaseManagedService(boolean enabled, DatabaseConfig config) {
        this.enabled = enabled;
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public String name() {
        return "database";
    }

    @Override
    public boolean required() {
        return enabled;
    }

    @Override
    public void start() {
        if (enabled) {
            manager = DatabaseManager.connect(config);
        }
    }

    @Override
    public void stop() {
        if (manager != null) {
            manager.close();
            manager = null;
        }
    }

    @Override
    public String detail() {
        if (!enabled) {
            return "disabled; persistent gameplay offline";
        }
        return manager == null ? "disconnected" : "connected; migrations applied";
    }

    public boolean connected() {
        return manager != null;
    }

    public Optional<DatabaseManager> manager() {
        return Optional.ofNullable(manager);
    }
}
