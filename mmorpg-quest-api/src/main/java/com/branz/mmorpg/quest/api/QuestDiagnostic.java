package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.nio.file.Path;
import java.util.Optional;

public record QuestDiagnostic(
        Severity severity,
        String code,
        Path source,
        int line,
        int column,
        Optional<ContentId> contentId,
        String fieldPath,
        String resolution) {
    public enum Severity { WARNING, ERROR }
}
