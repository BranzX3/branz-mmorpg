package com.branz.mmorpg.api.operation;

<<<<<<< HEAD
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable idempotency key for a valuable mutation.
 *
 * <p>The fixed format is:
 *
 * <pre>
 * mmo:&lt;subsystem&gt;:&lt;entity&gt;:&lt;playerUuid&gt;:&lt;discriminator&gt;
 * </pre>
 */
public record OperationId(String value) implements Comparable<OperationId> {
    public static final String PREFIX = "mmo";
    public static final int MAX_LENGTH = 128;

    public OperationId {
        Objects.requireNonNull(value, "value");
        validate(value);
    }

    public static OperationId of(String subsystem, String entity, UUID playerUuid, String discriminator) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return new OperationId(PREFIX
                + ':' + segment(subsystem, "subsystem")
                + ':' + segment(entity, "entity")
                + ':' + playerUuid
                + ':' + segment(discriminator, "discriminator"));
    }

    public static OperationId parse(String value) {
        return new OperationId(value);
    }

    private static String segment(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT, field + " must not be blank");
        }
        StringBuilder builder = new StringBuilder(raw.length());
        for (char character : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            builder.append(isSegmentCharacter(character) ? character : '_');
        }
        return builder.toString();
    }

    private static boolean isSegmentCharacter(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9')
                || character == '_'
                || character == '-';
    }

    private static void validate(String value) {
        if (value.length() > MAX_LENGTH) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "operation id exceeds " + MAX_LENGTH + " characters: " + value);
        }
        String[] parts = value.split(":", -1);
        if (parts.length != 5 || !PREFIX.equals(parts[0])) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "operation id must use mmo:<subsystem>:<entity>:<playerUuid>:<discriminator>: " + value);
        }
        for (String part : parts) {
            if (part.isEmpty()) {
                throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                        "operation id has an empty segment: " + value);
            }
        }
        for (char character : value.toCharArray()) {
            if (!isSegmentCharacter(character) && character != ':') {
                throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                        "operation id contains illegal character '" + character + "': " + value);
            }
        }
        try {
            UUID.fromString(parts[3]);
        } catch (IllegalArgumentException exception) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "operation id segment 4 must be a player UUID: " + value, exception);
        }
    }

    public String subsystem() {
        return value.split(":", -1)[1];
    }

    public UUID playerUuid() {
        return UUID.fromString(value.split(":", -1)[3]);
    }

    @Override
    public int compareTo(OperationId other) {
        return value.compareTo(other.value);
=======
import java.util.Objects;
import java.util.UUID;

public record OperationId(UUID value) {
    public OperationId {
        Objects.requireNonNull(value, "value");
    }

    public static OperationId parse(String value) {
        return new OperationId(UUID.fromString(value));
>>>>>>> parent of 3846639 (74)
    }

    @Override
    public String toString() {
<<<<<<< HEAD
        return value;
=======
        return value.toString();
>>>>>>> parent of 3846639 (74)
    }
}
