package com.branz.mmorpg.social.lfg;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Public eligibility tags only. Hidden Mastery is intentionally outside this contract. */
public record LfgEntryRequirements(Set<String> requiredPublicTags) {
    public static final int MAX_REQUIRED_TAGS = 8;
    private static final Pattern TAG = Pattern.compile("[a-z][a-z0-9_.-]{0,47}");

    public LfgEntryRequirements {
        requiredPublicTags = normalizeTags(requiredPublicTags);
        if (requiredPublicTags.size() > MAX_REQUIRED_TAGS) {
            throw new IllegalArgumentException("too many LFG entry requirements");
        }
    }

    public boolean satisfiedBy(LfgApplicantProfile profile) {
        return profile.publicEligibilityTags().containsAll(requiredPublicTags);
    }

    static Set<String> normalizeTags(Set<String> tags) {
        Objects.requireNonNull(tags, "tags");
        HashSet<String> normalized = new HashSet<>();
        for (String candidate : tags) {
            String value =
                    Objects.requireNonNull(candidate, "eligibility tag").toLowerCase(Locale.ROOT);
            if (!TAG.matcher(value).matches() || value.contains("mastery")) {
                throw new IllegalArgumentException("invalid public eligibility tag: " + candidate);
            }
            normalized.add(value);
        }
        return Set.copyOf(normalized);
    }
}
