package com.branz.mmorpg.content.reference;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.content.schema.DefinitionType;
import java.nio.file.Path;

public record ContentReference(
        DefinitionId sourceId,
        DefinitionId targetId,
        DefinitionType expectedType,
        Path sourceFile,
        int line,
        int column) {}
