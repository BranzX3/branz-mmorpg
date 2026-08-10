package com.branz.mmorpg.persistence.transaction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.LotId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrossbowBoltBindingTest {
    private static final CharacterId OWNER = new CharacterId(UUID.randomUUID());
    private static final DefinitionId BOLT = DefinitionId.of("ammo.test.bolt");

    @Test
    void physicalInventoryCrossbowIsAccepted() {
        ValueLocation crossbowLocation = ValueLocation.inventory("slot:3");
        ValueLocation quiverLocation = ValueLocation.quiver(new ItemId(UUID.randomUUID()));

        assertDoesNotThrow(
                () -> new CrossbowBoltBinding(
                        update(crossbowLocation), consumption(quiverLocation), BOLT));
    }

    @Test
    void retiredNativeMainHandAuthorityIsRejected() {
        ValueLocation quiverLocation = ValueLocation.quiver(new ItemId(UUID.randomUUID()));

        assertThrows(
                IllegalArgumentException.class,
                () -> new CrossbowBoltBinding(
                        update(ValueLocation.nativeEquipped("MAIN_HAND")),
                        consumption(quiverLocation),
                        BOLT));
    }

    private static ItemPayloadUpdate update(ValueLocation location) {
        return new ItemPayloadUpdate(
                new ItemId(UUID.randomUUID()),
                1,
                Optional.of(OWNER),
                location,
                "{}",
                "{\"displayRevision\":2}");
    }

    private static LotQuantityConsumption consumption(ValueLocation location) {
        return new LotQuantityConsumption(
                new LotId(UUID.randomUUID()), 1, Optional.of(OWNER), location, 1);
    }
}
