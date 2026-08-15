#!/usr/bin/env python3
"""C4 physical stackable-lot rejection/move/restart extension for the immutable MMORPG harness."""
from __future__ import annotations

import os
import re
import socket
import subprocess
import time
from typing import Any

C4_ACCEPTANCE_FLAG = "-PphysicalConsumableC4Acceptance=true"
C4_RESTART_CLIENT_FLAG = "-PphysicalConsumableC4RestartAcceptance=true"
COATING_STATUS = re.compile(
    r"LOT uuid=([0-9a-fA-F-]{36}) def=consumable\.training_weapon_coating "
    r"loc=CHARACTER_INVENTORY/slot:(\d+) ver=(\d+) qty=(\d+) "
    r"tx=([0-9a-fA-F-]{36}) content=(\S+)"
)

PHASE_MARKERS = (
    "PHYSICAL_AUTHORITY_C4_STATUS_FIRST_CLIENT",
    "PHYSICAL_AUTHORITY_C4_STATUS_STAGED_CLIENT",
    "PHYSICAL_AUTHORITY_C4_STATUS_AFTER_SPLIT_CLIENT",
    "PHYSICAL_AUTHORITY_C4_STATUS_AFTER_SWAP_CLIENT",
    "PHYSICAL_AUTHORITY_C4_STATUS_MOVED_CLIENT",
    "PHYSICAL_AUTHORITY_C4_PRE_RESTART_STABLE_CLIENT",
)


def augment_c4_argv(argv: Any) -> Any:
    if not isinstance(argv, list):
        return argv
    is_server = ":mmo-bootstrap:runServer" in argv
    is_client = "runClientGameTest" in argv
    if (is_server or is_client) and C4_ACCEPTANCE_FLAG not in argv:
        argv.append(C4_ACCEPTANCE_FLAG)
    return argv


def _row(match: re.Match[str]) -> dict[str, Any]:
    return {
        "uuid": match.group(1).lower(),
        "slot": int(match.group(2)),
        "version": int(match.group(3)),
        "quantity": int(match.group(4)),
        "transaction_id": match.group(5).lower(),
        "content_version": match.group(6),
    }


def coating_rows(text: str) -> list[dict[str, Any]]:
    return [_row(match) for match in COATING_STATUS.finditer(text)]


def snapshot_before_marker(text: str, marker: str, count: int) -> dict[str, dict[str, Any]]:
    if text.count(marker) != 1:
        return {}
    position = text.index(marker)
    matches = list(COATING_STATUS.finditer(text, 0, position))
    if len(matches) < count:
        return {}
    rows = [_row(match) for match in matches[-count:]]
    by_uuid = {row["uuid"]: row for row in rows}
    return by_uuid if len(by_uuid) == count else {}


def _moved(before: dict[str, Any], after: dict[str, Any]) -> bool:
    return (
        before["uuid"] == after["uuid"]
        and before["content_version"] == after["content_version"]
        and before["quantity"] == after["quantity"] == 64
        and before["slot"] == 9
        and after["slot"] == 7
        and after["version"] == before["version"] + 1
        and after["transaction_id"] != before["transaction_id"]
    )


def phase_snapshots(client_text: str) -> dict[str, dict[str, dict[str, Any]]]:
    return {
        "first": snapshot_before_marker(client_text, PHASE_MARKERS[0], 1),
        "staged": snapshot_before_marker(client_text, PHASE_MARKERS[1], 2),
        "after_split": snapshot_before_marker(client_text, PHASE_MARKERS[2], 2),
        "after_swap": snapshot_before_marker(client_text, PHASE_MARKERS[3], 2),
        "moved": snapshot_before_marker(client_text, PHASE_MARKERS[4], 2),
        "stable": snapshot_before_marker(client_text, PHASE_MARKERS[5], 2),
    }


def evaluate_c4_phase_checks(client_text: str, paper_text: str) -> dict[str, bool]:
    snapshots = phase_snapshots(client_text)
    first = snapshots["first"]
    staged = snapshots["staged"]
    after_split = snapshots["after_split"]
    after_swap = snapshots["after_swap"]
    moved = snapshots["moved"]
    stable = snapshots["stable"]

    identities_ok = False
    staged_ok = False
    moved_ok = False
    stable_ok = False
    if len(first) == 1 and len(staged) == 2:
        first_uuid = next(iter(first))
        first_row = first[first_uuid]
        other = [uuid for uuid in staged if uuid != first_uuid]
        if len(other) == 1:
            second_uuid = other[0]
            second_row = staged[second_uuid]
            identities_ok = staged.get(first_uuid) == first_row
            staged_ok = (
                identities_ok
                and first_row["slot"] == 9
                and first_row["quantity"] == 64
                and second_row["slot"] == 10
                and second_row["quantity"] == 64
            )
            moved_ok = (
                len(moved) == 2
                and first_uuid in moved
                and second_uuid in moved
                and _moved(first_row, moved[first_uuid])
                and moved[second_uuid] == second_row
            )
            stable_ok = moved_ok and stable == moved

    marker_names = (
        "PHYSICAL_AUTHORITY_C4_HANDSHAKE_CLIENT",
        "PHYSICAL_AUTHORITY_C4_FIRST_LOT_READY_CLIENT",
        "PHYSICAL_AUTHORITY_C4_SECOND_LOT_READY_CLIENT",
        "PHYSICAL_AUTHORITY_C4_SPLIT_MOUSE_SENT_CLIENT",
        "PHYSICAL_AUTHORITY_C4_SPLIT_TRANSIENT_CLIENT",
        "PHYSICAL_AUTHORITY_C4_SPLIT_RECONCILED_CLIENT",
        "PHYSICAL_AUTHORITY_C4_SWAP_PICKUP_CLIENT",
        "PHYSICAL_AUTHORITY_C4_SWAP_MOUSE_SENT_CLIENT",
        "PHYSICAL_AUTHORITY_C4_SWAP_RECONCILED_CLIENT",
        "PHYSICAL_AUTHORITY_C4_WHOLE_MOVE_CLIENT",
        "PHYSICAL_AUTHORITY_C4_PRE_RESTART_STABLE_CLIENT",
    )
    checks = {name.lower(): client_text.count(name) == 1 for name in marker_names}
    checks.update(
        {
            "c4_status_markers_exact": all(client_text.count(marker) == 1 for marker in PHASE_MARKERS),
            "c4_first_identity_preserved_on_second_grant": identities_ok,
            "c4_staged_locations_and_quantity": staged_ok,
            "c4_split_authority_byte_stable": staged_ok and after_split == staged,
            "c4_swap_authority_byte_stable": staged_ok and after_swap == staged,
            "c4_whole_lot_move_exact": moved_ok,
            "c4_pre_restart_authority_stable": stable_ok,
            "c4_server_observed_pickup_half": "action=PICKUP_HALF" in paper_text,
            "c4_server_observed_swap_with_cursor": "action=SWAP_WITH_CURSOR" in paper_text,
            "c4_server_split_duplicate_rejected": "code=PROJECTION_MOVE_DUPLICATE" in paper_text,
            "c4_server_swap_stackable_rejected": "code=PROJECTION_MOVE_STACKABLE_UNSUPPORTED" in paper_text,
            "c4_server_plan_failures_at_least_two": paper_text.count("PHYSICAL_AUTHORITY_INVENTORY_PLAN_FAILED_SERVER") >= 2,
            "c4_server_single_lot_commit": paper_text.count("PHYSICAL_AUTHORITY_INVENTORY_COMMIT_SUCCESS_SERVER") == 1,
            "c4_server_expected_lot_commit": (
                "PHYSICAL_AUTHORITY_INVENTORY_COMMIT_SUCCESS_SERVER" in paper_text
                and "type=STACKABLE_LOT source=9 destination=7" in paper_text
            ),
            "c4_server_two_dev_commands": paper_text.count("/mmo dev") == 2,
            "c4_server_nine_filler_commands": paper_text.count("/item replace entity @s hotbar.") == 9,
            "c4_server_status_commands": paper_text.count("/mmo physical status") >= 7,
        }
    )
    return checks


def evaluate_c4_restart_checks(
    phase_client_text: str,
    restart_client_text: str,
    restart_paper_text: str,
) -> dict[str, bool]:
    before = snapshot_before_marker(
        phase_client_text, "PHYSICAL_AUTHORITY_C4_PRE_RESTART_STABLE_CLIENT", 2
    )
    restart_status = snapshot_before_marker(
        restart_client_text, "PHYSICAL_AUTHORITY_C4_RESTART_STATUS_CLIENT", 2
    )
    restart_stable = snapshot_before_marker(
        restart_client_text, "PHYSICAL_AUTHORITY_C4_RESTART_STABLE_CLIENT", 2
    )
    return {
        "c4_restart_pre_snapshot_two": len(before) == 2,
        "c4_restart_status_two": len(restart_status) == 2,
        "c4_restart_exact_authority": len(before) == 2 and restart_status == before,
        "c4_restart_stable_authority": len(before) == 2 and restart_stable == before,
        "c4_restart_handshake_client": restart_client_text.count("PHYSICAL_AUTHORITY_C4_RESTART_HANDSHAKE_CLIENT") == 1,
        "c4_restart_projected_client": restart_client_text.count("PHYSICAL_AUTHORITY_C4_RESTART_PROJECTED_CLIENT") == 1,
        "c4_restart_status_client": restart_client_text.count("PHYSICAL_AUTHORITY_C4_RESTART_STATUS_CLIENT") == 1,
        "c4_restart_stable_client": restart_client_text.count("PHYSICAL_AUTHORITY_C4_RESTART_STABLE_CLIENT") == 1,
        "c4_restart_status_commands_server": restart_paper_text.count("/mmo physical status") == 3,
        "c4_restart_no_dev_restage_server": "/mmo dev" not in restart_paper_text,
    }


def runtime_selfcheck() -> None:
    uuid1 = "11111111-1111-1111-1111-111111111111"
    uuid2 = "22222222-2222-2222-2222-222222222222"
    tx1 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    tx2 = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    tx3 = "cccccccc-cccc-cccc-cccc-cccccccccccc"
    content = "v1.milestone-1.example.4"

    def row(uuid: str, slot: int, version: int, tx: str, qty: int = 64) -> str:
        return (
            f"LOT uuid={uuid} def=consumable.training_weapon_coating "
            f"loc=CHARACTER_INVENTORY/slot:{slot} ver={version} qty={qty} "
            f"tx={tx} content={content}"
        )

    first = row(uuid1, 9, 1, tx1)
    second = row(uuid2, 10, 1, tx2)
    moved = row(uuid1, 7, 2, tx3)
    client = "\n".join(
        [
            "PHYSICAL_AUTHORITY_C4_HANDSHAKE_CLIENT",
            first,
            PHASE_MARKERS[0],
            "PHYSICAL_AUTHORITY_C4_FIRST_LOT_READY_CLIENT",
            first,
            second,
            PHASE_MARKERS[1],
            "PHYSICAL_AUTHORITY_C4_SECOND_LOT_READY_CLIENT",
            "PHYSICAL_AUTHORITY_C4_SPLIT_MOUSE_SENT_CLIENT",
            "PHYSICAL_AUTHORITY_C4_SPLIT_TRANSIENT_CLIENT",
            first,
            second,
            PHASE_MARKERS[2],
            "PHYSICAL_AUTHORITY_C4_SPLIT_RECONCILED_CLIENT",
            "PHYSICAL_AUTHORITY_C4_SWAP_PICKUP_CLIENT",
            "PHYSICAL_AUTHORITY_C4_SWAP_MOUSE_SENT_CLIENT",
            first,
            second,
            PHASE_MARKERS[3],
            "PHYSICAL_AUTHORITY_C4_SWAP_RECONCILED_CLIENT",
            moved,
            second,
            PHASE_MARKERS[4],
            "PHYSICAL_AUTHORITY_C4_WHOLE_MOVE_CLIENT",
            moved,
            second,
            PHASE_MARKERS[5],
        ]
    )
    paper = "\n".join(
        [
            "/mmo dev",
            "/mmo dev",
            *["/item replace entity @s hotbar.0"] * 9,
            *["/mmo physical status"] * 7,
            "PHYSICAL_AUTHORITY_INVENTORY_CLICK_SERVER action=PICKUP_HALF",
            "PHYSICAL_AUTHORITY_INVENTORY_PLAN_FAILED_SERVER code=PROJECTION_MOVE_DUPLICATE",
            "PHYSICAL_AUTHORITY_INVENTORY_CLICK_SERVER action=SWAP_WITH_CURSOR",
            "PHYSICAL_AUTHORITY_INVENTORY_PLAN_FAILED_SERVER code=PROJECTION_MOVE_STACKABLE_UNSUPPORTED",
            "PHYSICAL_AUTHORITY_INVENTORY_COMMIT_SUCCESS_SERVER type=STACKABLE_LOT source=9 destination=7",
        ]
    )
    if not all(evaluate_c4_phase_checks(client, paper).values()):
        raise RuntimeError("C4 runtime self-check rejected valid phase evidence")

    restart = "\n".join(
        [
            "PHYSICAL_AUTHORITY_C4_RESTART_HANDSHAKE_CLIENT",
            "PHYSICAL_AUTHORITY_C4_RESTART_PROJECTED_CLIENT",
            moved,
            second,
            "PHYSICAL_AUTHORITY_C4_RESTART_STATUS_CLIENT",
            moved,
            second,
            moved,
            second,
            "PHYSICAL_AUTHORITY_C4_RESTART_STABLE_CLIENT",
        ]
    )
    restart_paper = "\n".join(["/mmo physical status"] * 3)
    if not all(evaluate_c4_restart_checks(client, restart, restart_paper).values()):
        raise RuntimeError("C4 runtime self-check rejected valid restart evidence")
    mutated = restart.replace(moved, row(uuid1, 7, 2, tx3, qty=63), 1)
    if evaluate_c4_restart_checks(client, mutated, restart_paper)["c4_restart_exact_authority"]:
        raise RuntimeError("C4 runtime self-check accepted mutated restart authority")


def install(core: Any) -> None:
    runtime_selfcheck()

    def action_client_acceptance_consumable_c4(repo, result_dir, manifest):
        started = time.monotonic()
        original_popen = core.subprocess.Popen

        def c4_popen(argv, *args, **kwargs):
            return original_popen(augment_c4_argv(argv), *args, **kwargs)

        try:
            core.subprocess.Popen = c4_popen
            phase_code, phase_stdout, phase_stderr, phase_record = (
                core.action_client_acceptance_ingress(repo, result_dir, manifest)
            )
        finally:
            core.subprocess.Popen = original_popen

        phase_client_path = result_dir / "client.log"
        phase_paper_path = result_dir / "paper.log"
        phase_client_text = phase_client_path.read_text(encoding="utf-8", errors="replace") if phase_client_path.is_file() else ""
        phase_paper_text = phase_paper_path.read_text(encoding="utf-8", errors="replace") if phase_paper_path.is_file() else ""
        phase_checks = evaluate_c4_phase_checks(phase_client_text, phase_paper_text)
        phase_record.setdefault("checks", {}).update(phase_checks)
        phase_record["c4_snapshots"] = phase_snapshots(phase_client_text)
        phase_record["fixed_command_id"] = "PHYSICAL_CLIENT_ACCEPTANCE_CONSUMABLE_C4"
        phase_passed = phase_code == 0 and all(phase_checks.values())
        if not phase_passed:
            phase_record["action_status"] = "FAIL"
            return 1, "PHYSICAL_CLIENT_ACCEPTANCE_CONSUMABLE_C4_FAIL\n", phase_stderr, phase_record

        server_run = repo / "mmo-bootstrap" / "run"
        database_dir = server_run / "plugins" / "BranzMMO" / "embedded-postgres"
        if not database_dir.is_dir():
            raise core.HarnessError(
                "CLIENT_ACCEPTANCE_C4_RESTART_DB_MISSING",
                "embedded PostgreSQL directory missing after C4 phase",
            )
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
            probe.settimeout(0.4)
            if probe.connect_ex(("127.0.0.1", 25565)) == 0:
                raise core.HarnessError("CLIENT_ACCEPTANCE_C4_RESTART_PORT_IN_USE", "127.0.0.1:25565")

        client_dir = repo / "acceptance" / "physical-client-26.2"
        client_wrapper = client_dir / ("gradlew.bat" if os.name == "nt" else "gradlew")
        root_wrapper = core.Path(core.gradle_wrapper(repo))
        if not client_wrapper.is_file():
            raise core.HarnessError("CLIENT_ACCEPTANCE_PROJECT_MISSING", str(client_wrapper.relative_to(repo)))

        paper_restart_log = result_dir / "paper-restart.log"
        client_restart_log = result_dir / "client-restart.log"
        server_env = core.gradle_env(repo)
        client_env = core.gradle_env(repo)
        client_env["GRADLE_USER_HOME"] = str((repo.parent / "gradle-home-client").resolve())
        server_argv = [
            str(root_wrapper),
            "--no-daemon",
            "--console=plain",
            ":mmo-bootstrap:runServer",
            C4_ACCEPTANCE_FLAG,
        ]
        client_argv = [
            str(client_wrapper),
            "--no-daemon",
            "--console=plain",
            "runClientGameTest",
            C4_RESTART_CLIENT_FLAG,
        ]

        def read_log(path):
            try:
                return path.read_text(encoding="utf-8", errors="replace")
            except FileNotFoundError:
                return ""

        def kill_tree(process):
            if process is None or process.poll() is not None:
                return
            if os.name == "nt":
                subprocess.run(
                    ["taskkill", "/PID", str(process.pid), "/T", "/F"],
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    check=False,
                )
            else:
                process.terminate()

        server = None
        client = None
        player_name = None
        server_rc = None
        client_rc = None
        database_preserved_before_restart = database_dir.is_dir()
        try:
            with paper_restart_log.open("w", encoding="utf-8") as paper_out:
                server = core.subprocess.Popen(
                    server_argv,
                    cwd=repo,
                    env=server_env,
                    stdin=core.subprocess.PIPE,
                    stdout=paper_out,
                    stderr=core.subprocess.STDOUT,
                    text=True,
                )
                deadline = time.monotonic() + 240
                while time.monotonic() < deadline:
                    if "Done (" in read_log(paper_restart_log):
                        break
                    if server.poll() is not None:
                        raise core.HarnessError("CLIENT_ACCEPTANCE_C4_RESTART_PAPER_EXITED", f"exit={server.returncode}")
                    time.sleep(1)
                else:
                    raise core.HarnessError("CLIENT_ACCEPTANCE_C4_RESTART_PAPER_TIMEOUT", "restarted Paper did not become ready")

                with client_restart_log.open("w", encoding="utf-8") as client_out:
                    client = core.subprocess.Popen(
                        client_argv,
                        cwd=client_dir,
                        env=client_env,
                        stdout=client_out,
                        stderr=core.subprocess.STDOUT,
                        text=True,
                    )
                    join_deadline = time.monotonic() + 300
                    while time.monotonic() < join_deadline:
                        match = re.search(r"([A-Za-z0-9_]{1,16}) joined the game", read_log(paper_restart_log))
                        if match is not None:
                            player_name = match.group(1)
                            break
                        if client.poll() is not None:
                            raise core.HarnessError("CLIENT_ACCEPTANCE_C4_RESTART_CLIENT_EXITED", f"exit={client.returncode}")
                        time.sleep(0.5)
                    if player_name is None:
                        raise core.HarnessError("CLIENT_ACCEPTANCE_C4_RESTART_JOIN_TIMEOUT", "restart client did not join Paper")
                    if server.stdin is None:
                        raise core.HarnessError("CLIENT_ACCEPTANCE_C4_RESTART_NO_CONSOLE", "restarted Paper stdin unavailable")
                    server.stdin.write(f"op {player_name}\n")
                    server.stdin.write(f"experience set {player_name} 7 levels\n")
                    server.stdin.flush()

                    completion_marker = "PHYSICAL_AUTHORITY_C4_RESTART_STABLE_CLIENT"
                    marker_deadline = time.monotonic() + 180
                    while time.monotonic() < marker_deadline:
                        if completion_marker in read_log(client_restart_log):
                            break
                        if client.poll() is not None:
                            raise core.HarnessError("CLIENT_ACCEPTANCE_C4_RESTART_CLIENT_EXITED", f"exit={client.returncode}")
                        time.sleep(0.5)
                    else:
                        raise core.HarnessError("CLIENT_ACCEPTANCE_C4_RESTART_STATUS_TIMEOUT", "restart client stability marker missing")

                    server.stdin.write("stop\n")
                    server.stdin.flush()
                    try:
                        server_rc = server.wait(timeout=60)
                    except core.subprocess.TimeoutExpired as exc:
                        raise core.HarnessError("CLIENT_ACCEPTANCE_C4_RESTART_PAPER_STOP_TIMEOUT", "restarted Paper did not stop") from exc
                    try:
                        client_rc = client.wait(timeout=60)
                    except core.subprocess.TimeoutExpired as exc:
                        raise core.HarnessError("CLIENT_ACCEPTANCE_C4_RESTART_CLIENT_STOP_TIMEOUT", "restart client did not finish") from exc

            restart_client_text = read_log(client_restart_log)
            restart_paper_text = read_log(paper_restart_log)
            restart_checks = evaluate_c4_restart_checks(
                phase_client_text, restart_client_text, restart_paper_text
            )
            restart_checks.update(
                {
                    "c4_phase_pass": phase_passed,
                    "c4_restart_database_preserved_before": database_preserved_before_restart,
                    "c4_restart_database_preserved_after": database_dir.is_dir(),
                    "c4_restart_client_exit_zero": client_rc == 0,
                    "c4_restart_server_exit_zero": server_rc == 0,
                }
            )
            checks = dict(phase_checks)
            checks.update(restart_checks)
            passed = all(checks.values())
            return (
                0 if passed else 1,
                "PHYSICAL_CLIENT_ACCEPTANCE_CONSUMABLE_C4_PASS\n" if passed else "PHYSICAL_CLIENT_ACCEPTANCE_CONSUMABLE_C4_FAIL\n",
                "",
                {
                    "action_status": "PASS" if passed else "FAIL",
                    "fixed_command_id": "PHYSICAL_CLIENT_ACCEPTANCE_CONSUMABLE_C4_RESTART",
                    "phase_record": phase_record,
                    "restart_server_argv": server_argv,
                    "restart_client_argv": client_argv,
                    "restart_player_name": player_name,
                    "phase_snapshots": phase_snapshots(phase_client_text),
                    "restart_status_snapshot": snapshot_before_marker(restart_client_text, "PHYSICAL_AUTHORITY_C4_RESTART_STATUS_CLIENT", 2),
                    "restart_stable_snapshot": snapshot_before_marker(restart_client_text, "PHYSICAL_AUTHORITY_C4_RESTART_STABLE_CLIENT", 2),
                    "checks": checks,
                    "duration_seconds": round(time.monotonic() - started, 3),
                },
            )
        finally:
            if server is not None and server.poll() is None and server.stdin is not None:
                try:
                    server.stdin.write("stop\n")
                    server.stdin.flush()
                    server.wait(timeout=15)
                except Exception:
                    kill_tree(server)
            kill_tree(client)
            kill_tree(server)

    core.C4_ACCEPTANCE_FLAG = C4_ACCEPTANCE_FLAG
    core.C4_RESTART_CLIENT_FLAG = C4_RESTART_CLIENT_FLAG
    core.augment_c4_argv = augment_c4_argv
    core.coating_rows = coating_rows
    core.snapshot_before_marker = snapshot_before_marker
    core.phase_snapshots = phase_snapshots
    core.evaluate_c4_phase_checks = evaluate_c4_phase_checks
    core.evaluate_c4_restart_checks = evaluate_c4_restart_checks
    core.action_client_acceptance_consumable_c4 = action_client_acceptance_consumable_c4
    core.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_CONSUMABLE_C4_V1"] = core.ActionSpec(
        action_client_acceptance_consumable_c4,
        "PHYSICAL_CLIENT_ACCEPTANCE_CONSUMABLE_C4_RESTART",
    )
