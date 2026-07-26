package com.branz.mmorpg.core.operation;

import com.branz.mmorpg.api.operation.EventId;
import com.branz.mmorpg.api.operation.IdGenerator;
import com.branz.mmorpg.api.operation.OperationId;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class UuidIdGenerator implements IdGenerator {
    private final Supplier<UUID> source;

    public UuidIdGenerator() {
        this(UUID::randomUUID);
    }

    public UuidIdGenerator(Supplier<UUID> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public OperationId newOperationId() {
        UUID value = source.get();
        return OperationId.of("core", "generated", value, "generated");
    }

    @Override
    public EventId newEventId() {
        return new EventId(source.get());
    }
}
