package com.branz.mmorpg.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresMigrationRunnerIntegrationTest {
    private EmbeddedPostgres postgres;
    private DataSource dataSource;

    @BeforeAll
    void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().setPort(0).start();
        dataSource = postgres.getPostgresDatabase();
    }

    @AfterAll
    void stopPostgres() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    @Test
    void appliesDefaultPostgresSchemaExactlyOnce() throws Exception {
        MigrationCatalog catalog = defaultCatalog();
        PostgresMigrationRunner runner = new PostgresMigrationRunner(dataSource);

        Result<MigrationReport, MigrationErrorCode> first = runner.migrate(catalog);
        Result<MigrationReport, MigrationErrorCode> second = runner.migrate(catalog);

        assertTrue(first.isSuccess());
        assertEquals(List.of(1, 2, 3, 4, 5), success(first).appliedVersions());
        assertTrue(second.isSuccess());
        assertEquals(List.of(), success(second).appliedVersions());
        assertEquals(5, scalarInt("SELECT COUNT(*) FROM mmo_schema_migrations"));
        assertEquals(
                1,
                scalarInt("SELECT COUNT(*) FROM pg_type WHERE typname = 'mmo_transaction_state'"));
        assertEquals(
                8,
                scalarInt(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = 'public' "
                                + "AND table_name IN ("
                                + "'character_leases', 'transaction_journal', "
                                + "'item_instance', 'commodity_lot', 'audit_log', "
                                + "'character_build_state', 'character_progression_track', "
                                + "'combat_progression_evidence')"));
    }

    @Test
    void refusesChangedOrUnknownAppliedMigrations() throws Exception {
        PostgresMigrationRunner runner = new PostgresMigrationRunner(dataSource);
        MigrationCatalog original =
                catalog(SqlMigration.of(1, "stable", "CREATE TABLE stable_table(id INTEGER)"));
        assertTrue(runner.migrate(original).isSuccess());

        Result<MigrationReport, MigrationErrorCode> changed =
                runner.migrate(
                        catalog(
                                SqlMigration.of(
                                        1, "stable", "CREATE TABLE stable_table(id BIGINT)")));
        assertEquals(MigrationErrorCode.MIGRATION_CHECKSUM_MISMATCH, failure(changed));

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    """
                    INSERT INTO mmo_schema_migrations(version, description, checksum)
                    VALUES (99, 'unknown', repeat('0', 64))
                    """);
        }
        Result<MigrationReport, MigrationErrorCode> unknown = runner.migrate(original);
        assertEquals(MigrationErrorCode.MIGRATION_UNKNOWN_APPLIED, failure(unknown));
    }

    @Test
    void failedBatchRollsBackDdlAndHistory() throws Exception {
        MigrationCatalog catalog =
                catalog(
                        SqlMigration.of(1, "good", "CREATE TABLE rolled_back_table(id INTEGER)"),
                        SqlMigration.of(2, "bad", "THIS IS NOT SQL"));

        Result<MigrationReport, MigrationErrorCode> result =
                new PostgresMigrationRunner(dataSource).migrate(catalog);

        assertFalse(result.isSuccess());
        assertEquals(MigrationErrorCode.MIGRATION_APPLY_FAILED, failure(result));
        assertNull(scalarObject("SELECT to_regclass('public.rolled_back_table')"));
        assertNull(scalarObject("SELECT to_regclass('public.mmo_schema_migrations')"));
    }

    @Test
    void advisoryLockSerializesConcurrentStartup() {
        MigrationCatalog catalog =
                catalog(
                        SqlMigration.of(
                                1,
                                "concurrent",
                                """
                                SELECT pg_sleep(0.2);
                                CREATE TABLE concurrent_table(id INTEGER);
                                """));
        PostgresMigrationRunner runner = new PostgresMigrationRunner(dataSource);

        CompletableFuture<Result<MigrationReport, MigrationErrorCode>> first =
                CompletableFuture.supplyAsync(() -> runner.migrate(catalog));
        CompletableFuture<Result<MigrationReport, MigrationErrorCode>> second =
                CompletableFuture.supplyAsync(() -> runner.migrate(catalog));
        MigrationReport firstReport = success(first.join());
        MigrationReport secondReport = success(second.join());

        assertEquals(
                1, firstReport.appliedVersions().size() + secondReport.appliedVersions().size());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM mmo_schema_migrations"));
    }

    private MigrationCatalog defaultCatalog() {
        Result<MigrationCatalog, MigrationErrorCode> loaded =
                ClasspathMigrationCatalog.loadDefault();
        assertTrue(loaded.isSuccess());
        return ((Result.Success<MigrationCatalog, MigrationErrorCode>) loaded).value();
    }

    private static MigrationCatalog catalog(SqlMigration... migrations) {
        Result<MigrationCatalog, MigrationErrorCode> result =
                MigrationCatalog.from(List.of(migrations));
        assertTrue(result.isSuccess());
        return ((Result.Success<MigrationCatalog, MigrationErrorCode>) result).value();
    }

    private static MigrationReport success(Result<MigrationReport, MigrationErrorCode> result) {
        assertTrue(result.isSuccess(), () -> failureDetail(result));
        return ((Result.Success<MigrationReport, MigrationErrorCode>) result).value();
    }

    private static MigrationErrorCode failure(Result<MigrationReport, MigrationErrorCode> result) {
        assertFalse(result.isSuccess());
        return ((Result.Failure<MigrationReport, MigrationErrorCode>) result).error();
    }

    private static String failureDetail(Result<MigrationReport, MigrationErrorCode> result) {
        if (result instanceof Result.Failure<MigrationReport, MigrationErrorCode> failure) {
            return failure.error() + ": " + failure.detail();
        }
        return "";
    }

    private int scalarInt(String sql) {
        return ((Number) scalarObject(sql)).intValue();
    }

    private Object scalarObject(String sql) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getObject(1);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
