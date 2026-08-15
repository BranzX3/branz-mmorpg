#!/usr/bin/env python3
"""Offline regression tests for the C3 consumable-use harness extension."""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("mmorpg_harness_runner_c3_test", HERE / "runner.py")
assert SPEC and SPEC.loader
R = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = R
SPEC.loader.exec_module(R)

UUID = "11111111-1111-1111-1111-111111111111"
TX1 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
TX2 = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
TX3 = "cccccccc-cccc-cccc-cccc-cccccccccccc"
CONTENT = "v1.milestone-1.example.4"


def row(slot: int, version: int, quantity: int, tx: str) -> str:
    return (
        f"LOT uuid={UUID} def=consumable.training_body_tonic "
        f"loc=CHARACTER_INVENTORY/slot:{slot} ver={version} qty={quantity} "
        f"tx={tx} content={CONTENT}"
    )


def good_client() -> str:
    return "\n".join(
        [
            "PHYSICAL_AUTHORITY_CONSUMABLE_USE_HANDSHAKE_CLIENT",
            row(9, 1, 64, TX1),
            "PHYSICAL_AUTHORITY_CONSUMABLE_USE_STATUS_STAGED_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_USE_STAGE_READY_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_USE_PHYSICAL_MOVE_CLIENT",
            row(9, 1, 64, TX1),
            row(7, 2, 64, TX2),
            "PHYSICAL_AUTHORITY_CONSUMABLE_USE_STATUS_BEFORE_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_USE_HOTBAR_READY_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_USE_SELECTED_CLIENT",
            row(7, 2, 64, TX2),
            "PHYSICAL_AUTHORITY_CONSUMABLE_USE_STATUS_SELECTED_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_USE_MOUSE_SENT_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_USE_WINDUP_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_USE_COMMITTED_CLIENT",
            row(7, 2, 64, TX2),
            row(7, 3, 63, TX3),
            "PHYSICAL_AUTHORITY_CONSUMABLE_USE_STATUS_AFTER_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_USE_DECREMENTED_ONCE_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_USE_RECOVERY_COMPLETE_CLIENT",
            row(7, 3, 63, TX3),
            row(7, 3, 63, TX3),
            row(7, 3, 63, TX3),
            "PHYSICAL_AUTHORITY_CONSUMABLE_USE_STABLE_CLIENT",
            "PHYSICAL_AUTHORITY_SERVER_HANDSHAKE_CLIENT",
            "PHYSICAL_AUTHORITY_STATUS_COMMAND_SENT_CLIENT",
        ]
    )


def good_paper() -> str:
    lines = ["Player0 issued server command: /mmo dev"]
    lines.extend(
        f"Player0 issued server command: /item replace entity @s hotbar.{slot} with minecraft:stone"
        for slot in range(8)
    )
    lines.append("Player0 issued server command: /item replace entity @s hotbar.7 with minecraft:air")
    lines.extend("Player0 issued server command: /mmo physical status" for _ in range(9))
    return "\n".join(lines)


def main() -> int:
    spec = R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_CONSUMABLE_USE_V1"]
    assert spec.identity == "PHYSICAL_CLIENT_ACCEPTANCE_CONSUMABLE_USE"
    assert spec.handler is R.action_client_acceptance_consumable_use

    server = ["gradlew.bat", ":mmo-bootstrap:runServer"]
    assert R.augment_c3_argv(server) is server
    assert server.count(R.C3_ACCEPTANCE_FLAG) == 1
    R.augment_c3_argv(server)
    assert server.count(R.C3_ACCEPTANCE_FLAG) == 1
    client = ["gradlew.bat", "runClientGameTest"]
    R.augment_c3_argv(client)
    assert client.count(R.C3_ACCEPTANCE_FLAG) == 1

    client_text = good_client()
    paper_text = good_paper()
    logical = R.c3_logical_lot_rows(client_text)
    assert len(logical) == 4
    assert R.c3_probe_rows_safe(client_text, logical)
    assert all(R.evaluate_consumable_use_checks(client_text, paper_text).values())

    second_decrement = client_text.replace(
        "\n".join([row(7, 3, 63, TX3), row(7, 3, 63, TX3), row(7, 3, 63, TX3)]),
        "\n".join([row(7, 3, 63, TX3), row(7, 4, 62, "dddddddd-dddd-dddd-dddd-dddddddddddd"), row(7, 4, 62, "dddddddd-dddd-dddd-dddd-dddddddddddd")]),
        1,
    )
    second_logical = R.c3_logical_lot_rows(second_decrement)
    assert len(second_logical) == 4
    assert not R.c3_probe_rows_safe(second_decrement, second_logical)
    assert not R.evaluate_consumable_use_checks(second_decrement, paper_text)["consumable_use_status_rows_safe"]

    skipped_quantity = client_text.replace(row(7, 3, 63, TX3), row(7, 3, 62, TX3))
    assert not R.evaluate_consumable_use_checks(skipped_quantity, paper_text)["consumable_use_quantity_sequence"]

    duplicate_mouse = client_text + "\nPHYSICAL_AUTHORITY_CONSUMABLE_USE_MOUSE_SENT_CLIENT"
    assert not R.evaluate_consumable_use_checks(duplicate_mouse, paper_text)["consumable_use_mouse_once_client"]

    missing_recovery = client_text.replace("PHYSICAL_AUTHORITY_CONSUMABLE_USE_RECOVERY_COMPLETE_CLIENT\n", "", 1)
    assert not R.evaluate_consumable_use_checks(missing_recovery, paper_text)["consumable_use_recovery_complete_client"]

    print("MMORPG_HARNESS_C3_SELFTEST_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
