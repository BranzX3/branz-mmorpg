from pathlib import Path

runner = Path('.mmorpg-harness/runner.py')
text = runner.read_text(encoding='utf-8')
needle = '''def action_content_validate_fixture(repo: Path, result_dir: Path, manifest: dict[str, Any]) -> tuple[int, str, str, dict[str, Any]]:\n'''
insert = '''def action_bootstrap_combat_acceptance(\n    repo: Path, result_dir: Path, manifest: dict[str, Any]\n) -> tuple[int, str, str, dict[str, Any]]:\n    run_dir = repo / "mmo-bootstrap" / "run"\n    run_dir.mkdir(parents=True, exist_ok=True)\n    (run_dir / "eula.txt").write_text("eula=true\\n", encoding="utf-8")\n    return action_gradle(\n        repo,\n        result_dir,\n        manifest,\n        (":mmo-bootstrap:runServer", "-PcombatAcceptance=true"),\n        600,\n        "BOOTSTRAP_COMBAT_ACCEPTANCE",\n    )\n\n\n'''
if text.count(needle) != 1:
    raise SystemExit(f'function insertion guard failed: found {text.count(needle)}')
text = text.replace(needle, insert + needle)
old = '    "MMO_BOOTSTRAP_COMBAT_ACCEPTANCE_V1": ActionSpec(make_gradle_action((":mmo-bootstrap:runServer", "-PcombatAcceptance=true"), 600, "BOOTSTRAP_COMBAT_ACCEPTANCE"), "BOOTSTRAP_COMBAT_ACCEPTANCE"),\n'
new = '    "MMO_BOOTSTRAP_COMBAT_ACCEPTANCE_V1": ActionSpec(action_bootstrap_combat_acceptance, "BOOTSTRAP_COMBAT_ACCEPTANCE"),\n'
if text.count(old) != 1:
    raise SystemExit(f'action replacement guard failed: found {text.count(old)}')
text = text.replace(old, new)
runner.write_text(text, encoding='utf-8')

selftest = Path('.mmorpg-harness/selftest.py')
text = selftest.read_text(encoding='utf-8')
needle = '    assert R.ACTION_SPECS["MMO_BOOTSTRAP_COMBAT_ACCEPTANCE_V1"].identity == "BOOTSTRAP_COMBAT_ACCEPTANCE"\n'
replacement = needle + '    assert R.ACTION_SPECS["MMO_BOOTSTRAP_COMBAT_ACCEPTANCE_V1"].handler is R.action_bootstrap_combat_acceptance\n'
if text.count(needle) != 1:
    raise SystemExit(f'selftest guard failed: found {text.count(needle)}')
text = text.replace(needle, replacement)
selftest.write_text(text, encoding='utf-8')
