package com.branz.mmorpg.quest.api;

import java.nio.file.Path;
import java.util.Set;

public interface QuestContentService {
    QuestCatalog catalog();
    QuestCatalogReload reload(Path directory, Set<String> capabilities);
}
