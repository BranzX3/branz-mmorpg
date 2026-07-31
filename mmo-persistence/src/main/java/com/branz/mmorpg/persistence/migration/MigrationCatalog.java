package com.branz.mmorpg.persistence.migration;

import com.branz.mmorpg.api.result.Result;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class MigrationCatalog {
    private final List<SqlMigration> migrations;

    private MigrationCatalog(List<SqlMigration> migrations) {
        this.migrations = List.copyOf(migrations);
    }

    public static Result<MigrationCatalog, MigrationErrorCode> from(
            Collection<SqlMigration> migrations) {
        Objects.requireNonNull(migrations, "migrations");
        List<SqlMigration> ordered = new ArrayList<>();
        Set<Integer> versions = new HashSet<>();
        for (SqlMigration migration : migrations) {
            if (migration == null) {
                return Result.failure(
                        MigrationErrorCode.MIGRATION_CATALOG_INVALID,
                        "Migration catalog contains a null entry.");
            }
            if (!versions.add(migration.version())) {
                return Result.failure(
                        MigrationErrorCode.MIGRATION_CATALOG_INVALID,
                        "Duplicate migration version: " + migration.version());
            }
            ordered.add(migration);
        }
        ordered.sort(Comparator.comparingInt(SqlMigration::version));
        return Result.success(new MigrationCatalog(ordered));
    }

    public List<SqlMigration> migrations() {
        return migrations;
    }
}
