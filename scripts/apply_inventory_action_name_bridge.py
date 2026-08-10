from pathlib import Path

path = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/PhysicalInventoryInteractionController.java")
text = path.read_text(encoding="utf-8")
old = "return PhysicalInventoryInteractionPolicy.supportsStorageAction(event.getAction());"
new = "return PhysicalInventoryInteractionPolicy.supportsStorageAction(event.getAction().name());"
if text.count(old) != 1:
    raise SystemExit("inventory action bridge insertion point changed")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
