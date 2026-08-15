#!/usr/bin/env python3
"""C3 physical consumable-use extension for the immutable MMORPG harness."""
from __future__ import annotations

import re
from typing import Any

C3_ACCEPTANCE_FLAG = "-PphysicalConsumableUseAcceptance=true"
STATUS_MARKERS = (
    "PHYSICAL_AUTHORITY_CONSUMABLE_USE_STATUS_STAGED_CLIENT",
    "PHYSICAL_AUTHORITY_CONSUMABLE_USE_STATUS_BEFORE_CLIENT",
    "PHYSICAL_AUTHORITY_CONSUMABLE_USE_STATUS_SELECTED_CLIENT",
    "PHYSICAL_AUTHORITY_CONSUMABLE_USE_STATUS_AFTER_CLIENT",
)
TONIC_STATUS = re.compile(
    r"LOT uuid=([0-9a-fA-F-]{36}) def=consumable\.training_body_tonic "
    r"loc=CHARACTER_INVENTORY/slot:(\d+) ver=(\d+) qty=(\d+) "
    r"tx=([0-9a-fA-F-]{36}) content=(\S+)"
)


def augment_c3_argv(argv: Any) -> Any:
    if not isinstance(argv, list):
        return argv
    is_server = ":mmo-bootstrap:runServer" in argv
    is_client = "runClientGameTest" in argv
    if (is_server or is_client) and C3_ACCEPTANCE_FLAG not in argv:
        argv.append(C3_ACCEPTANCE_FLAG)
    return argv


def _row(match: re.Match[str]) -> dict[str, Any]:
    return {
        "uuid": match.group(1).lower(),
        "slot": int(match.group(2)),
        "version": int(match.group(3)),
        "quantity": int(match.group(4)),
        "transaction_id": match.group(5).lower(),
        "content_version": match.group(6),
    }


def c3_lot_rows(client_text: str) -> list[dict[str, Any]]:
    return [_row(match) for match in TONIC_STATUS.finditer(client_text)]


def c3_logical_lot_rows(client_text: str) -> list[dict[str, Any]]:
    positions: list[int] = []
    for marker in STATUS_MARKERS:
        if client_text.count(marker) != 1:
            return []
        positions.append(client_text.index(marker))
    if positions != sorted(positions):
        return []
    logical: list[dict[str, Any]] = []
    for position in positions:
        preceding = list(TONIC_STATUS.finditer(client_text, 0, position))
        if not preceding:
            return []
        logical.append(_row(preceding[-1]))
    return logical


def c3_probe_rows_safe(client_text: str, logical: list[dict[str, Any]]) -> bool:
    if len(logical) != 4:
        return False
    marker_positions = [client_text.index(marker) for marker in STATUS_MARKERS]
    staged, before, selected, consumed = logical
    moved_seen = False
    consumed_seen = False
    for match in TONIC_STATUS.finditer(client_text):
        position = match.start()
        row = _row(match)
        if position < marker_positions[0]:
            if row != staged:
                return False
            continue
        if position < marker_positions[1]:
            if row == staged and not moved_seen:
                continue
            if row == before:
                moved_seen = True
                continue
            return False
        if position < marker_positions[2]:
            if row != selected:
                return False
            continue
        if position < marker_positions[3]:
            if row == selected and not consumed_seen:
                continue
            if row == consumed:
                consumed_seen = True
                continue
            return False
        if row != consumed:
            return False
    return moved_seen and consumed_seen


def evaluate_consumable_use_checks(client_text: str, paper_text: str) -> dict[str, bool]:
    rows = c3_logical_lot_rows(client_text)
    safe_rows = len(rows) == 4 and c3_probe_rows_safe(client_text, rows)
    if safe_rows:
        staged, before, selected, consumed = rows
        same_uuid = staged["uuid"] == before["uuid"] == selected["uuid"] == consumed["uuid"]
        same_content = (
            staged["content_version"]
            == before["content_version"]
            == selected["content_version"]
            == consumed["content_version"]
        )
        slot_sequence = [staged["slot"], before["slot"], selected["slot"], consumed["slot"]] == [9, 7, 7, 7]
        quantity_sequence = [
            staged["quantity"],
            before["quantity"],
            selected["quantity"],
            consumed["quantity"],
        ] == [64, 64, 64, 63]
        version_sequence = (
            before["version"] == staged["version"] + 1
            and selected["version"] == before["version"]
            and consumed["version"] == before["version"] + 1
        )
        transaction_sequence = (
            before["transaction_id"] != staged["transaction_id"]
            and selected["transaction_id"] == before["transaction_id"]
            and consumed["transaction_id"] != before["transaction_id"]
        )
    else:
        same_uuid = same_content = slot_sequence = quantity_sequence = False
        version_sequence = transaction_sequence = False

    marker_checks = {
        "consumable_use_handshake_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_USE_HANDSHAKE_CLIENT") == 1,
        "consumable_use_stage_ready_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_USE_STAGE_READY_CLIENT") == 1,
        "consumable_use_physical_move_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_USE_PHYSICAL_MOVE_CLIENT") == 1,
        "consumable_use_hotbar_ready_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_USE_HOTBAR_READY_CLIENT") == 1,
        "consumable_use_selected_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_USE_SELECTED_CLIENT") == 1,
        "consumable_use_mouse_once_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_USE_MOUSE_SENT_CLIENT") == 1,
        "consumable_use_windup_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_USE_WINDUP_CLIENT") == 1,
        "consumable_use_committed_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_USE_COMMITTED_CLIENT") == 1,
        "consumable_use_decremented_once_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_USE_DECREMENTED_ONCE_CLIENT") == 1,
        "consumable_use_recovery_complete_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_USE_RECOVERY_COMPLETE_CLIENT") == 1,
        "consumable_use_stable_client": client_text.count("PHYSICAL_AUTHORITY_CONSUMABLE_USE_STABLE_CLIENT") == 1,
    }
    return {
        **marker_checks,
        "consumable_use_status_rows_safe": safe_rows,
        "consumable_use_same_uuid": same_uuid,
        "consumable_use_same_content": same_content,
        "consumable_use_slot_sequence": slot_sequence,
        "consumable_use_quantity_sequence": quantity_sequence,
        "consumable_use_version_sequence": version_sequence,
        "consumable_use_transaction_sequence": transaction_sequence,
        "consumable_use_dev_command_once_server": paper_text.count("/mmo dev") == 1,
        "consumable_use_filler_commands_server": paper_text.count("/item replace entity @s hotbar.") == 9,
        "consumable_use_status_commands_server": paper_text.count("/mmo physical status") >= 6,
    }


def runtime_selfcheck() -> None:
    uuid = "11111111-1111-1111-1111-111111111111"
    tx1 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    tx2 = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    tx3 = "cccccccc-cccc-cccc-cccc-cccccccccccc"
    content = "v1.milestone-1.example.4"

    def row(slot: int, version: int, quantity: int, transaction_id: str) -> str:
        return (
            f"LOT uuid={uuid} def=consumable.training_body_tonic "
            f"loc=CHARACTER_INVENTORY/slot:{slot} ver={version} qty={quantity} "
            f"tx={transaction_id} content={content}"
        )

    good = "\n".join(
        [
            row(9, 1, 64, tx1),
            STATUS_MARKERS[0],
            row(9, 1, 64, tx1),
            row(7, 2, 64, tx2),
            STATUS_MARKERS[1],
            row(7, 2, 64, tx2),
            STATUS_MARKERS[2],
            row(7, 2, 64, tx2),
            row(7, 3, 63, tx3),
            STATUS_MARKERS[3],
            row(7, 3, 63, tx3),
        ]
    )
    logical = c3_logical_lot_rows(good)
    if len(logical) != 4 or not c3_probe_rows_safe(good, logical):
        raise RuntimeError("C3 runtime self-check rejected the valid authority progression")

    second_decrement = good + "\n" + row(
        7, 4, 62, "dddddddd-dddd-dddd-dddd-dddddddddddd"
    )
    if c3_probe_rows_safe(second_decrement, logical):
        raise RuntimeError("C3 runtime self-check accepted a second authoritative decrement")


def install(core: Any) -> None:
    runtime_selfcheck()

    def action_client_acceptance_consumable_use(repo, result_dir, manifest):
        original_popen = core.subprocess.Popen

        def c3_popen(argv, *args, **kwargs):
            return original_popen(augment_c3_argv(argv), *args, **kwargs)

        try:
            core.subprocess.Popen = c3_popen
            code, stdout, stderr, record = core.action_client_acceptance_ingress(
                repo,
                result_dir,
                manifest,
            )
        finally:
            core.subprocess.Popen = original_popen

        client_text = (result_dir / "client.log").read_text(encoding="utf-8", errors="replace")
        paper_text = (result_dir / "paper.log").read_text(encoding="utf-8", errors="replace")
        checks = evaluate_consumable_use_checks(client_text, paper_text)
        record.setdefault("checks", {}).update(checks)
        record["lot_snapshots_raw"] = c3_lot_rows(client_text)
        record["lot_snapshots"] = c3_logical_lot_rows(client_text)
        record["fixed_command_id"] = "PHYSICAL_CLIENT_ACCEPTANCE_CONSUMABLE_USE"
        passed = code == 0 and all(checks.values())
        record["action_status"] = "PASS" if passed else "FAIL"
        return (
            0 if passed else 1,
            "PHYSICAL_CLIENT_ACCEPTANCE_CONSUMABLE_USE_PASS\n"
            if passed
            else "PHYSICAL_CLIENT_ACCEPTANCE_CONSUMABLE_USE_FAIL\n",
            stderr,
            record,
        )

    core.C3_ACCEPTANCE_FLAG = C3_ACCEPTANCE_FLAG
    core.augment_c3_argv = augment_c3_argv
    core.c3_lot_rows = c3_lot_rows
    core.c3_logical_lot_rows = c3_logical_lot_rows
    core.c3_probe_rows_safe = c3_probe_rows_safe
    core.evaluate_consumable_use_checks = evaluate_consumable_use_checks
    core.action_client_acceptance_consumable_use = action_client_acceptance_consumable_use
    core.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_CONSUMABLE_USE_V1"] = core.ActionSpec(
        action_client_acceptance_consumable_use,
        "PHYSICAL_CLIENT_ACCEPTANCE_CONSUMABLE_USE",
    )
