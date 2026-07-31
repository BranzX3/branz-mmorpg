package com.branz.mmorpg.combat.projectile;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Stable ownership and content context carried for the projectile lifetime. */
public record ProjectileIdentity(
        UUID projectileId,
        UUID ownerEntityId,
        DefinitionId sourceMoveId,
        String contentVersion,
        Optional<DefinitionId> ammoCategory,
        String hitGroup) {
    public ProjectileIdentity {
        Objects.requireNonNull(projectileId, "projectileId");
        Objects.requireNonNull(ownerEntityId, "ownerEntityId");
        Objects.requireNonNull(sourceMoveId, "sourceMoveId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(ammoCategory, "ammoCategory");
        Objects.requireNonNull(hitGroup, "hitGroup");
        if (contentVersion.isBlank() || hitGroup.isBlank()) {
            throw new IllegalArgumentException(
                    "projectile content and hit group must not be blank");
        }
    }
}
