from pathlib import Path

path = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/CombatSessionController.java")
text = path.read_text()
old = '''        LoadedCharacterSession character = characters.active(player).orElse(null);
        if (character == null) {
            return Optional.of("Combat not ready: character build is unavailable.");
        }
        Result<BuildResolution, BuildErrorCode> build =
'''
new = '''        LoadedCharacterSession character = characters.active(player).orElse(null);
        if (character == null) {
            return Optional.of("Combat not ready: character build is unavailable.");
        }
        Optional<String> durabilityFailure =
                WeaponCombatReadiness.durabilityFailure(character, main);
        if (durabilityFailure.isPresent()) {
            return durabilityFailure;
        }
        Result<BuildResolution, BuildErrorCode> build =
'''
if text.count(old) != 1:
    raise SystemExit("Expected readiness insertion point exactly once")
path.write_text(text.replace(old, new, 1))
