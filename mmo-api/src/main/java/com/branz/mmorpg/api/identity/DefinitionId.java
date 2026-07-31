package com.branz.mmorpg.api.identity;

import com.branz.mmorpg.api.result.Result;
import java.util.Objects;
import java.util.regex.Pattern;

/** Stable definition identity. Display names and provider IDs must never be used in its place. */
public final class DefinitionId implements Comparable<DefinitionId> {
    private static final int MAX_LENGTH = 128;
    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+");

    private final String value;

    private DefinitionId(String value) {
        this.value = value;
    }

    public static Result<DefinitionId, IdentifierErrorCode> parse(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return Result.failure(IdentifierErrorCode.IDENTIFIER_BLANK, "Stable ID is required");
        }
        if (candidate.length() > MAX_LENGTH) {
            return Result.failure(
                    IdentifierErrorCode.IDENTIFIER_TOO_LONG,
                    "Stable ID exceeds " + MAX_LENGTH + " characters");
        }
        if (!FORMAT.matcher(candidate).matches()) {
            return Result.failure(
                    IdentifierErrorCode.IDENTIFIER_INVALID_FORMAT,
                    "Stable ID must be a lowercase dotted namespace");
        }
        return Result.success(new DefinitionId(candidate));
    }

    public static DefinitionId of(String value) {
        Result<DefinitionId, IdentifierErrorCode> parsed = parse(value);
        if (parsed instanceof Result.Success<DefinitionId, IdentifierErrorCode> success) {
            return success.value();
        }
        Result.Failure<DefinitionId, IdentifierErrorCode> failure =
                (Result.Failure<DefinitionId, IdentifierErrorCode>) parsed;
        throw new IllegalArgumentException(failure.error().code() + ": " + failure.detail());
    }

    public String value() {
        return value;
    }

    @Override
    public int compareTo(DefinitionId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof DefinitionId definitionId && value.equals(definitionId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
