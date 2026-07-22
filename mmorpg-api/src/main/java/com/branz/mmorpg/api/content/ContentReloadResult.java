package com.branz.mmorpg.api.content;

import java.util.List;

public record ContentReloadResult(boolean successful, long revision, int definitionCount, List<String> diagnostics) {
    public ContentReloadResult {
        diagnostics = List.copyOf(diagnostics);
    }
}
