package com.branz.mmorpg.api.operation;

<<<<<<< HEAD
import java.util.Objects;
import java.util.UUID;

public record OperationId(UUID value) {
    public OperationId {
        Objects.requireNonNull(value, "value");
    }

    public static OperationId parse(String value) {
        return new OperationId(UUID.fromString(value));
=======
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Idempotency key for a valuable mutation.
 *
 * <p>Format, as fixed by {@code EXTERNAL_PLUGIN_INTEGRATION_CONTRACT.md} §3:
 *
 * <pre>
 * mmo:&lt;subsystem&gt;:&lt;entity&gt;:&lt;playerUuid&gt;:&lt;discriminator&gt;
 * </pre>
 *
 * <p>The same string is handed verbatim to BranzWallet as its
 * {@code transactionId} / {@code idempotencyKey}, so the two systems agree on
 * what "the same operation" means without a translation table.
 *
 * <p>An operation ID must be <b>derived from durable state only</b>. Never build
 * one from a clock reading, a random value, or a runtime entity UUID: recomputing
 * it after a restart has to produce the same string, or a retry mints a duplicate
 * reward instead of being rejected.
 */
public record OperationId(String value) implements Comparable<OperationId> {

    /** Every operation ID this plugin produces starts with this segment. */
    public static final String PREFIX = "mmo";

    public static final int MAX_LENGTH = 128;

    public OperationId {
        Objects.requireNonNull(value, "value");
        validate(value);
    }

    /**
     * Builds an ID from its parts. Each part is lower-cased and sanitised:
     * characters outside {@code [a-z0-9_-]} — notably the {@code :} inside a
     * {@link com.branz.mmorpg.api.content.ContentId} — become {@code _}, so a
     * segment can never be mistaken for a separator.
     */
    public static OperationId of(String subsystem, String entity, UUID playerUuid, String discriminator) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return new OperationId(PREFIX
                + ':' + segment(subsystem, "subsystem")
                + ':' + segment(entity, "entity")
                + ':' + playerUuid
                + ':' + segment(discriminator, "discriminator"));
    }

    /** Parses a stored ID, rejecting anything this class would not have produced. */
    public static OperationId parse(String value) {
        return new OperationId(value);
    }

    private static String segment(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT, field + " must not be blank");
        }
        StringBuilder builder = new StringBuilder(raw.length());
        for (char c : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            builder.append(isSegmentChar(c) ? c : '_');
        }
        return builder.toString();
    }

    private static boolean isSegmentChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    private static void validate(String value) {
        if (value.length() > MAX_LENGTH) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "operation id exceeds " + MAX_LENGTH + " characters: " + value);
        }
        String[] parts = value.split(":", -1);
        if (parts.length != 5) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "operation id must have 5 colon-separated segments: " + value);
        }
        if (!PREFIX.equals(parts[0])) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "operation id must start with '" + PREFIX + ":': " + value);
        }
        for (String part : parts) {
            if (part.isEmpty()) {
                throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                        "operation id has an empty segment: " + value);
            }
        }
        for (char c : value.toCharArray()) {
            if (!isSegmentChar(c) && c != ':') {
                throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                        "operation id contains illegal character '" + c + "': " + value);
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
>>>>>>> 14f48819ebb179329fe30a79707d68429f4dc351
    }

    @Override
    public String toString() {
<<<<<<< HEAD
        return value.toString();
=======
        return value;
>>>>>>> 14f48819ebb179329fe30a79707d68429f4dc351
    }
}
