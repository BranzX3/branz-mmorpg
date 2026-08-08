from pathlib import Path

runner = Path('.mmorpg-harness/runner.py')
text = runner.read_text(encoding='utf-8')
needle = '    "MMO_BOOTSTRAP_SMOKE_V1": ActionSpec(make_gradle_action((":mmo-bootstrap:runServer", "-PsmokeTest=true"), 600, "BOOTSTRAP_SMOKE"), "BOOTSTRAP_SMOKE"),\n'
replacement = needle + '    "MMO_BOOTSTRAP_COMBAT_ACCEPTANCE_V1": ActionSpec(make_gradle_action((":mmo-bootstrap:runServer", "-PcombatAcceptance=true"), 600, "BOOTSTRAP_COMBAT_ACCEPTANCE"), "BOOTSTRAP_COMBAT_ACCEPTANCE"),\n'
if text.count(needle) != 1:
    raise SystemExit(f'runner guard failed: expected exactly one smoke action, found {text.count(needle)}')
text = text.replace(needle, replacement)
runner.write_text(text, encoding='utf-8')

protocol = Path('.mmorpg-harness/PROTOCOL.md')
text = protocol.read_text(encoding='utf-8')
needle = '- `MMO_BOOTSTRAP_SMOKE_V1`\n'
replacement = needle + '- `MMO_BOOTSTRAP_COMBAT_ACCEPTANCE_V1`\n'
if text.count(needle) != 1:
    raise SystemExit(f'protocol allowlist guard failed: found {text.count(needle)}')
text = text.replace(needle, replacement)
needle = '- `:mmo-bootstrap:runServer -PsmokeTest=true`\n'
replacement = needle + '- `:mmo-bootstrap:runServer -PcombatAcceptance=true`\n'
if text.count(needle) != 1:
    raise SystemExit(f'protocol command guard failed: found {text.count(needle)}')
text = text.replace(needle, replacement)
protocol.write_text(text, encoding='utf-8')

selftest = Path('.mmorpg-harness/selftest.py')
text = selftest.read_text(encoding='utf-8')
needle = '    bad = manifest(action="NOT_REAL")\n    expect_code("ACTION_NOT_ALLOWLISTED", lambda: R.validate_manifest(bad))\n'
replacement = '    assert "MMO_BOOTSTRAP_COMBAT_ACCEPTANCE_V1" in R.ACTION_SPECS\n    assert R.ACTION_SPECS["MMO_BOOTSTRAP_COMBAT_ACCEPTANCE_V1"].identity == "BOOTSTRAP_COMBAT_ACCEPTANCE"\n\n' + needle
if text.count(needle) != 1:
    raise SystemExit(f'selftest guard failed: found {text.count(needle)}')
text = text.replace(needle, replacement)
selftest.write_text(text, encoding='utf-8')
