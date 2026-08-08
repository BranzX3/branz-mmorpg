#!/usr/bin/env python3
"""Offline regression tests for the Branz MMORPG Harness protocol core."""
from __future__ import annotations

import importlib.util
import json
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

    print("MMORPG_HARNESS_SELFTEST_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
