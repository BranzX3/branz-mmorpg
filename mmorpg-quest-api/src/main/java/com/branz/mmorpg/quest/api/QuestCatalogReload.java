package com.branz.mmorpg.quest.api;

import java.util.List;

public record QuestCatalogReload(
        boolean successful,
        QuestCatalog catalog,
        List<QuestDiagnostic> diagnostics) {
    public QuestCatalogReload {
        diagnostics = List.copyOf(diagnostics);
    }
}
