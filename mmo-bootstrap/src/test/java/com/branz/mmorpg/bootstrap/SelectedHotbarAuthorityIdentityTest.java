package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SelectedHotbarAuthorityIdentityTest {
    private static final CharacterId OWNER = new CharacterId(UUID.randomUUID());
    private static final DefinitionId BLADE = DefinitionId.of("weapon.training_blade");
    private static final DefinitionId OTHER = DefinitionId.of("weapon.test.other");

    @Test
    void emptyToAuthoritativeItemRequiresCombatRefresh() {
        ItemId itemId = new ItemId(UUID.randomUUID());

        assertTrue(
                SelectedHotbarAuthorityIdentity.changed(
                        List.of(), List.of(item(itemId, BLADE, 0, 1, "{}")), 0));
    }

    @Test
    void sameSelectedIdentityWithPayloadAndVersionChangeDoesNotRefreshCombat() {
        ItemId itemId = new ItemId(UUID.randomUUID());
        ItemLocationRecord before = item(itemId, BLADE, 0, 1, "{\"durability\":100}");
        ItemLocationRecord after = item(itemId, BLADE, 0, 2, "{\"durability\":99}");

        assertFalse(
                SelectedHotbarAuthorityIdentity.changed(
                        List.of(before), List.of(after), 0));
    }

    @Test
    void mutationInUnselectedSlotDoesNotRefreshCombat() {
        ItemId heldId = new ItemId(UUID.randomUUID());
        ItemLocationRecord held = item(heldId, BLADE, 0, 1, "{}");
        ItemLocationRecord unrelated = item(new ItemId(UUID.randomUUID()), OTHER, 1, 1, "{}");

        assertFalse(
                SelectedHotbarAuthorityIdentity.changed(
                        List.of(held), List.of(held, unrelated), 0));
    }

    @Test
    void movingSelectedItemAwayRequiresCombatRefresh() {
        ItemId itemId = new ItemId(UUID.randomUUID());
        ItemLocationRecord before = item(itemId, BLADE, 0, 1, "{}");
        ItemLocationRecord after = item(itemId, BLADE, 1, 2, "{}");

        assertTrue(
                SelectedHotbarAuthorityIdentity.changed(
                        List.of(before), List.of(after), 0));
    }

    @Test
    void replacingSelectedItemRequiresCombatRefresh() {
        ItemLocationRecord before =
                item(new ItemId(UUID.randomUUID()), BLADE, 0, 1, "{}");
        ItemLocationRecord after =
                item(new ItemId(UUID.randomUUID()), OTHER, 0, 1, "{}");

        assertTrue(
                SelectedHotbarAuthorityIdentity.changed(
                        List.of(before), List.of(after), 0));
    }

    private static ItemLocationRecord item(
            ItemId itemId,
            DefinitionId definitionId,
            int slot,
            long version,
            String payloadJson) {
        return new ItemLocationRecord(
                itemId,
                definitionId,
                Optional.of(OWNER),
                ValueLocation.inventory("slot:" + slot),
                payloadJson,
                "content.test.1",
                version,
                new TransactionId(UUID.randomUUID()),
                Instant.EPOCH,
                Instant.EPOCH);
    }
}
