package com.branz.mmorpg.api.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import org.junit.jupiter.api.Test;

class DefinitionIdTest {
    @Test
    void acceptsDocumentedDottedIdentifiers() {
        Result<DefinitionId, IdentifierErrorCode> parsed =
                DefinitionId.parse("move.greatsword.rising_cleave");

        assertTrue(parsed.isSuccess());
        assertEquals(
                "move.greatsword.rising_cleave",
                ((Result.Success<DefinitionId, IdentifierErrorCode>) parsed).value().value());
    }

    @Test
    void rejectsDisplayNamesAndUppercaseValues() {
        Result<DefinitionId, IdentifierErrorCode> parsed = DefinitionId.parse("Rising Cleave");

        assertFalse(parsed.isSuccess());
        assertEquals(
                IdentifierErrorCode.IDENTIFIER_INVALID_FORMAT,
                ((Result.Failure<DefinitionId, IdentifierErrorCode>) parsed).error());
    }

    @Test
    void throwingFactoryCannotCreateInvalidIdentity() {
        assertThrows(IllegalArgumentException.class, () -> DefinitionId.of("status"));
    }
}
