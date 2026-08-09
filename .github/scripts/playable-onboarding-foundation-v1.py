from pathlib import Path


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    Path(path).write_text(text, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"guard failed for {path}: expected one match, found {count}\n{old}")
    write(path, text.replace(old, new, 1))


# Migration catalog: forward-only append.
index = "mmo-persistence/src/main/resources/db/migration/migrations.index"
text = read(index)
entry = "V0015__character_onboarding_state.sql\n"
if entry not in text:
    if not text.endswith("\n"):
        text += "\n"
    text += entry
    write(index, text)

# Fix the temporary repository validation sentinel to use a non-null Result payload.
jdbc = "mmo-persistence/src/main/java/com/branz/mmorpg/persistence/transaction/JdbcCharacterOnboardingStateRepository.java"
text = read(jdbc)
text = text.replace("Result<Void, TransactionErrorCode>", "Result<Boolean, TransactionErrorCode>")
text = text.replace("Result.Failure<Void, TransactionErrorCode>", "Result.Failure<Boolean, TransactionErrorCode>")
text = text.replace("return Result.success(null);", "return Result.success(Boolean.TRUE);")
write(jdbc, text)

# DatabaseRuntime owns the onboarding repository with the rest of the persistence graph.
database = "mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/DatabaseRuntime.java"
replace_once(
    database,
    "import com.branz.mmorpg.persistence.transaction.CharacterBuildRepository;\n",
    "import com.branz.mmorpg.persistence.transaction.CharacterBuildRepository;\n"
    "import com.branz.mmorpg.persistence.transaction.CharacterOnboardingStateRepository;\n",
)
replace_once(
    database,
    "import com.branz.mmorpg.persistence.transaction.JdbcCharacterBuildRepository;\n",
    "import com.branz.mmorpg.persistence.transaction.JdbcCharacterBuildRepository;\n"
    "import com.branz.mmorpg.persistence.transaction.JdbcCharacterOnboardingStateRepository;\n",
)
replace_once(
    database,
    "    private final CharacterBuildRepository builds;\n",
    "    private final CharacterBuildRepository builds;\n"
    "    private final CharacterOnboardingStateRepository onboarding;\n",
)
replace_once(
    database,
    "        builds = new JdbcCharacterBuildRepository(dataSource);\n",
    "        builds = new JdbcCharacterBuildRepository(dataSource);\n"
    "        onboarding = new JdbcCharacterOnboardingStateRepository(dataSource);\n",
)
replace_once(
    database,
    "    CharacterBuildRepository builds() {\n        return builds;\n    }\n",
    "    CharacterBuildRepository builds() {\n        return builds;\n    }\n\n"
    "    CharacterOnboardingStateRepository onboarding() {\n        return onboarding;\n    }\n",
)

# CharacterSessionService: durable foundation choice + idempotent starter grants + reload.
service = "mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/CharacterSessionService.java"
replace_once(
    service,
    "import com.branz.mmorpg.persistence.transaction.CharacterBuildRecord;\n",
    "import com.branz.mmorpg.persistence.transaction.CharacterBuildRecord;\n"
    "import com.branz.mmorpg.persistence.transaction.CharacterOnboardingStateCommitExecution;\n"
    "import com.branz.mmorpg.persistence.transaction.CharacterOnboardingStateRecord;\n"
    "import com.branz.mmorpg.persistence.transaction.JdbcCharacterOnboardingStateRepository;\n",
)
replace_once(
    service,
    "import java.time.Duration;\n",
    "import java.nio.charset.StandardCharsets;\nimport java.time.Duration;\n",
)
service_methods = r'''
    Result<Optional<CharacterOnboardingStateRecord>, CharacterSessionErrorCode>
            startingFoundationState(LoadedCharacterSession session) {
        Objects.requireNonNull(session, "session");
        Result<Optional<CharacterOnboardingStateRecord>, TransactionErrorCode> state =
                database.onboarding().find(session.characterId());
        if (state
                instanceof
                Result.Failure<Optional<CharacterOnboardingStateRecord>, TransactionErrorCode>
                        failure) {
            return persistenceFailure(failure.error(), failure.detail());
        }
        return Result.success(
                ((Result.Success<Optional<CharacterOnboardingStateRecord>, TransactionErrorCode>)
                                state)
                        .value());
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> provisionStartingFoundation(
            LoadedCharacterSession session,
            StartingFoundation foundation,
            String contentVersion) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(foundation, "foundation");
        Objects.requireNonNull(contentVersion, "contentVersion");

        Result<Optional<CharacterOnboardingStateRecord>, TransactionErrorCode> stateResult =
                database.onboarding().find(session.characterId());
        if (stateResult
                instanceof
                Result.Failure<Optional<CharacterOnboardingStateRecord>, TransactionErrorCode>
                        failure) {
            return persistenceFailure(failure.error(), failure.detail());
        }
        Optional<CharacterOnboardingStateRecord> existing =
                ((Result.Success<Optional<CharacterOnboardingStateRecord>, TransactionErrorCode>)
                                stateResult)
                        .value();
        CharacterOnboardingStateRecord state;
        if (existing.isEmpty()) {
            if (!session.snapshot().itemRecords().isEmpty() || !session.snapshot().lotRecords().isEmpty()) {
                return Result.failure(
                        CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                        "Starting foundation can only be chosen on a fresh character.");
            }
            UUID operationId = starterUuid(session.characterId(), foundation, "foundation-choice");
            TransactionRequest request =
                    TransactionRequest.forCharacter(
                            new TransactionId(operationId),
                            "starter-foundation-choice:" + session.characterId().value(),
                            session.characterId(),
                            session.sessionId(),
                            JdbcCharacterOnboardingStateRepository.FOUNDATION_CHOOSE,
                            "{\"foundationState\":\"ABSENT\"}",
                            "{\"foundation\":\"" + foundation.name() + "\"}",
                            contentVersion);
            Result<CharacterOnboardingStateCommitExecution, TransactionErrorCode> chosen =
                    database.onboarding()
                            .chooseFoundation(request, session.characterId(), foundation.name());
            if (chosen
                    instanceof
                    Result.Failure<CharacterOnboardingStateCommitExecution, TransactionErrorCode>
                            failure) {
                return transactionFailure(failure);
            }
            state =
                    ((Result.Success<
                                            CharacterOnboardingStateCommitExecution,
                                            TransactionErrorCode>)
                                    chosen)
                            .value()
                            .record();
        } else {
            state = existing.orElseThrow();
            if (!state.foundationId().equals(foundation.name())) {
                return Result.failure(
                        CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                        "Starting foundation was already chosen as " + state.foundationId() + ".");
            }
            if (state.kitReady()) {
                return reload(session);
            }
        }

        for (StartingFoundation.StarterItem item : foundation.items()) {
            ItemId itemId =
                    new ItemId(
                            starterUuid(
                                    session.characterId(),
                                    foundation,
                                    "item:" + item.definitionId().value()));
            UUID operationId =
                    starterUuid(
                            session.characterId(),
                            foundation,
                            "grant-item:" + item.definitionId().value());
            TransactionRequest request =
                    TransactionRequest.forCharacter(
                            new TransactionId(operationId),
                            "starter-item:" + itemId.value(),
                            session.characterId(),
                            session.sessionId(),
                            JdbcValueTransactionService.ITEM_GRANT,
                            "{\"foundation\":\"" + foundation.name() + "\"}",
                            "{\"itemId\":\""
                                    + itemId.value()
                                    + "\",\"definitionId\":\""
                                    + item.definitionId().value()
                                    + "\",\"slot\":\""
                                    + item.slot().name()
                                    + "\"}",
                            contentVersion);
            Result<TransactionExecution, TransactionErrorCode> granted =
                    database.values()
                            .grantItem(
                                    request,
                                    new NewItemLocation(
                                            itemId,
                                            item.definitionId(),
                                            Optional.of(session.characterId()),
                                            equippedLocation(item.slot()),
                                            foundation.uniquePayload(item)));
            if (granted instanceof Result.Failure<TransactionExecution, TransactionErrorCode> failure) {
                return transactionFailure(failure);
            }
        }

        if (foundation.lot().isPresent()) {
            StartingFoundation.StarterLot lot = foundation.lot().orElseThrow();
            DefinitionId quiverDefinition = foundation.quiverDefinitionId().orElseThrow();
            ItemId quiverId =
                    new ItemId(
                            starterUuid(
                                    session.characterId(),
                                    foundation,
                                    "item:" + quiverDefinition.value()));
            LotId lotId =
                    new LotId(
                            starterUuid(
                                    session.characterId(),
                                    foundation,
                                    "lot:" + lot.definitionId().value()));
            UUID operationId =
                    starterUuid(
                            session.characterId(),
                            foundation,
                            "grant-lot:" + lot.definitionId().value());
            TransactionRequest request =
                    TransactionRequest.forCharacter(
                            new TransactionId(operationId),
                            "starter-lot:" + lotId.value(),
                            session.characterId(),
                            session.sessionId(),
                            JdbcValueTransactionService.LOT_GRANT,
                            "{\"foundation\":\"" + foundation.name() + "\"}",
                            "{\"lotId\":\""
                                    + lotId.value()
                                    + "\",\"definitionId\":\""
                                    + lot.definitionId().value()
                                    + "\",\"quantity\":"
                                    + lot.quantity()
                                    + "}",
                            contentVersion);
            Result<TransactionExecution, TransactionErrorCode> granted =
                    database.values()
                            .grantLot(
                                    request,
                                    new NewLotLocation(
                                            lotId,
                                            lot.definitionId(),
                                            "starter",
                                            lot.quantity(),
                                            Optional.of(session.characterId()),
                                            ValueLocation.quiver(quiverId),
                                            foundation.lotLineage()));
            if (granted instanceof Result.Failure<TransactionExecution, TransactionErrorCode> failure) {
                return transactionFailure(failure);
            }
        }

        UUID readyOperation = starterUuid(session.characterId(), foundation, "kit-ready");
        TransactionRequest readyRequest =
                TransactionRequest.forCharacter(
                        new TransactionId(readyOperation),
                        "starter-kit-ready:" + session.characterId().value(),
                        session.characterId(),
                        session.sessionId(),
                        JdbcCharacterOnboardingStateRepository.KIT_READY,
                        "{\"version\":" + state.version() + "}",
                        "{\"kitReady\":true}",
                        contentVersion);
        Result<CharacterOnboardingStateCommitExecution, TransactionErrorCode> ready =
                database.onboarding().markKitReady(readyRequest, session.characterId(), state.version());
        if (ready
                instanceof
                Result.Failure<CharacterOnboardingStateCommitExecution, TransactionErrorCode>
                        failure) {
            return transactionFailure(failure);
        }
        return reload(session);
    }

    private static UUID starterUuid(
            CharacterId characterId, StartingFoundation foundation, String purpose) {
        String value =
                "branz-mmo:starter:v1:"
                        + characterId.value()
                        + ":"
                        + foundation.name()
                        + ":"
                        + purpose;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

'''
replace_once(service, "    void close(LoadedCharacterSession session) {\n", service_methods + "    void close(LoadedCharacterSession session) {\n")

# CharacterSessionController exposes async state lookup and one serialized durable provisioning mutation.
controller = "mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/CharacterSessionController.java"
replace_once(
    controller,
    "import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;\n",
    "import com.branz.mmorpg.persistence.transaction.CharacterOnboardingStateRecord;\n"
    "import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;\n",
)
controller_methods = r'''

    void startingFoundationState(
            Player player,
            Consumer<
                            Result<
                                    Optional<CharacterOnboardingStateRecord>,
                                    CharacterSessionErrorCode>>
                    completion) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(completion, "completion");
        LoadedCharacterSession session = active.get(player.getUniqueId());
        if (session == null || !ready(player)) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Character session is not ready."));
            return;
        }
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<
                                            Optional<CharacterOnboardingStateRecord>,
                                            CharacterSessionErrorCode>
                                    result = sessions.startingFoundationState(session);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(plugin, () -> completion.accept(result));
                        });
    }

    void chooseStartingFoundation(
            Player player,
            StartingFoundation foundation,
            String contentVersion,
            Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>> completion) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(foundation, "foundation");
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(completion, "completion");
        Optional<String> unavailable = foundation.availabilityFailure(itemEngine);
        if (unavailable.isPresent()) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            unavailable.orElseThrow()));
            return;
        }
        runDurableSnapshotMutation(
                player,
                session ->
                        sessions.provisionStartingFoundation(session, foundation, contentVersion),
                completion);
    }
'''
active_marker = '''    Optional<LoadedCharacterSession> active(Player player) {
        return Optional.ofNullable(
                active.get(Objects.requireNonNull(player, "player").getUniqueId()));
    }
'''
replace_once(controller, active_marker, active_marker + controller_methods)

# Plugin wiring: install the chooser as a ready handler and listener; clean it on shutdown.
plugin = "mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/BranzMmoPlugin.java"
replace_once(
    plugin,
    "    private CharacterSessionController characterSessionController;\n",
    "    private CharacterSessionController characterSessionController;\n"
    "    private StartingFoundationController startingFoundationController;\n",
)
replace_once(
    plugin,
    "        if (combatSessionController != null) {\n",
    "        if (startingFoundationController != null) {\n"
    "            startingFoundationController.shutdown();\n"
    "            startingFoundationController = null;\n"
    "        }\n"
    "        if (combatSessionController != null) {\n",
)
chronicle_marker = '''        ChronicleController chronicleController =
                new ChronicleController(this, chronicle, characterSessionController::ready);
        characterSessionController.addReadyHandler(chronicleController::reconcile);
'''
chronicle_new = '''        ChronicleController chronicleController =
                new ChronicleController(this, chronicle, characterSessionController::ready);
        startingFoundationController =
                new StartingFoundationController(
                        this,
                        characterSessionController,
                        snapshot.manifest().contentVersion());
        characterSessionController.addReadyHandler(chronicleController::reconcile);
        characterSessionController.addReadyHandler(startingFoundationController::onCharacterReady);
'''
replace_once(plugin, chronicle_marker, chronicle_new)
replace_once(
    plugin,
    "        getServer().getPluginManager().registerEvents(characterSessionController, this);\n",
    "        getServer().getPluginManager().registerEvents(characterSessionController, this);\n"
    "        getServer().getPluginManager().registerEvents(startingFoundationController, this);\n",
)
