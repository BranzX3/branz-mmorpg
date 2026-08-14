#!/usr/bin/env python3
"""Offline regression tests for the Branz MMORPG Harness protocol core."""
from __future__ import annotations

import importlib.util
import json
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("mmorpg_harness_runner", HERE / "runner.py")
assert SPEC and SPEC.loader
R = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = R
SPEC.loader.exec_module(R)


def expect_code(code: str, fn) -> None:
    try:
        fn()
    except R.HarnessError as exc:
        assert exc.code == code, (exc.code, code, exc.message)
    else:
        raise AssertionError(f"expected {code}")


def manifest(task_id: str = "MMO-TEST-0001", action: str = "HARNESS_CANARY_V1") -> dict:
    return {
        "protocol_version": 1,
        "task_id": task_id,
        "task_branch": f"harness-task/{task_id}",
        "base_commit": "a" * 40,
        "action_id": action,
        "writable_paths": [f".mmorpg-harness/results/{task_id}/**"],
    }


def main() -> int:
    valid = manifest()
    assert R.validate_manifest(valid) == (
        "MMO-TEST-0001",
        "harness-task/MMO-TEST-0001",
        "a" * 40,
        "HARNESS_CANARY_V1",
    )

    for key in ("commands", "argv", "shell", "gradle_args", "env"):
        bad = manifest()
        bad[key] = ["whoami"] if key in {"commands", "argv", "gradle_args"} else "whoami"
        expect_code("REMOTE_COMMANDS_FORBIDDEN", lambda b=bad: R.validate_manifest(b))

    assert "MMO_BOOTSTRAP_COMBAT_ACCEPTANCE_V1" in R.ACTION_SPECS
    assert R.ACTION_SPECS["MMO_BOOTSTRAP_COMBAT_ACCEPTANCE_V1"].identity == "BOOTSTRAP_COMBAT_ACCEPTANCE"
    assert R.ACTION_SPECS["MMO_BOOTSTRAP_COMBAT_ACCEPTANCE_V1"].handler is R.action_bootstrap_combat_acceptance
    assert R.ACTION_SPECS["MMO_BOOTSTRAP_INVALID_CONTENT_SMOKE_V1"].identity == "BOOTSTRAP_INVALID_CONTENT_SMOKE"
    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_COMPILE_V1"].identity == "PHYSICAL_CLIENT_ACCEPTANCE_COMPILE"
    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_COMPILE_V1"].handler is R.action_client_acceptance_compile
    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_INGRESS_V1"].identity == "PHYSICAL_CLIENT_ACCEPTANCE_INGRESS"
    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_INGRESS_V1"].handler is R.action_client_acceptance_ingress
    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_PRIMARY_INPUT_V1"].identity == "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_INPUT"
    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_PRIMARY_INPUT_V1"].handler is R.action_client_acceptance_primary_input
    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_PRIMARY_HIT_V1"].identity == "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_HIT"
    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_PRIMARY_HIT_V1"].handler is R.action_client_acceptance_primary_hit
    hit_client = "\n".join([
        "PHYSICAL_AUTHORITY_PRIMARY_HIT_TARGETS_READY_CLIENT",
        "PHYSICAL_AUTHORITY_PRIMARY_HIT_STATUS_BEFORE_CLIENT",
        "PHYSICAL_AUTHORITY_PRIMARY_HIT_SETTLED_CLIENT",
        "PHYSICAL_AUTHORITY_PRIMARY_HIT_STATUS_AFTER_CLIENT",
        "PHYSICAL_AUTHORITY_PRIMARY_HIT_AUTHORITY_WORN_ONCE_CLIENT",
    ])
    hit_paper = "\n".join([
        "PHYSICAL_AUTHORITY_B4_TARGET_A_STAGED_SERVER",
        "PHYSICAL_AUTHORITY_B4_TARGET_B_STAGED_SERVER",
        "PHYSICAL_AUTHORITY_B4_TARGET_A_CHANGED_SERVER",
        "PHYSICAL_AUTHORITY_B4_TARGET_B_CHANGED_SERVER",
        "PHYSICAL_AUTHORITY_WEAPON_SUCCESS_OBSERVED_SERVER actor=x action=a move=m",
    ])
    assert all(R.evaluate_primary_hit_log_checks(hit_client, hit_paper).values())
    duplicate_success = hit_paper + "\nPHYSICAL_AUTHORITY_WEAPON_SUCCESS_OBSERVED_SERVER actor=x action=a move=m"
    assert not R.evaluate_primary_hit_log_checks(hit_client, duplicate_success)[
        "primary_hit_success_observer_once_server"
    ]
    assert not R.evaluate_primary_hit_log_checks(client_text=hit_client, paper_text=hit_paper.replace(
        "PHYSICAL_AUTHORITY_B4_TARGET_B_CHANGED_SERVER", ""
    ))["primary_hit_target_b_changed_server"]

    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_PRIMARY_BROKEN_V1"].identity == "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_BROKEN"
    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_PRIMARY_BROKEN_V1"].handler is R.action_client_acceptance_primary_broken
    broken_client = "\n".join([
        "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_TARGET_READY_CLIENT",
        "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_STATUS_BEFORE_CLIENT",
        "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_FIRST_MOUSE_SENT_CLIENT",
        "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_ZERO_CLIENT",
        "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_RETRY_MOUSE_SENT_CLIENT",
        "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_REJECTION_MESSAGE_CLIENT",
        "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_STATUS_AFTER_REJECT_CLIENT",
        "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_REJECTED_STABLE_CLIENT",
    ])
    broken_paper = "\n".join([
        "PHYSICAL_AUTHORITY_B4_TARGET_A_STAGED_SERVER",
        "PHYSICAL_AUTHORITY_B4_TARGET_B_STAGED_SERVER",
        "PHYSICAL_AUTHORITY_B4_TARGET_A_CHANGED_SERVER",
        "PHYSICAL_AUTHORITY_B4_TARGET_B_CHANGED_SERVER",
        "PHYSICAL_AUTHORITY_ARM_SWING_SERVER player=Player0",
        "PHYSICAL_AUTHORITY_PRIMARY_ROUTED_SERVER player=Player0",
        "PHYSICAL_AUTHORITY_WEAPON_SUCCESS_OBSERVED_SERVER actor=x action=a move=m",
        "PHYSICAL_AUTHORITY_BROKEN_ACCELERATED_WEAR_SERVER action=a cost=100",
        "PHYSICAL_AUTHORITY_ARM_SWING_SERVER player=Player0",
    ])
    assert all(R.evaluate_broken_log_checks(broken_client, broken_paper).values())
    duplicate_route = broken_paper + "\nPHYSICAL_AUTHORITY_PRIMARY_ROUTED_SERVER player=Player0"
    assert not R.evaluate_broken_log_checks(broken_client, duplicate_route)[
        "primary_broken_routed_once_server"
    ]
    assert not R.evaluate_broken_log_checks(
        broken_client.replace("PHYSICAL_AUTHORITY_PRIMARY_BROKEN_REJECTION_MESSAGE_CLIENT", ""),
        broken_paper,
    )["primary_broken_rejection_message_client"]

    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_CHRONICLE_SLOT_V1"].identity == "PHYSICAL_CLIENT_ACCEPTANCE_CHRONICLE_SLOT"
    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_CHRONICLE_SLOT_V1"].handler is R.action_client_acceptance_chronicle_slot

    chronicle_client = "\n".join([
        "PHYSICAL_AUTHORITY_CHRONICLE_STAGE_READY_CLIENT",
        "PHYSICAL_AUTHORITY_CHRONICLE_STATUS_BEFORE_CLIENT",
        "PHYSICAL_AUTHORITY_CHRONICLE_PRESENT_BEFORE_CLIENT",
        "PHYSICAL_AUTHORITY_CHRONICLE_PICKUP_OBSERVED_CLIENT",
        "PHYSICAL_AUTHORITY_CHRONICLE_SLOT9_MOUSE_SENT_CLIENT",
        "PHYSICAL_AUTHORITY_CHRONICLE_REJECTION_MESSAGE_CLIENT",
        "PHYSICAL_AUTHORITY_CHRONICLE_PRESENT_AFTER_REJECT_CLIENT",
        "PHYSICAL_AUTHORITY_CHRONICLE_RECONCILED_CLIENT",
        "PHYSICAL_AUTHORITY_CHRONICLE_STATUS_AFTER_CLOSE_CLIENT",
        "PHYSICAL_AUTHORITY_CHRONICLE_AUTHORITY_STABLE_CLIENT",
        "PHYSICAL_AUTHORITY_CHRONICLE_RECONNECT_PROJECTED_CLIENT",
        "PHYSICAL_AUTHORITY_CHRONICLE_STATUS_RECONNECT_CLIENT",
        "PHYSICAL_AUTHORITY_CHRONICLE_RECONNECT_STABLE_CLIENT",
    ])
    chronicle_paper = "\n".join([
        "PHYSICAL_AUTHORITY_HOTBAR_STAGE_PROJECTED_SERVER player=Player0",
        "Player0 issued server command: /mmo physical status",
        "Player0 issued server command: /mmo physical status",
        "Player0 issued server command: /mmo physical status",
    ])
    assert all(R.evaluate_chronicle_slot_log_checks(
        chronicle_client, chronicle_paper
    ).values())

    chronicle_bad_commit = (
        chronicle_paper
        + "\nPHYSICAL_AUTHORITY_HOTBAR_MOVE_COMMITTED_SERVER "
          "value=11111111-1111-1111-1111-111111111111 "
          "source=0 destination=8"
    )
    assert not R.evaluate_chronicle_slot_log_checks(
        chronicle_client, chronicle_bad_commit
    )["chronicle_no_hotbar_commit_server"]

    assert not R.evaluate_chronicle_slot_log_checks(
        chronicle_client.replace(
            "PHYSICAL_AUTHORITY_CHRONICLE_REJECTION_MESSAGE_CLIENT", ""
        ),
        chronicle_paper,
    )["chronicle_rejection_message_once_client"]
    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_HOTBAR_MOVES_V1"].identity == "PHYSICAL_CLIENT_ACCEPTANCE_HOTBAR_MOVES"
    assert R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_HOTBAR_MOVES_V1"].handler is R.action_client_acceptance_hotbar_moves

    sequence_uuid = "11111111-1111-1111-1111-111111111111"
    dynamic_snapshots = [
        {"uuid": sequence_uuid, "slot": 0, "version": 4, "durability": 100, "max_durability": 100},
        {"uuid": sequence_uuid, "slot": 7, "version": 5, "durability": 100, "max_durability": 100},
        {"uuid": sequence_uuid, "slot": 7, "version": 5, "durability": 100, "max_durability": 100},
        {"uuid": sequence_uuid, "slot": 6, "version": 6, "durability": 100, "max_durability": 100},
        {"uuid": sequence_uuid, "slot": 6, "version": 6, "durability": 100, "max_durability": 100},
    ]
    dynamic_commits = [(sequence_uuid, 0, 7), (sequence_uuid, 7, 6)]
    assert all(R.evaluate_hotbar_move_sequence(dynamic_snapshots, dynamic_commits).values())

    bad_slots = [dict(row) for row in dynamic_snapshots]
    bad_slots[3]["slot"] = 7
    bad_slots[4]["slot"] = 7
    assert not R.evaluate_hotbar_move_sequence(bad_slots, dynamic_commits)["hotbar_slot_sequence"]

    bad_commit = [(sequence_uuid, 0, 7), (sequence_uuid, 7, 5)]
    assert not R.evaluate_hotbar_move_sequence(dynamic_snapshots, bad_commit)[
        "hotbar_server_commit_sequence"
    ]

    # Core daemon self-update gate must also exercise the C12 stale-probe contract. The dedicated
    # selftest_c12.py carries the exhaustive cases; this keeps the live runtime gate coupled to the
    # validator semantics even though only selftest.py is copied into the runtime directory.
    c12_uuid = "11111111-1111-1111-1111-111111111111"
    c12_tx1 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    c12_tx2 = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    c12_content = "v1.milestone-1.example.4"

    def c12_row(slot: int, version: int, transaction_id: str) -> str:
        return (
            f"LOT uuid={c12_uuid} def=consumable.training_body_tonic "
            f"loc=CHARACTER_INVENTORY/slot:{slot} ver={version} qty=64 "
            f"tx={transaction_id} content={c12_content}"
        )

    c12_client = "\n".join([
        c12_row(9, 1, c12_tx1),
        "PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_BEFORE_CLIENT",
        c12_row(9, 1, c12_tx1),
        c12_row(9, 1, c12_tx1),
        c12_row(7, 2, c12_tx2),
        "PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_AFTER_MOVE_CLIENT",
        c12_row(7, 2, c12_tx2),
        "PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_RECONNECT_CLIENT",
    ])
    c12_logical = R.logical_lot_rows(c12_client)
    assert len(c12_logical) == 3
    assert R.status_probe_rows_safe(c12_client, c12_logical)

    c12_regressed = c12_client.replace(
        "\n".join([
            c12_row(9, 1, c12_tx1),
            c12_row(9, 1, c12_tx1),
            c12_row(7, 2, c12_tx2),
            "PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_AFTER_MOVE_CLIENT",
        ]),
        "\n".join([
            c12_row(7, 2, c12_tx2),
            c12_row(9, 1, c12_tx1),
            c12_row(7, 2, c12_tx2),
            "PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_AFTER_MOVE_CLIENT",
        ]),
        1,
    )
    c12_regressed_logical = R.logical_lot_rows(c12_regressed)
    assert len(c12_regressed_logical) == 3
    assert not R.status_probe_rows_safe(c12_regressed, c12_regressed_logical)

    bad = manifest(action="NOT_REAL")
    expect_code("ACTION_NOT_ALLOWLISTED", lambda: R.validate_manifest(bad))

    bad = manifest()
    bad["unexpected"] = True
    expect_code("TASK_MANIFEST_UNKNOWN_KEYS", lambda: R.validate_manifest(bad))

    bad = manifest()
    bad["task_branch"] = "newmmo"
    expect_code("TASK_BRANCH_INVALID", lambda: R.validate_manifest(bad))

    bad = manifest()
    bad["writable_paths"] = ["**"]
    expect_code("WRITE_POLICY_MISMATCH", lambda: R.validate_manifest(bad))

    assert R.path_allowed(
        ".mmorpg-harness/results/MMO-TEST-0001/result.json",
        R.expected_writes("MMO-TEST-0001"),
    )
    assert not R.path_allowed(
        "mmo-combat/src/main/java/Evil.java",
        R.expected_writes("MMO-TEST-0001"),
    )

    with tempfile.TemporaryDirectory() as td:
        p = Path(td) / "x.json"
        R.write_json(p, {"b": 2, "a": 1})
        assert json.loads(p.read_text(encoding="utf-8")) == {"a": 1, "b": 2}

    with tempfile.TemporaryDirectory() as td:
        repo = Path(td)
        subprocess.run(
            ["git", "init", "--quiet"],
            cwd=repo,
            text=True,
            capture_output=True,
            check=True,
        )
        (repo / ".gitignore").write_text("*.log\n", encoding="utf-8")
        result_dir = repo / ".mmorpg-harness" / "results" / "MMO-TEST-0001"
        result_dir.mkdir(parents=True)
        (result_dir / "stdout.log").write_text("partial stdout\n", encoding="utf-8")
        (result_dir / "stderr.log").write_text("partial stderr\n", encoding="utf-8")
        R.write_json(result_dir / "result.json", {"execution_status": "FAIL"})
        staged = R.stage_evidence_files(repo, result_dir, R.expected_writes("MMO-TEST-0001"))
        assert ".mmorpg-harness/results/MMO-TEST-0001/stdout.log" in staged
        assert ".mmorpg-harness/results/MMO-TEST-0001/stderr.log" in staged
        cached = subprocess.run(
            ["git", "diff", "--cached", "--name-only"],
            cwd=repo,
            text=True,
            capture_output=True,
            check=True,
        ).stdout.splitlines()
        assert ".mmorpg-harness/results/MMO-TEST-0001/stdout.log" in cached
        assert ".mmorpg-harness/results/MMO-TEST-0001/stderr.log" in cached
        assert all(path.startswith(".mmorpg-harness/results/MMO-TEST-0001/") for path in cached)

    print("MMORPG_HARNESS_SELFTEST_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
