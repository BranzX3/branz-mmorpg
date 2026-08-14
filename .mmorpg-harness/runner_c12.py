#!/usr/bin/env python3
"""C1-C2 whole-consumable-lot extension for the immutable MMORPG harness."""
from __future__ import annotations

import re
from typing import Any

C12_ACCEPTANCE_FLAG = "-PphysicalConsumableLotAcceptance=true"
TONIC_STATUS = re.compile(
    r"LOT uuid=([0-9a-fA-F-]{36}) def=consumable\.training_body_tonic "
    r"loc=CHARACTER_INVENTORY/slot:(\d+) ver=(\d+) qty=(\d+) "
    r"tx=([0-9a-fA-F-]{36}) content=(\S+)"
)


def augment_c12_argv(argv: Any) -> Any:
    """Append the reviewed fixed C12 flag only to the server/client acceptance commands."""
    if not isinstance(argv, list):
        return argv
    is_server = ":mmo-bootstrap:runServer" in argv
    is_client = "runClientGameTest" in argv
    if (is_server or is_client) and C12_ACCEPTANCE_FLAG not in argv:
        argv.append(C12_ACCEPTANCE_FLAG)
    return argv


def lot_rows(client_text: str) -> list[dict[str, Any]]:
    return [
        {
            "uuid": match.group(1).lower(),
            "slot": int(match.group(2)),
            "version": int(match.group(3)),
            "quantity": int(match.group(4)),
            "transaction_id": match.group(5).lower(),
            "content_version": match.group(6),
        }
        for match in TONIC_STATUS.finditer(client_text)
    ]


def evaluate_consumable_lot_checks(client_text: str, paper_text: str) -> dict[str, bool]:
    rows = lot_rows(client_text)
    exactly_three = len(rows) == 3
    if exactly_three:
        before, moved, reconnect = rows
        same_uuid = before["uuid"] == moved["uuid"] == reconnect["uuid"]
        slot_sequence = [before["slot"], moved["slot"], reconnect["slot"]] == [9, 7, 7]
        quantity_stable = [before["quantity"], moved["quantity"], reconnect["quantity"]] == [64, 64, 64]
        version_sequence = moved["version"] == before["version"] + 1 and reconnect["version"] == moved["version"]
        transaction_sequence = (
            before["transaction_id"] != moved["transaction_id"]
            and reconnect["transaction_id"] == moved["transaction_id"]
        )
        content_stable = before["content_version"] == moved["content_version"] == reconnect["content_version"]
    else:
        same_uuid = slot_sequence = quantity_stable = version_sequence = False
        transaction_sequence = content_stable = False

    return {
        "consumable_filler_ready_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_FILLER_READY_CLIENT") == 1,
        "consumable_stage_ready_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_STAGE_READY_CLIENT") == 1,
        "consumable_target_ready_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_TARGET_READY_CLIENT") == 1,
        "consumable_status_before_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_BEFORE_CLIENT") == 1,
        "consumable_pickup_once_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_PICKUP_OBSERVED_CLIENT") == 1,
        "consumable_place_mouse_once_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_PLACE_MOUSE_SENT_CLIENT") == 1,
        "consumable_status_after_move_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_AFTER_MOVE_CLIENT") == 1,
        "consumable_moved_once_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_MOVED_ONCE_CLIENT") == 1,
        "consumable_reconnect_projected_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_RECONNECT_PROJECTED_CLIENT") == 1,
        "consumable_status_reconnect_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_RECONNECT_CLIENT") == 1,
        "consumable_reconnect_stable_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_RECONNECT_STABLE_CLIENT") == 1,
        "consumable_status_rows_exact": exactly_three,
        "consumable_same_uuid": same_uuid,
        "consumable_slot_sequence": slot_sequence,
        "consumable_quantity_stable": quantity_stable,
        "consumable_version_sequence": version_sequence,
        "consumable_transaction_sequence": transaction_sequence,
        "consumable_content_stable": content_stable,
        "consumable_dev_command_once_server": paper_text.count("/mmo dev") == 1,
        "consumable_filler_commands_server": paper_text.count("/item replace entity @s hotbar.") == 9,
        "consumable_status_commands_server": paper_text.count("/mmo physical status") >= 3,
    }


def install(core: Any) -> None:
    """Install one reviewed fixed C1-C2 capability without adding manifest inputs."""

    def action_client_acceptance_consumable_lot(repo, result_dir, manifest):
        original_popen = core.subprocess.Popen

        def c12_popen(argv, *args, **kwargs):
            return original_popen(augment_c12_argv(argv), *args, **kwargs)

        try:
            core.subprocess.Popen = c12_popen
            code, stdout, stderr, record = core.action_client_acceptance_ingress(
                repo,
                result_dir,
                manifest,
            )
        finally:
            core.subprocess.Popen = original_popen

        client_text = (result_dir / "client.log").read_text(encoding="utf-8", errors="replace")
        paper_text = (result_dir / "paper.log").read_text(encoding="utf-8", errors="replace")
        checks = evaluate_consumable_lot_checks(client_text, paper_text)
        record.setdefault("checks", {}).update(checks)
        record["lot_snapshots"] = lot_rows(client_text)
        record["fixed_command_id"] = "PHYSICAL_CLIENT_ACCEPTANCE_CONSUMABLE_LOT_MOVE"
        passed = code == 0 and all(checks.values())
        record["action_status"] = "PASS" if passed else "FAIL"
        return (
            0 if passed else 1,
            "PHYSICAL_CLIENT_ACCEPTANCE_CONSUMABLE_LOT_PASS\n"
            if passed
            else "PHYSICAL_CLIENT_ACCEPTANCE_CONSUMABLE_LOT_FAIL\n",
            stderr,
            record,
        )

    core.evaluate_consumable_lot_checks = evaluate_consumable_lot_checks
    core.action_client_acceptance_consumable_lot = action_client_acceptance_consumable_lot
    core.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_CONSUMABLE_LOT_MOVE_V1"] = core.ActionSpec(
        action_client_acceptance_consumable_lot,
        "PHYSICAL_CLIENT_ACCEPTANCE_CONSUMABLE_LOT_MOVE",
    )
