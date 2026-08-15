#!/usr/bin/env python3
"""Offline regression tests for the C4 stackable-lot rejection/restart harness extension."""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("mmorpg_harness_runner_c4_test", HERE / "runner.py")
assert SPEC and SPEC.loader
R = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = R
SPEC.loader.exec_module(R)

UUID1 = "11111111-1111-1111-1111-111111111111"
UUID2 = "22222222-2222-2222-2222-222222222222"
TX1 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
TX2 = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
TX3 = "cccccccc-cccc-cccc-cccc-cccccccccccc"
CONTENT = "v1.milestone-1.example.4"


def row(uuid: str, slot: int, version: int, tx: str, quantity: int = 64) -> str:
    return (
        f"LOT uuid={uuid} def=consumable.training_weapon_coating "
        f"loc=CHARACTER_INVENTORY/slot:{slot} ver={version} qty={quantity} "
        f"tx={tx} content={CONTENT}"
    )


def good_phase() -> str:
    first = row(UUID1, 9, 1, TX1)
    second = row(UUID2, 10, 1, TX2)
    moved = row(UUID1, 7, 2, TX3)
    return "\n".join(
        [
            "PHYSICAL_AUTHORITY_C4_HANDSHAKE_CLIENT",
            first,
            "PHYSICAL_AUTHORITY_C4_STATUS_FIRST_CLIENT",
            "PHYSICAL_AUTHORITY_C4_FIRST_LOT_READY_CLIENT",
            first,
            second,
            "PHYSICAL_AUTHORITY_C4_STATUS_STAGED_CLIENT",
            "PHYSICAL_AUTHORITY_C4_SECOND_LOT_READY_CLIENT",
            "PHYSICAL_AUTHORITY_C4_SPLIT_MOUSE_SENT_CLIENT",
            "PHYSICAL_AUTHORITY_C4_SPLIT_TRANSIENT_CLIENT",
            first,
            second,
            "PHYSICAL_AUTHORITY_C4_STATUS_AFTER_SPLIT_CLIENT",
            "PHYSICAL_AUTHORITY_C4_SPLIT_RECONCILED_CLIENT",
            "PHYSICAL_AUTHORITY_C4_SWAP_PICKUP_CLIENT",
            "PHYSICAL_AUTHORITY_C4_SWAP_MOUSE_SENT_CLIENT",
            first,
            second,
            "PHYSICAL_AUTHORITY_C4_STATUS_AFTER_SWAP_CLIENT",
            "PHYSICAL_AUTHORITY_C4_SWAP_RECONCILED_CLIENT",
            moved,
            second,
            "PHYSICAL_AUTHORITY_C4_STATUS_MOVED_CLIENT",
            "PHYSICAL_AUTHORITY_C4_WHOLE_MOVE_CLIENT",
            moved,
            second,
            "PHYSICAL_AUTHORITY_C4_PRE_RESTART_STABLE_CLIENT",
        ]
    )


def good_paper() -> str:
    return "\n".join(
        [
            "Player0 issued server command: /mmo dev",
            "Player0 issued server command: /mmo dev",
            *[
                f"Player0 issued server command: /item replace entity @s hotbar.{slot % 8} with minecraft:stone"
                for slot in range(9)
            ],
            *["Player0 issued server command: /mmo physical status" for _ in range(7)],
            "PHYSICAL_AUTHORITY_INVENTORY_CLICK_SERVER player=Player0 action=PICKUP_HALF slot=9",
            "PHYSICAL_AUTHORITY_INVENTORY_PLAN_FAILED_SERVER player=Player0 code=PROJECTION_MOVE_DUPLICATE detail=split",
            "PHYSICAL_AUTHORITY_INVENTORY_CLICK_SERVER player=Player0 action=SWAP_WITH_CURSOR slot=10",
            "PHYSICAL_AUTHORITY_INVENTORY_PLAN_FAILED_SERVER player=Player0 code=PROJECTION_MOVE_STACKABLE_UNSUPPORTED detail=swap",
            "PHYSICAL_AUTHORITY_INVENTORY_COMMIT_SUCCESS_SERVER player=Player0 type=STACKABLE_LOT source=9 destination=7 value=" + UUID1,
        ]
    )


def good_restart() -> str:
    moved = row(UUID1, 7, 2, TX3)
    second = row(UUID2, 10, 1, TX2)
    return "\n".join(
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


def main() -> int:
    spec = R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_CONSUMABLE_C4_V1"]
    assert spec.identity == "PHYSICAL_CLIENT_ACCEPTANCE_CONSUMABLE_C4_RESTART"
    assert spec.handler is R.action_client_acceptance_consumable_c4

    server = ["gradlew.bat", ":mmo-bootstrap:runServer"]
    assert R.augment_c4_argv(server) is server
    assert server.count(R.C4_ACCEPTANCE_FLAG) == 1
    R.augment_c4_argv(server)
    assert server.count(R.C4_ACCEPTANCE_FLAG) == 1
    client = ["gradlew.bat", "runClientGameTest"]
    R.augment_c4_argv(client)
    assert client.count(R.C4_ACCEPTANCE_FLAG) == 1

    phase = good_phase()
    paper = good_paper()
    phase_checks = R.evaluate_c4_phase_checks(phase, paper)
    assert all(phase_checks.values()), [name for name, ok in phase_checks.items() if not ok]

    split_mutation = phase.replace(
        row(UUID1, 9, 1, TX1) + "\n" + row(UUID2, 10, 1, TX2) + "\nPHYSICAL_AUTHORITY_C4_STATUS_AFTER_SPLIT_CLIENT",
        row(UUID1, 9, 1, TX1, 63) + "\n" + row(UUID2, 10, 1, TX2) + "\nPHYSICAL_AUTHORITY_C4_STATUS_AFTER_SPLIT_CLIENT",
        1,
    )
    assert not R.evaluate_c4_phase_checks(split_mutation, paper)["c4_split_authority_byte_stable"]

    no_physical_split = paper.replace("action=PICKUP_HALF", "action=PICKUP_ALL", 1)
    assert not R.evaluate_c4_phase_checks(phase, no_physical_split)["c4_server_observed_pickup_half"]

    restart = good_restart()
    restart_paper = "\n".join(["Player0 issued server command: /mmo physical status"] * 3)
    restart_checks = R.evaluate_c4_restart_checks(phase, restart, restart_paper)
    assert all(restart_checks.values()), [name for name, ok in restart_checks.items() if not ok]

    mutated_restart = restart.replace(row(UUID1, 7, 2, TX3), row(UUID1, 7, 2, TX3, 63), 1)
    assert not R.evaluate_c4_restart_checks(phase, mutated_restart, restart_paper)["c4_restart_exact_authority"]

    print("MMORPG_HARNESS_C4_SELFTEST_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
