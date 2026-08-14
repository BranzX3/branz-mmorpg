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

UUID = "11111111-1111-1111-1111-111111111111"
TX1 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
TX2 = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
CONTENT = "v1.milestone-1.example.4"


def tonic_row(slot: int, version: int, quantity: int, transaction_id: str) -> str:
    return (
        f"LOT uuid={UUID} def=consumable.training_body_tonic "
        f"loc=CHARACTER_INVENTORY/slot:{slot} ver={version} qty={quantity} "
        f"tx={transaction_id} content={CONTENT}"
    )


def good_client() -> str:
    return "\n".join(
        [
            "PHYSICAL_AUTHORITY_CONSUMABLE_FILLER_READY_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_STAGE_READY_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_TARGET_READY_CLIENT",
            tonic_row(9, 1, 64, TX1),
            "PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_BEFORE_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_PICKUP_OBSERVED_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_PLACE_MOUSE_SENT_CLIENT",
            tonic_row(7, 2, 64, TX2),
            "PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_AFTER_MOVE_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_MOVED_ONCE_CLIENT",
            "PHYSICAL_AUTHORITY_CONSUMABLE_RECONNECT_PROJECTED_CLIENT",
            tonic_row(7, 2, 64, TX2),
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
    logical = R.logical_lot_rows(client)
    assert len(logical) == 3
    assert R.status_probe_rows_safe(client, logical)
    assert all(R.evaluate_consumable_lot_checks(client, paper).values())

    # The physical client can observe the slot placement before the asynchronous authority move
    # commits. Bounded status polling may therefore return the exact pre-move row more than once
    # before the first moved row. Those stale reads are safe because authority is still monotonic.
    move_segment = "\n".join(
        [
            tonic_row(7, 2, 64, TX2),
            "PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_AFTER_MOVE_CLIENT",
        ]
    )
    stale_probe_segment = "\n".join(
        [
            tonic_row(9, 1, 64, TX1),
            tonic_row(9, 1, 64, TX1),
            tonic_row(7, 2, 64, TX2),
            "PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_AFTER_MOVE_CLIENT",
        ]
    )
    stale_before_probes = client.replace(move_segment, stale_probe_segment, 1)
    stale_logical = R.logical_lot_rows(stale_before_probes)
    assert len(stale_logical) == 3
    assert R.status_probe_rows_safe(stale_before_probes, stale_logical)
    stale_paper = paper + "\n" + "\n".join(
        "Player0 issued server command: /mmo physical status" for _ in range(2)
    )
    assert all(R.evaluate_consumable_lot_checks(stale_before_probes, stale_paper).values())

    # Once the moved authority has been observed, a later pre-move row would be a regression and
    # must fail closed even if the final row before the marker returns to the expected moved state.
    regressed_probe_segment = "\n".join(
        [
            tonic_row(7, 2, 64, TX2),
            tonic_row(9, 1, 64, TX1),
            tonic_row(7, 2, 64, TX2),
            "PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_AFTER_MOVE_CLIENT",
        ]
    )
    regressed_probe = client.replace(move_segment, regressed_probe_segment, 1)
    regressed_logical = R.logical_lot_rows(regressed_probe)
    assert len(regressed_logical) == 3
    assert not R.status_probe_rows_safe(regressed_probe, regressed_logical)
    assert not R.evaluate_consumable_lot_checks(regressed_probe, stale_paper)[
        "consumable_status_rows_exact"
    ]

    # A third authority state during polling is neither an allowed stale read nor the expected move.
    unexpected_probe_segment = "\n".join(
        [
            tonic_row(6, 2, 64, TX2),
            tonic_row(7, 2, 64, TX2),
            "PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_AFTER_MOVE_CLIENT",
        ]
    )
    unexpected_probe = client.replace(move_segment, unexpected_probe_segment, 1)
    unexpected_logical = R.logical_lot_rows(unexpected_probe)
    assert len(unexpected_logical) == 3
    assert not R.status_probe_rows_safe(unexpected_probe, unexpected_logical)

    # Base ingress may issue one final status probe after the client completion marker.
    # An exact repeat is not a fourth authority transition and must remain transparent in evidence.
    repeated_completion_probe = client + "\n" + tonic_row(7, 2, 64, TX2)
    repeated_logical = R.logical_lot_rows(repeated_completion_probe)
    assert len(R.lot_rows(repeated_completion_probe)) == 4
    assert len(repeated_logical) == 3
    assert R.status_probe_rows_safe(repeated_completion_probe, repeated_logical)
    assert all(R.evaluate_consumable_lot_checks(repeated_completion_probe, paper).values())

    # Any actual fourth state remains a hard failure. Marker-bound logical snapshots stay three,
    # but the raw probe-safety check rejects a changed post-reconnect authority row.
    changed_fourth = client + "\n" + tonic_row(
        7, 3, 63, "cccccccc-cccc-cccc-cccc-cccccccccccc"
    )
    changed_logical = R.logical_lot_rows(changed_fourth)
    assert len(changed_logical) == 3
    assert not R.status_probe_rows_safe(changed_fourth, changed_logical)
    assert not R.evaluate_consumable_lot_checks(changed_fourth, paper)[
        "consumable_status_rows_exact"
    ]

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
