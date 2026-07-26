package com.branz.mmorpg.quest.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.branz.mmorpg.storage.DatabaseConfig;
import com.branz.mmorpg.storage.DatabaseManager;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
final class MySqlMigrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("branz_test")
                    .withUsername("branz")
                    .withPassword("branz");

    @Test
    void appliesCoreAndQuestMigrationsToRealMySql() throws Exception {
        DatabaseConfig config = new DatabaseConfig(
                MYSQL.getHost(), MYSQL.getMappedPort(3306),
                MYSQL.getDatabaseName(), MYSQL.getUsername(), MYSQL.getPassword(),
                4, 5_000);
        try (DatabaseManager database = DatabaseManager.connect(config);
             var connection = database.dataSource().getConnection();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.tables "
                             + "WHERE table_schema = ? AND table_name IN "
                             + "('quest_progress','dialogue_session','quest_location',"
                             + "'mob_runtime','direct_trade')")) {
            statement.setString(1, MYSQL.getDatabaseName());
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                assertEquals(5, row.getInt(1));
            }
        }
    }
}
