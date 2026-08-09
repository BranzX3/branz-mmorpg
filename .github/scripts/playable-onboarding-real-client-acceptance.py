from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: guard expected one match, found {count}: {old[:140]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


controller = "mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/StartingFoundationController.java"
replace_once(
    controller,
    "import java.util.UUID;\n",
    "import java.util.UUID;\nimport java.util.function.BiConsumer;\n",
)
replace_once(
    controller,
    "    private final Map<UUID, Inventory> choiceInventories = new HashMap<>();\n",
    "    private final Map<UUID, Inventory> choiceInventories = new HashMap<>();\n"
    "    private BiConsumer<Player, StartingFoundation> foundationReadyObserver =\n"
    "            (player, foundation) -> {};\n",
)
replace_once(
    controller,
    "    void onCharacterReady(Player player) {\n",
    "    void setFoundationReadyObserver(BiConsumer<Player, StartingFoundation> observer) {\n"
    "        foundationReadyObserver = Objects.requireNonNull(observer, \"observer\");\n"
    "    }\n\n"
    "    void onCharacterReady(Player player) {\n",
)
replace_once(
    controller,
    "        if (record.kitReady()) {\n            locked.remove(playerId);\n            return;\n        }\n",
    "        if (record.kitReady()) {\n"
    "            locked.remove(playerId);\n"
    "            foundationReadyObserver.accept(player, foundation);\n"
    "            return;\n"
    "        }\n",
)
replace_once(
    controller,
    "                    player.sendMessage(\n"
    "                            Component.text(\n"
    "                                    \"Your Chronicle is in slot 9. Draw your weapon and try LMB when ready.\",\n"
    "                                    NamedTextColor.GRAY));\n",
    "                    player.sendMessage(\n"
    "                            Component.text(\n"
    "                                    \"Your Chronicle is in slot 9. Draw your weapon and try LMB when ready.\",\n"
    "                                    NamedTextColor.GRAY));\n"
    "                    foundationReadyObserver.accept(player, foundation);\n",
)

probe = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/OnboardingClientAcceptanceProbe.java")
if probe.exists():
    raise SystemExit(f"{probe}: already exists")
probe.write_text(
    '''package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.persistence.transaction.CharacterOnboardingStateRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Real-client acceptance for first choice -> durable kit -> reconnect without reprompt. */
final class OnboardingClientAcceptanceProbe {
    static final String ENABLE_PROPERTY = "mmo.bootstrap.onboarding-acceptance-test";
    static final String MARKER_PROPERTY = "mmo.bootstrap.onboarding-acceptance-marker";
    static final String PASS_MARKER = "ONBOARDING_CLIENT_ACCEPTANCE_PASS";
    private static final DefinitionId GREATSWORD = DefinitionId.of("weapon.training_greatsword");

    private final JavaPlugin plugin;
    private final CharacterSessionController characters;
    private UUID playerId;
    private int readyCount;
    private boolean checking;
    private boolean completed;

    private OnboardingClientAcceptanceProbe(
            JavaPlugin plugin, CharacterSessionController characters) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
    }

    static void install(
            JavaPlugin plugin,
            StartingFoundationController foundations,
            CharacterSessionController characters) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        OnboardingClientAcceptanceProbe probe =
                new OnboardingClientAcceptanceProbe(plugin, characters);
        foundations.setFoundationReadyObserver(probe::onFoundationReady);
        plugin.getServer().getScheduler().runTaskLater(plugin, probe::timeout, 20L * 120L);
        plugin.getLogger().info("ONBOARDING_CLIENT_ACCEPTANCE_ARMED");
    }

    private void onFoundationReady(Player player, StartingFoundation foundation) {
        if (completed || checking) {
            return;
        }
        if (foundation != StartingFoundation.GREATSWORD) {
            fail("expected GREATSWORD, got " + foundation.name());
            return;
        }
        if (playerId == null) {
            playerId = player.getUniqueId();
        } else if (!playerId.equals(player.getUniqueId())) {
            fail("reconnect used a different player identity");
            return;
        }
        checking = true;
        characters.startingFoundationState(
                player,
                result -> verifyDurableState(player, result));
    }

    private void verifyDurableState(
            Player player,
            Result<Optional<CharacterOnboardingStateRecord>, CharacterSessionErrorCode> result) {
        if (completed) {
            return;
        }
        if (result
                instanceof
                Result.Failure<Optional<CharacterOnboardingStateRecord>, CharacterSessionErrorCode>
                        failure) {
            fail("state read failed: " + failure.error().code() + " " + failure.detail());
            return;
        }
        CharacterOnboardingStateRecord record =
                ((Result.Success<
                                        Optional<CharacterOnboardingStateRecord>,
                                        CharacterSessionErrorCode>)
                                result)
                        .value()
                        .orElse(null);
        if (record == null || !record.kitReady() || !record.foundationId().equals("GREATSWORD")) {
            fail("durable onboarding state is incomplete");
            return;
        }
        try {
            validateGreatswordProjection(player);
        } catch (RuntimeException exception) {
            fail(exception.getMessage());
            return;
        }

        readyCount++;
        checking = false;
        if (readyCount == 1) {
            plugin.getLogger().info("ONBOARDING_CLIENT_ACCEPTANCE_FIRST_READY");
            plugin.getServer()
                    .getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                if (player.isOnline()) {
                                    player.kick(Component.text("ONBOARDING_ACCEPTANCE_RECONNECT"));
                                }
                            },
                            2L);
            return;
        }
        if (readyCount != 2) {
            fail("foundation ready observer fired too many times");
            return;
        }
        try {
            writeMarker();
            completed = true;
            plugin.getLogger().info(PASS_MARKER);
        } catch (IOException exception) {
            fail("marker write failed: " + exception.getMessage());
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, plugin.getServer()::shutdown, 20L);
    }

    private void validateGreatswordProjection(Player player) {
        LoadedCharacterSession session =
                characters
                        .active(player)
                        .orElseThrow(() -> new IllegalStateException("character session disappeared"));
        ItemId mainHand =
                session.snapshot()
                        .equipment()
                        .item(EquipmentSlot.MAIN_HAND)
                        .orElseThrow(() -> new IllegalStateException("starter main hand is empty"));
        boolean matchingRecord =
                session.snapshot().itemRecords().stream()
                        .anyMatch(
                                item ->
                                        item.itemId().equals(mainHand)
                                                && item.definitionId().equals(GREATSWORD));
        if (!matchingRecord) {
            throw new IllegalStateException("persisted main hand is not the training greatsword");
        }
    }

    private void timeout() {
        if (!completed) {
            fail("timed out before first-choice and reconnect proof completed");
        }
    }

    private void fail(String detail) {
        if (completed) {
            return;
        }
        completed = true;
        plugin.getLogger().severe("ONBOARDING_CLIENT_ACCEPTANCE_FAIL " + detail);
        plugin.getServer().getScheduler().runTask(plugin, plugin.getServer()::shutdown);
    }

    private static void writeMarker() throws IOException {
        String raw = System.getProperty(MARKER_PROPERTY, "").trim();
        if (raw.isEmpty()) {
            throw new IllegalStateException("onboarding acceptance marker path is missing");
        }
        Path marker = Path.of(raw).toAbsolutePath().normalize();
        Path parent = marker.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(marker, PASS_MARKER + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
''',
    encoding="utf-8",
)

plugin = "mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/BranzMmoPlugin.java"
replace_once(
    plugin,
    "        characterSessionController.addReadyHandler(chronicleController::reconcile);\n",
    "        OnboardingClientAcceptanceProbe.install(\n"
    "                this, startingFoundationController, characterSessionController);\n"
    "        characterSessionController.addReadyHandler(chronicleController::reconcile);\n",
)

build = "mmo-bootstrap/build.gradle.kts"
replace_once(
    build,
    '    val combatAcceptance = providers.gradleProperty("combatAcceptance").orNull == "true"\n',
    '    val onboardingAcceptance = providers.gradleProperty("onboardingAcceptance").orNull == "true"\n'
    '    val combatAcceptance = providers.gradleProperty("combatAcceptance").orNull == "true"\n',
)
replace_once(
    build,
    "    if (physicalLmbAcceptance) {\n",
    '''    if (onboardingAcceptance) {
        val acceptanceMarker =
            project.layout.buildDirectory.file("onboarding-client-acceptance.pass").get().asFile
        jvmArgs(
            "-Dmmo.bootstrap.onboarding-acceptance-test=true",
            "-Dmmo.bootstrap.onboarding-acceptance-marker=${acceptanceMarker.absolutePath}",
        )
        doFirst {
            acceptanceMarker.delete()
        }
        doLast {
            check(
                acceptanceMarker.isFile &&
                    acceptanceMarker.readText().trim() == "ONBOARDING_CLIENT_ACCEPTANCE_PASS",
            ) {
                "Onboarding client acceptance marker was not produced."
            }
        }
    } else if (physicalLmbAcceptance) {
''',
)

root = Path("acceptance/onboarding-client-26.2")
if root.exists():
    raise SystemExit(f"{root}: already exists")
(root / "src/gametest/java/com/branz/mmorpg/acceptance").mkdir(parents=True)
(root / "src/gametest/resources").mkdir(parents=True)
(root / "settings.gradle").write_text(
    '''pluginManagement {
    repositories {
        maven { url = 'https://maven.fabricmc.net/' }
        gradlePluginPortal()
        mavenCentral()
    }
}
rootProject.name = 'branz-onboarding-client-acceptance'
''',
    encoding="utf-8",
)
(root / "gradle.properties").write_text(
    '''org.gradle.jvmargs=-Xmx2G
org.gradle.daemon=false
minecraft_version=26.2
loader_version=0.19.3
loom_version=1.17-SNAPSHOT
fabric_api_version=0.156.0+26.2
''',
    encoding="utf-8",
)
(root / "build.gradle").write_text(
    '''plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
}

fabricApi.configureTests {
    createSourceSet = true
    modId = 'branz-onboarding-client-acceptance'
}

tasks.withType(JavaCompile).configureEach {
    options.release = 25
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
''',
    encoding="utf-8",
)
(root / "src/gametest/resources/fabric.mod.json").write_text(
    '''{
  "schemaVersion": 1,
  "id": "branz-onboarding-client-acceptance",
  "version": "1.0.0",
  "name": "Branz Onboarding Client Acceptance",
  "environment": "client",
  "entrypoints": {
    "fabric-client-gametest": [
      "com.branz.mmorpg.acceptance.OnboardingFoundationClientGameTest"
    ]
  },
  "depends": {
    "fabricloader": ">=0.19.3",
    "minecraft": "26.2",
    "fabric-api": "*"
  }
}
''',
    encoding="utf-8",
)
(root / "src/gametest/java/com/branz/mmorpg/acceptance/OnboardingFoundationClientGameTest.java").write_text(
    '''package com.branz.mmorpg.acceptance;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.inventory.ClickType;

public final class OnboardingFoundationClientGameTest implements FabricClientGameTest {
    private static final int GREATSWORD_SLOT = 10;

    @Override
    public void runTest(ClientGameTestContext context) {
        String address = System.getProperty("branz.acceptance.server", "localhost:25565");
        connect(context, address);
        context.waitFor(client -> client.level != null && client.player != null, 20 * 30);
        context.waitFor(client -> client.gui.screen() instanceof AbstractContainerScreen<?>, 20 * 30);
        context.runOnClient(
                client -> {
                    if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
                        throw new IllegalStateException("starting foundation inventory did not open");
                    }
                    if (screen.getMenu().slots.size() <= GREATSWORD_SLOT) {
                        throw new IllegalStateException("starting foundation inventory is too small");
                    }
                    client.gameMode.handleInventoryMouseClick(
                            screen.getMenu().containerId,
                            GREATSWORD_SLOT,
                            0,
                            ClickType.PICKUP,
                            client.player);
                });
        context.waitFor(client -> client.level == null && client.player == null, 20 * 30);

        connect(context, address);
        context.waitFor(client -> client.level != null && client.player != null, 20 * 30);
        context.waitTicks(5);
        context.runOnClient(
                client -> {
                    if (client.gui.screen() instanceof AbstractContainerScreen<?>) {
                        throw new IllegalStateException("foundation inventory reopened after durable reconnect");
                    }
                });
        context.waitFor(client -> client.level == null && client.player == null, 20 * 30);
        context.setScreen(TitleScreen::new);
        context.waitForScreen(TitleScreen.class);
    }

    private static void connect(ClientGameTestContext context, String address) {
        context.runOnClient(
                client -> {
                    ServerData server =
                            new ServerData("Branz onboarding acceptance", address, ServerData.Type.OTHER);
                    ConnectScreen.startConnecting(
                            client.gui.screen(),
                            client,
                            ServerAddress.parseString(address),
                            server,
                            false,
                            null);
                });
    }
}
''',
    encoding="utf-8",
)
