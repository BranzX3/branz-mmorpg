from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label} anchor count={count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    Path("mmo-bootstrap/build.gradle.kts"),
    '''    if (physicalPrimaryInputAcceptance) {
        jvmArgs("-Dmmo.physical-primary-input-acceptance=true")
    }

    val smokeTest = providers.gradleProperty("smokeTest").orNull == "true"
''',
    '''    if (physicalPrimaryInputAcceptance) {
        jvmArgs("-Dmmo.physical-primary-input-acceptance=true")
    }
    val physicalHotbarAcceptance =
        providers.gradleProperty("physicalHotbarAcceptance").orNull == "true"
    if (physicalHotbarAcceptance) {
        jvmArgs("-Dmmo.physical-hotbar-acceptance=true")
    }

    val smokeTest = providers.gradleProperty("smokeTest").orNull == "true"
''',
    "bootstrap gradle",
)

replace_once(
    Path("acceptance/physical-client-26.2/build.gradle"),
    '''tasks.withType(JavaExec).configureEach {
    if (name == 'runClientGameTest' && providers.gradleProperty('physicalPrimaryInputAcceptance').orNull == 'true') {
        systemProperty 'branz.acceptance.physicalPrimaryInput', 'true'
    }
}
''',
    '''tasks.withType(JavaExec).configureEach {
    if (name == 'runClientGameTest' && providers.gradleProperty('physicalPrimaryInputAcceptance').orNull == 'true') {
        systemProperty 'branz.acceptance.physicalPrimaryInput', 'true'
    }
    if (name == 'runClientGameTest' && providers.gradleProperty('physicalHotbarAcceptance').orNull == 'true') {
        systemProperty 'branz.acceptance.physicalHotbar', 'true'
    }
}
''',
    "client gradle",
)

path = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/BranzMmoPlugin.java")
replace_once(
    path,
    '''        characterSessionController.addReadyHandler(combatSessionController::onCharacterReady);
        if (Boolean.getBoolean("mmo.physical-primary-input-acceptance")) {
            var acceptanceWeapon =
''',
    '''        characterSessionController.addReadyHandler(combatSessionController::onCharacterReady);
        boolean physicalPrimaryInputAcceptance =
                Boolean.getBoolean("mmo.physical-primary-input-acceptance");
        boolean physicalHotbarAcceptance = Boolean.getBoolean("mmo.physical-hotbar-acceptance");
        if (physicalPrimaryInputAcceptance || physicalHotbarAcceptance) {
            java.util.Set<java.util.UUID> acceptancePreparedPlayers =
                    java.util.HashSet.newHashSet(1);
            var acceptanceWeapon =
''',
    "staging start",
)
replace_once(
    path,
    '''            characterSessionController.addReadyHandler(
                    player ->
                            characterSessionController.grantTestValue(
                                    player,
                                    acceptanceWeapon,
''',
    '''            characterSessionController.addReadyHandler(
                    player -> {
                        if (!acceptancePreparedPlayers.add(player.getUniqueId())) {
                            return;
                        }
                        characterSessionController.grantTestValue(
                                    player,
                                    acceptanceWeapon,
''',
    "staging handler",
)
replace_once(
    path,
    '''                                        getLogger()
                                                .info(
                                                        "PHYSICAL_AUTHORITY_PRIMARY_STAGE_PROJECTED_SERVER player="
                                                                + player.getName()
                                                                + " definition="
                                                                + acceptanceWeapon.id());
                                        player.setLevel(8);
                                    }));
        }
''',
    '''                                        getLogger()
                                                .info(
                                                        "PHYSICAL_AUTHORITY_PRIMARY_STAGE_PROJECTED_SERVER player="
                                                                + player.getName()
                                                                + " definition="
                                                                + acceptanceWeapon.id());
                                        if (physicalHotbarAcceptance) {
                                            getLogger()
                                                    .info(
                                                            "PHYSICAL_AUTHORITY_HOTBAR_STAGE_PROJECTED_SERVER player="
                                                                    + player.getName()
                                                                    + " definition="
                                                                    + acceptanceWeapon.id());
                                        }
                                        player.setLevel(8);
                                    });
                    });
        }
''',
    "staging end",
)

path = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/PhysicalInventoryInteractionController.java")
replace_once(
    path,
    '''                                                    finishCommit(
                                                            playerId,
                                                            interaction,
                                                            completed,
                                                            failureMessage));
''',
    '''                                                    finishCommit(
                                                            playerId,
                                                            interaction,
                                                            intent,
                                                            completed,
                                                            failureMessage));
''',
    "finish call",
)
replace_once(
    path,
    '''    private void finishCommit(
            UUID playerId,
            PendingInteraction interaction,
            Result<LoadedCharacterSession, CharacterSessionErrorCode> result,
            String originalFailure) {
''',
    '''    private void finishCommit(
            UUID playerId,
            PendingInteraction interaction,
            ProjectionMoveIntent intent,
            Result<LoadedCharacterSession, CharacterSessionErrorCode> result,
            String originalFailure) {
''',
    "finish signature",
)
replace_once(
    path,
    '''                    if (originalFailure != null) {
                        player.sendActionBar(
                                Component.text(
                                        "Inventory move rejected and reconciled: "
                                                + originalFailure,
                                        NamedTextColor.RED));
                    }
                });
    }
''',
    '''                    if (originalFailure != null) {
                        player.sendActionBar(
                                Component.text(
                                        "Inventory move rejected and reconciled: "
                                                + originalFailure,
                                        NamedTextColor.RED));
                        return;
                    }
                    if (Boolean.getBoolean("mmo.physical-hotbar-acceptance")
                            && completed
                                    instanceof Result.Success<
                                            LoadedCharacterSession, CharacterSessionErrorCode>
                                            success) {
                        recordHotbarAcceptanceMove(player, success.value(), intent);
                    }
                });
    }

    private void recordHotbarAcceptanceMove(
            Player player, LoadedCharacterSession completed, ProjectionMoveIntent intent) {
        if (intent.valueType()
                != com.branz.mmorpg.items.projection.ProjectionValueType.UNIQUE_ITEM) {
            return;
        }
        String expectedReference = "slot:" + intent.destinationSlot();
        boolean authoritative =
                completed.snapshot().itemRecords().stream()
                        .anyMatch(
                                record ->
                                        record.itemId().value().equals(intent.valueId())
                                                && record.location().type()
                                                        == com.branz.mmorpg.persistence.transaction.ValueLocationType.CHARACTER_INVENTORY
                                                && record.location()
                                                        .reference()
                                                        .equals(expectedReference));
        if (!authoritative) {
            plugin.getLogger()
                    .severe(
                            "PHYSICAL_AUTHORITY_HOTBAR_MOVE_VERIFY_FAILED_SERVER player="
                                    + player.getName()
                                    + " value="
                                    + intent.valueId()
                                    + " destination="
                                    + intent.destinationSlot());
            return;
        }
        plugin.getLogger()
                .info(
                        "PHYSICAL_AUTHORITY_HOTBAR_MOVE_COMMITTED_SERVER player="
                                + player.getName()
                                + " value="
                                + intent.valueId()
                                + " source="
                                + intent.sourceSlot()
                                + " destination="
                                + intent.destinationSlot());
        if (intent.sourceSlot() == 0 && intent.destinationSlot() == 1) {
            player.setLevel(9);
        } else if (intent.sourceSlot() == 1 && intent.destinationSlot() == 2) {
            player.setLevel(10);
        }
    }
''',
    "finish callback",
)
