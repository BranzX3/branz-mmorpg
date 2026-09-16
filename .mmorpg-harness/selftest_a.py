#!/usr/bin/env python3
"""Offline regression tests for the fixed Section A legacy MAIN_HAND migration harness."""
from __future__ import annotations

import copy
import importlib.util
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("mmorpg_harness_runner_a_test", HERE / "runner.py")
assert SPEC and SPEC.loader
R = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = R
SPEC.loader.exec_module(R)

LEGACY_UUID = "11111111-1111-1111-1111-111111111111"
OTHER_UUID = "22222222-2222-2222-2222-222222222222"
TX = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
CONTENT = "v1.milestone-1.example.4"
OWNER = "33333333-3333-3333-3333-333333333333"


def legacy_probe(version: int = 7, uuid: str = LEGACY_UUID) -> str:
    return "\n".join(
        [
            "PHYSICAL_AUTHORITY_A_HANDSHAKE_CLIENT",
            (
                "PHYSICAL_AUTHORITY_A_LEGACY_DUMP_CLIENT "
                f"{{\"branzmmo:projection_value_id\":\"{uuid}\","
                "\"branzmmo:projection_definition_id\":\"weapon.training_sword\","
                f"\"branzmmo:projection_authority_version\":{version}L,"
                f"\"branzmmo:projection_content_version\":\"{CONTENT}\"}}"
            ),
            "PHYSICAL_AUTHORITY_A_LEGACY_PROBE_COMPLETE_CLIENT",
        ]
    )


def target_line(
    marker: str,
    *,
    uuid: str = LEGACY_UUID,
    version: int = 8,
    durability: int = 120,
    maximum: int = 120,
    location: str = "CHARACTER_INVENTORY/slot:0",
) -> str:
    return (
        f"{marker} ITEM uuid={uuid} def=weapon.training_sword loc={location} "
        f"ver={version} durability={durability}/{maximum} tx={TX} content={CONTENT}"
    )


def good_positive() -> tuple[str, str, str, str, str]:
    return (
        "PHYSICAL_AUTHORITY_A_LEGACY_SEEDED_CLIENT",
        legacy_probe(),
        target_line("PHYSICAL_AUTHORITY_A_TARGET_STATUS_CLIENT"),
        target_line("PHYSICAL_AUTHORITY_A_TARGET_STABLE_STATUS_CLIENT"),
        target_line("PHYSICAL_AUTHORITY_A_TARGET_STABLE_STATUS_CLIENT"),
    )


def good_negative() -> tuple[str, str, str, str]:
    return (
        "PHYSICAL_AUTHORITY_A_LEGACY_SEEDED_CLIENT",
        "PHYSICAL_AUTHORITY_A_NEGATIVE_FULL_DB_CLIENT inventory_rows=35 main_hand=1",
        "PHYSICAL_AUTHORITY_A_NEGATIVE_TARGET_LOCKED_CLIENT",
        "PHYSICAL_AUTHORITY_A_NEGATIVE_TARGET_LOCKED_CLIENT",
    )


def integrity_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for index in range(36):
        rows.append(
            {
                "item_uuid": f"00000000-0000-0000-0000-{index:012d}",
                "definition_id": "weapon.training_sword",
                "owner_character_id": OWNER,
                "location_type": (
                    "NATIVE_EQUIPPED" if index == 0 else "CHARACTER_INVENTORY"
                ),
                "location_ref": "MAIN_HAND" if index == 0 else f"slot:{index - 1}",
                "payload": '{"durability": 120}',
                "content_version": CONTENT,
                "version": "7",
                "last_transaction_id": TX,
            }
        )
    return rows


def main() -> int:
    spec = R.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_LEGACY_MAIN_HAND_MIGRATION_V1"]
    assert spec.identity == "PHYSICAL_CLIENT_ACCEPTANCE_LEGACY_MAIN_HAND_MIGRATION_V1"
    assert spec.handler is R.action_client_acceptance_legacy_main_hand_migration

    assert R.reviewed_section_a_revisions_match(
        "8c5a04271f9385730aff0b3332608812a216dc95",
        "2bfbcc74f81a57fb6d4e61151efba4446eff75d5",
    )
    assert not R.reviewed_section_a_revisions_match(
        "8c5a04271f9385730aff0b3332608812a216dc95",
        "df62da2ce6efbc230f3337200da2083a6b417b62",
    )

    root = Path("/tmp/mmorpg-worker").resolve()
    positive = root / R.SECTION_A_POSITIVE_WORKTREE_NAME
    negative = root / R.SECTION_A_NEGATIVE_WORKTREE_NAME
    assert R.fixed_section_a_runtime_paths_match(root, positive, negative)
    assert not R.fixed_section_a_runtime_paths_match(
        root, root / "substituted-positive", negative
    )
    assert not R.fixed_section_a_runtime_paths_match(
        root, positive, root / "substituted-negative"
    )

    identity = R.parse_section_a_legacy_identity(legacy_probe())
    assert identity is not None
    assert identity["uuid"] == LEGACY_UUID
    assert identity["definition_id"] == "weapon.training_sword"
    assert identity["authority_version"] == 7
    assert identity["content_version"] == CONTENT
    assert R.parse_section_a_legacy_identity(
        "PHYSICAL_AUTHORITY_A_LEGACY_PROBE_COMPLETE_CLIENT"
    ) is None

    seed, probe, target, reconnect, restart = good_positive()
    checks = R.evaluate_section_a_positive_evidence(
        seed, probe, target, reconnect, restart
    )
    assert all(checks.values()), [name for name, ok in checks.items() if not ok]

    bad_version = target_line("PHYSICAL_AUTHORITY_A_TARGET_STATUS_CLIENT", version=9)
    assert not R.evaluate_section_a_positive_evidence(
        seed, probe, bad_version, reconnect, restart
    )["a_exact_version_plus_one"]

    bad_uuid = target_line("PHYSICAL_AUTHORITY_A_TARGET_STATUS_CLIENT", uuid=OTHER_UUID)
    assert not R.evaluate_section_a_positive_evidence(
        seed, probe, bad_uuid, reconnect, restart
    )["a_exact_uuid_preserved"]

    bad_durability = target_line(
        "PHYSICAL_AUTHORITY_A_TARGET_STATUS_CLIENT", durability=119
    )
    assert not R.evaluate_section_a_positive_evidence(
        seed, probe, bad_durability, reconnect, restart
    )["a_durability_120_120"]

    bad_location = target_line(
        "PHYSICAL_AUTHORITY_A_TARGET_STATUS_CLIENT",
        location="NATIVE_EQUIPPED/MAIN_HAND",
    )
    bad_location_checks = R.evaluate_section_a_positive_evidence(
        seed, probe, bad_location, reconnect, restart
    )
    assert not bad_location_checks["a_migrated_to_character_inventory"]
    assert not bad_location_checks["a_no_persistent_main_hand_status"]

    duplicate_target = target + "\n" + target
    assert not R.evaluate_section_a_positive_evidence(
        seed, probe, duplicate_target, reconnect, restart
    )["a_target_status_exactly_one"]

    drifted_reconnect = target_line(
        "PHYSICAL_AUTHORITY_A_TARGET_STABLE_STATUS_CLIENT", version=9
    )
    assert not R.evaluate_section_a_positive_evidence(
        seed, probe, target, drifted_reconnect, restart
    )["a_target_reconnect_restart_byte_stable"]

    missing_restart = "PHYSICAL_AUTHORITY_A_HANDSHAKE_CLIENT"
    assert not R.evaluate_section_a_positive_evidence(
        seed, probe, target, reconnect, missing_restart
    )["a_restart_status_exactly_one"]

    negative_prep, negative_fill, negative_fail, negative_restart = good_negative()
    negative_checks = R.evaluate_section_a_negative_evidence(
        negative_prep, negative_fill, negative_fail, negative_restart
    )
    assert all(negative_checks.values()), [
        name for name, ok in negative_checks.items() if not ok
    ]
    duplicate_fail = negative_fail + "\n" + negative_fail
    assert not R.evaluate_section_a_negative_evidence(
        negative_prep, negative_fill, duplicate_fail, negative_restart
    )["a_negative_target_fail_marker_once"]
    success_in_negative = (
        negative_fail
        + "\n"
        + target_line("PHYSICAL_AUTHORITY_A_TARGET_STATUS_CLIENT")
    )
    assert not R.evaluate_section_a_negative_evidence(
        negative_prep, negative_fill, success_in_negative, negative_restart
    )["a_negative_no_target_success_status"]

    baseline = integrity_rows()
    integrity = R.evaluate_section_a_negative_integrity(
        baseline, copy.deepcopy(baseline), copy.deepcopy(baseline)
    )
    assert all(integrity.values()), [name for name, ok in integrity.items() if not ok]

    row_loss = copy.deepcopy(baseline)
    row_loss.pop()
    assert not R.evaluate_section_a_negative_integrity(
        baseline, row_loss, copy.deepcopy(baseline)
    )["a_negative_integrity_no_row_loss_or_invention"]

    duplicate_uuid = copy.deepcopy(baseline)
    duplicate_uuid[1]["item_uuid"] = duplicate_uuid[0]["item_uuid"]
    duplicate_checks = R.evaluate_section_a_negative_integrity(
        baseline, duplicate_uuid, copy.deepcopy(baseline)
    )
    assert not duplicate_checks["a_negative_integrity_uuid_unique"]
    assert not duplicate_checks["a_negative_integrity_no_duplicate_training_sword"]

    location_mutation = copy.deepcopy(baseline)
    location_mutation[1]["location_ref"] = "slot:35"
    assert not R.evaluate_section_a_negative_integrity(
        baseline, location_mutation, copy.deepcopy(baseline)
    )["a_negative_integrity_no_invented_destination"]

    invented_destination = copy.deepcopy(baseline)
    invented_destination[2]["location_type"] = "NATIVE_EQUIPPED"
    invented_destination[2]["location_ref"] = "OFF_HAND"
    assert not R.evaluate_section_a_negative_integrity(
        baseline, invented_destination, copy.deepcopy(baseline)
    )["a_negative_integrity_no_invented_destination"]

    version_mutation = copy.deepcopy(baseline)
    version_mutation[3]["version"] = "8"
    assert not R.evaluate_section_a_negative_integrity(
        baseline, version_mutation, copy.deepcopy(baseline)
    )["a_negative_integrity_version_content_payload_stable"]

    content_mutation = copy.deepcopy(baseline)
    content_mutation[4]["content_version"] = "unexpected"
    assert not R.evaluate_section_a_negative_integrity(
        baseline, content_mutation, copy.deepcopy(baseline)
    )["a_negative_integrity_version_content_payload_stable"]

    payload_mutation = copy.deepcopy(baseline)
    payload_mutation[5]["payload"] = '{"durability": 119}'
    assert not R.evaluate_section_a_negative_integrity(
        baseline, payload_mutation, copy.deepcopy(baseline)
    )["a_negative_integrity_version_content_payload_stable"]

    main_hand_mutation = copy.deepcopy(baseline)
    main_hand_mutation[0]["location_type"] = "CHARACTER_INVENTORY"
    main_hand_mutation[0]["location_ref"] = "slot:35"
    assert not R.evaluate_section_a_negative_integrity(
        baseline, main_hand_mutation, copy.deepcopy(baseline)
    )["a_negative_integrity_main_hand_uuid_stable"]

    print("MMORPG_HARNESS_SECTION_A_SELFTEST_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
