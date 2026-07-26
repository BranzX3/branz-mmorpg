package com.branz.mmorpg.api.operation;

public interface IdGenerator {
    OperationId newOperationId();

    EventId newEventId();
}
