package com.branz.mmorpg.progression.build;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.definition.ContentDefinition;
import com.branz.mmorpg.content.schema.DefinitionType;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.progression.evidence.ReadinessBand;
import com.branz.mmorpg.progression.knowledge.KnowledgeKey;
import com.branz.mmorpg.progression.knowledge.KnowledgeType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable technique, form and attunement authority compiled from one content snapshot. */
public final class BuildEngine {
    private static final int MAX_REPLACEABLE_TECHNIQUES = 8;

    private final Map<DefinitionId, TechniqueDefinition> techniques;
    private final Map<DefinitionId, FormDefinition> forms;
    private final Map<DefinitionId, AttunableEffectDefinition> attunableEffects;

    private BuildEngine(
            Map<DefinitionId, TechniqueDefinition> techniques,
            Map<DefinitionId, FormDefinition> forms,
            Map<DefinitionId, AttunableEffectDefinition> attunableEffects) {
        this.techniques = Collections.unmodifiableMap(new LinkedHashMap<>(techniques));
        this.forms = Collections.unmodifiableMap(new LinkedHashMap<>(forms));
        this.attunableEffects = Collections.unmodifiableMap(new LinkedHashMap<>(attunableEffects));
    }

    /**
     * Empty engine for persistence-only tests and callers that cannot activate authored content.
     */
    public static BuildEngine empty() {
        return new BuildEngine(Map.of(), Map.of(), Map.of());
    }

    public static Result<BuildEngine, BuildErrorCode> compile(ContentSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        LinkedHashMap<DefinitionId, TechniqueDefinition> techniques = new LinkedHashMap<>();
        LinkedHashMap<DefinitionId, FormDefinition> forms = new LinkedHashMap<>();
        LinkedHashMap<DefinitionId, AttunableEffectDefinition> effects = new LinkedHashMap<>();
        try {
            for (ContentDefinition source :
                    snapshot.definitions().byType(DefinitionType.TECHNIQUE)) {
                TechniqueDefinition definition = compileTechnique(source);
                techniques.put(definition.id(), definition);
            }
            for (ContentDefinition source : snapshot.definitions().byType(DefinitionType.FORM)) {
                FormDefinition definition = compileForm(source);
                forms.put(definition.id(), definition);
            }
            for (ContentDefinition source : snapshot.definitions().byType(DefinitionType.SPELL)) {
                AttunableEffectDefinition definition = compileSpellAttunement(source);
                effects.put(definition.id(), definition);
            }
            return Result.success(new BuildEngine(techniques, forms, effects));
        } catch (BuildCompileException exception) {
            return Result.failure(BuildErrorCode.BUILD_DEFINITION_INVALID, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return Result.failure(BuildErrorCode.BUILD_DEFINITION_INVALID, exception.getMessage());
        }
    }

    public Collection<TechniqueDefinition> techniques() {
        return techniques.values();
    }

    public Collection<FormDefinition> forms() {
        return forms.values();
    }

    public Collection<AttunableEffectDefinition> attunableEffects() {
        return attunableEffects.values();
    }

    public Optional<TechniqueDefinition> technique(DefinitionId id) {
        return Optional.ofNullable(techniques.get(Objects.requireNonNull(id, "id")));
    }

    public Optional<FormDefinition> form(DefinitionId id) {
        return Optional.ofNullable(forms.get(Objects.requireNonNull(id, "id")));
    }

    public Result<BuildResolution, BuildErrorCode> resolve(CharacterBuild build) {
        return resolve(build, Optional.empty(), Optional.empty());
    }

    public Result<BuildResolution, BuildErrorCode> resolve(
            CharacterBuild build, String weaponFamily) {
        return resolve(
                build, Optional.of(requireText(weaponFamily, "weaponFamily")), Optional.empty());
    }

    public Result<BuildResolution, BuildErrorCode> resolve(
            CharacterBuild build, Set<KnowledgeKey> learnedKnowledge) {
        return resolve(
                build,
                Optional.empty(),
                Optional.of(
                        Set.copyOf(Objects.requireNonNull(learnedKnowledge, "learnedKnowledge"))));
    }

    public Result<BuildResolution, BuildErrorCode> resolve(
            CharacterBuild build, String weaponFamily, Set<KnowledgeKey> learnedKnowledge) {
        return resolve(
                build,
                Optional.of(requireText(weaponFamily, "weaponFamily")),
                Optional.of(
                        Set.copyOf(Objects.requireNonNull(learnedKnowledge, "learnedKnowledge"))));
    }

    private Result<BuildResolution, BuildErrorCode> resolve(
            CharacterBuild build,
            Optional<String> weaponFamily,
            Optional<Set<KnowledgeKey>> learnedKnowledge) {
        Objects.requireNonNull(build, "build");
        int replaceable =
                (int)
                        build.techniques().keySet().stream()
                                .filter(MovesetBranch::countsTowardReplaceableLimit)
                                .count();
        if (replaceable > MAX_REPLACEABLE_TECHNIQUES) {
            return Result.failure(
                    BuildErrorCode.BUILD_TECHNIQUE_LIMIT_EXCEEDED,
                    "Active moveset exceeds eight replaceable branch techniques.");
        }

        EnumMap<MovesetBranch, DefinitionId> resolvedMoves = new EnumMap<>(MovesetBranch.class);
        List<AttunementComponent> components = new ArrayList<>();
        for (Map.Entry<MovesetBranch, DefinitionId> entry : build.techniques().entrySet()) {
            TechniqueDefinition technique = techniques.get(entry.getValue());
            if (technique == null || technique.branch() != entry.getKey()) {
                return Result.failure(
                        BuildErrorCode.BUILD_UNKNOWN_TECHNIQUE,
                        "Technique is missing or does not own branch " + entry.getKey() + ".");
            }
            KnowledgeKey required = new KnowledgeKey(KnowledgeType.TECHNIQUE, technique.id());
            if (learnedKnowledge.isPresent()
                    && !learnedKnowledge.orElseThrow().contains(required)) {
                return Result.failure(
                        BuildErrorCode.BUILD_KNOWLEDGE_REQUIRED,
                        "Technique " + technique.id().value() + " has not been learned.");
            }
            if (weaponFamily.isPresent() && !technique.supports(weaponFamily.orElseThrow())) {
                return Result.failure(
                        BuildErrorCode.BUILD_FAMILY_INCOMPATIBLE,
                        technique.id().value()
                                + " is incompatible with "
                                + weaponFamily.orElseThrow());
            }
            resolvedMoves.put(entry.getKey(), technique.moveId());
            if (technique.supernatural()) {
                components.add(
                        new AttunementComponent(
                                technique.id(),
                                technique.attunementCost(),
                                technique.tags(),
                                technique.conflictsWithTags()));
            }
        }

        Optional<FormDefinition> activeForm = Optional.empty();
        if (build.form().isPresent()) {
            FormDefinition form = forms.get(build.form().orElseThrow());
            if (form == null) {
                return Result.failure(
                        BuildErrorCode.BUILD_UNKNOWN_FORM, "Selected form is unavailable.");
            }
            if (weaponFamily.isPresent() && !form.supports(weaponFamily.orElseThrow())) {
                return Result.failure(
                        BuildErrorCode.BUILD_FAMILY_INCOMPATIBLE,
                        form.id().value() + " is incompatible with " + weaponFamily.orElseThrow());
            }
            activeForm = Optional.of(form);
            if (form.attunementCost() > 0) {
                components.add(
                        new AttunementComponent(
                                form.id(),
                                form.attunementCost(),
                                form.tags(),
                                form.conflictsWithTags()));
            }
        }

        for (DefinitionId effectId : build.attunedEffects()) {
            AttunableEffectDefinition effect = attunableEffects.get(effectId);
            if (effect == null) {
                return Result.failure(
                        BuildErrorCode.BUILD_UNKNOWN_ATTUNEMENT,
                        effectId.value() + " is not an attunable effect.");
            }
            components.add(
                    new AttunementComponent(
                            effect.id(),
                            effect.attunementCost(),
                            effect.tags(),
                            effect.conflictsWithTags()));
        }

        int load = components.stream().mapToInt(AttunementComponent::cost).sum();
        if (load > build.attunementCapacity()) {
            return Result.failure(
                    BuildErrorCode.BUILD_ATTUNEMENT_CAPACITY_EXCEEDED,
                    "Attunement load "
                            + load
                            + " exceeds capacity "
                            + build.attunementCapacity()
                            + ".");
        }
        Optional<String> conflict = conflict(components);
        if (conflict.isPresent()) {
            return Result.failure(BuildErrorCode.BUILD_ATTUNEMENT_CONFLICT, conflict.orElseThrow());
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        components.forEach(component -> tags.addAll(component.tags()));
        return Result.success(new BuildResolution(build, resolvedMoves, activeForm, load, tags));
    }

    private static Optional<String> conflict(List<AttunementComponent> components) {
        for (int first = 0; first < components.size(); first++) {
            for (int second = first + 1; second < components.size(); second++) {
                AttunementComponent left = components.get(first);
                AttunementComponent right = components.get(second);
                if (!Collections.disjoint(left.conflictsWithTags(), right.tags())
                        || !Collections.disjoint(right.conflictsWithTags(), left.tags())) {
                    return Optional.of(
                            "Attunement conflict between "
                                    + left.id().value()
                                    + " and "
                                    + right.id().value()
                                    + ".");
                }
            }
        }
        return Optional.empty();
    }

    private static TechniqueDefinition compileTechnique(ContentDefinition source) {
        JsonNode body = source.body();
        return new TechniqueDefinition(
                source.id(),
                text(body, "family"),
                MovesetBranch.valueOf(text(body, "branch")),
                definitionId(body, "move"),
                TechniqueMode.valueOf(text(body, "mode")),
                text(body, "mastery_discipline"),
                bool(body, "supernatural"),
                integer(body, "attunement_cost"),
                ReadinessBand.valueOf(text(body, "learning_readiness")),
                ReadinessBand.valueOf(text(body, "teaching_readiness")),
                optionalTextSet(body, "tags"),
                optionalTextSet(body, "conflicts_with_tags"));
    }

    private static FormDefinition compileForm(ContentDefinition source) {
        JsonNode body = source.body();
        return new FormDefinition(
                source.id(),
                textSet(body, "families"),
                text(body, "tradeoff"),
                integer(body, "attunement_cost"),
                number(body, "resource.stamina_cost_multiplier"),
                number(body, "resource.mana_cost_multiplier"),
                optionalTextSet(body, "tags"),
                optionalTextSet(body, "conflicts_with_tags"));
    }

    private static AttunableEffectDefinition compileSpellAttunement(ContentDefinition source) {
        JsonNode body = source.body();
        return new AttunableEffectDefinition(
                source.id(),
                integer(body, "requirements.attunement"),
                optionalTextSet(body, "requirements.attunement_tags"),
                optionalTextSet(body, "requirements.conflicts_with_tags"));
    }

    private static DefinitionId definitionId(JsonNode root, String path) {
        Result<DefinitionId, ?> parsed = DefinitionId.parse(text(root, path));
        if (parsed instanceof Result.Failure<?, ?> failure) {
            throw new BuildCompileException(path + " is invalid: " + failure.detail());
        }
        return ((Result.Success<DefinitionId, ?>) parsed).value();
    }

    private static Set<String> textSet(JsonNode root, String path) {
        Set<String> values = optionalTextSet(root, path);
        if (values.isEmpty()) {
            throw new BuildCompileException(path + " must contain at least one entry");
        }
        return values;
    }

    private static Set<String> optionalTextSet(JsonNode root, String path) {
        JsonNode node = at(root, path);
        if (node.isMissingNode() || node.isNull()) {
            return Set.of();
        }
        if (!node.isArray()) {
            throw new BuildCompileException(path + " must be an array");
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode entry : node) {
            if (!entry.isTextual() || entry.textValue().isBlank()) {
                throw new BuildCompileException(path + " entries must be non-blank text");
            }
            values.add(entry.textValue());
        }
        return Set.copyOf(values);
    }

    private static String text(JsonNode root, String path) {
        JsonNode node = at(root, path);
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw new BuildCompileException(path + " must be non-blank text");
        }
        return node.textValue();
    }

    private static int integer(JsonNode root, String path) {
        JsonNode node = at(root, path);
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new BuildCompileException(path + " must be an integer");
        }
        return node.intValue();
    }

    private static double number(JsonNode root, String path) {
        JsonNode node = at(root, path);
        if (!node.isNumber() || !Double.isFinite(node.doubleValue())) {
            throw new BuildCompileException(path + " must be a finite number");
        }
        return node.doubleValue();
    }

    private static boolean bool(JsonNode root, String path) {
        JsonNode node = at(root, path);
        if (!node.isBoolean()) {
            throw new BuildCompileException(path + " must be a boolean");
        }
        return node.booleanValue();
    }

    private static JsonNode at(JsonNode root, String path) {
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            current = current.path(segment);
        }
        return current;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private record AttunementComponent(
            DefinitionId id, int cost, Set<String> tags, Set<String> conflictsWithTags) {}

    private static final class BuildCompileException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        private BuildCompileException(String message) {
            super(message);
        }
    }
}
