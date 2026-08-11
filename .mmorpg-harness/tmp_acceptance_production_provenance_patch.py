from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 anchor, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

service = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/CharacterSessionService.java")
replace_once(
    service,
    '''    Result<LoadedCharacterSession, CharacterSessionErrorCode> consumeAmmo(\n''',
    '''    Result<LoadedCharacterSession, CharacterSessionErrorCode> grantAcceptanceValue(\n            LoadedCharacterSession session,\n            ItemDefinition definition,\n            int inventorySlot,\n            String contentVersion) {\n        Objects.requireNonNull(session, "session");\n        Objects.requireNonNull(definition, "definition");\n        Objects.requireNonNull(contentVersion, "contentVersion");\n        if (definition.itemClass() != ItemClass.UNIQUE_DURABLE) {\n            return Result.failure(\n                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,\n                    "Physical acceptance staging requires one unique durable item.");\n        }\n        UUID valueId = UUID.randomUUID();\n        String payload = "{\\\"displayRevision\\\":1}";\n        TransactionRequest request =\n                TransactionRequest.forCharacter(\n                        new TransactionId(UUID.randomUUID()),\n                        "acceptance-grant:" + valueId,\n                        session.characterId(),\n                        session.sessionId(),\n                        JdbcValueTransactionService.ITEM_GRANT,\n                        "{}",\n                        "{\\\"definitionId\\\":\\\""\n                                + definition.id().value()\n                                + "\\\",\\\"quantity\\\":1}",\n                        contentVersion);\n        Result<TransactionExecution, TransactionErrorCode> granted =\n                database.values()\n                        .grantItem(\n                                request,\n                                new NewItemLocation(\n                                        new ItemId(valueId),\n                                        definition.id(),\n                                        java.util.Optional.of(session.characterId()),\n                                        ValueLocation.inventory("slot:" + inventorySlot),\n                                        payload));\n        if (granted instanceof Result.Failure<TransactionExecution, TransactionErrorCode> failure) {\n            return Result.failure(\n                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,\n                    failure.error().code() + ": " + failure.detail());\n        }\n        return reload(session);\n    }\n\n    Result<LoadedCharacterSession, CharacterSessionErrorCode> consumeAmmo(\n''',
    "service-acceptance-grant",
)

controller = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/CharacterSessionController.java")
replace_once(
    controller,
    '''    void consumeAmmo(\n            Player player,\n''',
    '''    void grantAcceptanceValue(\n            Player player,\n            ItemDefinition definition,\n            String contentVersion,\n            Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>> completion) {\n        Objects.requireNonNull(player, "player");\n        Objects.requireNonNull(definition, "definition");\n        Objects.requireNonNull(contentVersion, "contentVersion");\n        Objects.requireNonNull(completion, "completion");\n        LoadedCharacterSession session = active.get(player.getUniqueId());\n        int slot = firstFreeStorageSlot(player);\n        if (session == null || !ready(player)) {\n            completion.accept(\n                    Result.failure(\n                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,\n                            "Character session is not ready."));\n            return;\n        }\n        if (slot < 0) {\n            completion.accept(\n                    Result.failure(\n                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,\n                            "No free inventory slot is available."));\n            return;\n        }\n        if (!valueMutationInFlight.add(player.getUniqueId())) {\n            completion.accept(valueMutationBusy());\n            return;\n        }\n        plugin.getServer()\n                .getScheduler()\n                .runTaskAsynchronously(\n                        plugin,\n                        () -> {\n                            Result<LoadedCharacterSession, CharacterSessionErrorCode> result =\n                                    sessions.grantAcceptanceValue(\n                                            session, definition, slot, contentVersion);\n                            plugin.getServer()\n                                    .getScheduler()\n                                    .runTask(\n                                            plugin,\n                                            () ->\n                                                    completeSnapshotMutation(\n                                                            session, result, completion));\n                        });\n    }\n\n    void consumeAmmo(\n            Player player,\n''',
    "controller-acceptance-grant",
)

plugin = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/BranzMmoPlugin.java")
replace_once(
    plugin,
    '''                        characterSessionController.grantTestValue(\n                                player,\n                                acceptanceWeapon,\n                                snapshot.manifest().contentVersion(),\n''',
    '''                        characterSessionController.grantAcceptanceValue(\n                                player,\n                                acceptanceWeapon,\n                                snapshot.manifest().contentVersion(),\n''',
    "physical-acceptance-staging",
)
