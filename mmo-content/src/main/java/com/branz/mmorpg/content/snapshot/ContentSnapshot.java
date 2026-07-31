package com.branz.mmorpg.content.snapshot;

import com.branz.mmorpg.content.definition.DefinitionRegistry;
import com.branz.mmorpg.content.manifest.ContentManifest;
import com.branz.mmorpg.content.reference.ReferenceIndex;
import java.util.Objects;

public record ContentSnapshot(
        ContentManifest manifest, DefinitionRegistry definitions, ReferenceIndex references) {
    public ContentSnapshot {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(references, "references");
    }
}
