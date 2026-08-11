from pathlib import Path

patcher = Path(".mmorpg-harness/tmp_hotbar_free_destination_patch.py")
text = patcher.read_text(encoding="utf-8")

helper = '''\n\ndef replace_first(path: Path, old: str, new: str, label: str) -> None:\n    text = path.read_text(encoding="utf-8")\n    count = text.count(old)\n    if count < 1:\n        raise SystemExit(f"{label}: expected at least 1 anchor, found {count}")\n    path.write_text(text.replace(old, new, 1), encoding="utf-8")\n'''
anchor = '\ncontroller = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/PhysicalInventoryInteractionController.java")\n'
if anchor not in text:
    raise SystemExit("patcher helper insertion anchor missing")
text = text.replace(anchor, helper + anchor, 1)

lines = text.splitlines()
for label in ('"move-one-projection",', '"move-two-projection",'):
    try:
        index = lines.index(f"    {label}")
    except ValueError as exc:
        raise SystemExit(f"patcher label missing: {label}") from exc
    for cursor in range(index, -1, -1):
        if lines[cursor] == "replace_once(":
            lines[cursor] = "replace_first("
            break
    else:
        raise SystemExit(f"replace_once call missing before {label}")

corrected = "\n".join(lines) + "\n"
exec(compile(corrected, str(patcher), "exec"), {"__name__": "__main__"})
