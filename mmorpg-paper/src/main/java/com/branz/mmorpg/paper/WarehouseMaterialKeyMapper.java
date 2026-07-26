package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Locale;
import java.util.Optional;

/** Single canonical ContentId ↔ BranzWallet warehouse-key mapping. */
public final class WarehouseMaterialKeyMapper {
    private WarehouseMaterialKeyMapper() {}

    public static String toWarehouseKey(
            ContentId contentId, Optional<String> bukkitMaterialName) {
        return bukkitMaterialName.filter(name -> !name.isBlank())
                .map(name -> name.toUpperCase(Locale.ROOT))
                .orElseGet(() -> contentId.toString().toUpperCase(Locale.ROOT));
    }

    public static ContentId fromWarehouseKey(String warehouseKey) {
        String normalized = warehouseKey.trim().toLowerCase(Locale.ROOT);
        return ContentId.parse(normalized.contains(":")
                ? normalized : "minecraft:" + normalized);
    }
}
