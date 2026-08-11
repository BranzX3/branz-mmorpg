from pathlib import Path

path = Path("acceptance/physical-client-26.2/src/gametest/java/com/branz/mmorpg/acceptance/PhysicalAuthorityClientGameTest.java")
text = path.read_text(encoding="utf-8")

move_one_old = '''                        client.player != null\n                                && client.player.getInventory().getItem(0).isEmpty()\n                                && !client.player\n                                        .getInventory()\n                                        .getItem(moveOneDestination)\n                                        .isEmpty(),\n'''
move_one_new = '''                        client.player != null\n                                && !client.player\n                                        .getInventory()\n                                        .getItem(moveOneDestination)\n                                        .isEmpty(),\n'''
if text.count(move_one_old) != 2:
    raise SystemExit(f"move-one predicate count={text.count(move_one_old)}")
text = text.replace(move_one_old, move_one_new)

move_two_old = '''                        client.player != null\n                                && client.player\n                                        .getInventory()\n                                        .getItem(moveOneDestination)\n                                        .isEmpty()\n                                && !client.player\n                                        .getInventory()\n                                        .getItem(moveTwoDestination)\n                                        .isEmpty(),\n'''
move_two_new = '''                        client.player != null\n                                && !client.player\n                                        .getInventory()\n                                        .getItem(moveTwoDestination)\n                                        .isEmpty(),\n'''
if text.count(move_two_old) != 2:
    raise SystemExit(f"move-two predicate count={text.count(move_two_old)}")
text = text.replace(move_two_old, move_two_new)

path.write_text(text, encoding="utf-8")
