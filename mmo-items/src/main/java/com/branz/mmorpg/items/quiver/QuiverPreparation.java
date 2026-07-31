package com.branz.mmorpg.items.quiver;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable prepared-category order and current selection owned by one Quiver item UUID. */
public record QuiverPreparation(List<DefinitionId> preparedAmmo, int selectedIndex) {
    public static final int ABSOLUTE_MAX_PREPARED = 4;

    public QuiverPreparation {
        preparedAmmo = List.copyOf(Objects.requireNonNull(preparedAmmo, "preparedAmmo"));
        if (preparedAmmo.size() > ABSOLUTE_MAX_PREPARED
                || new LinkedHashSet<>(preparedAmmo).size() != preparedAmmo.size()) {
            throw new IllegalArgumentException(
                    "prepared ammo must be unique and contain at most four categories");
        }
        preparedAmmo.forEach(ammo -> Objects.requireNonNull(ammo, "prepared ammo"));
        if ((preparedAmmo.isEmpty() && selectedIndex != -1)
                || (!preparedAmmo.isEmpty()
                        && (selectedIndex < 0 || selectedIndex >= preparedAmmo.size()))) {
            throw new IllegalArgumentException("selected ammo index is outside the prepared list");
        }
    }

    public static QuiverPreparation empty() {
        return new QuiverPreparation(List.of(), -1);
    }

    public Optional<DefinitionId> selectedAmmo() {
        return preparedAmmo.isEmpty()
                ? Optional.empty()
                : Optional.of(preparedAmmo.get(selectedIndex));
    }

    public QuiverPreparation toggle(DefinitionId ammo, int categoryLimit) {
        Objects.requireNonNull(ammo, "ammo");
        if (categoryLimit < 1 || categoryLimit > ABSOLUTE_MAX_PREPARED) {
            throw new IllegalArgumentException("categoryLimit must be between one and four");
        }
        ArrayList<DefinitionId> updated = new ArrayList<>(preparedAmmo);
        int existing = updated.indexOf(ammo);
        if (existing >= 0) {
            updated.remove(existing);
            if (updated.isEmpty()) {
                return empty();
            }
            int nextIndex = selectedIndex;
            if (existing < selectedIndex) {
                nextIndex--;
            } else if (existing == selectedIndex && nextIndex >= updated.size()) {
                nextIndex = 0;
            }
            return new QuiverPreparation(updated, nextIndex);
        }
        if (updated.size() >= categoryLimit) {
            throw new IllegalStateException("prepared ammo category limit reached");
        }
        updated.add(ammo);
        return new QuiverPreparation(updated, selectedIndex < 0 ? 0 : selectedIndex);
    }

    public QuiverPreparation cycle(int direction) {
        if (direction == 0) {
            throw new IllegalArgumentException("cycle direction must not be zero");
        }
        if (preparedAmmo.size() < 2) {
            return this;
        }
        int next = Math.floorMod(selectedIndex + Integer.signum(direction), preparedAmmo.size());
        return new QuiverPreparation(preparedAmmo, next);
    }
}
