package com.branz.mmorpg.core.service;

import com.branz.mmorpg.api.content.ContentReloadResult;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.core.lifecycle.ManagedService;
import java.nio.file.Path;
import java.util.Objects;

public final class ContentManagedService implements ManagedService {
    private final ContentService contentService;
    private final Path contentDirectory;
    private ContentReloadResult lastResult;

    public ContentManagedService(ContentService contentService, Path contentDirectory) {
        this.contentService = Objects.requireNonNull(contentService, "contentService");
        this.contentDirectory = Objects.requireNonNull(contentDirectory, "contentDirectory");
    }

    @Override
    public String name() {
        return "content";
    }

    @Override
    public boolean required() {
        return true;
    }

    @Override
    public void start() {
        lastResult = contentService.reload(contentDirectory);
        if (!lastResult.successful()) {
            throw new IllegalStateException("Content load rejected: " + String.join("; ", lastResult.diagnostics()));
        }
    }

    @Override
    public void stop() {
        // Immutable snapshots need no explicit shutdown.
    }

    @Override
    public String detail() {
        if (lastResult == null) {
            return "not loaded";
        }
        return "revision " + lastResult.revision() + ", definitions " + lastResult.definitionCount();
    }

    public ContentReloadResult lastResult() {
        if (lastResult == null) {
            throw new IllegalStateException("Content service has not started");
        }
        return lastResult;
    }
}
