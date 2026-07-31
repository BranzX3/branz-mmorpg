package com.branz.mmorpg.items.quiver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuiverPreparationTest {
    private static final DefinitionId BASIC = DefinitionId.of("ammo.test.basic");
    private static final DefinitionId BODKIN = DefinitionId.of("ammo.test.bodkin");
    private static final DefinitionId UTILITY = DefinitionId.of("ammo.test.utility");

    @Test
    void togglePreservesOrderAndRepairsSelectionWhenRemovingEntries() {
        QuiverPreparation prepared =
                QuiverPreparation.empty().toggle(BASIC, 4).toggle(BODKIN, 4).toggle(UTILITY, 4);

        assertEquals(BASIC, prepared.selectedAmmo().orElseThrow());
        assertEquals(BODKIN, prepared.cycle(1).selectedAmmo().orElseThrow());
        assertEquals(UTILITY, prepared.cycle(-1).selectedAmmo().orElseThrow());
        assertEquals(List.of(BASIC, UTILITY), prepared.cycle(1).toggle(BODKIN, 4).preparedAmmo());
        assertEquals(BODKIN, prepared.toggle(BASIC, 4).selectedAmmo().orElseThrow());
    }

    @Test
    void enforcesUniqueBoundedPreparedCategoriesAndWrappedCycling() {
        QuiverPreparation two = QuiverPreparation.empty().toggle(BASIC, 2).toggle(BODKIN, 2);

        assertEquals(BODKIN, two.cycle(-1).selectedAmmo().orElseThrow());
        assertThrows(IllegalStateException.class, () -> two.toggle(UTILITY, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> new QuiverPreparation(List.of(BASIC, BASIC), 0));
    }
}
