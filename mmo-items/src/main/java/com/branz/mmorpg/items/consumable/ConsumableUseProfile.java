package com.branz.mmorpg.items.consumable;

/** Server timing contract; commit may occur before the end of windup presentation. */
public record ConsumableUseProfile(int windupTicks, int commitTick, int recoveryTicks) {
    public ConsumableUseProfile {
        if (windupTicks < 1 || commitTick < 1 || commitTick > windupTicks || recoveryTicks < 0) {
            throw new IllegalArgumentException("invalid consumable use timing");
        }
    }

    public static ConsumableUseProfile expeditionFlask() {
        return new ConsumableUseProfile(28, 18, 20);
    }

    public int completeTickOffset() {
        return windupTicks + recoveryTicks;
    }
}
