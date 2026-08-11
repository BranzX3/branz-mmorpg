from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 anchor, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

controller = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/PhysicalInventoryInteractionController.java")
replace_once(
    controller,
    '''    private static final int STORAGE_SIZE = 36;\n    private static final int CURSOR_OBSERVATION_SLOT = 1000;\n''',
    '''    private static final int STORAGE_SIZE = 36;\n    private static final int CURSOR_OBSERVATION_SLOT = 1000;\n    private static final int FIRST_GAMEPLAY_HOTBAR_SLOT = 0;\n    private static final int LAST_GAMEPLAY_HOTBAR_SLOT = 7;\n''',
    "hotbar-slot-bounds",
)
replace_once(
    controller,
    '''    private final Map<UUID, PendingInteraction> pending = new HashMap<>();\n''',
    '''    private final Map<UUID, PendingInteraction> pending = new HashMap<>();\n    private final Map<UUID, HotbarAcceptanceProgress> hotbarAcceptanceProgress = new HashMap<>();\n''',
    "acceptance-progress-map",
)
replace_once(
    controller,
    '''        if (intent.sourceSlot() == 0 && intent.destinationSlot() == 1) {\n            player.setLevel(9);\n        } else if (intent.sourceSlot() == 1 && intent.destinationSlot() == 2) {\n            player.setLevel(10);\n        }\n''',
    '''        if (!gameplayHotbarSlot(intent.sourceSlot())\n                || !gameplayHotbarSlot(intent.destinationSlot())\n                || intent.sourceSlot() == intent.destinationSlot()) {\n            plugin.getLogger()\n                    .severe(\n                            "PHYSICAL_AUTHORITY_HOTBAR_SEQUENCE_INVALID_SERVER player="\n                                    + player.getName()\n                                    + " value="\n                                    + intent.valueId()\n                                    + " source="\n                                    + intent.sourceSlot()\n                                    + " destination="\n                                    + intent.destinationSlot());\n            return;\n        }\n        UUID playerId = player.getUniqueId();\n        HotbarAcceptanceProgress progress = hotbarAcceptanceProgress.get(playerId);\n        if (progress == null) {\n            if (intent.sourceSlot() != FIRST_GAMEPLAY_HOTBAR_SLOT) {\n                plugin.getLogger()\n                        .severe(\n                                "PHYSICAL_AUTHORITY_HOTBAR_SEQUENCE_INVALID_SERVER player="\n                                        + player.getName()\n                                        + " expectedSource="\n                                        + FIRST_GAMEPLAY_HOTBAR_SLOT\n                                        + " actualSource="\n                                        + intent.sourceSlot());\n                return;\n            }\n            hotbarAcceptanceProgress.put(\n                    playerId,\n                    new HotbarAcceptanceProgress(intent.valueId(), intent.destinationSlot()));\n            player.setLevel(9);\n            return;\n        }\n        if (!progress.valueId().equals(intent.valueId())\n                || intent.sourceSlot() != progress.currentSlot()) {\n            plugin.getLogger()\n                    .severe(\n                            "PHYSICAL_AUTHORITY_HOTBAR_SEQUENCE_INVALID_SERVER player="\n                                    + player.getName()\n                                    + " expectedValue="\n                                    + progress.valueId()\n                                    + " actualValue="\n                                    + intent.valueId()\n                                    + " expectedSource="\n                                    + progress.currentSlot()\n                                    + " actualSource="\n                                    + intent.sourceSlot());\n            return;\n        }\n        hotbarAcceptanceProgress.remove(playerId);\n        player.setLevel(10);\n''',
    "dynamic-acceptance-handshake",
)
replace_once(
    controller,
    '''    private boolean hasProjection(ItemStack stack) {\n        return codec.hasProjectionMarker(stack);\n    }\n''',
    '''    private static boolean gameplayHotbarSlot(int slot) {\n        return slot >= FIRST_GAMEPLAY_HOTBAR_SLOT && slot <= LAST_GAMEPLAY_HOTBAR_SLOT;\n    }\n\n    private boolean hasProjection(ItemStack stack) {\n        return codec.hasProjectionMarker(stack);\n    }\n''',
    "hotbar-slot-helper",
)
replace_once(
    controller,
    '''    private record PendingInteraction(\n            LoadedCharacterSession session, UUID operationId, PendingPhase phase) {\n''',
    '''    private record HotbarAcceptanceProgress(UUID valueId, int currentSlot) {\n        private HotbarAcceptanceProgress {\n            Objects.requireNonNull(valueId, "valueId");\n        }\n    }\n\n    private record PendingInteraction(\n            LoadedCharacterSession session, UUID operationId, PendingPhase phase) {\n''',
    "acceptance-progress-record",
)

client = Path("acceptance/physical-client-26.2/src/gametest/java/com/branz/mmorpg/acceptance/PhysicalAuthorityClientGameTest.java")
replace_once(
    client,
    '''    private static final int INVENTORY_IMAGE_WIDTH = 176;\n    private static final int INVENTORY_IMAGE_HEIGHT = 166;\n''',
    '''    private static final int INVENTORY_IMAGE_WIDTH = 176;\n    private static final int INVENTORY_IMAGE_HEIGHT = 166;\n    private static final int FIRST_GAMEPLAY_HOTBAR_SLOT = 0;\n    private static final int LAST_GAMEPLAY_HOTBAR_SLOT = 7;\n''',
    "client-hotbar-bounds",
)
replace_once(
    client,
    '''        moveHotbarItem(context, 0, 1);\n        System.out.println("PHYSICAL_AUTHORITY_HOTBAR_MOVE1_MOUSE_SENT_CLIENT");\n''',
    '''        int moveOneDestination = moveHotbarItem(context, 0);\n        System.out.println(\n                "PHYSICAL_AUTHORITY_HOTBAR_MOVE1_MOUSE_SENT_CLIENT source=0 destination="\n                        + moveOneDestination);\n''',
    "move-one-dynamic",
)
replace_once(
    client,
    '''                                && client.player.getInventory().getItem(0).isEmpty()\n                                && !client.player.getInventory().getItem(1).isEmpty(),\n''',
    '''                                && client.player.getInventory().getItem(0).isEmpty()\n                                && !client.player\n                                        .getInventory()\n                                        .getItem(moveOneDestination)\n                                        .isEmpty(),\n''',
    "move-one-projection",
)
replace_once(
    client,
    '''                                && client.player.getInventory().getItem(0).isEmpty()\n                                && !client.player.getInventory().getItem(1).isEmpty(),\n                20 * 30);\n        System.out.println("PHYSICAL_AUTHORITY_HOTBAR_RECONNECT1_PROJECTED_CLIENT");\n''',
    '''                                && client.player.getInventory().getItem(0).isEmpty()\n                                && !client.player\n                                        .getInventory()\n                                        .getItem(moveOneDestination)\n                                        .isEmpty(),\n                20 * 30);\n        System.out.println(\n                "PHYSICAL_AUTHORITY_HOTBAR_RECONNECT1_PROJECTED_CLIENT slot="\n                        + moveOneDestination);\n''',
    "reconnect-one-projection",
)
replace_once(
    client,
    '''        moveHotbarItem(context, 1, 2);\n        System.out.println("PHYSICAL_AUTHORITY_HOTBAR_MOVE2_MOUSE_SENT_CLIENT");\n''',
    '''        int moveTwoDestination = moveHotbarItem(context, moveOneDestination);\n        System.out.println(\n                "PHYSICAL_AUTHORITY_HOTBAR_MOVE2_MOUSE_SENT_CLIENT source="\n                        + moveOneDestination\n                        + " destination="\n                        + moveTwoDestination);\n''',
    "move-two-dynamic",
)
replace_once(
    client,
    '''                                && client.player.getInventory().getItem(1).isEmpty()\n                                && !client.player.getInventory().getItem(2).isEmpty(),\n''',
    '''                                && client.player\n                                        .getInventory()\n                                        .getItem(moveOneDestination)\n                                        .isEmpty()\n                                && !client.player\n                                        .getInventory()\n                                        .getItem(moveTwoDestination)\n                                        .isEmpty(),\n''',
    "move-two-projection",
)
replace_once(
    client,
    '''                                && client.player.getInventory().getItem(1).isEmpty()\n                                && !client.player.getInventory().getItem(2).isEmpty(),\n                20 * 30);\n        System.out.println("PHYSICAL_AUTHORITY_HOTBAR_RECONNECT2_PROJECTED_CLIENT");\n''',
    '''                                && client.player\n                                        .getInventory()\n                                        .getItem(moveOneDestination)\n                                        .isEmpty()\n                                && !client.player\n                                        .getInventory()\n                                        .getItem(moveTwoDestination)\n                                        .isEmpty(),\n                20 * 30);\n        System.out.println(\n                "PHYSICAL_AUTHORITY_HOTBAR_RECONNECT2_PROJECTED_CLIENT slot="\n                        + moveTwoDestination);\n''',
    "reconnect-two-projection",
)
replace_once(
    client,
    '''    private static void moveHotbarItem(\n            ClientGameTestContext context, int sourceSlot, int destinationSlot) {\n        context.getInput().pressKey(options -> options.keyInventory);\n        context.waitForScreen(InventoryScreen.class);\n\n        setHotbarCursor(context, sourceSlot);\n''',
    '''    private static int moveHotbarItem(ClientGameTestContext context, int sourceSlot) {\n        context.getInput().pressKey(options -> options.keyInventory);\n        context.waitForScreen(InventoryScreen.class);\n        int destinationSlot = selectEmptyGameplayHotbarDestination(context, sourceSlot);\n        System.out.println(\n                "PHYSICAL_AUTHORITY_HOTBAR_DESTINATION_SELECTED_CLIENT source="\n                        + sourceSlot\n                        + " destination="\n                        + destinationSlot);\n\n        setHotbarCursor(context, sourceSlot);\n''',
    "dynamic-move-signature",
)
replace_once(
    client,
    '''        System.out.println(\n                "PHYSICAL_AUTHORITY_HOTBAR_PLACE_OBSERVED_CLIENT destination="\n                        + destinationSlot);\n        context.waitTicks(2);\n    }\n\n    private static Slot findHotbarSlot(InventoryScreen screen, Object inventory, int hotbarSlot) {\n''',
    '''        System.out.println(\n                "PHYSICAL_AUTHORITY_HOTBAR_PLACE_OBSERVED_CLIENT destination="\n                        + destinationSlot);\n        context.waitTicks(2);\n        return destinationSlot;\n    }\n\n    private static int selectEmptyGameplayHotbarDestination(\n            ClientGameTestContext context, int sourceSlot) {\n        return context.computeOnClient(\n                client -> {\n                    if (!(client.gui.screen() instanceof InventoryScreen screen)\n                            || client.player == null) {\n                        throw new AssertionError(\n                                "InventoryScreen and player must be present for destination selection");\n                    }\n                    for (int slot = LAST_GAMEPLAY_HOTBAR_SLOT;\n                            slot >= FIRST_GAMEPLAY_HOTBAR_SLOT;\n                            slot--) {\n                        if (slot == sourceSlot) {\n                            continue;\n                        }\n                        Slot candidate =\n                                findHotbarSlot(screen, client.player.getInventory(), slot);\n                        if (candidate.getItem().isEmpty()) {\n                            return slot;\n                        }\n                    }\n                    throw new AssertionError(\n                            "No empty gameplay hotbar destination is available for physical acceptance");\n                });\n    }\n\n    private static Slot findHotbarSlot(InventoryScreen screen, Object inventory, int hotbarSlot) {\n''',
    "destination-selector",
)
