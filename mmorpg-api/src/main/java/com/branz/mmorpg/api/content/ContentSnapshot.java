package com.branz.mmorpg.api.content;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ContentSnapshot {
    long revision();

    Instant loadedAt();

    Collection<ContentDefinition> definitions();

    Optional<ContentDefinition> find(ContentId id);

    <T extends ContentDefinition> Optional<T> find(ContentId id, Class<T> type);

    Map<ContentId, MaterialDefinition> materials();
}
