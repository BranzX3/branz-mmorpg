package com.branz.mmorpg.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DatabaseConfigTest {
    @Test
    void createsDeterministicMysqlUrl() {
        var config = new DatabaseConfig("localhost", 3306, "branz_mmorpg", "branz", "secret", 10, 5000);

        assertEquals(
                "jdbc:mysql://localhost:3306/branz_mmorpg?useUnicode=true&characterEncoding=utf8"
                        + "&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true",
                config.jdbcUrl());
    }

    @Test
    void rejectsUnsafeDatabaseName() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig("localhost", 3306, "db?x=1", "branz", "secret", 10, 5000));
    }
}
