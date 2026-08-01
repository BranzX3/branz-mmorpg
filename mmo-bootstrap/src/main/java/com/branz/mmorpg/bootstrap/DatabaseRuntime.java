package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.lease.CharacterLeaseRepository;
import com.branz.mmorpg.persistence.lease.JdbcCharacterLeaseRepository;
import com.branz.mmorpg.persistence.lease.ServerInstanceId;
import com.branz.mmorpg.persistence.migration.ClasspathMigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationErrorCode;
import com.branz.mmorpg.persistence.migration.PostgresMigrationRunner;
import com.branz.mmorpg.persistence.progression.JdbcKnowledgeProgressionRepository;
import com.branz.mmorpg.persistence.progression.JdbcProgressionEvidenceRepository;
import com.branz.mmorpg.persistence.progression.KnowledgeProgressionRepository;
import com.branz.mmorpg.persistence.progression.ProgressionEvidenceRepository;
import com.branz.mmorpg.persistence.transaction.BossEncounterStateRepository;
import com.branz.mmorpg.persistence.transaction.CharacterBuildRepository;
import com.branz.mmorpg.persistence.transaction.CharacterExpeditionStateRepository;
import com.branz.mmorpg.persistence.transaction.JdbcBossEncounterStateRepository;
import com.branz.mmorpg.persistence.transaction.JdbcCharacterBuildRepository;
import com.branz.mmorpg.persistence.transaction.JdbcCharacterExpeditionStateRepository;
import com.branz.mmorpg.persistence.transaction.JdbcReconciliationScanner;
import com.branz.mmorpg.persistence.transaction.JdbcValueTransactionService;
import com.branz.mmorpg.persistence.transaction.ReconciliationScanner;
import com.branz.mmorpg.persistence.transaction.ValueTransactionService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/** Owns the local/external PostgreSQL lifecycle and repository graph. */
final class DatabaseRuntime implements AutoCloseable {
    private final DatabaseSettings settings;
    private final EmbeddedPostgres embedded;
    private final HikariDataSource pool;
    private final CharacterLeaseRepository leases;
    private final ValueTransactionService values;
    private final CharacterBuildRepository builds;
    private final CharacterExpeditionStateRepository expeditionStates;
    private final BossEncounterStateRepository bossEncounters;
    private final ProgressionEvidenceRepository progression;
    private final KnowledgeProgressionRepository knowledge;
    private final ReconciliationScanner reconciliation;
    private final ServerInstanceId serverInstanceId;

    private DatabaseRuntime(
            DatabaseSettings settings,
            EmbeddedPostgres embedded,
            HikariDataSource pool,
            DataSource dataSource) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.embedded = embedded;
        this.pool = pool;
        leases = new JdbcCharacterLeaseRepository(dataSource);
        values = new JdbcValueTransactionService(dataSource);
        builds = new JdbcCharacterBuildRepository(dataSource);
        expeditionStates = new JdbcCharacterExpeditionStateRepository(dataSource);
        bossEncounters = new JdbcBossEncounterStateRepository(dataSource);
        progression = new JdbcProgressionEvidenceRepository(dataSource);
        knowledge = new JdbcKnowledgeProgressionRepository(dataSource);
        reconciliation = new JdbcReconciliationScanner(dataSource);
        serverInstanceId =
                new ServerInstanceId(
                        settings.environment().toLowerCase(java.util.Locale.ROOT)
                                + "-"
                                + UUID.randomUUID());
    }

    static DatabaseRuntime start(DatabaseSettings settings) throws IOException {
        Objects.requireNonNull(settings, "settings");
        if (settings.mode() == DatabaseMode.DISABLED) {
            throw new IllegalArgumentException("disabled database has no runtime");
        }

        EmbeddedPostgres embedded = null;
        HikariDataSource pool = null;
        try {
            DataSource dataSource;
            if (settings.mode() == DatabaseMode.EMBEDDED_LOCAL) {
                embedded =
                        EmbeddedPostgres.builder()
                                .setPort(0)
                                .setDataDirectory(settings.embeddedDataDirectory())
                                .setCleanDataDirectory(false)
                                .start();
                dataSource = embedded.getPostgresDatabase();
            } else {
                HikariConfig hikari = new HikariConfig();
                hikari.setJdbcUrl(settings.jdbcUrl());
                hikari.setUsername(settings.username());
                hikari.setPassword(settings.password());
                hikari.setMaximumPoolSize(settings.maximumPoolSize());
                hikari.setConnectionTimeout(settings.connectionTimeout().toMillis());
                hikari.setPoolName("BranzMMO-PostgreSQL");
                pool = new HikariDataSource(hikari);
                dataSource = pool;
            }
            migrateIfEnabled(settings, dataSource);
            return new DatabaseRuntime(settings, embedded, pool, dataSource);
        } catch (IOException | RuntimeException exception) {
            closeQuietly(pool);
            closeQuietly(embedded);
            throw exception;
        }
    }

    DatabaseSettings settings() {
        return settings;
    }

    CharacterLeaseRepository leases() {
        return leases;
    }

    ValueTransactionService values() {
        return values;
    }

    CharacterBuildRepository builds() {
        return builds;
    }

    CharacterExpeditionStateRepository expeditionStates() {
        return expeditionStates;
    }

    BossEncounterStateRepository bossEncounters() {
        return bossEncounters;
    }

    ProgressionEvidenceRepository progression() {
        return progression;
    }

    KnowledgeProgressionRepository knowledge() {
        return knowledge;
    }

    ReconciliationScanner reconciliation() {
        return reconciliation;
    }

    ServerInstanceId serverInstanceId() {
        return serverInstanceId;
    }

    @Override
    public void close() {
        closeQuietly(pool);
        closeQuietly(embedded);
    }

    private static void migrateIfEnabled(DatabaseSettings settings, DataSource dataSource) {
        if (!settings.runMigrations()) {
            return;
        }
        Result<MigrationCatalog, MigrationErrorCode> loaded =
                ClasspathMigrationCatalog.loadDefault();
        if (loaded instanceof Result.Failure<MigrationCatalog, MigrationErrorCode> failure) {
            throw new IllegalStateException(failure.error().code() + ": " + failure.detail());
        }
        MigrationCatalog catalog =
                ((Result.Success<MigrationCatalog, MigrationErrorCode>) loaded).value();
        Result<?, MigrationErrorCode> migrated =
                new PostgresMigrationRunner(dataSource).migrate(catalog);
        if (migrated instanceof Result.Failure<?, MigrationErrorCode> failure) {
            throw new IllegalStateException(failure.error().code() + ": " + failure.detail());
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Startup/shutdown recovery cannot do more than log at the owning plugin layer.
        }
    }
}
