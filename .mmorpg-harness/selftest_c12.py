#!/usr/bin/env python3
"""Offline regression tests for the C1-C2 consumable-lot harness extension."""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("mmorpg_harness_runner_c12_test", HERE / "runner.py")
assert SPEC and SPEC.loader
R = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = R
SPEC.loader.exec_module(R)


def good_client() -> str:
    uuid = "11111111-1111-1111-1111-111111111111"
    tx1 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    tx2 = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    content = "v1.milestone-1.example.4"
    return "\n".join(
        [
            "PHYSICAL_AUTHORITY_CONSUMABLE_FILLER_READY_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_STAGE_READY_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_TARGET_READY_CLIENT",
            f"LOT uuid={uuid} def=consumable.training_body_tonic loc=CHARACTER_INVENTORY/slot:9 ver=1 qty=64 tx={tx1} content={content}",
            "PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_BEFORE_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_PICKUP_OBSERVED_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_PLACE_MOUSE_SENT_CLIENT",
            f"LOT uuid={uuid} def=consumable.training_body_tonic loc=CHARACTER_INVENTORY/slot:7 ver=2 qty=64 tx={tx2} content={content}",
            "PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_AFTER_MOVE_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_MOVED_ONCE_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_RECONNECT_PROJECTED_CLIENT",
            f"LOT uuid={uuid} def=consumable.training_body_tonic loc=CHARACTER_INVENTORY/slot:7 ver=2 qty=64 tx={tx2} content={content}",
            "PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_RECONNECT_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_RECONNECT_STABLE_CLIENT",
        ]
    )


def good_paper() -> str:
    lines = ["Player0 issued server command: /mmo dev"]
    lines.extend(
        f"Player0 issued server command: /item replace entity @s hotbar.{slot} with minecraft:stone"
        for slot in range(8)
    )
    lines.append(
        "Player0 issued server command: /item replace entity @s hotbar.7 with minecraft:air"
    )
    lines.extend("Player0 issued server command: /mmo physical status" for _ in range(3))
    return "\n".join(lines)


def main() -> int:
    spec = R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_CONSUMABLE_LOT_MOVE_V1"]
    assert spec.identity == "PHYSICAL_CLIENT_ACCEPTANCE_CONSUMABLE_LOT_MOVE"
    assert spec.handler is R.action_client_acceptance_consumable_lot

    server = ["gradlew.bat", ":mmo-bootstrap:runServer"]
    returned_server = R.augment_c12_argv(server)
    assert returned_server is server
    assert server.count(R.C12_ACCEPTANCE_FLAG) == 1
    R.augment_c12_argv(server)
    assert server.count(R.C12_ACCEPTANCE_FLAG) == 1

    client_argv = ["gradlew.bat", "runClientGameTest"]
    R.augment_c12_argv(client_argv)
    assert client_argv.count(R.C12_ACCEPTANCE_FLAG) == 1

    unrelated = ["gradlew.bat", "build"]
    assert R.augment_c12_argv(unrelated) == ["gradlew.bat", "build"]
    assert R.C12_ACCEPTANCE_FLAG not in unrelated
    immutable = ("gradlew.bat", ":mmo-bootstrap:runServer")
    assert R.augment_c12_argv(immutable) is immutable

    client = good_client()
    paper = good_paper()
    assert all(R.evaluate_consumable_lot_checks(client, paper).values())

    bad_quantity = client.replace("slot:7 ver=2 qty=64", "slot:7 ver=2 qty=63", 1)
    assert not R.evaluate_consumable_lot_checks(bad_quantity, paper)[
        "consumable_quantity_stable"
    ]

    bad_slot = client.replace("slot:7 ver=2 qty=64", "slot:6 ver=2 qty=64", 1)
    assert not R.evaluate_consumable_lot_checks(bad_slot, paper)["consumable_slot_sequence"]

    bad_reconnect_tx = client.rsplit(
        "tx=bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 1
    )[0] + "tx=cccccccc-cccc-cccc-cccc-cccccccccccc" + client.rsplit(
        "tx=bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 1
    )[1]
    assert not R.evaluate_consumable_lot_checks(bad_reconnect_tx, paper)[
        "consumable_transaction_sequence"
    ]

    duplicate_mouse = client + "\nPHYSICAL_AUTHORITY_CONSUMABLE_PLACE_MOUSE_SENT_CLIENT"
    assert not R.evaluate_consumable_lot_checks(duplicate_mouse, paper)[
        "consumable_place_mouse_once_client"
    ]

    missing_command = paper.replace(
        "Player0 issued server command: /item replace entity @s hotbar.7 with minecraft:air\n",
        "",
        1,
    )
    assert not R.evaluate_consumable_lot_checks(client, missing_command)[
        "consumable_filler_commands_server"
    ]

    print("MMORPG_HARNESS_C12_SELFTEST_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
