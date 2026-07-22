package com.branz.mmorpg.api.content;

import java.nio.file.Path;

public interface ContentService {
    ContentSnapshot snapshot();

    ContentReloadResult reload(Path root);
}
