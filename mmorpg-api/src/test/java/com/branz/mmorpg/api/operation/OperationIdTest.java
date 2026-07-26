package com.branz.mmorpg.api.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationIdTest {

    private static final UUID PLAYER = UUID.fromString("9a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f8");

    @Test
    void buildsContractFormat() {
        OperationId id = OperationId.of("quest", "branz:broken_seal", PLAYER, "reward");
        assertEquals("mmo:quest:branz_broken_seal:" + PLAYER + ":reward", id.value());
        assertEquals("quest", id.subsystem());
        assertEquals(PLAYER, id.playerUuid());
    }

    @Test
    void sameInputsProduceSameId() {
        assertEquals(OperationId.of("mastery", "branz:mining", PLAYER, "milestone_25"),
                OperationId.of("mastery", "branz:mining", PLAYER, "milestone_25"));
    }

    @Test
    void sanitisesCaseAndIllegalCharacters() {
        assertEquals("mmo:loot:branz_seal_guardian__a_:" + PLAYER + ":enc_7c22",
                OperationId.of("Loot", "branz:Seal Guardian (A)", PLAYER, "enc_7c22").value());
    }

    @Test
    void roundTripsThroughParse() {
        OperationId id = OperationId.of("quest", "branz:broken_seal", PLAYER, "reward");
        assertEquals(id, OperationId.parse(id.value()));
    }

    @Test
    void rejectsMalformedIds() {
        assertEquals(ErrorCode.INVALID_ARGUMENT, codeOf(() -> OperationId.parse("mmo:quest:x:" + PLAYER)));
        assertEquals(ErrorCode.INVALID_ARGUMENT, codeOf(() -> OperationId.parse("idle:quest:x:" + PLAYER + ":r")));
        assertEquals(ErrorCode.INVALID_ARGUMENT, codeOf(() -> OperationId.parse("mmo:quest::" + PLAYER + ":r")));
        assertEquals(ErrorCode.INVALID_ARGUMENT, codeOf(() -> OperationId.parse("mmo:quest:x:not-a-uuid:r")));
        assertEquals(ErrorCode.INVALID_ARGUMENT, codeOf(() -> OperationId.of("quest", "  ", PLAYER, "r")));
    }

    @Test
    void rejectsOverlongIds() {
        String longEntity = "x".repeat(120);
        assertEquals(ErrorCode.INVALID_ARGUMENT,
                codeOf(() -> OperationId.of("quest", longEntity, PLAYER, "reward")));
    }

    private static ErrorCode codeOf(Runnable work) {
        return assertThrows(MMOException.class, work::run).code();
    }
}
