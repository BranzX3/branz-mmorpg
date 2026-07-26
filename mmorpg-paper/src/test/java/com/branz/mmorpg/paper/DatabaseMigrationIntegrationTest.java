package com.branz.mmorpg.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.storage.DatabaseConfig;
import com.branz.mmorpg.storage.DatabaseManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "BRANZ_MYSQL_INTEGRATION", matches = "true")
class DatabaseMigrationIntegrationTest {

    @Test
    void productionClasspathAppliesCoreAndQuestMigrationsWithoutVersionCollisions() throws SQLException {
        DatabaseConfig config = new DatabaseConfig(
                environment("BRANZ_MYSQL_HOST", "127.0.0.1"),
                Integer.parseInt(environment("BRANZ_MYSQL_PORT", "3407")),
                environment("BRANZ_MYSQL_DATABASE", "branz_mmorpg_test"),
                environment("BRANZ_MYSQL_USERNAME", "root"),
                environment("BRANZ_MYSQL_PASSWORD", ""),
                4,
                5000);

        try (DatabaseManager database = DatabaseManager.connect(config);
                Connection connection = database.dataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT version FROM mmorpg_schema_history WHERE success = TRUE")) {
            Set<String> versions = new java.util.HashSet<>();
            while (rows.next()) {
                versions.add(rows.getString(1));
            }
            Set<String> expected = IntStream.rangeClosed(1, 17)
                    .mapToObj(Integer::toString)
                    .collect(Collectors.toSet());
            expected.add("2.1");
            assertEquals(expected, versions);
            assertTrue(versions.contains("2"), "legacy Player Session migration must remain installed");
            assertTrue(versions.contains("2.1"), "Life Skill migration must follow the legacy V2 migration");
        }
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
