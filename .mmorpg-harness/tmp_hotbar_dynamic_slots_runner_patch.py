from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 anchor, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

runner = Path(".mmorpg-harness/runner.py")
replace_once(
    runner,
    '''\n\ndef action_client_acceptance_ingress(\n''',
    '''\n\ndef evaluate_hotbar_move_sequence(\n    snapshots: list[dict[str, Any]],\n    commits: list[tuple[str, int, int]],\n) -> dict[str, bool]:\n    snapshots_exact = len(snapshots) == 5\n    same_uuid = snapshots_exact and len({row["uuid"] for row in snapshots}) == 1\n    slots = [row["slot"] for row in snapshots]\n    gameplay_chain = (\n        snapshots_exact\n        and slots[0] == 0\n        and 0 <= slots[1] <= 7\n        and slots[1] != slots[0]\n        and slots[2] == slots[1]\n        and 0 <= slots[3] <= 7\n        and slots[3] != slots[1]\n        and slots[4] == slots[3]\n    )\n    versions = [row["version"] for row in snapshots]\n    versions_exact = (\n        snapshots_exact\n        and versions[1] == versions[0] + 1\n        and versions[2] == versions[1]\n        and versions[3] == versions[0] + 2\n        and versions[4] == versions[3]\n    )\n    durability_exact = (\n        snapshots_exact\n        and len(\n            {\n                (row["durability"], row["max_durability"])\n                for row in snapshots\n            }\n        )\n        == 1\n    )\n    commit_sequence = (\n        gameplay_chain\n        and same_uuid\n        and len(commits) == 2\n        and commits[0][1:] == (slots[0], slots[1])\n        and commits[1][1:] == (slots[1], slots[3])\n        and all(row[0] == snapshots[0]["uuid"] for row in commits)\n    )\n    return {\n        "hotbar_snapshot_count": snapshots_exact,\n        "hotbar_same_uuid": same_uuid,\n        "hotbar_slot_sequence": gameplay_chain,\n        "hotbar_version_sequence": versions_exact,\n        "hotbar_durability_stable": durability_exact,\n        "hotbar_server_commit_sequence": commit_sequence,\n    }\n\n\ndef action_client_acceptance_ingress(\n''',
    "sequence-helper",
)

old = '''            expected_slots = [0, 1, 1, 2, 2]\n            snapshots_exact = len(hotbar_snapshots) == 5\n            same_uuid = snapshots_exact and len({row["uuid"] for row in hotbar_snapshots}) == 1\n            versions = [row["version"] for row in hotbar_snapshots]\n            versions_exact = (\n                snapshots_exact\n                and versions[1] == versions[0] + 1\n                and versions[2] == versions[1]\n                and versions[3] == versions[0] + 2\n                and versions[4] == versions[3]\n            )\n            durability_exact = (\n                snapshots_exact\n                and len(\n                    {\n                        (row["durability"], row["max_durability"])\n                        for row in hotbar_snapshots\n                    }\n                )\n                == 1\n            )\n            commit_pattern = re.compile(\n                r"PHYSICAL_AUTHORITY_HOTBAR_MOVE_COMMITTED_SERVER .*?value=([0-9a-fA-F-]{36}) "\n                r"source=(\\d+) destination=(\\d+)"\n            )\n            commits = [\n                (match.group(1).lower(), int(match.group(2)), int(match.group(3)))\n                for match in commit_pattern.finditer(paper_text)\n            ]\n            commit_sequence = (\n                len(commits) == 2\n                and [(row[1], row[2]) for row in commits] == [(0, 1), (1, 2)]\n                and same_uuid\n                and all(row[0] == hotbar_snapshots[0]["uuid"] for row in commits)\n            )\n'''
new = '''            commit_pattern = re.compile(\n                r"PHYSICAL_AUTHORITY_HOTBAR_MOVE_COMMITTED_SERVER .*?value=([0-9a-fA-F-]{36}) "\n                r"source=(\\d+) destination=(\\d+)"\n            )\n            commits = [\n                (match.group(1).lower(), int(match.group(2)), int(match.group(3)))\n                for match in commit_pattern.finditer(paper_text)\n            ]\n            hotbar_sequence_checks = evaluate_hotbar_move_sequence(hotbar_snapshots, commits)\n'''
replace_once(runner, old, new, "inline-sequence-parser")
replace_once(
    runner,
    '''                    "hotbar_snapshot_count": snapshots_exact,\n                    "hotbar_same_uuid": same_uuid,\n                    "hotbar_slot_sequence": snapshots_exact\n                    and [row["slot"] for row in hotbar_snapshots] == expected_slots,\n                    "hotbar_version_sequence": versions_exact,\n                    "hotbar_durability_stable": durability_exact,\n                    "hotbar_server_commit_sequence": commit_sequence,\n''',
    '''                    **hotbar_sequence_checks,\n''',
    "sequence-checks-update",
)

selftest = Path(".mmorpg-harness/selftest.py")
replace_once(
    selftest,
    '''    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_HOTBAR_MOVES_V1"].handler is R.action_client_acceptance_hotbar_moves\n\n    bad = manifest(action="NOT_REAL")\n''',
    '''    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_HOTBAR_MOVES_V1"].handler is R.action_client_acceptance_hotbar_moves\n\n    sequence_uuid = "11111111-1111-1111-1111-111111111111"\n    dynamic_snapshots = [\n        {"uuid": sequence_uuid, "slot": 0, "version": 4, "durability": 100, "max_durability": 100},\n        {"uuid": sequence_uuid, "slot": 7, "version": 5, "durability": 100, "max_durability": 100},\n        {"uuid": sequence_uuid, "slot": 7, "version": 5, "durability": 100, "max_durability": 100},\n        {"uuid": sequence_uuid, "slot": 6, "version": 6, "durability": 100, "max_durability": 100},\n        {"uuid": sequence_uuid, "slot": 6, "version": 6, "durability": 100, "max_durability": 100},\n    ]\n    dynamic_commits = [(sequence_uuid, 0, 7), (sequence_uuid, 7, 6)]\n    assert all(R.evaluate_hotbar_move_sequence(dynamic_snapshots, dynamic_commits).values())\n\n    bad_slots = [dict(row) for row in dynamic_snapshots]\n    bad_slots[3]["slot"] = 7\n    bad_slots[4]["slot"] = 7\n    assert not R.evaluate_hotbar_move_sequence(bad_slots, dynamic_commits)["hotbar_slot_sequence"]\n\n    bad_commit = [(sequence_uuid, 0, 7), (sequence_uuid, 7, 5)]\n    assert not R.evaluate_hotbar_move_sequence(dynamic_snapshots, bad_commit)[\n        "hotbar_server_commit_sequence"\n    ]\n\n    bad = manifest(action="NOT_REAL")\n''',
    "selftest-dynamic-sequence",
)

protocol = Path(".mmorpg-harness/PROTOCOL.md")
replace_once(
    protocol,
    '''- `MMO_CLIENT_ACCEPTANCE_HOTBAR_MOVES_V1` — reuse the fixed real-client ingress path with `-PphysicalHotbarAcceptance=true`; require two real InventoryScreen cursor pickup/place moves (slot 0→1 and 1→2), two reconnect reconstructions, exactly five `/mmo physical status` snapshots, one stable Training Blade UUID/durability, slot sequence `0,1,1,2,2`, version sequence `v,v+1,v+1,v+2,v+2`, and matching authoritative server commit markers. This is the bounded Section B1-B2 gate only.\n''',
    '''- `MMO_CLIENT_ACCEPTANCE_HOTBAR_MOVES_V1` — reuse the fixed real-client ingress path with `-PphysicalHotbarAcceptance=true`; require two real InventoryScreen cursor pickup/place moves through currently empty gameplay hotbar destinations, two reconnect reconstructions, exactly five `/mmo physical status` snapshots, one stable Training Blade UUID/durability, slot continuity `0,d1,d1,d2,d2` where `d1` and `d2` are distinct gameplay slots 0–7, version sequence `v,v+1,v+1,v+2,v+2`, and authoritative server commits exactly matching `0→d1` then `d1→d2`. This is the bounded Section B1-B2 gate only.\n''',
    "protocol-dynamic-slots",
)
