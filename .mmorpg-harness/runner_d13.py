#!/usr/bin/env python3
"""D1-D3 physical shield OFF_HAND extension for the immutable MMORPG harness."""
from __future__ import annotations

import re
from typing import Any

D13_ACCEPTANCE_FLAG = "-PphysicalShieldD13Acceptance=true"
STATUS_MARKERS = (
    "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_FIRST_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_STAGED_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_FIRST_MOVED_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_EQUIPPED_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_SECOND_MOVED_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_SWAPPED_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_RECONNECT_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_UNEQUIPPED_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_FINAL_CLIENT",
)
EXPECTED_COUNTS = (1, 2, 2, 2, 2, 2, 2, 2, 2)
SHIELD_STATUS = re.compile(
    r"ITEM uuid=([0-9a-fA-F-]{36}) def=equipment\.training_shield "
    r"loc=([^ ]+) ver=(\d+) durability=(\d+)/(\d+) "
    r"tx=([0-9a-fA-F-]{36}) content=(\S+)"
)


def augment_d13_argv(argv: Any) -> Any:
    if not isinstance(argv, list):
        return argv
    is_server = ":mmo-bootstrap:runServer" in argv
    is_client = "runClientGameTest" in argv
    if (is_server or is_client) and D13_ACCEPTANCE_FLAG not in argv:
        argv.append(D13_ACCEPTANCE_FLAG)
    return argv


def _row(match: re.Match[str]) -> dict[str, Any]:
    return {
        "uuid": match.group(1).lower(),
        "location": match.group(2),
        "version": int(match.group(3)),
        "current_durability": int(match.group(4)),
        "maximum_durability": int(match.group(5)),
        "transaction_id": match.group(6).lower(),
        "content_version": match.group(7),
    }


def d13_status_rows(client_text: str) -> list[dict[str, Any]]:
    return [_row(match) for match in SHIELD_STATUS.finditer(client_text)]


def d13_logical_snapshots(client_text: str) -> list[dict[str, dict[str, Any]]]:
    positions: list[int] = []
    for marker in STATUS_MARKERS:
        if client_text.count(marker) != 1:
            return []
        positions.append(client_text.index(marker))
    if positions != sorted(positions):
        return []

    snapshots: list[dict[str, dict[str, Any]]] = []
    lower = 0
    for marker_position, expected_count in zip(positions, EXPECTED_COUNTS, strict=True):
        latest: dict[str, dict[str, Any]] = {}
        for match in SHIELD_STATUS.finditer(client_text, lower, marker_position):
            row = _row(match)
            latest[row["uuid"]] = row
        if len(latest) != expected_count:
            return []
        snapshots.append(latest)
        lower = marker_position + 1
    return snapshots


def _fresh(row: dict[str, Any] | None, location: str) -> bool:
    return bool(
        row
        and row["location"] == location
        and row["current_durability"] == 180
        and row["maximum_durability"] == 180
    )


def _move(before: dict[str, Any] | None, after: dict[str, Any] | None, location: str) -> bool:
    return bool(
        before
        and after
        and before["uuid"] == after["uuid"]
        and before["content_version"] == after["content_version"]
        and before["current_durability"] == after["current_durability"]
        and before["maximum_durability"] == after["maximum_durability"]
        and after["version"] == before["version"] + 1
        and after["transaction_id"] != before["transaction_id"]
        and after["location"] == location
    )


def evaluate_d13_checks(client_text: str, paper_text: str) -> dict[str, bool]:
    snapshots = d13_logical_snapshots(client_text)
    progression_ready = len(snapshots) == len(STATUS_MARKERS)
    if progression_ready:
        first_only, staged, first_moved, equipped, second_moved, swapped, reconnect, unequipped, final = snapshots
        first_uuid = next(iter(first_only))
        first = first_only[first_uuid]
        second_ids = set(staged) - {first_uuid}
        progression_ready = len(second_ids) == 1 and first_uuid in staged
    if progression_ready:
        second_uuid = next(iter(second_ids))
        second = staged[second_uuid]
        staged_ok = (
            first_only[first_uuid] == staged[first_uuid]
            and _fresh(staged[first_uuid], "CHARACTER_INVENTORY/slot:9")
            and _fresh(second, "CHARACTER_INVENTORY/slot:10")
        )
        first_move_ok = (
            _move(staged[first_uuid], first_moved.get(first_uuid), "CHARACTER_INVENTORY/slot:7")
            and first_moved.get(second_uuid) == staged[second_uuid]
        )
        equip_ok = (
            _move(first_moved.get(first_uuid), equipped.get(first_uuid), "NATIVE_EQUIPPED/OFF_HAND")
            and equipped.get(second_uuid) == first_moved.get(second_uuid)
        )
        second_move_ok = (
            second_moved.get(first_uuid) == equipped.get(first_uuid)
            and _move(equipped.get(second_uuid), second_moved.get(second_uuid), "CHARACTER_INVENTORY/slot:7")
        )
        swap_ok = (
            _move(second_moved.get(first_uuid), swapped.get(first_uuid), "CHARACTER_INVENTORY/slot:7")
            and _move(second_moved.get(second_uuid), swapped.get(second_uuid), "NATIVE_EQUIPPED/OFF_HAND")
            and swapped.get(first_uuid) is not None
            and swapped.get(second_uuid) is not None
            and swapped[first_uuid]["transaction_id"] == swapped[second_uuid]["transaction_id"]
        )
        reconnect_ok = reconnect == swapped
        unequip_ok = (
            unequipped.get(first_uuid) == reconnect.get(first_uuid)
            and _move(reconnect.get(second_uuid), unequipped.get(second_uuid), "CHARACTER_INVENTORY/slot:6")
        )
        final_ok = final == unequipped
    else:
        staged_ok = first_move_ok = equip_ok = second_move_ok = False
        swap_ok = reconnect_ok = unequip_ok = final_ok = False

    marker_names = (
        "PHYSICAL_AUTHORITY_SHIELD_D13_HANDSHAKE_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_FILLER_READY_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_FIRST_READY_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_SECOND_READY_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_FIRST_MOVED_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_EQUIP_F_SENT_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_EQUIPPED_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_SECOND_MOVED_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_SWAP_F_SENT_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_ATOMIC_SWAP_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_RECONNECTED_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_RECONNECT_STABLE_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_UNEQUIP_F_SENT_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_UNEQUIPPED_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_COMPLETE_CLIENT",
    )
    checks = {f"d13_marker_{index:02d}": client_text.count(marker) == 1 for index, marker in enumerate(marker_names, 1)}
    status_count = paper_text.count("/mmo physical status")
    checks.update(
        {
            "d13_snapshots_complete": progression_ready,
            "d13_staged_exact": staged_ok,
            "d13_first_inventory_move_exact": first_move_ok,
            "d13_first_offhand_equip_exact": equip_ok,
            "d13_second_inventory_move_exact": second_move_ok,
            "d13_atomic_shield_swap_exact": swap_ok,
            "d13_reconnect_byte_stable": reconnect_ok,
            "d13_unequip_exact": unequip_ok,
            "d13_final_byte_stable": final_ok,
            "d13_two_dev_commands_server": paper_text.count("/mmo dev") == 2,
            "d13_ten_item_replace_commands_server": paper_text.count("/item replace entity @s hotbar.") == 10,
            "d13_status_commands_bounded_server": 9 <= status_count <= 128,
        }
    )
    return checks


def runtime_selfcheck() -> None:
    first = "11111111-1111-1111-1111-111111111111"
    second = "22222222-2222-2222-2222-222222222222"
    content = "v1.milestone-1.example.4"

    def row(uuid: str, location: str, version: int, tx: str) -> str:
        return (
            f"ITEM uuid={uuid} def=equipment.training_shield loc={location} "
            f"ver={version} durability=180/180 tx={tx} content={content}"
        )

    tx1 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"
    tx2 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2"
    tx3 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3"
    tx4 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4"
    tx5 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5"
    tx6 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa6"
    tx7 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa7"
    snapshots = (
        (row(first, "CHARACTER_INVENTORY/slot:9", 1, tx1),),
        (row(first, "CHARACTER_INVENTORY/slot:9", 1, tx1), row(second, "CHARACTER_INVENTORY/slot:10", 1, tx2)),
        (row(first, "CHARACTER_INVENTORY/slot:7", 2, tx3), row(second, "CHARACTER_INVENTORY/slot:10", 1, tx2)),
        (row(first, "NATIVE_EQUIPPED/OFF_HAND", 3, tx4), row(second, "CHARACTER_INVENTORY/slot:10", 1, tx2)),
        (row(first, "NATIVE_EQUIPPED/OFF_HAND", 3, tx4), row(second, "CHARACTER_INVENTORY/slot:7", 2, tx5)),
        (row(first, "CHARACTER_INVENTORY/slot:7", 4, tx6), row(second, "NATIVE_EQUIPPED/OFF_HAND", 3, tx6)),
        (row(first, "CHARACTER_INVENTORY/slot:7", 4, tx6), row(second, "NATIVE_EQUIPPED/OFF_HAND", 3, tx6)),
        (row(first, "CHARACTER_INVENTORY/slot:7", 4, tx6), row(second, "CHARACTER_INVENTORY/slot:6", 4, tx7)),
        (row(first, "CHARACTER_INVENTORY/slot:7", 4, tx6), row(second, "CHARACTER_INVENTORY/slot:6", 4, tx7)),
    )
    chunks: list[str] = []
    for rows, marker in zip(snapshots, STATUS_MARKERS, strict=True):
        chunks.extend(rows)
        chunks.append(marker)
    for marker in (
        "PHYSICAL_AUTHORITY_SHIELD_D13_HANDSHAKE_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_FILLER_READY_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_FIRST_READY_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_SECOND_READY_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_FIRST_MOVED_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_EQUIP_F_SENT_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_EQUIPPED_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_SECOND_MOVED_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_SWAP_F_SENT_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_ATOMIC_SWAP_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_RECONNECTED_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_RECONNECT_STABLE_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_UNEQUIP_F_SENT_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_UNEQUIPPED_CLIENT",
        "PHYSICAL_AUTHORITY_SHIELD_D13_COMPLETE_CLIENT",
    ):
        chunks.append(marker)
    client = "\n".join(chunks)
    paper = "\n".join(["/mmo dev"] * 2 + ["/item replace entity @s hotbar."] * 10 + ["/mmo physical status"] * 9)
    checks = evaluate_d13_checks(client, paper)
    if not checks or not all(checks.values()):
        failed = sorted(name for name, passed in checks.items() if not passed)
        raise RuntimeError(f"D13 runtime self-check rejected valid progression: {failed}")
    bad = client.replace(
        row(second, "NATIVE_EQUIPPED/OFF_HAND", 3, tx6) + "\n" + STATUS_MARKERS[6],
        row(second, "NATIVE_EQUIPPED/OFF_HAND", 4, tx7) + "\n" + STATUS_MARKERS[6],
        1,
    )
    if evaluate_d13_checks(bad, paper)["d13_reconnect_byte_stable"]:
        raise RuntimeError("D13 runtime self-check accepted mutated reconnect authority")


def install(core: Any) -> None:
    runtime_selfcheck()

    def action_client_acceptance_shield_d13(repo, result_dir, manifest):
        original_popen = core.subprocess.Popen

        def d13_popen(argv, *args, **kwargs):
            return original_popen(augment_d13_argv(argv), *args, **kwargs)

        try:
            core.subprocess.Popen = d13_popen
            code, stdout, stderr, record = core.action_client_acceptance_ingress(
                repo,
                result_dir,
                manifest,
            )
        finally:
            core.subprocess.Popen = original_popen

        client_text = (result_dir / "client.log").read_text(encoding="utf-8", errors="replace")
        paper_text = (result_dir / "paper.log").read_text(encoding="utf-8", errors="replace")
        checks = evaluate_d13_checks(client_text, paper_text)
        record.setdefault("checks", {}).update(checks)
        record["shield_status_rows_raw"] = d13_status_rows(client_text)
        record["shield_snapshots"] = d13_logical_snapshots(client_text)
        record["fixed_command_id"] = "PHYSICAL_CLIENT_ACCEPTANCE_SHIELD_D13"
        passed = code == 0 and all(checks.values())
        record["action_status"] = "PASS" if passed else "FAIL"
        return (
            0 if passed else 1,
            "PHYSICAL_CLIENT_ACCEPTANCE_SHIELD_D13_PASS\n"
            if passed
            else "PHYSICAL_CLIENT_ACCEPTANCE_SHIELD_D13_FAIL\n",
            stderr,
            record,
        )

    core.D13_ACCEPTANCE_FLAG = D13_ACCEPTANCE_FLAG
    core.augment_d13_argv = augment_d13_argv
    core.d13_status_rows = d13_status_rows
    core.d13_logical_snapshots = d13_logical_snapshots
    core.evaluate_d13_checks = evaluate_d13_checks
    core.action_client_acceptance_shield_d13 = action_client_acceptance_shield_d13
    core.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_SHIELD_D13_V1"] = core.ActionSpec(
        action_client_acceptance_shield_d13,
        "PHYSICAL_CLIENT_ACCEPTANCE_SHIELD_D13",
    )
