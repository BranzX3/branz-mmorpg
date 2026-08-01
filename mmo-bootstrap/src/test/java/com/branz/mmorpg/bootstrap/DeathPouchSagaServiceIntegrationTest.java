package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.migration.ClasspathMigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationErrorCode;
import com.branz.mmorpg.persistence.migration.PostgresMigrationRunner;
import com.branz.mmorpg.persistence.transaction.CarriedWalletOperationKind;
import com.branz.mmorpg.persistence.transaction.DeathPouchRecord;
import com.branz.mmorpg.persistence.transaction.DeathPouchState;
import com.branz.mmorpg.persistence.transaction.JdbcCarriedWalletService;
import com.branz.mmorpg.persistence.transaction.JdbcDeathPouchRepository;
import com.branz.mmorpg.persistence.transaction.TransactionErrorCode;
import com.branz.mmorpg.worldloop.death.DeathPouchContext;
import com.branz.mmorpg.worldloop.death.DeathPouchDraft;
import com.branz.mmorpg.worldloop.death.DeathPouchEngine;
import com.branz.mmorpg.worldloop.death.DeathPouchLocation;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeathPouchSagaServiceIntegrationTest {
    private static final String CONTENT_VERSION = "test-v1";

    private EmbeddedPostgres postgres;
    private DataSource dataSource;
    private JdbcCarriedWalletService wallet;
    private DurableDeathPouchStore store;
    private DeathPouchSagaService saga;

    @BeforeAll
    void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().setPort(0).start();
        dataSource = postgres.getPostgresDatabase();
        Result<MigrationCatalog, MigrationErrorCode> loaded =
                ClasspathMigrationCatalog.loadDefault();
        assertTrue(loaded.isSuccess());
        assertTrue(
                new PostgresMigrationRunner(dataSource)
                        .migrate(
                                ((Result.Success<MigrationCatalog, MigrationErrorCode>) loaded)
                                        .value())
                        .isSuccess());
    }

    @AfterAll
    void stopPostgres() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void resetState() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("TRUNCATE TABLE transaction_journal CASCADE");
        }
        wallet = new JdbcCarriedWalletService(dataSource);
        store =
                new DurableDeathPouchStore(
                        new JdbcDeathPouchRepository(dataSource), CONTENT_VERSION);
        saga = new DeathPouchSagaService(store, wallet, CONTENT_VERSION);
    }

    @Test
    void activationAndRecoveryMoveExactlyTenPercentAndReplayExactly() {
        CharacterId owner = fundedCharacter(1_000);
        DeathPouchDraft draft = draft(owner, Instant.parse("2026-08-01T00:00:00Z"));

        DeathPouchRecord active = success(saga.activate(draft));
        assertEquals(DeathPouchState.ACTIVE, active.state());
        assertEquals(100, active.amount());
        assertEquals(900, success(wallet.balance(owner)).balance());

        DeathPouchRecord recovered = success(saga.recover(active));
        assertEquals(DeathPouchState.RECOVERED, recovered.state());
        assertEquals(1_000, success(wallet.balance(owner)).balance());

        assertEquals(DeathPouchState.RECOVERED, success(saga.recover(active)).state());
        assertEquals(1_000, success(wallet.balance(owner)).balance());
        assertTrue(success(store.active(owner)).isEmpty());
    }

    @Test
    void restartAfterConfirmedDebitActivatesWithoutSecondDebit() {
        CharacterId owner = fundedCharacter(1_000);
        DeathPouchDraft draft = draft(owner, Instant.parse("2026-08-01T00:00:00Z"));
        DeathPouchRecord pending = success(store.create(draft));
        success(
                saga.adjust(
                        draft.walletDebitOperationId(),
                        owner,
                        CarriedWalletOperationKind.DEBIT,
                        draft.amount()));

        DeathPouchSagaService restarted = new DeathPouchSagaService(store, wallet, CONTENT_VERSION);
        DeathPouchRecord active =
                success(restarted.resume(pending, Instant.parse("2026-08-02T00:00:00Z")));

        assertEquals(DeathPouchState.ACTIVE, active.state());
        assertEquals(900, success(wallet.balance(owner)).balance());
        assertTrue(wallet.findOperation(draft.walletDebitOperationId()).isSuccess());
    }

    @Test
    void expiredUnconfirmedIntentDoesNotDebit() {
        CharacterId owner = fundedCharacter(1_000);
        DeathPouchDraft draft = draft(owner, Instant.parse("2026-07-01T00:00:00Z"));
        DeathPouchRecord pending = success(store.create(draft));

        DeathPouchRecord expired =
                success(saga.expire(pending, Instant.parse("2026-07-09T00:00:00Z")));

        assertEquals(DeathPouchState.EXPIRED, expired.state());
        assertEquals(1_000, success(wallet.balance(owner)).balance());
        assertFalse(success(wallet.findOperation(draft.walletDebitOperationId())).isPresent());
    }

    @Test
    void expiredConfirmedDebitIsRecognizedWithoutSecondDebit() {
        CharacterId owner = fundedCharacter(1_000);
        DeathPouchDraft draft = draft(owner, Instant.parse("2026-07-01T00:00:00Z"));
        DeathPouchRecord pending = success(store.create(draft));
        success(
                saga.adjust(
                        draft.walletDebitOperationId(),
                        owner,
                        CarriedWalletOperationKind.DEBIT,
                        draft.amount()));

        DeathPouchRecord expired =
                success(saga.expire(pending, Instant.parse("2026-07-09T00:00:00Z")));

        assertEquals(DeathPouchState.EXPIRED, expired.state());
        assertEquals(900, success(wallet.balance(owner)).balance());
    }

    private CharacterId fundedCharacter(long amount) {
        CharacterId owner = new CharacterId(UUID.randomUUID());
        success(saga.adjust(UUID.randomUUID(), owner, CarriedWalletOperationKind.CREDIT, amount));
        return owner;
    }

    private static DeathPouchDraft draft(CharacterId owner, Instant createdAt) {
        return new DeathPouchEngine()
                .plan(
                        UUID.randomUUID(),
                        owner,
                        DeathPouchContext.OPEN_WORLD_PVE,
                        1_000,
                        new DeathPouchLocation("minecraft:overworld", 10, 64, -3),
                        createdAt)
                .draft()
                .orElseThrow();
    }

    private static <T> T success(Result<T, TransactionErrorCode> result) {
        assertTrue(result.isSuccess(), () -> failureDetail(result));
        return ((Result.Success<T, TransactionErrorCode>) result).value();
    }

    private static String failureDetail(Result<?, TransactionErrorCode> result) {
        if (result instanceof Result.Failure<?, TransactionErrorCode> failure) {
            return failure.error() + ": " + failure.detail();
        }
        return "";
    }
}
