package com.branz.mmorpg.core.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItemTokenSignerTest {
    private static final UUID ITEM =
            UUID.fromString("8a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f1");
    private static final UUID OWNER =
            UUID.fromString("9a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f8");
    private final ItemTokenSigner signer = new ItemTokenSigner(new byte[32]);

    @Test
    void signatureBindsInstanceDefinitionAndOwner() {
        ContentId definition = ContentId.parse("branz:broadsword");
        String signature = signer.sign(ITEM, definition, OWNER);

        assertTrue(signer.verify(ITEM, definition, OWNER, signature));
        assertFalse(signer.verify(ITEM, ContentId.parse("branz:longbow"), OWNER, signature));
        assertFalse(signer.verify(ITEM, definition, UUID.fromString(
                "7a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f2"), signature));
        assertFalse(signer.verify(ITEM, definition, OWNER, "malformed"));
    }
}
