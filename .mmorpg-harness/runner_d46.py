#!/usr/bin/env python3
"""D4+D6 physical shield wear and Staff-F ownership extension."""
from __future__ import annotations

import re
from typing import Any

SERVER_FLAG = "-PphysicalShieldD13Acceptance=true"
CLIENT_FLAG = "-PphysicalShieldD46Acceptance=true"
SHIELD_STATUS = re.compile(
    r"ITEM uuid=([0-9a-fA-F-]{36}) def=equipment\.training_shield "
    r"loc=([^ ]+) ver=(\d+) durability=(\d+)/(\d+) "
    r"tx=([0-9a-fA-F-]{36}) content=(\S+)"
)
SNAPSHOT_MARKERS = (
    "PHYSICAL_AUTHORITY_SHIELD_D46_STATUS_STAGED_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_STATUS_EQUIPPED_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_STATUS_BEFORE_IMPACT_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_STATUS_WORN_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_STATUS_WORN_STABLE_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_STATUS_AFTER_STAFF_F_CLIENT",
)
CLIENT_MARKERS = (
    "PHYSICAL_AUTHORITY_SHIELD_D46_HANDSHAKE_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_FILLER_READY_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_ITEMS_READY_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_EQUIP_F_SENT_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_EQUIPPED_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_SOURCE_STAGED_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_ENGAGEMENT_PRIMER_HIT_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_GUARD_ACTIVE_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_REAL_BLOCKED_IMPACT_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_WORN_ONCE_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_NO_DOUBLE_SPEND_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_STAFF_F_SENT_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_STAFF_OWNS_F_CLIENT",
    "PHYSICAL_AUTHORITY_SHIELD_D46_COMPLETE_CLIENT",
)


def _row(match: re.Match[str]) -> dict[str, Any]:
    return {
        "uuid": match.group(1).lower(),
        "location": match.group(2),
        "version": int(match.group(3)),
        "current": int(match.group(4)),
        "maximum": int(match.group(5)),
        "tx": match.group(6).lower(),
        "content": match.group(7),
    }


def _marker_matches(text: str, marker: str) -> list[re.Match[str]]:
    return list(re.finditer(re.escape(marker) + r"(?=\r?$)", text, re.MULTILINE))


def snapshots(client_text: str) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    lower = 0
    for marker in SNAPSHOT_MARKERS:
        matches = [match for match in _marker_matches(client_text, marker) if match.start() >= lower]
        if len(matches) != 1:
            return []
        marker_match = matches[0]
        rows = list(SHIELD_STATUS.finditer(client_text, lower, marker_match.start()))
        if not rows:
            return []
        result.append(_row(rows[-1]))
        lower = marker_match.end()
    return result


def _same(a: dict[str, Any], b: dict[str, Any]) -> bool:
    return a == b


def _move(a: dict[str, Any], b: dict[str, Any], location: str) -> bool:
    return (
        a["uuid"] == b["uuid"]
        and a["content"] == b["content"]
        and a["current"] == b["current"]
        and a["maximum"] == b["maximum"]
        and b["version"] == a["version"] + 1
        and b["tx"] != a["tx"]
        and b["location"] == location
    )


def _wear(a: dict[str, Any], b: dict[str, Any]) -> bool:
    return (
        a["uuid"] == b["uuid"]
        and a["location"] == b["location"] == "NATIVE_EQUIPPED/OFF_HAND"
        and a["content"] == b["content"]
        and a["maximum"] == b["maximum"] == 180
        and a["current"] == 180
        and b["current"] == 179
        and b["version"] == a["version"] + 1
        and b["tx"] != a["tx"]
    )


def evaluate(client_text: str, paper_text: str) -> dict[str, bool]:
    rows = snapshots(client_text)
    ready = len(rows) == len(SNAPSHOT_MARKERS)
    if ready:
        staged, equipped, before, worn, stable, after_staff_f = rows
        progression = {
            "d46_staged_exact": (
                staged["location"] == "CHARACTER_INVENTORY/slot:6"
                and staged["current"] == staged["maximum"] == 180
            ),
            "d46_equip_exact": _move(staged, equipped, "NATIVE_EQUIPPED/OFF_HAND"),
            "d46_guard_did_not_mutate": _same(equipped, before),
            "d46_one_point_wear_exact": _wear(before, worn),
            "d46_no_double_spend": _same(worn, stable),
            "d46_staff_f_shield_stable": _same(worn, after_staff_f),
        }
    else:
        progression = {
            "d46_staged_exact": False,
            "d46_equip_exact": False,
            "d46_guard_did_not_mutate": False,
            "d46_one_point_wear_exact": False,
            "d46_no_double_spend": False,
            "d46_staff_f_shield_stable": False,
        }

    checks = {
        f"d46_marker_{i:02d}": len(_marker_matches(client_text, marker)) == 1
        for i, marker in enumerate(CLIENT_MARKERS, 1)
    }
    checks.update(progression)
    checks.update(
        {
            "d46_snapshots_complete": ready,
            "d46_three_dev_commands_server": paper_text.count("/mmo dev") == 3,
            "d46_eleven_item_replace_commands_server": (
                paper_text.count("/item replace entity @s hotbar.") == 11
            ),
            "d46_husk_summon_once_server": paper_text.count("summon minecraft:husk") == 1,
            "d46_husk_slow_once_server": (
                paper_text.count("minecraft:slowness infinite 255 true") == 1
            ),
            "d46_husk_three_teleports_server": (
                paper_text.count("tp @e[tag=branz_d46_source,limit=1]") == 3
            ),
            "d46_husk_cleanup_server": (
                paper_text.count("/kill @e[tag=branz_d46_source]") in (1, 2)
            ),
            "d46_status_commands_bounded_server": (
                6 <= paper_text.count("/mmo physical status") <= 128
            ),
        }
    )
    return checks


def runtime_selfcheck() -> None:
    uuid = "11111111-1111-1111-1111-111111111111"
    content = "v1.milestone-1.example.4"

    def row(location: str, version: int, current: int, tx: str) -> str:
        return (
            f"ITEM uuid={uuid} def=equipment.training_shield loc={location} "
            f"ver={version} durability={current}/180 tx={tx} content={content}"
        )

    tx1 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"
    tx2 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2"
    tx3 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3"
    values = (
        row("CHARACTER_INVENTORY/slot:6", 1, 180, tx1),
        row("NATIVE_EQUIPPED/OFF_HAND", 2, 180, tx2),
        row("NATIVE_EQUIPPED/OFF_HAND", 2, 180, tx2),
        row("NATIVE_EQUIPPED/OFF_HAND", 3, 179, tx3),
        row("NATIVE_EQUIPPED/OFF_HAND", 3, 179, tx3),
        row("NATIVE_EQUIPPED/OFF_HAND", 3, 179, tx3),
    )
    chunks: list[str] = list(CLIENT_MARKERS)
    for value, marker in zip(values, SNAPSHOT_MARKERS, strict=True):
        chunks.extend((value, marker))
    client = "\n".join(chunks)
    paper = "\n".join(
        ["/mmo dev"] * 3
        + ["/item replace entity @s hotbar."] * 11
        + ["/mmo physical status"] * 6
        + [
            "summon minecraft:husk",
            "minecraft:slowness infinite 255 true",
        ]
        + ["tp @e[tag=branz_d46_source,limit=1]"] * 3
        + ["/kill @e[tag=branz_d46_source]"]
    )
    checks = evaluate(client, paper)
    failed = sorted(name for name, passed in checks.items() if not passed)
    if failed:
        raise RuntimeError(f"D46 v2 runtime self-check rejected valid progression: {failed}")
    bad = client.replace("durability=179/180", "durability=178/180", 1)
    if evaluate(bad, paper)["d46_one_point_wear_exact"]:
        raise RuntimeError("D46 v2 runtime self-check accepted double shield wear")


def install(core: Any) -> None:
    runtime_selfcheck()

    def action_client_acceptance_shield_d46_v2(repo, result_dir, manifest):
        original_popen = core.subprocess.Popen

        def d46_popen(argv, *args, **kwargs):
            if isinstance(argv, list):
                if ":mmo-bootstrap:runServer" in argv and SERVER_FLAG not in argv:
                    argv.append(SERVER_FLAG)
                if "runClientGameTest" in argv and CLIENT_FLAG not in argv:
                    argv.append(CLIENT_FLAG)
            return original_popen(argv, *args, **kwargs)

        try:
            core.subprocess.Popen = d46_popen
            code, stdout, stderr, record = core.action_client_acceptance_ingress(
                repo, result_dir, manifest
            )
        finally:
            core.subprocess.Popen = original_popen

        client_text = (result_dir / "client.log").read_text(
            encoding="utf-8", errors="replace"
        )
        paper_text = (result_dir / "paper.log").read_text(
            encoding="utf-8", errors="replace"
        )
        checks = evaluate(client_text, paper_text)
        record.setdefault("checks", {}).update(checks)
        record["shield_snapshots"] = snapshots(client_text)
        record["fixed_command_id"] = "PHYSICAL_CLIENT_ACCEPTANCE_SHIELD_D46_V2"
        passed = code == 0 and all(checks.values())
        record["action_status"] = "PASS" if passed else "FAIL"
        return (
            0 if passed else 1,
            (
                "PHYSICAL_CLIENT_ACCEPTANCE_SHIELD_D46_V2_PASS\n"
                if passed
                else "PHYSICAL_CLIENT_ACCEPTANCE_SHIELD_D46_V2_FAIL\n"
            ),
            stderr,
            record,
        )

    core.evaluate_d46_v2_checks = evaluate
    core.action_client_acceptance_shield_d46_v2 = action_client_acceptance_shield_d46_v2
    core.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_SHIELD_D46_V2"] = core.ActionSpec(
        action_client_acceptance_shield_d46_v2,
        "PHYSICAL_CLIENT_ACCEPTANCE_SHIELD_D46_V2",
    )
