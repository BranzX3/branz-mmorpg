package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.snapshot.ContentLoadFailure;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import java.nio.file.Path;

@FunctionalInterface
interface ContentSnapshotSource {
    Result<ContentSnapshot, ContentLoadFailure> load(Path root);
}
