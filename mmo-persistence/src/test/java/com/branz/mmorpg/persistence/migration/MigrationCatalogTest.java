package com.branz.mmorpg.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import java.util.List;
import org.junit.jupiter.api.Test;

class MigrationCatalogTest {
    @Test
    void loadsDeterministicDefaultCatalogAndChecksumsSql() {
        Result<MigrationCatalog, MigrationErrorCode> loaded =
                ClasspathMigrationCatalog.loadDefault();

        assertTrue(loaded.isSuccess());
        MigrationCatalog catalog =
                ((Result.Success<MigrationCatalog, MigrationErrorCode>) loaded).value();
        assertEquals(4, catalog.migrations().size());
        assertEquals(1, catalog.migrations().getFirst().version());
        assertEquals(4, catalog.migrations().getLast().version());
        assertEquals(64, catalog.migrations().getFirst().checksum().length());
        assertTrue(catalog.migrations().getFirst().sql().contains("character_leases"));
        assertTrue(catalog.migrations().get(1).sql().contains("commodity_lot"));
        assertTrue(catalog.migrations().get(2).sql().contains("audit_log"));
        assertTrue(catalog.migrations().getLast().sql().contains("character_build_state"));
    }

    @Test
    void rejectsDuplicateVersions() {
        Result<MigrationCatalog, MigrationErrorCode> result =
                MigrationCatalog.from(
                        List.of(
                                SqlMigration.of(1, "first", "SELECT 1"),
                                SqlMigration.of(1, "duplicate", "SELECT 2")));

        assertFalse(result.isSuccess());
        assertEquals(
                MigrationErrorCode.MIGRATION_CATALOG_INVALID,
                ((Result.Failure<MigrationCatalog, MigrationErrorCode>) result).error());
    }
}
