from pathlib import Path

path = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/BranzMmoPlugin.java")
text = path.read_text(encoding="utf-8")

old = """    private DatabaseRuntime databaseRuntime;\n    private CharacterSessionController characterSessionController;\n    private CombatSessionController combatSessionController;\n"""
new = """    private DatabaseRuntime databaseRuntime;\n    private CharacterSessionController characterSessionController;\n    private PhysicalInventoryInteractionController physicalInventoryInteractionController;\n    private CombatSessionController combatSessionController;\n"""
if old not in text:
    raise SystemExit("field insertion point changed")
text = text.replace(old, new, 1)

old = """        CharacterSessionService characterSessionService =\n                new CharacterSessionService(databaseRuntime, activeBuildEngine.get());\n        characterSessionController =\n                new CharacterSessionController(\n                        this,\n                        characterSessionService,\n                        new BukkitInventoryProjectionService(projectionCodec),\n                        activeItemEngine.get(),\n                        databaseRuntime.settings());\n"""
new = """        CharacterSessionService characterSessionService =\n                new CharacterSessionService(databaseRuntime, activeBuildEngine.get());\n        BukkitInventoryProjectionService inventoryProjectionService =\n                new BukkitInventoryProjectionService(projectionCodec);\n        characterSessionController =\n                new CharacterSessionController(\n                        this,\n                        characterSessionService,\n                        inventoryProjectionService,\n                        activeItemEngine.get(),\n                        databaseRuntime.settings());\n        physicalInventoryInteractionController =\n                new PhysicalInventoryInteractionController(\n                        this,\n                        characterSessionController,\n                        projectionCodec,\n                        new PhysicalInventoryItemMoveService(databaseRuntime, characterSessionService),\n                        snapshot.manifest().contentVersion());\n"""
if old not in text:
    raise SystemExit("runtime construction point changed")
text = text.replace(old, new, 1)

old = """        if (combatSessionController != null) {\n            combatSessionController.shutdown();\n            combatSessionController = null;\n        }\n        if (characterSessionController != null) {\n"""
new = """        if (combatSessionController != null) {\n            combatSessionController.shutdown();\n            combatSessionController = null;\n        }\n        if (physicalInventoryInteractionController != null) {\n            physicalInventoryInteractionController.shutdown();\n            physicalInventoryInteractionController = null;\n        }\n        if (characterSessionController != null) {\n"""
if old not in text:
    raise SystemExit("shutdown insertion point changed")
text = text.replace(old, new, 1)

old = """        getServer().getPluginManager().registerEvents(resourcePackGate, this);\n        getServer().getPluginManager().registerEvents(characterSessionController, this);\n        getServer().getPluginManager().registerEvents(combatSessionController, this);\n"""
new = """        getServer().getPluginManager().registerEvents(resourcePackGate, this);\n        getServer().getPluginManager().registerEvents(characterSessionController, this);\n        getServer().getPluginManager().registerEvents(physicalInventoryInteractionController, this);\n        getServer().getPluginManager().registerEvents(combatSessionController, this);\n"""
if old not in text:
    raise SystemExit("listener registration point changed")
text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
