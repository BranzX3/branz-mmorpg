package com.branz.mmorpg.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WarehouseMaterialKeyMapperTest {
    @Test
    void vanillaUsesUppercaseBukkitMaterialAndRoundTrips() {
        ContentId iron = ContentId.parse("minecraft:iron_ore");
        String key = WarehouseMaterialKeyMapper.toWarehouseKey(
                iron, Optional.of("iron_ore"));
        assertEquals("IRON_ORE", key);
        assertEquals(iron, WarehouseMaterialKeyMapper.fromWarehouseKey(key));
    }

    @Test
    void customMaterialUsesUppercaseNamespacedKeyAndRoundTrips() {
        ContentId seal = ContentId.parse("branz:seal_fragment");
        String key = WarehouseMaterialKeyMapper.toWarehouseKey(seal, Optional.empty());
        assertEquals("BRANZ:SEAL_FRAGMENT", key);
        assertEquals(seal, WarehouseMaterialKeyMapper.fromWarehouseKey(key));
    }
}
