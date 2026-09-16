#!/usr/bin/env python3
"""Section F Chronicle/native-slot boundary extension for the deterministic harness."""
from __future__ import annotations

from typing import Any

CLIENT_FLAG = "-PphysicalChronicleBoundaryAcceptance=true"

SINGLE_MARKERS = (
    "PHYSICAL_AUTHORITY_CHRONICLE_F_ITEMS_READY_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_STATUS_BEFORE_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_QUIVER_BEFORE_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_NATIVE_REJECTED_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_STATUS_AFTER_NATIVE_REJECT_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_VIRTUAL_COMMIT_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_QUIVER_COMMITTED_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_STATUS_AFTER_VIRTUAL_COMMIT_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_REOPENED_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_STATUS_AFTER_REOPEN_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_STATUS_RECONNECT_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_QUIVER_RECONNECT_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_COMPLETE_CLIENT",
)


def evaluate(client_text: str, paper_text: str) -> dict[str, bool]:
    checks = {
        f"f_marker_{index:02d}": client_text.count(marker) == 1
        for index, marker in enumerate(SINGLE_MARKERS, 1)
    }
    checks.update(
        {
            # waitForServerHandshake executes once initially and once after reconnect.
            "f_handshake_twice_client": (
                client_text.count("PHYSICAL_AUTHORITY_CHRONICLE_F_HANDSHAKE_CLIENT") == 2
            ),
            # Chronicle is opened for the native rejection, virtual commit, and explicit reopen check.
            "f_chronicle_opened_three_times_client": (
                client_text.count("PHYSICAL_AUTHORITY_CHRONICLE_F_OPENED_CLIENT") == 3
            ),
            # Fixed client staging: fill hotbar 0..7 and free exactly three grant destinations.
            "f_three_dev_grants_server": paper_text.count("/mmo dev") == 3,
            "f_eleven_item_replace_commands_server": (
                paper_text.count("/item replace entity @s hotbar.") == 11
            ),
            # The client snapshots sword, shield and quiver repeatedly. Retry is bounded by the
            # checked-in immutable client test, so accept only the expected lower bound and its
            # finite retry ceiling rather than task-controlled input.
            "f_status_commands_bounded_server": (
                13 <= paper_text.count("/mmo physical status") <= 156
            ),
        }
    )
    return checks


def runtime_selfcheck() -> None:
    client = "\n".join(
        ["PHYSICAL_AUTHORITY_CHRONICLE_F_HANDSHAKE_CLIENT"] * 2
        + ["PHYSICAL_AUTHORITY_CHRONICLE_F_OPENED_CLIENT"] * 3
        + list(SINGLE_MARKERS)
    )
    paper = "\n".join(
        ["Player0 issued server command: /mmo dev"] * 3
        + ["Player0 issued server command: /item replace entity @s hotbar.0 with minecraft:stone"] * 11
        + ["Player0 issued server command: /mmo physical status"] * 13
    )
    checks = evaluate(client, paper)
    failed = sorted(name for name, passed in checks.items() if not passed)
    if failed:
        raise RuntimeError(f"Section F harness self-check rejected valid evidence: {failed}")

    duplicate = client + "\nPHYSICAL_AUTHORITY_CHRONICLE_F_COMPLETE_CLIENT"
    if evaluate(duplicate, paper)["f_marker_13"]:
        raise RuntimeError("Section F harness self-check accepted duplicate completion evidence")

    too_few_status = "\n".join(
        ["Player0 issued server command: /mmo dev"] * 3
        + ["Player0 issued server command: /item replace entity @s hotbar.0 with minecraft:stone"] * 11
        + ["Player0 issued server command: /mmo physical status"] * 12
    )
    if evaluate(client, too_few_status)["f_status_commands_bounded_server"]:
        raise RuntimeError("Section F harness self-check accepted incomplete physical snapshots")


def install(core: Any) -> None:
    """Install one fixed Section F capability; no task-controlled argv or environment is accepted."""
    runtime_selfcheck()

    def action_client_acceptance_chronicle_boundary_f(repo, result_dir, manifest):
        original_popen = core.subprocess.Popen

        def section_f_popen(argv, *args, **kwargs):
            if isinstance(argv, list) and "runClientGameTest" in argv and CLIENT_FLAG not in argv:
                argv.append(CLIENT_FLAG)
            return original_popen(argv, *args, **kwargs)

        try:
            core.subprocess.Popen = section_f_popen
            code, stdout, stderr, record = core.action_client_acceptance_ingress(
                repo, result_dir, manifest
            )
        finally:
            core.subprocess.Popen = original_popen

        client_text = (result_dir / "client.log").read_text(
            encoding="utf-8", errors="replace"
        )
        paper_text = (result_dir / "paper.log").read_text(
            encoding="utf-8", errors="replace"
        )
        checks = evaluate(client_text, paper_text)
        record.setdefault("checks", {}).update(checks)
        record["fixed_command_id"] = "PHYSICAL_CLIENT_ACCEPTANCE_CHRONICLE_BOUNDARY_F_V1"
        record["section_f_client_flag"] = CLIENT_FLAG
        passed = code == 0 and all(checks.values())
        record["action_status"] = "PASS" if passed else "FAIL"
        return (
            0 if passed else 1,
            (
                "PHYSICAL_CLIENT_ACCEPTANCE_CHRONICLE_BOUNDARY_F_V1_PASS\n"
                if passed
                else "PHYSICAL_CLIENT_ACCEPTANCE_CHRONICLE_BOUNDARY_F_V1_FAIL\n"
            ),
            stderr,
            record,
        )

    core.evaluate_chronicle_boundary_f_checks = evaluate
    core.action_client_acceptance_chronicle_boundary_f = (
        action_client_acceptance_chronicle_boundary_f
    )
    core.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_CHRONICLE_BOUNDARY_F_V1"] = core.ActionSpec(
        action_client_acceptance_chronicle_boundary_f,
        "PHYSICAL_CLIENT_ACCEPTANCE_CHRONICLE_BOUNDARY_F_V1",
    )
