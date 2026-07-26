package com.branz.mmorpg.core.item;

import com.branz.mmorpg.api.content.ContentId;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC signer for untrusted Bukkit/PDC presentation tokens. */
public final class ItemTokenSigner {
    private static final String VERSION = "v1";
    private final byte[] secret;

    public ItemTokenSigner(byte[] secret) {
        this.secret = Objects.requireNonNull(secret, "secret").clone();
        if (secret.length < 32) throw new IllegalArgumentException("token secret must be 256 bits");
    }

    public String sign(UUID itemId, ContentId definitionId, UUID ownerId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(payload(itemId, definitionId, ownerId)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 unavailable", impossible);
        }
    }

    public boolean verify(UUID itemId, ContentId definitionId, UUID ownerId, String signature) {
        if (signature == null) return false;
        try {
            return MessageDigest.isEqual(
                    Base64.getUrlDecoder().decode(sign(itemId, definitionId, ownerId)),
                    Base64.getUrlDecoder().decode(signature));
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private static byte[] payload(UUID itemId, ContentId definitionId, UUID ownerId) {
        return (VERSION + '|' + itemId + '|' + definitionId + '|' + ownerId)
                .getBytes(StandardCharsets.UTF_8);
    }
}
