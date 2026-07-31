package com.branz.mmorpg.persistence.migration;

import java.util.List;

public record MigrationReport(int currentVersion, List<Integer> appliedVersions) {
    public MigrationReport {
        if (currentVersion < 0) {
            throw new IllegalArgumentException("currentVersion cannot be negative");
        }
        appliedVersions = List.copyOf(appliedVersions);
    }
}
