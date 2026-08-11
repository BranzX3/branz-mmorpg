from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label} anchor count={count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


runner = Path('.mmorpg-harness/runner.py')
replace_once(
    runner,
    '''def action_client_acceptance_ingress(
    repo: Path,
    result_dir: Path,
    manifest: dict[str, Any],
    require_primary_input: bool = False,
) -> tuple[int, str, str, dict[str, Any]]:
''',
    '''def action_client_acceptance_ingress(
    repo: Path,
    result_dir: Path,
    manifest: dict[str, Any],
    require_primary_input: bool = False,
    require_hotbar_moves: bool = False,
) -> tuple[int, str, str, dict[str, Any]]:
''',
    'ingress signature',
)
replace_once(
    runner,
    '''    if require_primary_input:
        server_argv.append("-PphysicalPrimaryInputAcceptance=true")
    client_argv = [
''',
    '''    if require_primary_input:
        server_argv.append("-PphysicalPrimaryInputAcceptance=true")
    if require_hotbar_moves:
        server_argv.append("-PphysicalHotbarAcceptance=true")
    client_argv = [
''',
    'server argv',
)
replace_once(
    runner,
    '''    if require_primary_input:
        client_argv.append("-PphysicalPrimaryInputAcceptance=true")

    def read_log(path: Path) -> str:
''',
    '''    if require_primary_input:
        client_argv.append("-PphysicalPrimaryInputAcceptance=true")
    if require_hotbar_moves:
        client_argv.append("-PphysicalHotbarAcceptance=true")

    def read_log(path: Path) -> str:
''',
    'client argv',
)
replace_once(
    runner,
    '''                marker_deadline = time.monotonic() + 120
                while time.monotonic() < marker_deadline:
                    client_text = read_log(client_log)
                    if "PHYSICAL_AUTHORITY_STATUS_COMMAND_SENT_CLIENT" in client_text:
                        break
''',
    '''                completion_marker = (
                    "PHYSICAL_AUTHORITY_HOTBAR_SEQUENCE_COMPLETE_CLIENT"
                    if require_hotbar_moves
                    else "PHYSICAL_AUTHORITY_STATUS_COMMAND_SENT_CLIENT"
                )
                marker_deadline = time.monotonic() + (240 if require_hotbar_moves else 120)
                while time.monotonic() < marker_deadline:
                    client_text = read_log(client_log)
                    if completion_marker in client_text:
                        break
''',
    'completion marker',
)
replace_once(
    runner,
    '''            "status_command_sent_client": "PHYSICAL_AUTHORITY_STATUS_COMMAND_SENT_CLIENT"
            in client_text,
            "status_command_logged_server": "/mmo physical status" in paper_text,
''',
    '''            "status_command_sent_client": completion_marker in client_text,
            "status_command_logged_server": "/mmo physical status" in paper_text,
''',
    'base checks',
)
replace_once(
    runner,
    '''        passed = all(checks.values())
        return (
''',
    '''        hotbar_snapshots: list[dict[str, Any]] = []
        if require_hotbar_moves:
            item_pattern = re.compile(
                r"ITEM uuid=([0-9a-fA-F-]{36}) def=weapon\\.training_blade "
                r"loc=CHARACTER_INVENTORY/slot:(\\d+) ver=(\\d+) durability=(\\d+)/(\\d+)"
            )
            for match in item_pattern.finditer(client_text):
                hotbar_snapshots.append(
                    {
                        "uuid": match.group(1).lower(),
                        "slot": int(match.group(2)),
                        "version": int(match.group(3)),
                        "durability": int(match.group(4)),
                        "max_durability": int(match.group(5)),
                    }
                )
            expected_slots = [0, 1, 1, 2, 2]
            snapshots_exact = len(hotbar_snapshots) == 5
            same_uuid = snapshots_exact and len({row["uuid"] for row in hotbar_snapshots}) == 1
            versions = [row["version"] for row in hotbar_snapshots]
            versions_exact = (
                snapshots_exact
                and versions[1] == versions[0] + 1
                and versions[2] == versions[1]
                and versions[3] == versions[0] + 2
                and versions[4] == versions[3]
            )
            durability_exact = (
                snapshots_exact
                and len(
                    {
                        (row["durability"], row["max_durability"])
                        for row in hotbar_snapshots
                    }
                )
                == 1
            )
            commit_pattern = re.compile(
                r"PHYSICAL_AUTHORITY_HOTBAR_MOVE_COMMITTED_SERVER .*?value=([0-9a-fA-F-]{36}) "
                r"source=(\\d+) destination=(\\d+)"
            )
            commits = [
                (match.group(1).lower(), int(match.group(2)), int(match.group(3)))
                for match in commit_pattern.finditer(paper_text)
            ]
            commit_sequence = (
                len(commits) == 2
                and [(row[1], row[2]) for row in commits] == [(0, 1), (1, 2)]
                and same_uuid
                and all(row[0] == hotbar_snapshots[0]["uuid"] for row in commits)
            )
            checks.update(
                {
                    "hotbar_stage_projected_server": "PHYSICAL_AUTHORITY_HOTBAR_STAGE_PROJECTED_SERVER"
                    in paper_text,
                    "hotbar_move1_mouse_client": "PHYSICAL_AUTHORITY_HOTBAR_MOVE1_MOUSE_SENT_CLIENT"
                    in client_text,
                    "hotbar_move2_mouse_client": "PHYSICAL_AUTHORITY_HOTBAR_MOVE2_MOUSE_SENT_CLIENT"
                    in client_text,
                    "hotbar_reconnect1_projected_client": "PHYSICAL_AUTHORITY_HOTBAR_RECONNECT1_PROJECTED_CLIENT"
                    in client_text,
                    "hotbar_reconnect2_projected_client": "PHYSICAL_AUTHORITY_HOTBAR_RECONNECT2_PROJECTED_CLIENT"
                    in client_text,
                    "hotbar_sequence_complete_client": "PHYSICAL_AUTHORITY_HOTBAR_SEQUENCE_COMPLETE_CLIENT"
                    in client_text,
                    "hotbar_status_command_count_server": paper_text.count("/mmo physical status") == 5,
                    "hotbar_snapshot_count": snapshots_exact,
                    "hotbar_same_uuid": same_uuid,
                    "hotbar_slot_sequence": snapshots_exact
                    and [row["slot"] for row in hotbar_snapshots] == expected_slots,
                    "hotbar_version_sequence": versions_exact,
                    "hotbar_durability_stable": durability_exact,
                    "hotbar_server_commit_sequence": commit_sequence,
                }
            )
        passed = all(checks.values())
        return (
''',
    'hotbar checks',
)
replace_once(
    runner,
    '''                "fixed_command_id": (
                    "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_INPUT"
                    if require_primary_input
                    else "PHYSICAL_CLIENT_ACCEPTANCE_INGRESS"
                ),
''',
    '''                "fixed_command_id": (
                    "PHYSICAL_CLIENT_ACCEPTANCE_HOTBAR_MOVES"
                    if require_hotbar_moves
                    else (
                        "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_INPUT"
                        if require_primary_input
                        else "PHYSICAL_CLIENT_ACCEPTANCE_INGRESS"
                    )
                ),
''',
    'identity',
)
replace_once(
    runner,
    '''                "checks": checks,
                "client_wrapper_sha256": sha256_file(client_wrapper),
''',
    '''                "checks": checks,
                "hotbar_snapshots": hotbar_snapshots,
                "client_wrapper_sha256": sha256_file(client_wrapper),
''',
    'record snapshots',
)
replace_once(
    runner,
    '''def action_client_acceptance_primary_input(
    repo: Path, result_dir: Path, manifest: dict[str, Any]
) -> tuple[int, str, str, dict[str, Any]]:
    return action_client_acceptance_ingress(
        repo, result_dir, manifest, require_primary_input=True
    )


ActionHandler = Callable[[Path, Path, dict[str, Any]], tuple[int, str, str, dict[str, Any]]]
''',
    '''def action_client_acceptance_primary_input(
    repo: Path, result_dir: Path, manifest: dict[str, Any]
) -> tuple[int, str, str, dict[str, Any]]:
    return action_client_acceptance_ingress(
        repo, result_dir, manifest, require_primary_input=True
    )


def action_client_acceptance_hotbar_moves(
    repo: Path, result_dir: Path, manifest: dict[str, Any]
) -> tuple[int, str, str, dict[str, Any]]:
    return action_client_acceptance_ingress(
        repo, result_dir, manifest, require_hotbar_moves=True
    )


ActionHandler = Callable[[Path, Path, dict[str, Any]], tuple[int, str, str, dict[str, Any]]]
''',
    'hotbar wrapper',
)
replace_once(
    runner,
    '''    "MMO_CLIENT_ACCEPTANCE_PRIMARY_INPUT_V1": ActionSpec(
        action_client_acceptance_primary_input, "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_INPUT"
    ),
''',
    '''    "MMO_CLIENT_ACCEPTANCE_PRIMARY_INPUT_V1": ActionSpec(
        action_client_acceptance_primary_input, "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_INPUT"
    ),
    "MMO_CLIENT_ACCEPTANCE_HOTBAR_MOVES_V1": ActionSpec(
        action_client_acceptance_hotbar_moves, "PHYSICAL_CLIENT_ACCEPTANCE_HOTBAR_MOVES"
    ),
''',
    'action spec',
)

protocol = Path('.mmorpg-harness/PROTOCOL.md')
replace_once(
    protocol,
    '- `MMO_CLIENT_ACCEPTANCE_PRIMARY_INPUT_V1` — reuse the fixed real-client ingress path with `-PphysicalPrimaryInputAcceptance=true` on both Paper and the checked-in client test; require TransactionService-backed training-blade persistence, authoritative selected-slot projection observed by both server and client, a real client left-mouse press, then Paper evidence that the existing `PlayerAnimationEvent.ARM_SWING` path resolved to `SemanticInput.PRIMARY` and was accepted by `InputRouter.routeFrame`. This remains a bounded primary-input authority gate, not full A–F acceptance.\n',
    '- `MMO_CLIENT_ACCEPTANCE_PRIMARY_INPUT_V1` — reuse the fixed real-client ingress path with `-PphysicalPrimaryInputAcceptance=true` on both Paper and the checked-in client test; require TransactionService-backed training-blade persistence, authoritative selected-slot projection observed by both server and client, a real client left-mouse press, then Paper evidence that the existing `PlayerAnimationEvent.ARM_SWING` path resolved to `SemanticInput.PRIMARY` and was accepted by `InputRouter.routeFrame`. This remains a bounded primary-input authority gate, not full A–F acceptance.\n- `MMO_CLIENT_ACCEPTANCE_HOTBAR_MOVES_V1` — reuse the fixed real-client ingress path with `-PphysicalHotbarAcceptance=true`; require two real InventoryScreen cursor pickup/place moves (slot 0→1 and 1→2), two reconnect reconstructions, exactly five `/mmo physical status` snapshots, one stable Training Blade UUID/durability, slot sequence `0,1,1,2,2`, version sequence `v,v+1,v+1,v+2,v+2`, and matching authoritative server commit markers. This is the bounded Section B1-B2 gate only.\n',
    'protocol line',
)

selftest = Path('.mmorpg-harness/selftest.py')
replace_once(
    selftest,
    '    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_PRIMARY_INPUT_V1"].handler is R.action_client_acceptance_primary_input\n',
    '    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_PRIMARY_INPUT_V1"].handler is R.action_client_acceptance_primary_input\n    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_HOTBAR_MOVES_V1"].identity == "PHYSICAL_CLIENT_ACCEPTANCE_HOTBAR_MOVES"\n    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_HOTBAR_MOVES_V1"].handler is R.action_client_acceptance_hotbar_moves\n',
    'selftest action',
)
