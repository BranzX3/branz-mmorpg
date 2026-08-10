package com.branz.mmorpg.bootstrap;

/** Compatibility wrapper for weapon callers over shared durable-item payload state. */
final class WeaponPayloadCodec {
    private WeaponPayloadCodec() {}

    static WeaponDurability decode(String payloadJson, int baseMaximum) {
        ItemDurability durability = ItemDurabilityPayloadCodec.decode(payloadJson, baseMaximum);
        return new WeaponDurability(durability.current(), durability.maximum());
    }

    static String encode(String payloadJson, WeaponDurability durability) {
        return ItemDurabilityPayloadCodec.encode(
                payloadJson, new ItemDurability(durability.current(), durability.maximum()));
    }
}
