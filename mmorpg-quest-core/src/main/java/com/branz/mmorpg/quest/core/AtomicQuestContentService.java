package com.branz.mmorpg.quest.core;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.quest.api.ActionDefinition;
import com.branz.mmorpg.quest.api.ConditionDefinition;
import com.branz.mmorpg.quest.api.ObjectiveDefinition;
import com.branz.mmorpg.quest.api.QuestCatalog;
import com.branz.mmorpg.quest.api.QuestCatalogReload;
import com.branz.mmorpg.quest.api.QuestContentService;
import com.branz.mmorpg.quest.api.QuestDefinition;
import com.branz.mmorpg.quest.api.QuestDiagnostic;
import com.branz.mmorpg.quest.api.QuestMigrationDefinition;
import com.branz.mmorpg.quest.api.QuestStageDefinition;
import com.branz.mmorpg.quest.api.DialogueDefinition;
import com.branz.mmorpg.quest.api.DialogueNode;
import com.branz.mmorpg.quest.api.DialogueChoice;
import com.branz.mmorpg.quest.api.CutsceneDefinition;
import com.branz.mmorpg.quest.api.CutsceneAction;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class AtomicQuestContentService implements QuestContentService {
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final Supplier<ContentSnapshot> gameContent;
    private final AtomicReference<QuestCatalog> active =
            new AtomicReference<>(QuestCatalog.empty());
    private final AtomicReference<Map<MigrationKey, QuestMigrationDefinition>> migrations =
            new AtomicReference<>(Map.of());

    public AtomicQuestContentService(Supplier<ContentSnapshot> gameContent) {
        this.gameContent = java.util.Objects.requireNonNull(gameContent, "gameContent");
    }

    @Override public QuestCatalog catalog() { return active.get(); }

    public Optional<QuestMigrationDefinition> migration(
            ContentId questId, int fromVersion, int toVersion) {
        return Optional.ofNullable(migrations.get().get(
                new MigrationKey(questId, fromVersion, toVersion)));
    }

    @Override public synchronized QuestCatalogReload reload(
            Path directory, Set<String> capabilities) {
        ArrayList<QuestDiagnostic> diagnostics = new ArrayList<>();
        LinkedHashMap<ContentId, QuestDefinition> definitions = new LinkedHashMap<>();
        LinkedHashMap<ContentId, DialogueDefinition> dialogues = new LinkedHashMap<>();
        LinkedHashMap<ContentId, CutsceneDefinition> cutscenes = new LinkedHashMap<>();
        LinkedHashMap<MigrationKey, QuestMigrationDefinition> compiledMigrations =
                new LinkedHashMap<>();
        if (!Files.isDirectory(directory)) {
            diagnostics.add(error("Q-CONTENT-DIRECTORY", directory, Optional.empty(), "",
                    "Create the configured quest content directory."));
            return new QuestCatalogReload(false, active.get(), diagnostics);
        }
        List<Path> files;
        try (Stream<Path> stream = Files.walk(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".yml")
                            || path.toString().endsWith(".yaml"))
                    .sorted().toList();
        } catch (IOException failure) {
            diagnostics.add(error("Q-CONTENT-SCAN", directory, Optional.empty(), "",
                    failure.getMessage()));
            return new QuestCatalogReload(false, active.get(), diagnostics);
        }
        for (Path file : files) {
            try {
                JsonNode root = mapper.readTree(file.toFile());
                String type = text(root, "type", "");
                if ("quest".equals(type)) {
                    QuestDefinition definition = parse(root);
                    if (definitions.putIfAbsent(definition.id(), definition) != null) {
                        diagnostics.add(error("Q-DUPLICATE-QUEST", file,
                                Optional.of(definition.id()), "id",
                                "Use a permanent unique quest ID."));
                    } else {
                        validate(definition, file, capabilities, diagnostics);
                    }
                } else if ("dialogue".equals(type)) {
                    DialogueDefinition definition = parseDialogue(root);
                    if (dialogues.putIfAbsent(definition.id(), definition) != null) {
                        diagnostics.add(error("Q-DUPLICATE-DIALOGUE", file,
                                Optional.of(definition.id()), "id",
                                "Use a unique dialogue ID."));
                    } else new DialogueEngine().validate(definition);
                } else if ("cutscene".equals(type)) {
                    CutsceneDefinition definition = parseCutscene(root);
                    if (cutscenes.putIfAbsent(definition.id(), definition) != null) {
                        diagnostics.add(error("Q-DUPLICATE-CUTSCENE", file,
                                Optional.of(definition.id()), "id",
                                "Use a unique cutscene ID."));
                    } else new CutsceneEngine().validate(definition);
                } else if ("quest_migration".equals(type)
                        || "quest-migration".equals(type)) {
                    QuestMigrationDefinition migration = parseMigration(root);
                    MigrationKey key = new MigrationKey(migration.questId(),
                            migration.fromVersion(), migration.toVersion());
                    if (compiledMigrations.putIfAbsent(key, migration) != null) {
                        throw new IllegalArgumentException("duplicate quest migration " + key);
                    }
                }
            } catch (RuntimeException | IOException failure) {
                diagnostics.add(error("Q-PARSE", file, Optional.empty(), "",
                        "Fix quest YAML: " + failure.getMessage()));
            }
        }
        if (diagnostics.stream().anyMatch(value ->
                value.severity() == QuestDiagnostic.Severity.ERROR)) {
            return new QuestCatalogReload(false, active.get(), diagnostics);
        }
        validateCrossReferences(definitions, dialogues, cutscenes, directory, diagnostics);
        if (diagnostics.stream().anyMatch(value ->
                value.severity() == QuestDiagnostic.Severity.ERROR)) {
            return new QuestCatalogReload(false, active.get(), diagnostics);
        }
        QuestCatalog next = new QuestCatalog(active.get().revision() + 1,
                Instant.now(), definitions, dialogues, cutscenes);
        validateMigrations(definitions, compiledMigrations, directory, diagnostics);
        if (diagnostics.stream().anyMatch(value ->
                value.severity() == QuestDiagnostic.Severity.ERROR)) {
            return new QuestCatalogReload(false, active.get(), diagnostics);
        }
        migrations.set(Map.copyOf(compiledMigrations));
        active.set(next);
        return new QuestCatalogReload(true, next, diagnostics);
    }

    private QuestMigrationDefinition parseMigration(JsonNode root) {
        rejectUnknown(root, Set.of("type", "quest_id", "from_version",
                "to_version", "stage_mappings", "objective_mappings"),
                "quest_migration");
        return new QuestMigrationDefinition(
                ContentId.parse(required(root, "quest_id")),
                integer(root, "from_version", 0),
                integer(root, "to_version", 0),
                stringMap(root.path("stage_mappings")),
                stringMap(root.path("objective_mappings")));
    }

    private static void validateMigrations(
            Map<ContentId, QuestDefinition> quests,
            Map<MigrationKey, QuestMigrationDefinition> migrations,
            Path source, List<QuestDiagnostic> diagnostics) {
        migrations.values().forEach(migration -> {
            QuestDefinition target = quests.get(migration.questId());
            if (target == null || target.version() != migration.toVersion()) {
                diagnostics.add(error("Q-MIGRATION-TARGET", source,
                        Optional.of(migration.questId()), "to_version",
                        "Migration target must be the compiled quest version."));
                return;
            }
            migration.stageMappings().values().stream()
                    .filter(stage -> !target.stages().containsKey(stage))
                    .forEach(stage -> diagnostics.add(error("Q-MIGRATION-STAGE", source,
                            Optional.of(migration.questId()), "stage_mappings",
                            "Mapped stage does not exist: " + stage)));
            Set<String> objectiveIds = target.stages().values().stream()
                    .flatMap(stage -> stage.objectives().stream())
                    .map(ObjectiveDefinition::id)
                    .collect(java.util.stream.Collectors.toSet());
            migration.objectiveMappings().values().stream()
                    .filter(id -> !objectiveIds.contains(id))
                    .forEach(id -> diagnostics.add(error("Q-MIGRATION-OBJECTIVE", source,
                            Optional.of(migration.questId()), "objective_mappings",
                            "Mapped objective does not exist: " + id)));
        });
    }

    private DialogueDefinition parseDialogue(JsonNode root) {
        rejectUnknown(root, Set.of("type", "id", "version", "start_node",
                "nodes", "interruption_policy", "history_policy",
                "presentation_defaults"), "dialogue");
        LinkedHashMap<String, DialogueNode> nodes = new LinkedHashMap<>();
        requiredNode(root, "nodes").fields().forEachRemaining(entry -> {
            JsonNode node = entry.getValue();
            rejectUnknown(node, Set.of("node_type", "speaker", "text", "portrait",
                    "advance_mode", "duration_ms", "next", "choices", "conditions",
                    "actions", "jump_target"), "nodes." + entry.getKey());
            ArrayList<DialogueChoice> choices = new ArrayList<>();
            for (JsonNode choice : node.path("choices")) {
                rejectUnknown(choice, Set.of("id", "text", "conditions",
                        "disabled_reason", "actions", "next", "record_history"),
                        "nodes." + entry.getKey() + ".choices");
                choices.add(new DialogueChoice(required(choice, "id"),
                        required(choice, "text"), parseConditions(choice.path("conditions")),
                        text(choice, "disabled_reason", ""),
                        parseActions(choice.path("actions")), required(choice, "next"),
                        choice.path("record_history").asBoolean(true)));
            }
            DialogueNode value = new DialogueNode(entry.getKey(),
                    DialogueNode.Type.valueOf(
                            required(node, "node_type").toUpperCase(Locale.ROOT)),
                    text(node, "speaker", ""), text(node, "text", ""),
                    text(node, "portrait", ""),
                    DialogueNode.AdvanceMode.valueOf(
                            text(node, "advance_mode", "MANUAL").toUpperCase(Locale.ROOT)),
                    longValue(node, "duration_ms", 0),
                    optionalText(node, "next"), choices,
                    parseConditions(node.path("conditions")),
                    parseActions(node.path("actions")),
                    optionalText(node, "jump_target"));
            if (nodes.putIfAbsent(value.id(), value) != null) {
                throw new IllegalArgumentException("duplicate dialogue node " + value.id());
            }
        });
        return new DialogueDefinition(ContentId.parse(required(root, "id")),
                integer(root, "version", 1), required(root, "start_node"), nodes,
                DialogueDefinition.InterruptionPolicy.valueOf(
                        text(root, "interruption_policy", "CANCEL_ON_DISTANCE")
                                .toUpperCase(Locale.ROOT)),
                DialogueDefinition.HistoryPolicy.valueOf(
                        text(root, "history_policy", "LINES_AND_CHOICES")
                                .toUpperCase(Locale.ROOT)),
                stringMap(root.path("presentation_defaults")));
    }

    private CutsceneDefinition parseCutscene(JsonNode root) {
        rejectUnknown(root, Set.of("type", "id", "version", "scope", "skip_policy",
                "setup", "timeline", "checkpoints_ms", "final_state", "skip_state",
                "cleanup", "disconnect_recovery"), "cutscene");
        return new CutsceneDefinition(ContentId.parse(required(root, "id")),
                integer(root, "version", 1),
                CutsceneDefinition.Scope.valueOf(
                        text(root, "scope", "PRIVATE").toUpperCase(Locale.ROOT)),
                CutsceneDefinition.SkipPolicy.valueOf(
                        text(root, "skip_policy", "ALWAYS").toUpperCase(Locale.ROOT)),
                parseCutsceneActions(root.path("setup")),
                parseCutsceneActions(root.path("timeline")),
                longs(root.path("checkpoints_ms")),
                parseCutsceneActions(root.path("final_state")),
                parseCutsceneActions(root.path("skip_state")),
                parseCutsceneActions(root.path("cleanup")),
                CutsceneDefinition.DisconnectRecovery.valueOf(
                        text(root, "disconnect_recovery", "APPLY_SKIP_STATE")
                                .toUpperCase(Locale.ROOT)));
    }

    private List<CutsceneAction> parseCutsceneActions(JsonNode nodes) {
        ArrayList<CutsceneAction> result = new ArrayList<>();
        if (!nodes.isArray()) return List.of();
        for (JsonNode node : nodes) {
            rejectUnknown(node, Set.of("id", "at_ms", "priority", "track",
                    "action_type", "values", "numbers"), "cutscene.actions");
            result.add(new CutsceneAction(required(node, "id"),
                    longValue(node, "at_ms", 0), integer(node, "priority", 0),
                    CutsceneAction.Track.valueOf(
                            required(node, "track").toUpperCase(Locale.ROOT)),
                    CutsceneAction.Type.valueOf(
                            required(node, "action_type").toUpperCase(Locale.ROOT)),
                    stringMap(node.path("values")), doubleMap(node.path("numbers"))));
        }
        return List.copyOf(result);
    }

    private void validateCrossReferences(
            Map<ContentId, QuestDefinition> quests,
            Map<ContentId, DialogueDefinition> dialogues,
            Map<ContentId, CutsceneDefinition> cutscenes,
            Path source, List<QuestDiagnostic> diagnostics) {
        quests.values().forEach(quest -> java.util.stream.Stream.concat(
                quest.rewards().stream(), quest.stages().values().stream()
                        .flatMap(stage -> java.util.stream.Stream.concat(
                                stage.activationActions().stream(),
                                stage.completionActions().stream()))).forEach(action -> {
            String id = action.values().get("id");
            if (id == null) return;
            if (action.type() == ActionDefinition.Type.START_DIALOGUE
                    && !dialogues.containsKey(ContentId.parse(id))) {
                diagnostics.add(error("Q-UNKNOWN-DIALOGUE", source,
                        Optional.of(quest.id()), "actions." + action.id(),
                        "Reference a compiled dialogue."));
            }
            if (action.type() == ActionDefinition.Type.START_CUTSCENE
                    && !cutscenes.containsKey(ContentId.parse(id))) {
                diagnostics.add(error("Q-UNKNOWN-CUTSCENE", source,
                        Optional.of(quest.id()), "actions." + action.id(),
                        "Reference a compiled cutscene."));
            }
        }));
    }

    private QuestDefinition parse(JsonNode root) {
        rejectUnknown(root, Set.of("type", "id", "version", "title", "description",
                "category", "repeat_policy", "requirements", "start_trigger",
                "start_stage", "stages", "rewards", "migration_policy", "tags",
                "tracking_priority"), "quest");
        ContentId id = ContentId.parse(required(root, "id"));
        LinkedHashMap<String, QuestStageDefinition> stages = new LinkedHashMap<>();
        JsonNode stageNodes = requiredNode(root, "stages");
        stageNodes.fields().forEachRemaining(entry -> {
            QuestStageDefinition stage = parseStage(entry.getKey(), entry.getValue());
            if (stages.putIfAbsent(stage.id(), stage) != null) {
                throw new IllegalArgumentException("duplicate stage " + stage.id());
            }
        });
        return new QuestDefinition(id, integer(root, "version", 1),
                required(root, "title"), required(root, "description"),
                required(root, "category"),
                QuestDefinition.RepeatPolicy.valueOf(
                        text(root, "repeat_policy", "NEVER").toUpperCase(Locale.ROOT)),
                parseConditions(root.path("requirements")), required(root, "start_trigger"),
                required(root, "start_stage"), stages, parseActions(root.path("rewards")),
                QuestDefinition.MigrationPolicy.valueOf(
                        text(root, "migration_policy", "SAFE").toUpperCase(Locale.ROOT)),
                strings(root.path("tags")), integer(root, "tracking_priority", 0));
    }

    private QuestStageDefinition parseStage(String id, JsonNode node) {
        rejectUnknown(node, Set.of("activation_actions", "objectives",
                "completion_policy", "completion_count", "completion_actions",
                "next", "failure", "checkpoint"), "stages." + id);
        ArrayList<ObjectiveDefinition> objectives = new ArrayList<>();
        HashSet<String> objectiveIds = new HashSet<>();
        for (JsonNode value : node.path("objectives")) {
            ObjectiveDefinition objective = parseObjective(value);
            if (!objectiveIds.add(objective.id())) {
                throw new IllegalArgumentException("duplicate objective " + objective.id());
            }
            objectives.add(objective);
        }
        return new QuestStageDefinition(id, parseActions(node.path("activation_actions")),
                objectives, QuestStageDefinition.CompletionPolicy.valueOf(
                        text(node, "completion_policy", "ALL").toUpperCase(Locale.ROOT)),
                integer(node, "completion_count", 0),
                parseActions(node.path("completion_actions")),
                optionalText(node, "next"), optionalText(node, "failure"),
                node.path("checkpoint").asBoolean(false));
    }

    private ObjectiveDefinition parseObjective(JsonNode node) {
        rejectUnknown(node, Set.of("id", "objective_type", "target", "amount",
                "credit_policy", "accepted_sources", "options"), "objective");
        String target = text(node, "target", "");
        return new ObjectiveDefinition(required(node, "id"),
                ObjectiveDefinition.Type.valueOf(
                        required(node, "objective_type").toUpperCase(Locale.ROOT).replace('-', '_')),
                target.isBlank() ? Optional.empty() : Optional.of(ContentId.parse(target)),
                longValue(node, "amount", 1),
                ObjectiveDefinition.CreditPolicy.valueOf(
                        text(node, "credit_policy", "PERSONAL").toUpperCase(Locale.ROOT)),
                strings(node.path("accepted_sources")), stringMap(node.path("options")));
    }

    private List<ActionDefinition> parseActions(JsonNode nodes) {
        ArrayList<ActionDefinition> result = new ArrayList<>();
        HashSet<String> ids = new HashSet<>();
        if (!nodes.isArray()) return List.of();
        for (JsonNode node : nodes) {
            rejectUnknown(node, Set.of("id", "action_type", "values", "numbers",
                    "required", "idempotent", "paper_thread", "reversible"), "action");
            String id = required(node, "id");
            if (!ids.add(id)) throw new IllegalArgumentException("duplicate action " + id);
            ActionDefinition.Type type = ActionDefinition.Type.valueOf(
                    required(node, "action_type").toUpperCase(Locale.ROOT).replace('-', '_'));
            result.add(new ActionDefinition(id, type, stringMap(node.path("values")),
                    longMap(node.path("numbers")), node.path("required").asBoolean(true),
                    node.path("idempotent").asBoolean(true),
                    node.path("paper_thread").asBoolean(false),
                    node.path("reversible").asBoolean(false)));
        }
        return List.copyOf(result);
    }

    private List<ConditionDefinition> parseConditions(JsonNode nodes) {
        if (!nodes.isArray()) return List.of();
        ArrayList<ConditionDefinition> result = new ArrayList<>();
        nodes.forEach(node -> {
            rejectUnknown(node, Set.of("condition_type", "values", "numbers",
                    "children", "unavailable_as_false"), "condition");
            result.add(new ConditionDefinition(ConditionDefinition.Type.valueOf(
                    required(node, "condition_type").toUpperCase(Locale.ROOT).replace('-', '_')),
                    stringMap(node.path("values")), longMap(node.path("numbers")),
                    parseConditions(node.path("children")),
                    node.path("unavailable_as_false").asBoolean(true)));
        });
        return List.copyOf(result);
    }

    private void validate(QuestDefinition quest, Path source, Set<String> capabilities,
                          List<QuestDiagnostic> diagnostics) {
        Set<String> reachable = new HashSet<>();
        walk(quest.startStage(), quest, reachable);
        detectCycles(quest, source, diagnostics);
        quest.stages().keySet().stream().filter(stage -> !reachable.contains(stage))
                .forEach(stage -> diagnostics.add(error("Q-UNREACHABLE-STAGE", source,
                        Optional.of(quest.id()), "stages." + stage,
                        "Connect or remove the unreachable stage.")));
        quest.stages().forEach((id, stage) -> {
            stage.nextStage().ifPresent(next -> {
                if (!quest.stages().containsKey(next)) diagnostics.add(error(
                        "Q-UNKNOWN-STAGE", source, Optional.of(quest.id()),
                        "stages." + id + ".next", "Reference an existing stage."));
            });
            stage.failureStage().ifPresent(next -> {
                if (!quest.stages().containsKey(next)) diagnostics.add(error(
                        "Q-UNKNOWN-STAGE", source, Optional.of(quest.id()),
                        "stages." + id + ".failure", "Reference an existing stage."));
            });
            if (stage.nextStage().isEmpty() && stage.completionActions().stream()
                    .noneMatch(action -> action.type() == ActionDefinition.Type.COMPLETE_QUEST)) {
                diagnostics.add(error("Q-NO-COMPLETION-PATH", source,
                        Optional.of(quest.id()), "stages." + id,
                        "Add next or a complete-quest action."));
            }
            validateActions(quest, source, stage.activationActions(), capabilities, diagnostics);
            validateActions(quest, source, stage.completionActions(), capabilities, diagnostics);
            stage.objectives().forEach(objective ->
                    validateObjective(quest, source, objective, diagnostics));
        });
        validateActions(quest, source, quest.rewards(), capabilities, diagnostics);
        if (quest.repeatPolicy() != QuestDefinition.RepeatPolicy.NEVER
                && quest.rewards().stream().anyMatch(action -> !action.idempotent())) {
            diagnostics.add(error("Q-UNSAFE-REPEAT-REWARD", source,
                    Optional.of(quest.id()), "rewards",
                    "Make every repeat reward idempotent per occurrence."));
        }
    }

    private void validateActions(
            QuestDefinition quest, Path source, List<ActionDefinition> actions,
            Set<String> capabilities, List<QuestDiagnostic> diagnostics) {
        actions.forEach(action -> {
            if (action.required() && !action.idempotent()) {
                diagnostics.add(error("Q-NON-IDEMPOTENT-ACTION", source,
                        Optional.of(quest.id()), "actions." + action.id(),
                        "Required retryable actions must be idempotent."));
            }
            String capability = switch (action.type()) {
                case START_DIALOGUE -> "dialogue";
                case START_CUTSCENE -> "cutscene";
                case START_ENCOUNTER -> "encounter";
                case SPAWN_ACTOR, DESPAWN_ACTOR -> "actors";
                case TELEPORT -> "locations";
                default -> "";
            };
            if (!capability.isEmpty() && !capabilities.contains(capability)) {
                diagnostics.add(error("Q-CAPABILITY-MISSING", source,
                        Optional.of(quest.id()), "actions." + action.id(),
                        "Install/enable capability " + capability + '.'));
            }
            if (action.type() == ActionDefinition.Type.START_ENCOUNTER
                    || action.type() == ActionDefinition.Type.GRANT_ITEM
                    || action.type() == ActionDefinition.Type.TAKE_ITEM
                    || action.type() == ActionDefinition.Type.GRANT_MASTERY_XP) {
                String id = action.values().get("id");
                if (id == null || gameContent.get().find(ContentId.parse(id)).isEmpty()) {
                    diagnostics.add(error("Q-UNKNOWN-ACTION-CONTENT", source,
                            Optional.of(quest.id()), "actions." + action.id() + ".values.id",
                            "Reference an active game content ID."));
                }
            }
        });
    }

    private void validateObjective(
            QuestDefinition quest, Path source, ObjectiveDefinition objective,
            List<QuestDiagnostic> diagnostics) {
        if (objective.targetId().isEmpty()) return;
        ContentId target = objective.targetId().orElseThrow();
        boolean exists = gameContent.get().find(target).isPresent();
        if (!exists && objective.type() != ObjectiveDefinition.Type.TALK
                && objective.type() != ObjectiveDefinition.Type.INTERACT
                && objective.type() != ObjectiveDefinition.Type.ENTER_REGION
                && objective.type() != ObjectiveDefinition.Type.CHOOSE) {
            diagnostics.add(error("Q-UNKNOWN-CONTENT", source, Optional.of(quest.id()),
                    "objectives." + objective.id() + ".target",
                    "Reference an active game content ID."));
        }
    }

    private static void walk(String stageId, QuestDefinition quest, Set<String> visited) {
        if (!visited.add(stageId)) return;
        QuestStageDefinition stage = quest.stages().get(stageId);
        if (stage == null) return;
        stage.nextStage().ifPresent(next -> walk(next, quest, visited));
        stage.failureStage().ifPresent(next -> walk(next, quest, visited));
    }

    private static void detectCycles(
            QuestDefinition quest, Path source, List<QuestDiagnostic> diagnostics) {
        HashSet<String> visiting = new HashSet<>();
        HashSet<String> complete = new HashSet<>();
        detectCycleAt(quest.startStage(), quest, visiting, complete, source, diagnostics);
    }

    private static void detectCycleAt(
            String id, QuestDefinition quest, Set<String> visiting, Set<String> complete,
            Path source, List<QuestDiagnostic> diagnostics) {
        if (complete.contains(id) || !quest.stages().containsKey(id)) return;
        if (!visiting.add(id)) {
            diagnostics.add(error("Q-UNBOUNDED-STAGE-CYCLE", source,
                    Optional.of(quest.id()), "stages." + id,
                    "Remove the unconditional cycle or model a bounded repeat as objectives."));
            return;
        }
        QuestStageDefinition stage = quest.stages().get(id);
        stage.nextStage().ifPresent(next -> detectCycleAt(
                next, quest, visiting, complete, source, diagnostics));
        stage.failureStage().ifPresent(next -> detectCycleAt(
                next, quest, visiting, complete, source, diagnostics));
        visiting.remove(id);
        complete.add(id);
    }

    private static QuestDiagnostic error(
            String code, Path source, Optional<ContentId> id,
            String field, String resolution) {
        return new QuestDiagnostic(QuestDiagnostic.Severity.ERROR, code, source,
                -1, -1, id, field, resolution);
    }

    private static JsonNode requiredNode(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) throw new IllegalArgumentException("missing " + field);
        return value;
    }

    private static void rejectUnknown(
            JsonNode node, Set<String> allowed, String path) {
        if (!node.isObject()) {
            throw new IllegalArgumentException(path + " must be an object");
        }
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException(
                        "unknown field " + path + "." + field);
            }
        });
    }
    private static String required(JsonNode node, String field) {
        String value = text(node, field, "").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("missing " + field);
        return value;
    }
    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || !value.isValueNode() ? fallback : value.asText(fallback);
    }
    private static Optional<String> optionalText(JsonNode node, String field) {
        String value = text(node, field, "").trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }
    private static int integer(JsonNode node, String field, int fallback) {
        return node.path(field).asInt(fallback);
    }
    private static long longValue(JsonNode node, String field, long fallback) {
        return node.path(field).asLong(fallback);
    }
    private static Set<String> strings(JsonNode node) {
        HashSet<String> result = new HashSet<>();
        if (node.isArray()) node.forEach(value -> result.add(value.asText()));
        return Set.copyOf(result);
    }
    private static Map<String, String> stringMap(JsonNode node) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (node.isObject()) node.fields().forEachRemaining(
                entry -> result.put(entry.getKey(), entry.getValue().asText()));
        return Map.copyOf(result);
    }
    private static Map<String, Long> longMap(JsonNode node) {
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        if (node.isObject()) node.fields().forEachRemaining(
                entry -> result.put(entry.getKey(), entry.getValue().asLong()));
        return Map.copyOf(result);
    }
    private static Map<String, Double> doubleMap(JsonNode node) {
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        if (node.isObject()) node.fields().forEachRemaining(
                entry -> result.put(entry.getKey(), entry.getValue().asDouble()));
        return Map.copyOf(result);
    }
    private static List<Long> longs(JsonNode node) {
        ArrayList<Long> result = new ArrayList<>();
        if (node.isArray()) node.forEach(value -> result.add(value.asLong()));
        return List.copyOf(result);
    }

    private record MigrationKey(ContentId questId, int fromVersion, int toVersion) {
    }
}
