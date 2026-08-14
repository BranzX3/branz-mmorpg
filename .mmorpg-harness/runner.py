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

for _name in dir(_runner_core):
    if not _name.startswith("__"):
        globals()[_name] = getattr(_runner_core, _name)

RUNNER_EXTENSION_VERSION = (
    "b5-broken-v1+b6-chronicle-v1+b7-broken-restart-v1+c12-consumable-lot-v2"
)

if __name__ == "__main__":
    raise SystemExit(_runner_core.main())
