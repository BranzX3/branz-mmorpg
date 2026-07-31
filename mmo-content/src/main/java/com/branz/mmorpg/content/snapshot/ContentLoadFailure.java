package com.branz.mmorpg.content.snapshot;

import com.branz.mmorpg.api.result.ErrorCode;
import com.branz.mmorpg.content.diagnostic.ContentDiagnostic;
import java.util.List;

public record ContentLoadFailure(List<ContentDiagnostic> diagnostics) implements ErrorCode {
    public ContentLoadFailure {
        diagnostics = List.copyOf(diagnostics);
    }

    @Override
    public String code() {
        return "CONTENT_SNAPSHOT_INVALID";
    }
}
