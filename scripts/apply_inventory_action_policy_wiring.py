from pathlib import Path

path = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/PhysicalInventoryInteractionController.java")
text = path.read_text(encoding="utf-8")
old = '''        return switch (event.getAction()) {
            case PICKUP_ALL,
                    PICKUP_SOME,
                    PICKUP_HALF,
                    PICKUP_ONE,
                    PLACE_ALL,
                    PLACE_SOME,
                    PLACE_ONE,
                    SWAP_WITH_CURSOR ->
                    true;
            default -> false;
        };
'''
new = '''        return PhysicalInventoryInteractionPolicy.supportsStorageAction(event.getAction());
'''
if old not in text:
    raise SystemExit("formatted action policy block changed")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
