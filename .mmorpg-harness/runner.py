#!/usr/bin/env python3
"""Stable harness wrapper for reviewed physical acceptance extensions."""
from __future__ import annotations

import subprocess as _bootstrap_subprocess
import sys as _bootstrap_sys
import types as _bootstrap_types
from pathlib import Path as _BootstrapPath

_CONTROL_BRANCH = "HARNESS_MMORPG_CONTROL"


def _worker_repo() -> _BootstrapPath:
    cwd = _BootstrapPath.cwd().resolve()
    for candidate in (cwd, cwd.parent / "repo"):
        if (candidate / ".git").exists():
            return candidate
    raise RuntimeError("MMORPG harness worker repository is unavailable")


def _load_control_module(name: str, relative_path: str):
    repo = _worker_repo()
    ref = f"origin/{_CONTROL_BRANCH}:{relative_path}"
    loaded = _bootstrap_subprocess.run(
        ["git", "-C", str(repo), "show", ref],
        text=True,
        encoding="utf-8",
        errors="strict",
        stdout=_bootstrap_subprocess.PIPE,
        stderr=_bootstrap_subprocess.PIPE,
        check=False,
    )
    if loaded.returncode != 0:
        raise RuntimeError(f"Could not load reviewed harness module {relative_path}: {loaded.stderr.strip()}")
    module = _bootstrap_types.ModuleType(name)
    module.__file__ = str(repo / relative_path)
    _bootstrap_sys.modules[name] = module
    exec(compile(loaded.stdout, module.__file__, "exec"), module.__dict__)
    return module


_runner_core = _load_control_module("runner_core", ".mmorpg-harness/runner_core.py")
_runner_core.__file__ = __file__
_runner_b5 = _load_control_module("runner_b5", ".mmorpg-harness/runner_b5.py")
_runner_b5.install(_runner_core)
_runner_b6 = _load_control_module("runner_b6", ".mmorpg-harness/runner_b6.py")
_runner_b6.install(_runner_core)
_runner_b7 = _load_control_module("runner_b7", ".mmorpg-harness/runner_b7.py")
_runner_b7.install(_runner_core)
_runner_c12 = _load_control_module("runner_c12", ".mmorpg-harness/runner_c12.py")
_runner_c12.install(_runner_core)
_runner_c3 = _load_control_module("runner_c3", ".mmorpg-harness/runner_c3.py")
_runner_c3.install(_runner_core)
_runner_c4 = _load_control_module("runner_c4", ".mmorpg-harness/runner_c4.py")
_runner_c4.install(_runner_core)
_runner_c4_status_retry = _load_control_module(
    "runner_c4_status_retry", ".mmorpg-harness/runner_c4_status_retry.py"
)
_runner_c4_status_retry.install(_runner_c4)
_runner_d13 = _load_control_module("runner_d13", ".mmorpg-harness/runner_d13.py")
_runner_d13.install(_runner_core)
_runner_d46 = _load_control_module("runner_d46", ".mmorpg-harness/runner_d46.py")
_runner_d46.install(_runner_core)
_runner_e = _load_control_module("runner_e", ".mmorpg-harness/runner_e.py")
_runner_e.install(_runner_core)

_SECTION_F_CLIENT_FLAG = "-PphysicalChronicleBoundaryAcceptance=true"
_SECTION_F_SINGLE_MARKERS = (
    "PHYSICAL_AUTHORITY_CHRONICLE_F_ITEMS_READY_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_STATUS_BEFORE_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_QUIVER_BEFORE_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_NATIVE_REJECTED_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_STATUS_AFTER_NATIVE_REJECT_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_VIRTUAL_COMMIT_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_QUIVER_COMMITTED_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_STATUS_AFTER_VIRTUAL_COMMIT_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_REOPENED_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_STATUS_AFTER_REOPEN_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_STATUS_RECONNECT_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_QUIVER_RECONNECT_CLIENT",
    "PHYSICAL_AUTHORITY_CHRONICLE_F_COMPLETE_CLIENT",
)


def _evaluate_chronicle_boundary_f(client_text: str, paper_text: str) -> dict[str, bool]:
    checks = {
        f"f_marker_{index:02d}": client_text.count(marker) == 1
        for index, marker in enumerate(_SECTION_F_SINGLE_MARKERS, 1)
    }
    checks.update(
        {
            "f_handshake_twice_client": (
                client_text.count("PHYSICAL_AUTHORITY_CHRONICLE_F_HANDSHAKE_CLIENT") == 2
            ),
            "f_chronicle_opened_three_times_client": (
                client_text.count("PHYSICAL_AUTHORITY_CHRONICLE_F_OPENED_CLIENT") == 3
            ),
            "f_three_dev_grants_server": paper_text.count("/mmo dev") == 3,
            "f_eleven_item_replace_commands_server": (
                paper_text.count("/item replace entity @s hotbar.") == 11
            ),
            "f_status_commands_bounded_server": (
                13 <= paper_text.count("/mmo physical status") <= 156
            ),
        }
    )
    return checks


def _section_f_runtime_selfcheck() -> None:
    client = "\n".join(
        ["PHYSICAL_AUTHORITY_CHRONICLE_F_HANDSHAKE_CLIENT"] * 2
        + ["PHYSICAL_AUTHORITY_CHRONICLE_F_OPENED_CLIENT"] * 3
        + list(_SECTION_F_SINGLE_MARKERS)
    )
    paper = "\n".join(
        ["Player0 issued server command: /mmo dev"] * 3
        + [
            "Player0 issued server command: /item replace entity @s hotbar.0 with minecraft:stone"
        ]
        * 11
        + ["Player0 issued server command: /mmo physical status"] * 13
    )
    checks = _evaluate_chronicle_boundary_f(client, paper)
    failed = sorted(name for name, passed in checks.items() if not passed)
    if failed:
        raise RuntimeError(f"Section F harness self-check rejected valid evidence: {failed}")
    duplicate = client + "\nPHYSICAL_AUTHORITY_CHRONICLE_F_COMPLETE_CLIENT"
    if _evaluate_chronicle_boundary_f(duplicate, paper)["f_marker_13"]:
        raise RuntimeError("Section F harness self-check accepted duplicate completion evidence")
    too_few_status = "\n".join(
        ["Player0 issued server command: /mmo dev"] * 3
        + [
            "Player0 issued server command: /item replace entity @s hotbar.0 with minecraft:stone"
        ]
        * 11
        + ["Player0 issued server command: /mmo physical status"] * 12
    )
    if _evaluate_chronicle_boundary_f(client, too_few_status)[
        "f_status_commands_bounded_server"
    ]:
        raise RuntimeError("Section F harness self-check accepted incomplete physical snapshots")


def _install_section_f(core) -> None:
    _section_f_runtime_selfcheck()

    def action_client_acceptance_chronicle_boundary_f(repo, result_dir, manifest):
        original_popen = core.subprocess.Popen

        def section_f_popen(argv, *args, **kwargs):
            if (
                isinstance(argv, list)
                and "runClientGameTest" in argv
                and _SECTION_F_CLIENT_FLAG not in argv
            ):
                argv.append(_SECTION_F_CLIENT_FLAG)
            return original_popen(argv, *args, **kwargs)

        try:
            core.subprocess.Popen = section_f_popen
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
        checks = _evaluate_chronicle_boundary_f(client_text, paper_text)
        record.setdefault("checks", {}).update(checks)
        record["fixed_command_id"] = "PHYSICAL_CLIENT_ACCEPTANCE_CHRONICLE_BOUNDARY_F_V1"
        record["section_f_client_flag"] = _SECTION_F_CLIENT_FLAG
        passed = code == 0 and all(checks.values())
        record["action_status"] = "PASS" if passed else "FAIL"
        return (
            0 if passed else 1,
            (
                "PHYSICAL_CLIENT_ACCEPTANCE_CHRONICLE_BOUNDARY_F_V1_PASS\n"
                if passed
                else "PHYSICAL_CLIENT_ACCEPTANCE_CHRONICLE_BOUNDARY_F_V1_FAIL\n"
            ),
            stderr,
            record,
        )

    core.evaluate_chronicle_boundary_f_checks = _evaluate_chronicle_boundary_f
    core.action_client_acceptance_chronicle_boundary_f = (
        action_client_acceptance_chronicle_boundary_f
    )
    core.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_CHRONICLE_BOUNDARY_F_V1"] = core.ActionSpec(
        action_client_acceptance_chronicle_boundary_f,
        "PHYSICAL_CLIENT_ACCEPTANCE_CHRONICLE_BOUNDARY_F_V1",
    )


_install_section_f(_runner_core)

for _name in dir(_runner_core):
    if not _name.startswith("__"):
        globals()[_name] = getattr(_runner_core, _name)

RUNNER_EXTENSION_VERSION = (
    "b5-broken-v1+b6-chronicle-v1+b7-broken-restart-v1+"
    "c12-consumable-lot-v2+c3-consumable-use-v1+c4-consumable-restart-v1+"
    "c4-status-retry-v1+d13-shield-offhand-v1+d46-shield-wear-staff-v3+"
    "e-world-mob-v1+f-chronicle-boundary-v1"
)

if __name__ == "__main__":
    raise SystemExit(_runner_core.main())