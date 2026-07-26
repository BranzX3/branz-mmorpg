package com.branz.mmorpg.core.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidIdGeneratorTest {
    @Test
    void wrapsGeneratedUuidInTypedIds() {
        UUID value = UUID.fromString("61bc8f4a-019c-40d3-b5f3-ae57d94d0ee4");
        UuidIdGenerator generator = new UuidIdGenerator(() -> value);

        assertEquals(value, generator.newOperationId().playerUuid());
        assertEquals("core", generator.newOperationId().subsystem());
        assertEquals(value, generator.newEventId().value());
    }
}
