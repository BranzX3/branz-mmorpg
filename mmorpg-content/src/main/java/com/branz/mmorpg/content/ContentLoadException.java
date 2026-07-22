package com.branz.mmorpg.content;

import java.util.List;

public final class ContentLoadException extends Exception {
    private final List<String> diagnostics;

    ContentLoadException(List<String> diagnostics) {
        super(String.join(System.lineSeparator(), diagnostics));
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<String> diagnostics() {
        return diagnostics;
    }
}
