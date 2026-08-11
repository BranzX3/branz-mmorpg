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
