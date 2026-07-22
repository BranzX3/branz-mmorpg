package com.branz.mmorpg.content;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentReloadResult;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.content.ContentSnapshot;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class AtomicContentService implements ContentService {
    private final YamlContentLoader loader;
    private final Clock clock;
    private final AtomicLong revisions = new AtomicLong();
    private final AtomicReference<ContentSnapshot> active =
            new AtomicReference<>(ImmutableContentSnapshot.empty());

    public AtomicContentService() {
        this(new YamlContentLoader(), Clock.systemUTC());
    }

    AtomicContentService(YamlContentLoader loader, Clock clock) {
        this.loader = loader;
        this.clock = clock;
    }

    @Override
    public ContentSnapshot snapshot() {
        return active.get();
    }

    @Override
    public synchronized ContentReloadResult reload(Path root) {
        try {
            Map<ContentId, ContentDefinition> definitions = loader.load(root);
            long revision = revisions.incrementAndGet();
            ContentSnapshot replacement =
                    new ImmutableContentSnapshot(revision, clock.instant(), definitions);
            active.set(replacement);
            return new ContentReloadResult(true, revision, definitions.size(), List.of());
        } catch (ContentLoadException exception) {
            ContentSnapshot current = active.get();
            return new ContentReloadResult(
                    false, current.revision(), current.definitions().size(), exception.diagnostics());
        }
    }
}
