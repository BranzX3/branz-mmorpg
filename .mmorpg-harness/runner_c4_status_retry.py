#!/usr/bin/env python3
"""Bound C4 restart status polling by the client retry contract instead of exact timing."""
from __future__ import annotations

from typing import Any

MIN_RESTART_STATUS_COMMANDS = 3
MAX_RESTART_STATUS_COMMANDS = 36


def _status_command_count_is_valid(restart_paper_text: str) -> bool:
    count = restart_paper_text.count("/mmo physical status")
    return MIN_RESTART_STATUS_COMMANDS <= count <= MAX_RESTART_STATUS_COMMANDS


def runtime_selfcheck() -> None:
    for count in (3, 6, 36):
        text = "\n".join(["/mmo physical status"] * count)
        if not _status_command_count_is_valid(text):
            raise RuntimeError(f"C4 status retry self-check rejected valid count {count}")
    for count in (0, 2, 37):
        text = "\n".join(["/mmo physical status"] * count)
        if _status_command_count_is_valid(text):
            raise RuntimeError(f"C4 status retry self-check accepted invalid count {count}")


def install(c4_module: Any) -> None:
    runtime_selfcheck()
    original = c4_module.evaluate_c4_restart_checks

    def evaluate_c4_restart_checks(
        phase_client_text: str,
        restart_client_text: str,
        restart_paper_text: str,
    ) -> dict[str, bool]:
        checks = dict(original(phase_client_text, restart_client_text, restart_paper_text))
        checks["c4_restart_status_commands_server"] = _status_command_count_is_valid(
            restart_paper_text
        )
        return checks

    c4_module.evaluate_c4_restart_checks = evaluate_c4_restart_checks
