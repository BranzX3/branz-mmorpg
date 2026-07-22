package com.branz.mmorpg.api.content;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Stable namespaced identifier shared by every Branz content type. */
public record ContentId(String namespace, String value) implements Comparable<ContentId> {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9][a-z0-9_.-]*");
    private static final Pattern VALUE = Pattern.compile("[a-z0-9][a-z0-9_./-]*");

    public ContentId {
        namespace = requirePart(namespace, "namespace", NAMESPACE);
        value = requirePart(value, "value", VALUE);
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid content ID path: " + value);
            }
        }
    }

    public static ContentId parse(String input) {
        Objects.requireNonNull(input, "input");
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(':');
        if (separator <= 0 || separator == normalized.length() - 1
                || separator != normalized.lastIndexOf(':')) {
            throw new IllegalArgumentException("Content ID must use namespace:value format: " + input);
        }
        return new ContentId(normalized.substring(0, separator), normalized.substring(separator + 1));
    }

    private static String requirePart(String part, String label, Pattern pattern) {
        Objects.requireNonNull(part, label);
        String normalized = part.trim().toLowerCase(Locale.ROOT);
        if (!pattern.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid content ID " + label + ": " + part);
        }
        return normalized;
    }

    @Override
    public String toString() {
        return namespace + ':' + value;
    }

    @Override
    public int compareTo(ContentId other) {
        return toString().compareTo(other.toString());
    }
}
