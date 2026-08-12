#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / ".mmorpg-harness" / "runner.py"
SELFTEST = ROOT / ".mmorpg-harness" / "selftest.py"
PROTOCOL = ROOT / ".mmorpg-harness" / "PROTOCOL.md"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


runner = RUNNER.read_text(encoding="utf-8")

anchor = '''\n\ndef action_client_acceptance_ingress(\n'''
helper = '''\n\ndef evaluate_primary_hit_log_checks(client_text: str, paper_text: str) -> dict[str, bool]:
    return {
        "primary_hit_targets_ready_client": "PHYSICAL_AUTHORITY_PRIMARY_HIT_TARGETS_READY_CLIENT" in client_text,
        "primary_hit_status_before_client": "PHYSICAL_AUTHORITY_PRIMARY_HIT_STATUS_BEFORE_CLIENT" in client_text,
        "primary_hit_settled_client": "PHYSICAL_AUTHORITY_PRIMARY_HIT_SETTLED_CLIENT" in client_text,
        "primary_hit_status_after_client": "PHYSICAL_AUTHORITY_PRIMARY_HIT_STATUS_AFTER_CLIENT" in client_text,
        "primary_hit_authority_worn_once_client": "PHYSICAL_AUTHORITY_PRIMARY_HIT_AUTHORITY_WORN_ONCE_CLIENT" in client_text,
        "primary_hit_target_a_staged_server": "PHYSICAL_AUTHORITY_B4_TARGET_A_STAGED_SERVER" in paper_text,
        "primary_hit_target_b_staged_server": "PHYSICAL_AUTHORITY_B4_TARGET_B_STAGED_SERVER" in paper_text,
        "primary_hit_target_a_changed_server": "PHYSICAL_AUTHORITY_B4_TARGET_A_CHANGED_SERVER" in paper_text,
        "primary_hit_target_b_changed_server": "PHYSICAL_AUTHORITY_B4_TARGET_B_CHANGED_SERVER" in paper_text,
        "primary_hit_success_observer_once_server": paper_text.count("PHYSICAL_AUTHORITY_WEAPON_SUCCESS_OBSERVED_SERVER") == 1,
    }


def action_client_acceptance_ingress(
'''
runner = replace_once(runner, anchor, helper, "insert hit evaluator")

runner = replace_once(
    runner,
    '''    require_primary_input: bool = False,\n    require_hotbar_moves: bool = False,\n''',
    '''    require_primary_input: bool = False,\n    require_primary_hit: bool = False,\n    require_hotbar_moves: bool = False,\n''',
    "signature",
)

runner = replace_once(
    runner,
    '''    if require_primary_input:\n        client_argv.append("-PphysicalPrimaryInputAcceptance=true")\n    if require_hotbar_moves:\n''',
    '''    if require_primary_input:\n        client_argv.append("-PphysicalPrimaryInputAcceptance=true")\n    if require_primary_hit:\n        client_argv.append("-PphysicalPrimaryHitAcceptance=true")\n    if require_hotbar_moves:\n''',
    "client hit property",
)

staging_old = '''                server.stdin.write(f"op {player_name}\\n")\n                server.stdin.write(f"experience set {player_name} 7 levels\\n")\n                server.stdin.flush()\n\n                completion_marker = (\n'''
staging_new = '''                server.stdin.write(f"op {player_name}\\n")\n                server.stdin.write(f"experience set {player_name} 7 levels\\n")\n                server.stdin.flush()\n\n                if require_primary_hit:\n                    stage_deadline = time.monotonic() + 30\n                    while time.monotonic() < stage_deadline:\n                        if "PHYSICAL_AUTHORITY_PRIMARY_STAGE_PROJECTED_SERVER" in read_log(paper_log):\n                            break\n                        if client.poll() is not None:\n                            raise HarnessError(\n                                "CLIENT_ACCEPTANCE_CLIENT_EXITED", f"exit={client.returncode}"\n                            )\n                        time.sleep(0.25)\n                    else:\n                        raise HarnessError(\n                            "CLIENT_ACCEPTANCE_PRIMARY_STAGE_TIMEOUT",\n                            "authoritative primary stage projection marker missing",\n                        )\n                    server.stdin.write(f"tp {player_name} ~ ~ ~ 0 0\\n")\n                    server.stdin.write(\n                        f'execute as {player_name} at @s run summon minecraft:iron_golem ^-0.45 ^ ^2 '\n                        '{NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Tags:["branz_b4_target_a"]}\\n'\n                    )\n                    server.stdin.write(\n                        f'execute as {player_name} at @s run summon minecraft:iron_golem ^0.45 ^ ^2 '\n                        '{NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Tags:["branz_b4_target_b"]}\\n'\n                    )\n                    server.stdin.write(\n                        'execute if entity @e[tag=branz_b4_target_a,limit=1] run say '\n                        'PHYSICAL_AUTHORITY_B4_TARGET_A_STAGED_SERVER\\n'\n                    )\n                    server.stdin.write(\n                        'execute if entity @e[tag=branz_b4_target_b,limit=1] run say '\n                        'PHYSICAL_AUTHORITY_B4_TARGET_B_STAGED_SERVER\\n'\n                    )\n                    server.stdin.write(f"experience set {player_name} 11 levels\\n")\n                    server.stdin.flush()\n                    targets_deadline = time.monotonic() + 10\n                    while time.monotonic() < targets_deadline:\n                        staged_text = read_log(paper_log)\n                        if (\n                            "PHYSICAL_AUTHORITY_B4_TARGET_A_STAGED_SERVER" in staged_text\n                            and "PHYSICAL_AUTHORITY_B4_TARGET_B_STAGED_SERVER" in staged_text\n                        ):\n                            break\n                        time.sleep(0.25)\n                    else:\n                        raise HarnessError(\n                            "CLIENT_ACCEPTANCE_PRIMARY_TARGET_STAGE_TIMEOUT",\n                            "B4 target staging markers missing",\n                        )\n\n                completion_marker = (\n'''
runner = replace_once(runner, staging_old, staging_new, "B4 staging")

post_old = '''                if not receipt:\n                    raise HarnessError(\n                        "CLIENT_ACCEPTANCE_COMMAND_NOT_LOGGED",\n                        "Paper did not log status command",\n                    )\n\n                server.stdin.write("stop\\n")\n'''
post_new = '''                if not receipt:\n                    raise HarnessError(\n                        "CLIENT_ACCEPTANCE_COMMAND_NOT_LOGGED",\n                        "Paper did not log status command",\n                    )\n\n                if require_primary_hit:\n                    server.stdin.write(\n                        'execute unless entity @e[tag=branz_b4_target_a,limit=1,nbt={Health:100.0f}] run say '\n                        'PHYSICAL_AUTHORITY_B4_TARGET_A_CHANGED_SERVER\\n'\n                    )\n                    server.stdin.write(\n                        'execute unless entity @e[tag=branz_b4_target_b,limit=1,nbt={Health:100.0f}] run say '\n                        'PHYSICAL_AUTHORITY_B4_TARGET_B_CHANGED_SERVER\\n'\n                    )\n                    server.stdin.flush()\n                    changed_deadline = time.monotonic() + 10\n                    while time.monotonic() < changed_deadline:\n                        changed_text = read_log(paper_log)\n                        if (\n                            "PHYSICAL_AUTHORITY_B4_TARGET_A_CHANGED_SERVER" in changed_text\n                            and "PHYSICAL_AUTHORITY_B4_TARGET_B_CHANGED_SERVER" in changed_text\n                        ):\n                            break\n                        time.sleep(0.25)\n                    else:\n                        raise HarnessError(\n                            "CLIENT_ACCEPTANCE_PRIMARY_TARGET_HIT_TIMEOUT",\n                            "both B4 targets did not show canonical health change",\n                        )\n\n                server.stdin.write("stop\\n")\n'''
runner = replace_once(runner, post_old, post_new, "B4 post-hit evidence")

primary_checks_old = '''        if require_primary_input:\n            checks.update(\n                {\n                    "primary_stage_persisted_server": "PHYSICAL_AUTHORITY_PRIMARY_STAGE_PERSISTED_SERVER"\n                    in paper_text,\n                    "primary_stage_projected_server": "PHYSICAL_AUTHORITY_PRIMARY_STAGE_PROJECTED_SERVER"\n                    in paper_text,\n                    "primary_projection_ready_client": "PHYSICAL_AUTHORITY_PRIMARY_PROJECTION_READY_CLIENT"\n                    in client_text,\n                    "primary_mouse_sent_client": "PHYSICAL_AUTHORITY_PRIMARY_MOUSE_SENT_CLIENT"\n                    in client_text,\n                    "primary_intent_server": "PHYSICAL_AUTHORITY_PRIMARY_INTENT_SERVER"\n                    in paper_text,\n                    "primary_routed_server": "PHYSICAL_AUTHORITY_PRIMARY_ROUTED_SERVER"\n                    in paper_text,\n                }\n            )\n        hotbar_snapshots: list[dict[str, Any]] = []\n'''
primary_checks_new = primary_checks_old.replace(
    '        hotbar_snapshots: list[dict[str, Any]] = []\n',
    '''        if require_primary_hit:\n            checks.update(evaluate_primary_hit_log_checks(client_text, paper_text))\n        hotbar_snapshots: list[dict[str, Any]] = []\n''',
)
runner = replace_once(runner, primary_checks_old, primary_checks_new, "B4 checks")

identity_old = '''                "fixed_command_id": (\n                    "PHYSICAL_CLIENT_ACCEPTANCE_HOTBAR_MOVES"\n                    if require_hotbar_moves\n                    else (\n                        "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_INPUT"\n                        if require_primary_input\n                        else "PHYSICAL_CLIENT_ACCEPTANCE_INGRESS"\n                    )\n                ),\n'''
identity_new = '''                "fixed_command_id": (\n                    "PHYSICAL_CLIENT_ACCEPTANCE_HOTBAR_MOVES"\n                    if require_hotbar_moves\n                    else (\n                        "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_HIT"\n                        if require_primary_hit\n                        else (\n                            "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_INPUT"\n                            if require_primary_input\n                            else "PHYSICAL_CLIENT_ACCEPTANCE_INGRESS"\n                        )\n                    )\n                ),\n'''
runner = replace_once(runner, identity_old, identity_new, "identity")

wrapper_old = '''\n\ndef action_client_acceptance_hotbar_moves(\n'''
wrapper_new = '''\n\ndef action_client_acceptance_primary_hit(\n    repo: Path, result_dir: Path, manifest: dict[str, Any]\n) -> tuple[int, str, str, dict[str, Any]]:\n    return action_client_acceptance_ingress(\n        repo,\n        result_dir,\n        manifest,\n        require_primary_input=True,\n        require_primary_hit=True,\n    )\n\n\ndef action_client_acceptance_hotbar_moves(\n'''
runner = replace_once(runner, wrapper_old, wrapper_new, "hit action wrapper")

spec_old = '''    "MMO_CLIENT_ACCEPTANCE_PRIMARY_INPUT_V1": ActionSpec(\n        action_client_acceptance_primary_input, "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_INPUT"\n    ),\n    "MMO_CLIENT_ACCEPTANCE_HOTBAR_MOVES_V1": ActionSpec(\n'''
spec_new = '''    "MMO_CLIENT_ACCEPTANCE_PRIMARY_INPUT_V1": ActionSpec(\n        action_client_acceptance_primary_input, "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_INPUT"\n    ),\n    "MMO_CLIENT_ACCEPTANCE_PRIMARY_HIT_V1": ActionSpec(\n        action_client_acceptance_primary_hit, "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_HIT"\n    ),\n    "MMO_CLIENT_ACCEPTANCE_HOTBAR_MOVES_V1": ActionSpec(\n'''
runner = replace_once(runner, spec_old, spec_new, "action spec")
RUNNER.write_text(runner, encoding="utf-8")

selftest = SELFTEST.read_text(encoding="utf-8")
selftest_anchor = '''    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_PRIMARY_INPUT_V1"].handler is R.action_client_acceptance_primary_input\n    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_HOTBAR_MOVES_V1"].identity == "PHYSICAL_CLIENT_ACCEPTANCE_HOTBAR_MOVES"\n'''
selftest_insert = '''    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_PRIMARY_INPUT_V1"].handler is R.action_client_acceptance_primary_input\n    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_PRIMARY_HIT_V1"].identity == "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_HIT"\n    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_PRIMARY_HIT_V1"].handler is R.action_client_acceptance_primary_hit\n    hit_client = "\\n".join([\n        "PHYSICAL_AUTHORITY_PRIMARY_HIT_TARGETS_READY_CLIENT",\n        "PHYSICAL_AUTHORITY_PRIMARY_HIT_STATUS_BEFORE_CLIENT",\n        "PHYSICAL_AUTHORITY_PRIMARY_HIT_SETTLED_CLIENT",\n        "PHYSICAL_AUTHORITY_PRIMARY_HIT_STATUS_AFTER_CLIENT",\n        "PHYSICAL_AUTHORITY_PRIMARY_HIT_AUTHORITY_WORN_ONCE_CLIENT",\n    ])\n    hit_paper = "\\n".join([\n        "PHYSICAL_AUTHORITY_B4_TARGET_A_STAGED_SERVER",\n        "PHYSICAL_AUTHORITY_B4_TARGET_B_STAGED_SERVER",\n        "PHYSICAL_AUTHORITY_B4_TARGET_A_CHANGED_SERVER",\n        "PHYSICAL_AUTHORITY_B4_TARGET_B_CHANGED_SERVER",\n        "PHYSICAL_AUTHORITY_WEAPON_SUCCESS_OBSERVED_SERVER actor=x action=a move=m",\n    ])\n    assert all(R.evaluate_primary_hit_log_checks(hit_client, hit_paper).values())\n    duplicate_success = hit_paper + "\\nPHYSICAL_AUTHORITY_WEAPON_SUCCESS_OBSERVED_SERVER actor=x action=a move=m"\n    assert not R.evaluate_primary_hit_log_checks(hit_client, duplicate_success)[\n        "primary_hit_success_observer_once_server"\n    ]\n    assert not R.evaluate_primary_hit_log_checks(hit_client, hit_paper.replace(\n        "PHYSICAL_AUTHORITY_B4_TARGET_B_CHANGED_SERVER", ""\n    ))["primary_hit_target_b_changed_server"]\n    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_HOTBAR_MOVES_V1"].identity == "PHYSICAL_CLIENT_ACCEPTANCE_HOTBAR_MOVES"\n'''
selftest = replace_once(selftest, selftest_anchor, selftest_insert, "selftest")
SELFTEST.write_text(selftest, encoding="utf-8")

protocol = PROTOCOL.read_text(encoding="utf-8")
protocol_anchor = '- `MMO_CLIENT_ACCEPTANCE_PRIMARY_INPUT_V1` — reuse the fixed real-client ingress path with `-PphysicalPrimaryInputAcceptance=true` on both Paper and the checked-in client test; require TransactionService-backed training-blade persistence, authoritative selected-slot projection observed by both server and client, a real client left-mouse press, then Paper evidence that the existing `PlayerAnimationEvent.ARM_SWING` path resolved to `SemanticInput.PRIMARY` and was accepted by `InputRouter.routeFrame`. This remains a bounded primary-input authority gate, not full A–F acceptance.\n'
protocol_new = protocol_anchor + '- `MMO_CLIENT_ACCEPTANCE_PRIMARY_HIT_V1` — extend the reviewed physical PRIMARY path with fixed B4 target staging only: two immobile eligible iron golems are summoned by repository-owned Paper-console commands inside the authored Training Blade ARC, XP level 11 releases the checked-in client to send one real LMB, both targets must show canonical health change, the successful-action observer must fire exactly once, and the client must prove the same Training Blade UUID/location/content with version `v+1`, durability `d-1`, and a changed transaction id. No task-controlled command, target, position, marker, regex, argv, or environment input is accepted. This is Section B4 only.\n'
protocol = replace_once(protocol, protocol_anchor, protocol_new, "protocol")
PROTOCOL.write_text(protocol, encoding="utf-8")

print("PHYSICAL_PRIMARY_HIT_V1_PATCH_APPLIED")
