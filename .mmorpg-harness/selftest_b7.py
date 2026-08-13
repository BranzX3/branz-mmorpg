#!/usr/bin/env python3
"""Offline regression checks for the reviewed B7 broken-restart extension."""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("mmorpg_harness_runner_b7_test", HERE / "runner.py")
assert SPEC and SPEC.loader
R = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = R
SPEC.loader.exec_module(R)


def main() -> int:
    action = R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_PRIMARY_BROKEN_RESTART_V1"]
    assert action.identity == "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_BROKEN_RESTART"
    assert action.handler is R.action_client_acceptance_primary_broken_restart

    status = (
        "ITEM uuid=11111111-1111-1111-1111-111111111111 "
        "def=weapon.training_blade loc=CHARACTER_INVENTORY/slot:0 "
        "ver=2 durability=0/100 "
        "tx=22222222-2222-2222-2222-222222222222 "
        "content=v1.milestone-1.example.4"
    )
    before_client = status + "\n" + status
    restart_client = "\n".join(
        [
            "PHYSICAL_AUTHORITY_SERVER_HANDSHAKE_CLIENT",
            "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_RESTART_PROJECTED_CLIENT",
            status,
            "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_RESTART_STATUS_CLIENT",
            "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_RESTART_STABLE_CLIENT",
        ]
    )
    restart_paper = "Player0 issued server command: /mmo physical status"

    checks = R.evaluate_broken_restart_log_checks(
        before_client, restart_client, restart_paper
    )
    assert all(checks.values()), checks

    changed_uuid = restart_client.replace(
        "11111111-1111-1111-1111-111111111111",
        "33333333-3333-3333-3333-333333333333",
    )
    assert not R.evaluate_broken_restart_log_checks(
        before_client, changed_uuid, restart_paper
    )["broken_restart_exact_authority"]

    restaged = restart_paper + "\nPHYSICAL_AUTHORITY_PRIMARY_STAGE_PERSISTED_SERVER player=Player0"
    assert not R.evaluate_broken_restart_log_checks(
        before_client, restart_client, restaged
    )["broken_restart_no_primary_restage_server"]

    duplicate_status = restart_paper + "\nPlayer0 issued server command: /mmo physical status"
    assert not R.evaluate_broken_restart_log_checks(
        before_client, restart_client, duplicate_status
    )["broken_restart_status_command_once_server"]

    print("MMORPG_HARNESS_B7_SELFTEST_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
