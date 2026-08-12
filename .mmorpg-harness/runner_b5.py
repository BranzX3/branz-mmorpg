#!/usr/bin/env python3
"""B5 broken-weapon extension for the immutable MMORPG harness runner core."""
from __future__ import annotations

from typing import Any


def evaluate_broken_log_checks(client_text: str, paper_text: str) -> dict[str, bool]:
    return {
        "primary_broken_target_ready_client":
            "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_TARGET_READY_CLIENT" in client_text,
        "primary_broken_status_before_client":
            "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_STATUS_BEFORE_CLIENT" in client_text,
        "primary_broken_first_mouse_client":
            "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_FIRST_MOUSE_SENT_CLIENT" in client_text,
        "primary_broken_zero_client":
            "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_ZERO_CLIENT" in client_text,
        "primary_broken_retry_mouse_client":
            "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_RETRY_MOUSE_SENT_CLIENT" in client_text,
        "primary_broken_rejection_message_client":
            "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_REJECTION_MESSAGE_CLIENT" in client_text,
        "primary_broken_status_after_reject_client":
            "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_STATUS_AFTER_REJECT_CLIENT" in client_text,
        "primary_broken_rejected_stable_client":
            "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_REJECTED_STABLE_CLIENT" in client_text,
        "primary_broken_accelerated_wear_once_server":
            paper_text.count("PHYSICAL_AUTHORITY_BROKEN_ACCELERATED_WEAR_SERVER") == 1,
        "primary_broken_success_observer_once_server":
            paper_text.count("PHYSICAL_AUTHORITY_WEAPON_SUCCESS_OBSERVED_SERVER") == 1,
        "primary_broken_routed_once_server":
            paper_text.count("PHYSICAL_AUTHORITY_PRIMARY_ROUTED_SERVER") == 1,
        "primary_broken_arm_swing_twice_server":
            paper_text.count("PHYSICAL_AUTHORITY_ARM_SWING_SERVER") == 2,
        "primary_broken_target_a_staged_server":
            "PHYSICAL_AUTHORITY_B4_TARGET_A_STAGED_SERVER" in paper_text,
        "primary_broken_target_b_staged_server":
            "PHYSICAL_AUTHORITY_B4_TARGET_B_STAGED_SERVER" in paper_text,
        "primary_broken_target_a_changed_server":
            "PHYSICAL_AUTHORITY_B4_TARGET_A_CHANGED_SERVER" in paper_text,
        "primary_broken_target_b_changed_server":
            "PHYSICAL_AUTHORITY_B4_TARGET_B_CHANGED_SERVER" in paper_text,
    }


def install(core: Any) -> None:
    """Install exactly one reviewed B5 capability without mutating runner_core.py."""

    def action_client_acceptance_primary_broken(repo, result_dir, manifest):
        original_popen = core.subprocess.Popen
        original_evaluator = core.evaluate_primary_hit_log_checks

        def b5_popen(argv, *args, **kwargs):
            if isinstance(argv, list):
                if ":mmo-bootstrap:runServer" in argv:
                    flag = "-PphysicalPrimaryBrokenAcceptance=true"
                    if flag not in argv:
                        argv.append(flag)
                if "runClientGameTest" in argv:
                    flag = "-PphysicalPrimaryBrokenAcceptance=true"
                    if flag not in argv:
                        argv.append(flag)
            return original_popen(argv, *args, **kwargs)

        try:
            core.subprocess.Popen = b5_popen
            core.evaluate_primary_hit_log_checks = evaluate_broken_log_checks
            code, stdout, stderr, record = core.action_client_acceptance_ingress(
                repo,
                result_dir,
                manifest,
                require_primary_input=True,
                require_primary_hit=True,
            )
            record["fixed_command_id"] = "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_BROKEN"
            record["b5_fixed_staging"] = "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_HIT"
            return code, stdout, stderr, record
        finally:
            core.evaluate_primary_hit_log_checks = original_evaluator
            core.subprocess.Popen = original_popen

    core.evaluate_broken_log_checks = evaluate_broken_log_checks
    core.action_client_acceptance_primary_broken = action_client_acceptance_primary_broken
    core.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_PRIMARY_BROKEN_V1"] = core.ActionSpec(
        action_client_acceptance_primary_broken,
        "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_BROKEN",
    )
