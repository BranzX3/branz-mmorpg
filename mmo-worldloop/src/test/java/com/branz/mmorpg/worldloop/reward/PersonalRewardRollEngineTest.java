package com.branz.mmorpg.worldloop.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonalRewardRollEngineTest {
    private final PersonalRewardRollEngine engine = new PersonalRewardRollEngine();
    private final EncounterRewardTable table =
            new EncounterRewardTable(
                    DefinitionId.of("encounter.boss.training_golem"),
                    new RewardEligibilityProfile(100, 50, 75, 1, 600),
                    0.20,
                    List.of(
                            new RewardTableEntry(
                                    DefinitionId.of("material.infusion_stock"), 3, 1, 2),
                            new RewardTableEntry(DefinitionId.of("material.iron_ore"), 1, 2, 4)));

    @Test
    void exactGrantReplayProducesSameEntryQuantityAndLotIdentity() {
        PersonalRewardGrant grant = grant(1234);
        assertEquals(engine.roll(table, grant), engine.roll(table, grant));
    }

    @Test
    void weightedCursorAndQuantityBoundsHandleNegativeSeeds() {
        RolledPersonalReward common = engine.roll(table, grant(-4));
        assertEquals(DefinitionId.of("material.infusion_stock"), common.itemDefinitionId());
        org.junit.jupiter.api.Assertions.assertTrue(
                common.quantity() >= 1 && common.quantity() <= 2);

        RolledPersonalReward rare = engine.roll(table, grant(-1));
        assertEquals(DefinitionId.of("material.iron_ore"), rare.itemDefinitionId());
        org.junit.jupiter.api.Assertions.assertTrue(rare.quantity() >= 2 && rare.quantity() <= 4);
        assertNotEquals(common.lotId(), rare.lotId());
    }

    private static PersonalRewardGrant grant(long seed) {
        return new PersonalRewardGrant(new CharacterId(UUID.randomUUID()), UUID.randomUUID(), seed);
    }
}
