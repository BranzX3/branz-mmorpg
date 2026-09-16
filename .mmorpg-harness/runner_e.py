#!/usr/bin/env python3
"""Section E fixed real-client ordinary-world-mob acceptance extension."""
from __future__ import annotations

import re
import threading
import time
from pathlib import Path
from typing import Any

CLIENT_FLAG = "-PphysicalWorldMobEAcceptance=true"
WORLD_TAG = "branz_e_world"
TRAINING_TAG = "branz_e_training"
TRAINING_DUMMY_TAG = "branzmmo.training_dummy"
WORLD_READY_LEVEL = 21
TRAINING_READY_LEVEL = 22

WORLD_HIT = re.compile(
    r"^PHYSICAL_AUTHORITY_E_WORLD_HIT_CLIENT HIT targets=(\d+) damage=([0-9.]+) "
    r"health=([0-9.]+)/([0-9.]+)(.*)$",
    re.MULTILINE,
)
TRAINING_HIT = re.compile(
    r"^PHYSICAL_AUTHORITY_E_TRAINING_HIT_CLIENT HIT targets=(\d+) damage=([0-9.]+) "
    r"health=([0-9.]+)/([0-9.]+)(.*)$",
    re.MULTILINE,
)


def _read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except FileNotFoundError:
        return ""


def _wait_for(path: Path, marker: str, timeout: float) -> str:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        text = _read(path)
        if marker in text:
            return text
        time.sleep(0.1)
    raise RuntimeError(f"Section E staging timeout waiting for {marker}")


def _write_console(server: Any, *commands: str) -> None:
    if server.stdin is None:
        raise RuntimeError("Section E Paper stdin is unavailable")
    for command in commands:
        server.stdin.write(command + "\n")
    server.stdin.flush()


def _stage(repo_result_dir: Path, server: Any) -> None:
    paper_log = repo_result_dir / "paper.log"
    client_log = repo_result_dir / "client.log"
    paper_text = _wait_for(
        paper_log, "PHYSICAL_AUTHORITY_PRIMARY_STAGE_PROJECTED_SERVER", 60
    )
    joined = re.search(r"([A-Za-z0-9_]{1,16}) joined the game", paper_text)
    if joined is None:
        paper_text = _wait_for(paper_log, "joined the game", 30)
        joined = re.search(r"([A-Za-z0-9_]{1,16}) joined the game", paper_text)
    if joined is None:
        raise RuntimeError("Section E could not resolve the physical client player name")
    player = joined.group(1)

    _write_console(
        server,
        f"execute as {player} at @s run tp @s ~ ~ ~ 0 10",
        f'execute as {player} at @s run summon minecraft:cow ~ ~ ~2 '
        f'{{NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Tags:["{WORLD_TAG}"]}}',
        f"execute if entity @e[tag={WORLD_TAG},limit=1] run say PHYSICAL_AUTHORITY_E_WORLD_STAGED_SERVER",
        f"execute if entity @e[tag={WORLD_TAG},limit=1,nbt={{Health:10.0f}}] run say PHYSICAL_AUTHORITY_E_WORLD_NATURAL_HEALTH_SERVER",
        f"execute if entity @e[tag={WORLD_TAG},limit=1] unless entity @e[tag={WORLD_TAG},tag={TRAINING_DUMMY_TAG},limit=1] run say PHYSICAL_AUTHORITY_E_WORLD_UNTAGGED_SERVER",
        f"experience set {player} {WORLD_READY_LEVEL} levels",
    )
    _wait_for(paper_log, "PHYSICAL_AUTHORITY_E_WORLD_STAGED_SERVER", 15)
    _wait_for(paper_log, "PHYSICAL_AUTHORITY_E_WORLD_NATURAL_HEALTH_SERVER", 15)
    _wait_for(paper_log, "PHYSICAL_AUTHORITY_E_WORLD_UNTAGGED_SERVER", 15)
    _wait_for(client_log, "PHYSICAL_AUTHORITY_E_WORLD_DEATH_CLIENT", 120)

    removed = False
    for _ in range(30):
        _write_console(
            server,
            f"execute unless entity @e[tag={WORLD_TAG},limit=1] run say PHYSICAL_AUTHORITY_E_WORLD_REMOVED_SERVER",
        )
        if "PHYSICAL_AUTHORITY_E_WORLD_REMOVED_SERVER" in _read(paper_log):
            removed = True
            break
        time.sleep(0.2)
    if not removed:
        raise RuntimeError("Section E ordinary cow did not leave the Bukkit entity lifecycle")

    _write_console(
        server,
        f'execute as {player} at @s run summon minecraft:cow ~ ~ ~2 '
        f'{{NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Tags:["{TRAINING_TAG}","{TRAINING_DUMMY_TAG}"]}}',
        f"execute if entity @e[tag={TRAINING_TAG},tag={TRAINING_DUMMY_TAG},limit=1] run say PHYSICAL_AUTHORITY_E_TRAINING_STAGED_SERVER",
        f"execute if entity @e[tag={TRAINING_TAG},limit=1,nbt={{Health:10.0f}}] run say PHYSICAL_AUTHORITY_E_TRAINING_NATURAL_HEALTH_SERVER",
        f"experience set {player} {TRAINING_READY_LEVEL} levels",
    )
    _wait_for(paper_log, "PHYSICAL_AUTHORITY_E_TRAINING_STAGED_SERVER", 15)
    _wait_for(paper_log, "PHYSICAL_AUTHORITY_E_TRAINING_NATURAL_HEALTH_SERVER", 15)
    _wait_for(client_log, "PHYSICAL_AUTHORITY_E_TRAINING_HIT_CLIENT", 60)

    stable = False
    for _ in range(20):
        _write_console(
            server,
            f"execute if entity @e[tag={TRAINING_TAG},tag={TRAINING_DUMMY_TAG},limit=1,nbt={{Health:10.0f}}] run say PHYSICAL_AUTHORITY_E_TRAINING_BUKKIT_HEALTH_STABLE_SERVER",
        )
        if "PHYSICAL_AUTHORITY_E_TRAINING_BUKKIT_HEALTH_STABLE_SERVER" in _read(paper_log):
            stable = True
            break
        time.sleep(0.1)
    if not stable:
        raise RuntimeError(
            "Section E tagged dummy changed Bukkit health instead of retaining hidden training health"
        )


def _count(text: str, marker: str) -> int:
    return text.count(marker)


def evaluate(client_text: str, paper_text: str) -> dict[str, bool]:
    world = list(WORLD_HIT.finditer(client_text))
    training = list(TRAINING_HIT.finditer(client_text))
    world_health = [float(match.group(3)) for match in world]
    world_max = [float(match.group(4)) for match in world]
    world_tails = [match.group(5) for match in world]
    descending = bool(world_health) and all(
        current < previous
        for previous, current in zip([10.0, *world_health[:-1]], world_health)
    )
    world_death_hits = sum(" deaths=1" in tail for tail in world_tails)
    training_valid = False
    if len(training) == 1:
        row = training[0]
        training_valid = (
            int(row.group(1)) == 1
            and float(row.group(4)) == 1000.0
            and 0.0 <= float(row.group(3)) < 1000.0
            and " deaths=" not in row.group(5)
        )

    client_markers = (
        "PHYSICAL_AUTHORITY_E_WORLD_TARGET_READY_CLIENT",
        "PHYSICAL_AUTHORITY_E_WORLD_STATUS_BEFORE_CLIENT",
        "PHYSICAL_AUTHORITY_E_WORLD_DEATH_CLIENT",
        "PHYSICAL_AUTHORITY_E_WORLD_STATUS_AFTER_CLIENT",
        "PHYSICAL_AUTHORITY_E_WORLD_AUTHORITY_MATCHED_HITS_CLIENT",
        "PHYSICAL_AUTHORITY_E_TRAINING_TARGET_READY_CLIENT",
        "PHYSICAL_AUTHORITY_E_TRAINING_STATUS_BEFORE_CLIENT",
        "PHYSICAL_AUTHORITY_E_TRAINING_STATUS_AFTER_CLIENT",
        "PHYSICAL_AUTHORITY_E_TRAINING_HIDDEN_HEALTH_CLIENT",
        "PHYSICAL_AUTHORITY_E_TRAINING_AUTHORITY_WORN_ONCE_CLIENT",
        "PHYSICAL_AUTHORITY_E_COMPLETE_CLIENT",
    )
    checks = {
        f"e_client_marker_{index:02d}": _count(client_text, marker) == 1
        for index, marker in enumerate(client_markers, 1)
    }
    checks.update(
        {
            "e_world_hit_count_bounded": 1 <= len(world) <= 16,
            "e_world_targets_exact": bool(world) and all(int(row.group(1)) == 1 for row in world),
            "e_world_uses_cow_max_health": bool(world) and all(value == 10.0 for value in world_max),
            "e_world_health_strictly_decreases": descending,
            "e_world_lethal_resolution_once": (
                world_death_hits == 1 and " deaths=1" in world_tails[-1]
                if world_tails
                else False
            ),
            "e_training_hidden_health_resolution": training_valid,
            "e_world_staged_server": _count(paper_text, "PHYSICAL_AUTHORITY_E_WORLD_STAGED_SERVER") == 1,
            "e_world_natural_health_server": _count(paper_text, "PHYSICAL_AUTHORITY_E_WORLD_NATURAL_HEALTH_SERVER") == 1,
            "e_world_explicitly_untagged_server": _count(paper_text, "PHYSICAL_AUTHORITY_E_WORLD_UNTAGGED_SERVER") == 1,
            "e_world_removed_server": _count(paper_text, "PHYSICAL_AUTHORITY_E_WORLD_REMOVED_SERVER") == 1,
            "e_training_staged_server": _count(paper_text, "PHYSICAL_AUTHORITY_E_TRAINING_STAGED_SERVER") == 1,
            "e_training_natural_health_server": _count(paper_text, "PHYSICAL_AUTHORITY_E_TRAINING_NATURAL_HEALTH_SERVER") == 1,
            "e_training_bukkit_health_stable_server": _count(paper_text, "PHYSICAL_AUTHORITY_E_TRAINING_BUKKIT_HEALTH_STABLE_SERVER") == 1,
            "e_two_fixed_cow_summons_server": paper_text.count("summon minecraft:cow") == 2,
            "e_no_direct_health_injection_server": "data merge" not in paper_text,
            "e_no_direct_kill_command_server": "kill @e[tag=branz_e_" not in paper_text,
        }
    )
    return checks


def runtime_selfcheck() -> None:
    client = "\n".join(
        [
            "PHYSICAL_AUTHORITY_E_WORLD_TARGET_READY_CLIENT",
            "PHYSICAL_AUTHORITY_E_WORLD_STATUS_BEFORE_CLIENT",
            "PHYSICAL_AUTHORITY_E_WORLD_HIT_CLIENT HIT targets=1 damage=4.0 health=6.0/10.0 posture=0.0",
            "PHYSICAL_AUTHORITY_E_WORLD_HIT_CLIENT HIT targets=1 damage=6.0 health=0.0/10.0 posture=0.0 deaths=1",
            "PHYSICAL_AUTHORITY_E_WORLD_DEATH_CLIENT hits=2 max=10.0",
            "PHYSICAL_AUTHORITY_E_WORLD_STATUS_AFTER_CLIENT",
            "PHYSICAL_AUTHORITY_E_WORLD_AUTHORITY_MATCHED_HITS_CLIENT",
            "PHYSICAL_AUTHORITY_E_TRAINING_TARGET_READY_CLIENT",
            "PHYSICAL_AUTHORITY_E_TRAINING_STATUS_BEFORE_CLIENT",
            "PHYSICAL_AUTHORITY_E_TRAINING_HIT_CLIENT HIT targets=1 damage=4.0 health=996.0/1000.0 posture=0.0",
            "PHYSICAL_AUTHORITY_E_TRAINING_STATUS_AFTER_CLIENT",
            "PHYSICAL_AUTHORITY_E_TRAINING_HIDDEN_HEALTH_CLIENT",
            "PHYSICAL_AUTHORITY_E_TRAINING_AUTHORITY_WORN_ONCE_CLIENT",
            "PHYSICAL_AUTHORITY_E_COMPLETE_CLIENT",
        ]
    )
    paper = "\n".join(
        [
            "summon minecraft:cow",
            "summon minecraft:cow",
            "PHYSICAL_AUTHORITY_E_WORLD_STAGED_SERVER",
            "PHYSICAL_AUTHORITY_E_WORLD_NATURAL_HEALTH_SERVER",
            "PHYSICAL_AUTHORITY_E_WORLD_UNTAGGED_SERVER",
            "PHYSICAL_AUTHORITY_E_WORLD_REMOVED_SERVER",
            "PHYSICAL_AUTHORITY_E_TRAINING_STAGED_SERVER",
            "PHYSICAL_AUTHORITY_E_TRAINING_NATURAL_HEALTH_SERVER",
            "PHYSICAL_AUTHORITY_E_TRAINING_BUKKIT_HEALTH_STABLE_SERVER",
        ]
    )
    checks = evaluate(client, paper)
    failed = sorted(name for name, passed in checks.items() if not passed)
    if failed:
        raise RuntimeError(f"Section E runtime self-check rejected valid evidence: {failed}")
    bad = client.replace("health=6.0/10.0", "health=996.0/1000.0", 1)
    if evaluate(bad, paper)["e_world_uses_cow_max_health"]:
        raise RuntimeError("Section E runtime self-check accepted hidden health for ordinary cow")


def install(core: Any) -> None:
    runtime_selfcheck()

    def action_client_acceptance_world_mob_e(repo, result_dir, manifest):
        original_popen = core.subprocess.Popen
        server_holder: dict[str, Any] = {}
        stage_errors: list[str] = []
        stage_thread: list[threading.Thread] = []

        def stage_worker() -> None:
            try:
                server = server_holder.get("server")
                if server is None:
                    raise RuntimeError("Section E Paper process was not captured")
                _stage(result_dir, server)
            except Exception as exc:
                stage_errors.append(f"{type(exc).__name__}: {exc}")

        def e_popen(argv, *args, **kwargs):
            is_client = isinstance(argv, list) and "runClientGameTest" in argv
            if is_client and CLIENT_FLAG not in argv:
                argv.append(CLIENT_FLAG)
            process = original_popen(argv, *args, **kwargs)
            if isinstance(argv, list):
                if ":mmo-bootstrap:runServer" in argv:
                    server_holder["server"] = process
                if is_client:
                    thread = threading.Thread(target=stage_worker, daemon=True)
                    stage_thread.append(thread)
                    thread.start()
            return process

        try:
            core.subprocess.Popen = e_popen
            code, stdout, stderr, record = core.action_client_acceptance_ingress(
                repo,
                result_dir,
                manifest,
                require_primary_input=True,
            )
        finally:
            core.subprocess.Popen = original_popen

        for thread in stage_thread:
            thread.join(timeout=5)
        client_text = _read(result_dir / "client.log")
        paper_text = _read(result_dir / "paper.log")
        checks = evaluate(client_text, paper_text)
        checks["e_stage_worker_clean"] = not stage_errors
        record.setdefault("checks", {}).update(checks)
        record["stage_errors"] = stage_errors
        record["fixed_command_id"] = "PHYSICAL_CLIENT_ACCEPTANCE_WORLD_MOB_E"
        passed = code == 0 and all(checks.values())
        record["action_status"] = "PASS" if passed else "FAIL"
        return (
            0 if passed else 1,
            "PHYSICAL_CLIENT_ACCEPTANCE_WORLD_MOB_E_PASS\n"
            if passed
            else "PHYSICAL_CLIENT_ACCEPTANCE_WORLD_MOB_E_FAIL\n",
            stderr,
            record,
        )

    core.evaluate_world_mob_e_checks = evaluate
    core.action_client_acceptance_world_mob_e = action_client_acceptance_world_mob_e
    core.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_WORLD_MOB_E_V1"] = core.ActionSpec(
        action_client_acceptance_world_mob_e,
        "PHYSICAL_CLIENT_ACCEPTANCE_WORLD_MOB_E",
    )