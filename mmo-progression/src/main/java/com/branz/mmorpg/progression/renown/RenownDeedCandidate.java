package com.branz.mmorpg.progression.renown;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;
import java.util.UUID;

/** Server-authored public-world deed; it never contains or produces combat statistics. */
public record RenownDeedCandidate(
        UUID deedId,
        CharacterId characterId,
        DefinitionId deedType,
        String noveltyFingerprint,
        int baseRenown,
        String contentVersion) {
    public static final int MAXIMUM_BASE_RENOWN = 100;

    public RenownDeedCandidate {
        Objects.requireNonNull(deedId, "deedId");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(deedType, "deedType");
        Objects.requireNonNull(noveltyFingerprint, "noveltyFingerprint");
        Objects.requireNonNull(contentVersion, "contentVersion");
        if (noveltyFingerprint.isBlank() || contentVersion.isBlank()) {
            throw new IllegalArgumentException("fingerprint and contentVersion must not be blank");
        }
        if (baseRenown < 1 || baseRenown > MAXIMUM_BASE_RENOWN) {
            throw new IllegalArgumentException("baseRenown must be between 1 and 100");
        }
    }
}
