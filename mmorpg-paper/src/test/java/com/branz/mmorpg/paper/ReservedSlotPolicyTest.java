package com.branz.mmorpg.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReservedSlotPolicyTest {
    private final ReservedSlotPolicy policy = new ReservedSlotPolicy();

    @Test
    void slotNineUsesBukkitIndexEight() {
        assertEquals(8, PaperClassCompassRuntime.RESERVED_SLOT);
    }

    @Test
    void emptyAndValidTokenCasesNeverRelocateAnything() {
        assertEquals(ReservedSlotPolicy.Action.PLACE_TOKEN,
                policy.decide(true, false, false, 2).action());
        assertEquals(ReservedSlotPolicy.Action.REFRESH_VALID_TOKEN,
                policy.decide(false, true, true, 2).action());
        assertEquals(ReservedSlotPolicy.Action.REPLACE_INVALID_TOKEN,
                policy.decide(false, true, false, 2).action());
    }

    @Test
    void normalItemMovesToFreeStorageBeforeCompassPlacement() {
        var decision = policy.decide(false, false, false, 17);
        assertEquals(ReservedSlotPolicy.Action.RELOCATE_NORMAL_ITEM, decision.action());
        assertEquals(17, decision.destinationSlot());
    }

    @Test
    void fullInventoryPersistsNormalItemBeforeCompassPlacement() {
        assertEquals(ReservedSlotPolicy.Action.PERSIST_NORMAL_ITEM,
                policy.decide(false, false, false, -1).action());
    }
}
