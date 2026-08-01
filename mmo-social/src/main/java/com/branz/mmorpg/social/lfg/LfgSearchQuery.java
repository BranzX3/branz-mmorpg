package com.branz.mmorpg.social.lfg;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record LfgSearchQuery(
        Optional<DefinitionId> activityId,
        Optional<DefinitionId> regionId,
        Set<String> languages,
        Set<LfgRolePreference> roles,
        LfgApplicantProfile applicantProfile) {
    public LfgSearchQuery {
        activityId = Objects.requireNonNull(activityId, "activityId");
        regionId = Objects.requireNonNull(regionId, "regionId");
        languages =
                Objects.requireNonNull(languages, "languages").stream()
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        Objects.requireNonNull(applicantProfile, "applicantProfile");
    }
}
