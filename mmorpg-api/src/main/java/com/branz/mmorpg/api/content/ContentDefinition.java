package com.branz.mmorpg.api.content;

/** Marker contract for validated, immutable runtime content. */
public interface ContentDefinition {
    ContentId id();

    ContentType type();
}
