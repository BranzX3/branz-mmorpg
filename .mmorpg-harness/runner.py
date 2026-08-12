#!/usr/bin/env python3
"""Stable harness wrapper: byte-preserved core plus reviewed capability extensions."""
from __future__ import annotations

import runner_b5 as _runner_b5
import runner_core as _runner_core

_runner_b5.install(_runner_core)

for _name in dir(_runner_core):
    if not _name.startswith("__"):
        globals()[_name] = getattr(_runner_core, _name)

RUNNER_EXTENSION_VERSION = "b5-broken-v1"

if __name__ == "__main__":
    raise SystemExit(_runner_core.main())
