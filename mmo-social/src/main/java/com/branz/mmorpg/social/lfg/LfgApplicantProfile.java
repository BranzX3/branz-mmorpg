package com.branz.mmorpg.social.lfg;

import java.util.Objects;
import java.util.Set;

public record LfgApplicantProfile(
        LfgRolePreference rolePreference,
        String experienceNote,
        Set<String> publicEligibilityTags) {
    public static final int MAX_NOTE_LENGTH = 160;
    public static final int MAX_ELIGIBILITY_TAGS = 16;

    public LfgApplicantProfile {
        Objects.requireNonNull(rolePreference, "rolePreference");
        experienceNote = normalizeNote(experienceNote);
        publicEligibilityTags = LfgEntryRequirements.normalizeTags(publicEligibilityTags);
        if (publicEligibilityTags.size() > MAX_ELIGIBILITY_TAGS) {
            throw new IllegalArgumentException("too many public eligibility tags");
        }
    }

    private static String normalizeNote(String note) {
        String normalized = Objects.requireNonNull(note, "experienceNote").strip();
        if (normalized.length() > MAX_NOTE_LENGTH
                || normalized.indexOf('\n') >= 0
                || normalized.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("experience note must be one line up to 160 chars");
        }
        return normalized;
    }
}
