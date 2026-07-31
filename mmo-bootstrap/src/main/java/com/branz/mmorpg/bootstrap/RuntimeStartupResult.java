package com.branz.mmorpg.bootstrap;

import java.util.Objects;
import java.util.Optional;

record RuntimeStartupResult(
        StartupDecision contentDecision,
        Optional<DatabaseRuntime> databaseRuntime,
        Optional<String> databaseFailure) {
    RuntimeStartupResult {
        Objects.requireNonNull(contentDecision, "contentDecision");
        Objects.requireNonNull(databaseRuntime, "databaseRuntime");
        Objects.requireNonNull(databaseFailure, "databaseFailure");
        if (databaseRuntime.isPresent() == databaseFailure.isPresent()) {
            throw new IllegalArgumentException(
                    "startup must contain either a database runtime or a failure");
        }
    }
}
