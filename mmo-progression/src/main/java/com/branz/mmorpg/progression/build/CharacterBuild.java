package com.branz.mmorpg.progression.build;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Persisted virtual build state. Ownership/knowledge remain separate authoritative concerns. */
public record CharacterBuild(
        Map<MovesetBranch, DefinitionId> techniques,
        Optional<DefinitionId> form,
        Set<DefinitionId> attunedEffects,
        int attunementCapacity) {
    public static final int DEFAULT_ATTUNEMENT_CAPACITY = 6;

    public CharacterBuild {
        Objects.requireNonNull(techniques, "techniques");
        EnumMap<MovesetBranch, DefinitionId> techniqueCopy = new EnumMap<>(MovesetBranch.class);
        techniques.forEach(
                (branch, id) ->
                        techniqueCopy.put(
                                Objects.requireNonNull(branch, "technique branch"),
                                Objects.requireNonNull(id, "technique id")));
        techniques = Collections.unmodifiableMap(techniqueCopy);
        Objects.requireNonNull(form, "form");
        attunedEffects =
                Collections.unmodifiableSet(
                        new LinkedHashSet<>(
                                Objects.requireNonNull(attunedEffects, "attunedEffects")));
        if (attunementCapacity < 0) {
            throw new IllegalArgumentException("attunementCapacity must not be negative");
        }
    }

    public static CharacterBuild initial() {
        return new CharacterBuild(
                Map.of(), Optional.empty(), Set.of(), DEFAULT_ATTUNEMENT_CAPACITY);
    }

    public CharacterBuild withTechnique(MovesetBranch branch, Optional<DefinitionId> techniqueId) {
        EnumMap<MovesetBranch, DefinitionId> next = new EnumMap<>(MovesetBranch.class);
        next.putAll(techniques);
        if (techniqueId.isPresent()) {
            next.put(branch, techniqueId.orElseThrow());
        } else {
            next.remove(branch);
        }
        return new CharacterBuild(next, form, attunedEffects, attunementCapacity);
    }

    public CharacterBuild withForm(Optional<DefinitionId> formId) {
        return new CharacterBuild(techniques, formId, attunedEffects, attunementCapacity);
    }

    public CharacterBuild toggleAttunedEffect(DefinitionId effectId) {
        LinkedHashSet<DefinitionId> next = new LinkedHashSet<>(attunedEffects);
        if (!next.remove(effectId)) {
            next.add(effectId);
        }
        return new CharacterBuild(techniques, form, next, attunementCapacity);
    }
}
