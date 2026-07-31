package com.branz.mmorpg.bootstrap;

record ProjectionApplyReport(int kept, int removed, int materialized, int relocatedVanilla) {
    ProjectionApplyReport {
        if (kept < 0 || removed < 0 || materialized < 0 || relocatedVanilla < 0) {
            throw new IllegalArgumentException("projection report counters must not be negative");
        }
    }

    boolean changed() {
        return removed > 0 || materialized > 0 || relocatedVanilla > 0;
    }
}
