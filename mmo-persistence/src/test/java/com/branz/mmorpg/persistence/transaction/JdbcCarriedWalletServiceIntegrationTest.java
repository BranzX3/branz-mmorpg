package com.branz.mmorpg.persistence.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.migration.ClasspathMigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationErrorCode;
import com.branz.mmorpg.persistence.migration.PostgresMigrationRunner;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcCarriedWalletServiceIntegrationTest {
    private EmbeddedPostgres postgres;
    private DataSource dataSource;
    private JdbcCarriedWalletService service;

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
        Result<MigrationCatalog, MigrationErrorCode> loaded =
                ClasspathMigrationCatalog.loadDefault();
        assertTrue(loaded.isSuccess());
        assertTrue(
                new PostgresMigrationRunner(dataSource)
                        .migrate(
                                ((Result.Success<MigrationCatalog, MigrationErrorCode>) loaded)
                                        .value())
                        .isSuccess());
        service = new JdbcCarriedWalletService(dataSource);
    }

    @Test
    void creditsDebitsAndExactReplayAreDurable() throws Exception {
        CharacterId character = new CharacterId(UUID.randomUUID());
        assertEquals(0, success(service.balance(character)).balance());

        CarriedWalletAdjustment credit =
                adjustment(character, CarriedWalletOperationKind.CREDIT, 1_000);
        TransactionRequest creditRequest = request(credit);
        CarriedWalletAdjustmentExecution credited = success(service.adjust(creditRequest, credit));
        assertEquals(1_000, credited.balance().balance());
        assertEquals(1, credited.balance().version());
        assertFalse(credited.transaction().replayed());

        CarriedWalletAdjustmentExecution replayed = success(service.adjust(creditRequest, credit));
        assertEquals(1_000, replayed.balance().balance());
        assertTrue(replayed.transaction().replayed());

        CarriedWalletAdjustment debit =
                adjustment(character, CarriedWalletOperationKind.DEBIT, 100);
        CarriedWalletAdjustmentExecution debited = success(service.adjust(request(debit), debit));
        assertEquals(900, debited.balance().balance());
        assertEquals(2, debited.balance().version());
        assertEquals(900, success(service.balance(character)).balance());
        assertEquals(2, auditCount());
    }

    @Test
    void insufficientDebitAndChangedRetryLeaveBalanceUntouched() {
        CharacterId character = new CharacterId(UUID.randomUUID());
        CarriedWalletAdjustment credit =
                adjustment(character, CarriedWalletOperationKind.CREDIT, 100);
        TransactionRequest creditRequest = request(credit);
        success(service.adjust(creditRequest, credit));

        CarriedWalletAdjustment changedRetry =
                new CarriedWalletAdjustment(
                        credit.operationId(), character, CarriedWalletOperationKind.CREDIT, 101);
        assertEquals(
                TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                failure(service.adjust(creditRequest, changedRetry)));

        CarriedWalletAdjustment excessive =
                adjustment(character, CarriedWalletOperationKind.DEBIT, 101);
        assertEquals(
                TransactionErrorCode.VALUE_INSUFFICIENT_QUANTITY,
                failure(service.adjust(request(excessive), excessive)));
        assertEquals(100, success(service.balance(character)).balance());
        assertEquals(1, success(service.balance(character)).version());
    }

    @Test
    void concurrentFirstCreditsSerializeWithoutLostUpdates() {
        CharacterId character = new CharacterId(UUID.randomUUID());
        CarriedWalletAdjustment first =
                adjustment(character, CarriedWalletOperationKind.CREDIT, 100);
        CarriedWalletAdjustment second =
                adjustment(character, CarriedWalletOperationKind.CREDIT, 200);

        CompletableFuture<CarriedWalletAdjustmentExecution> one =
                CompletableFuture.supplyAsync(() -> success(service.adjust(request(first), first)));
        CompletableFuture<CarriedWalletAdjustmentExecution> two =
                CompletableFuture.supplyAsync(
                        () -> success(service.adjust(request(second), second)));
        one.join();
        two.join();

        CarriedWalletBalance balance = success(service.balance(character));
        assertEquals(300, balance.balance());
        assertEquals(2, balance.version());
    }

    private static CarriedWalletAdjustment adjustment(
            CharacterId character, CarriedWalletOperationKind kind, long amount) {
        return new CarriedWalletAdjustment(UUID.randomUUID(), character, kind, amount);
    }

    private static TransactionRequest request(CarriedWalletAdjustment adjustment) {
        return TransactionRequest.system(
                new TransactionId(adjustment.operationId()),
                "carried-wallet:" + adjustment.operationId(),
                JdbcCarriedWalletService.CARRIED_WALLET_ADJUST,
                "{\"characterId\":\""
                        + adjustment.characterId().value()
                        + "\",\"kind\":\""
                        + adjustment.kind()
                        + "\",\"amount\":"
                        + adjustment.amount()
                        + "}",
                "{\"operationId\":\"" + adjustment.operationId() + "\"}",
                "test-v1");
    }

    private int auditCount() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet row =
                        statement.executeQuery(
                                "SELECT COUNT(*) FROM audit_log "
                                        + "WHERE action_type = 'wallet.carried.adjust'")) {
            row.next();
            return row.getInt(1);
        }
    }

    private static <T> T success(Result<T, TransactionErrorCode> result) {
        assertTrue(result.isSuccess(), () -> failureDetail(result));
        return ((Result.Success<T, TransactionErrorCode>) result).value();
    }

    private static TransactionErrorCode failure(Result<?, TransactionErrorCode> result) {
        assertFalse(result.isSuccess());
        return ((Result.Failure<?, TransactionErrorCode>) result).error();
    }

    private static String failureDetail(Result<?, TransactionErrorCode> result) {
        if (result instanceof Result.Failure<?, TransactionErrorCode> failure) {
            return failure.error() + ": " + failure.detail();
        }
        return "";
    }
}
