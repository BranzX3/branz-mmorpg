from pathlib import Path

path = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/CombatSessionController.java")
source = path.read_text(encoding="utf-8")

needle = """                        feedback -> {
                            Location location =
"""
replacement = """                        feedback -> {
                            entity.playHurtAnimation(0.0f);
                            Location location =
"""
if source.count(needle) != 1:
    raise SystemExit(f"melee feedback lambda guard failed: {source.count(needle)}")
source = source.replace(needle, replacement, 1)
path.write_text(source, encoding="utf-8")
