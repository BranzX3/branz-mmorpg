#!/usr/bin/env python3
from pathlib import Path

runner = Path(__file__).with_name("runner.py")
text = runner.read_text(encoding="utf-8")
old = '                    server.stdin.write(f"tp {player_name} ~ ~ ~ 0 0\\n")\n'
new = '                    server.stdin.write(\n                        f"execute as {player_name} at @s run tp @s ~ ~ ~ 0 0\\n"\n                    )\n'
if text.count(old) != 1:
    raise SystemExit(f"expected exact rotation anchor once, found {text.count(old)}")
runner.write_text(text.replace(old, new, 1), encoding="utf-8")
print("B4_ROTATION_PATCH_APPLIED")
