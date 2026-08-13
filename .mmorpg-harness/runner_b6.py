#!/usr/bin/env python3
"""B6 Chronicle-slot rejection extension for the immutable MMORPG harness runner core."""
from __future__ import annotations

from typing import Any


def evaluate_chronicle_slot_log_checks(client_text: str, paper_text: str) -> dict[str, bool]:
    return {
        "chronicle_stage_projected_server":
            "PHYSICAL_AUTHORITY_HOTBAR_STAGE_PROJECTED_SERVER" in paper_text,
        "chronicle_stage_ready_client":
            "PHYSICAL_AUTHORITY_CHRONICLE_STAGE_READY_CLIENT" in client_text,
        "chronicle_status_before_client":
            "PHYSICAL_AUTHORITY_CHRONICLE_STATUS_BEFORE_CLIENT" in client_text,
        "chronicle_present_before_client":
            "PHYSICAL_AUTHORITY_CHRONICLE_PRESENT_BEFORE_CLIENT" in client_text,
        "chronicle_pickup_observed_client":
            "PHYSICAL_AUTHORITY_CHRONICLE_PICKUP_OBSERVED_CLIENT" in client_text,
        "chronicle_slot9_mouse_once_client":
            client_text.count("PHYSICAL_AUTHORITY_CHRONICLE_SLOT9_MOUSE_SENT_CLIENT") == 1,
        "chronicle_rejection_message_once_client":
            client_text.count("PHYSICAL_AUTHORITY_CHRONICLE_REJECTION_MESSAGE_CLIENT") == 1,
        "chronicle_present_after_reject_client":
            "PHYSICAL_AUTHORITY_CHRONICLE_PRESENT_AFTER_REJECT_CLIENT" in client_text,
        "chronicle_reconciled_client":
            "PHYSICAL_AUTHORITY_CHRONICLE_RECONCILED_CLIENT" in client_text,
        "chronicle_status_after_close_client":
            "PHYSICAL_AUTHORITY_CHRONICLE_STATUS_AFTER_CLOSE_CLIENT" in client_text,
        "chronicle_authority_stable_client":
            "PHYSICAL_AUTHORITY_CHRONICLE_AUTHORITY_STABLE_CLIENT" in client_text,
        "chronicle_reconnect_projected_client":
            "PHYSICAL_AUTHORITY_CHRONICLE_RECONNECT_PROJECTED_CLIENT" in client_text,
        "chronicle_status_reconnect_client":
            "PHYSICAL_AUTHORITY_CHRONICLE_STATUS_RECONNECT_CLIENT" in client_text,
        "chronicle_reconnect_stable_client":
            "PHYSICAL_AUTHORITY_CHRONICLE_RECONNECT_STABLE_CLIENT" in client_text,
        "chronicle_status_command_count_server":
            paper_text.count("/mmo physical status") == 3,
        "chronicle_no_hotbar_commit_server":
            "PHYSICAL_AUTHORITY_HOTBAR_MOVE_COMMITTED_SERVER" not in paper_text,
    }


def install(core: Any) -> None:
    """Install exactly one reviewed B6 capability without mutating runner_core.py."""

    def action_client_acceptance_chronicle_slot(repo, result_dir, manifest):
        original_popen = core.subprocess.Popen

        def b6_popen(argv, *args, **kwargs):
            if isinstance(argv, list):
                if ":mmo-bootstrap:runServer" in argv:
                    flag = "-PphysicalHotbarAcceptance=true"
                    if flag not in argv:
                        argv.append(flag)
                if "runClientGameTest" in argv:
                    flag = "-PphysicalChronicleSlotAcceptance=true"
                    if flag not in argv:
                        argv.append(flag)
            return original_popen(argv, *args, **kwargs)

        try:
            core.subprocess.Popen = b6_popen
            code, stdout, stderr, record = core.action_client_acceptance_ingress(
                repo,
                result_dir,
                manifest,
            )
            client_text = (result_dir / "client.log").read_text(
                encoding="utf-8", errors="replace"
            )
            paper_text = (result_dir / "paper.log").read_text(
                encoding="utf-8", errors="replace"
            )
            b6_checks = evaluate_chronicle_slot_log_checks(client_text, paper_text)
            record.setdefault("checks", {}).update(b6_checks)
            passed = code == 0 and all(b6_checks.values())
            record["action_status"] = "PASS" if passed else "FAIL"
            record["fixed_command_id"] = "PHYSICAL_CLIENT_ACCEPTANCE_CHRONICLE_SLOT"
            record["b6_fixed_staging"] = "PHYSICAL_CLIENT_ACCEPTANCE_HOTBAR_STAGE"
            return (
                0 if passed else 1,
                "PHYSICAL_CLIENT_CHRONICLE_SLOT_PASS\n"
                if passed
                else "PHYSICAL_CLIENT_CHRONICLE_SLOT_FAIL\n",
                stderr,
                record,
            )
        finally:
            core.subprocess.Popen = original_popen

    core.evaluate_chronicle_slot_log_checks = evaluate_chronicle_slot_log_checks
    core.action_client_acceptance_chronicle_slot = action_client_acceptance_chronicle_slot
    core.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_CHRONICLE_SLOT_V1"] = core.ActionSpec(
        action_client_acceptance_chronicle_slot,
        "PHYSICAL_CLIENT_ACCEPTANCE_CHRONICLE_SLOT",
    )
