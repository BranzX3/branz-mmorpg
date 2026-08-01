package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.combat.resource.FlaskAllocation;
import com.branz.mmorpg.combat.resource.FlaskDose;
import com.branz.mmorpg.combat.resource.FlaskState;
import com.branz.mmorpg.combat.resource.PreparedFlaskSnapshot;
import com.branz.mmorpg.combat.status.AilmentType;
import com.branz.mmorpg.items.consumable.ConsumableCategory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpeditionStateJsonCodecTest {
    @Test
    void roundTripsCanonicalRelativeExpeditionState() {
        UUID checkpointId = UUID.randomUUID();
        PersistentExpeditionState expected =
                new PersistentExpeditionState(
                        new FlaskState(
                                FlaskAllocation.balanced(),
                                Map.of(
                                        FlaskDose.HEALING,
                                        2,
                                        FlaskDose.MANA,
                                        1,
                                        FlaskDose.STAMINA,
                                        0)),
                        List.of(
                                new PersistentConsumableEffect(
                                        DefinitionId.of("consumable.training_body_tonic"),
                                        ConsumableCategory.BODY_TONIC,
                                        900,
                                        true)),
                        Map.of(
                                AilmentType.BURN,
                                new PersistentAilmentState(AilmentType.BURN, 45.5, 20, 0, 0),
                                AilmentType.CORRUPTION,
                                new PersistentAilmentState(AilmentType.CORRUPTION, 0, 0, 400, 2)),
                        Optional.of(
                                new PreparedFlaskSnapshot(
                                        checkpointId,
                                        FlaskState.full(FlaskAllocation.balanced()))));

        String encoded = ExpeditionStateJsonCodec.encode(expected);

        assertEquals(expected, ExpeditionStateJsonCodec.decode(encoded));
        assertEquals(encoded, ExpeditionStateJsonCodec.encode(expected));
    }

    @Test
    void rejectsUnknownSchemaAndInvalidChargeBounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExpeditionStateJsonCodec.decode("{\"schemaVersion\":3}"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ExpeditionStateJsonCodec.decode(
                                """
                                {
                                  "schemaVersion": 1,
                                  "flask": {
                                    "capacity": 5,
                                    "allocation": {"HEALING": 3, "MANA": 1, "STAMINA": 1},
                                    "charges": {"HEALING": 4, "MANA": 0, "STAMINA": 0}
                                  },
                                  "consumableEffects": [],
                                  "ailments": []
                                }
                                """));
    }

    @Test
    void decodesSchemaOneWithoutInventingAPreparedCheckpoint() {
        PersistentExpeditionState decoded =
                ExpeditionStateJsonCodec.decode(
                        """
                        {
                          "schemaVersion": 1,
                          "flask": {
                            "capacity": 5,
                            "allocation": {"HEALING": 3, "MANA": 1, "STAMINA": 1},
                            "charges": {"HEALING": 2, "MANA": 1, "STAMINA": 0}
                          },
                          "consumableEffects": [],
                          "ailments": []
                        }
                        """);

        assertEquals(3, decoded.flaskState().totalCharges());
        assertEquals(Optional.empty(), decoded.preparedFlaskSnapshot());
    }
}
