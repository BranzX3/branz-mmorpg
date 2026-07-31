package com.branz.mmorpg.items.projection;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC signer for PDC projection references. The signing key never enters the ItemStack. */
public final class ProjectionTokenSigner {
    private static final int KEY_BYTES = 32;
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] key;

    public ProjectionTokenSigner(byte[] key) {
        Objects.requireNonNull(key, "key");
        if (key.length < KEY_BYTES) {
            throw new IllegalArgumentException("projection signing key must be at least 32 bytes");
        }
        this.key = Arrays.copyOf(key, key.length);
    }

    public static ProjectionTokenSigner random() {
        byte[] generated = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(generated);
        return new ProjectionTokenSigner(generated);
    }

    public byte[] sign(ExpectedProjection projection) {
        Objects.requireNonNull(projection, "projection");
        return mac(canonical(projection));
    }

    public boolean verify(ObservedProjection projection, byte[] signature) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(signature, "signature");
        return MessageDigest.isEqual(mac(canonical(projection.expectedForm())), signature);
    }

    private byte[] mac(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("JVM does not provide " + ALGORITHM, exception);
        }
    }

    private static byte[] canonical(ExpectedProjection projection) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                write(output, projection.valueId().toString());
                write(output, projection.definitionId().value());
                write(output, projection.valueType().name());
                output.writeInt(projection.slot());
                output.writeInt(projection.quantity());
                output.writeLong(projection.authorityVersion());
                output.writeLong(projection.displayRevision());
                write(output, projection.contentVersion());
                write(output, projection.testProvenance().orElse(""));
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode projection token", exception);
        }
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}
