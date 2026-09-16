#!/usr/bin/env python3
"""Section A exact legacy MAIN_HAND migration extension for the deterministic MMORPG harness."""
from __future__ import annotations

import os
import re
import socket
import subprocess
import time
from pathlib import Path
from typing import Any

LEGACY_RUNTIME_SHA = "8c5a04271f9385730aff0b3332608812a216dc95"
TARGET_RUNTIME_SHA = "2bfbcc74f81a57fb6d4e61151efba4446eff75d5"
ACTION_ID = "MMO_CLIENT_ACCEPTANCE_LEGACY_MAIN_HAND_MIGRATION_V1"
ACTION_IDENTITY = "PHYSICAL_CLIENT_ACCEPTANCE_LEGACY_MAIN_HAND_MIGRATION_V1"
POSITIVE_WORKTREE_NAME = "section-a-positive-runtime"
NEGATIVE_WORKTREE_NAME = "section-a-negative-runtime"
DATABASE_RELATIVE_PATH = Path("mmo-bootstrap/run/plugins/BranzMMO/embedded-postgres")
CLIENT_RELATIVE_PATH = Path("acceptance/physical-client-26.2")
CLIENT_PHASE_PROPERTY = "physicalLegacyMainHandPhase"
SWORD_DEFINITION = "weapon.training_sword"

UUID_PATTERN = r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
TARGET_STATUS_RE = re.compile(
    rf"ITEM uuid=({UUID_PATTERN}) def=weapon\.training_sword "
    r"loc=([^ ]+) ver=(\d+) durability=(\d+)/(\d+) "
    rf"tx=({UUID_PATTERN}) content=(\S+)"
)

PHASE_MARKERS = {
    "legacy-seed": "PHYSICAL_AUTHORITY_A_LEGACY_SEEDED_CLIENT",
    "legacy-probe": "PHYSICAL_AUTHORITY_A_LEGACY_PROBE_COMPLETE_CLIENT",
    "target-verify": "PHYSICAL_AUTHORITY_A_TARGET_STATUS_CLIENT",
    "target-stable": "PHYSICAL_AUTHORITY_A_TARGET_STABLE_STATUS_CLIENT",
    "negative-prep": "PHYSICAL_AUTHORITY_A_LEGACY_SEEDED_CLIENT",
    "negative-fill": "PHYSICAL_AUTHORITY_A_NEGATIVE_FULL_DB_CLIENT",
    "negative-fail": "PHYSICAL_AUTHORITY_A_NEGATIVE_TARGET_LOCKED_CLIENT",
}


def reviewed_revisions_match(legacy_sha: str, target_sha: str) -> bool:
    return legacy_sha == LEGACY_RUNTIME_SHA and target_sha == TARGET_RUNTIME_SHA


def fixed_runtime_paths_match(
    worker_root: Path, positive_path: Path, negative_path: Path
) -> bool:
    worker_root = worker_root.resolve()
    return (
        positive_path.resolve() == (worker_root / POSITIVE_WORKTREE_NAME).resolve()
        and negative_path.resolve() == (worker_root / NEGATIVE_WORKTREE_NAME).resolve()
        and positive_path.resolve() != negative_path.resolve()
    )


def _dump_text(text: str) -> str:
    return "\n".join(
        line
        for line in text.splitlines()
        if "PHYSICAL_AUTHORITY_A_LEGACY_DUMP_CLIENT" in line
    )


def _capture_uuid(text: str, key: str) -> str | None:
    match = re.search(rf"{re.escape(key)}[^\n\r]{{0,120}}?({UUID_PATTERN})", text, re.I)
    return match.group(1).lower() if match else None


def _capture_literal(text: str, key: str, literal: str) -> str | None:
    match = re.search(
        rf"{re.escape(key)}[^\n\r]{{0,120}}?({re.escape(literal)})", text, re.I
    )
    return match.group(1) if match else None


def _capture_integer(text: str, key: str) -> int | None:
    match = re.search(rf"{re.escape(key)}[^\n\r\d]{{0,80}}?(\d+)[lL]?", text, re.I)
    return int(match.group(1)) if match else None


def _capture_content_version(text: str) -> str | None:
    # Paper dumpitem renders Bukkit PDC in SNBT-like text. Prefer a quoted value and
    # fall back to the first token after the key for tolerance across Paper formatting.
    quoted = re.search(
        r"projection_content_version[^\n\r]{0,80}?[\"']([A-Za-z0-9][A-Za-z0-9._:+-]*)[\"']",
        text,
        re.I,
    )
    if quoted:
        return quoted.group(1)
    plain = re.search(
        r"projection_content_version[^\n\rA-Za-z0-9]{0,40}([A-Za-z0-9][A-Za-z0-9._:+-]*)",
        text,
        re.I,
    )
    return plain.group(1) if plain else None


def parse_legacy_identity(text: str) -> dict[str, Any] | None:
    if text.count(PHASE_MARKERS["legacy-probe"]) != 1:
        return None
    dump = _dump_text(text)
    value_id = _capture_uuid(dump, "projection_value_id")
    definition_id = _capture_literal(dump, "projection_definition_id", SWORD_DEFINITION)
    authority_version = _capture_integer(dump, "projection_authority_version")
    content_version = _capture_content_version(dump)
    if (
        value_id is None
        or definition_id is None
        or authority_version is None
        or content_version is None
    ):
        return None
    return {
        "uuid": value_id,
        "definition_id": definition_id,
        "authority_version": authority_version,
        "content_version": content_version,
    }


def target_statuses(text: str, marker: str) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for line in text.splitlines():
        if marker not in line:
            continue
        match = TARGET_STATUS_RE.search(line)
        if match is None:
            continue
        rows.append(
            {
                "uuid": match.group(1).lower(),
                "definition_id": SWORD_DEFINITION,
                "location": match.group(2),
                "version": int(match.group(3)),
                "durability": int(match.group(4)),
                "maximum_durability": int(match.group(5)),
                "transaction_id": match.group(6).lower(),
                "content_version": match.group(7),
                "raw": match.group(0),
            }
        )
    return rows


def _one_status(text: str, marker: str) -> dict[str, Any] | None:
    rows = target_statuses(text, marker)
    return rows[0] if len(rows) == 1 else None


def evaluate_positive_evidence(
    legacy_seed_text: str,
    legacy_probe_text: str,
    target_text: str,
    reconnect_text: str,
    restart_text: str,
) -> dict[str, bool]:
    legacy = parse_legacy_identity(legacy_probe_text)
    target = _one_status(target_text, PHASE_MARKERS["target-verify"])
    reconnect = _one_status(reconnect_text, PHASE_MARKERS["target-stable"])
    restart = _one_status(restart_text, PHASE_MARKERS["target-stable"])

    target_single = len(target_statuses(target_text, PHASE_MARKERS["target-verify"])) == 1
    reconnect_single = (
        len(target_statuses(reconnect_text, PHASE_MARKERS["target-stable"])) == 1
    )
    restart_single = (
        len(target_statuses(restart_text, PHASE_MARKERS["target-stable"])) == 1
    )
    target_valid = target is not None
    identity_valid = legacy is not None
    location_valid = bool(
        target_valid
        and re.fullmatch(r"CHARACTER_INVENTORY/slot:(\d|[12]\d|3[0-5])", target["location"])
        and target["location"] != "CHARACTER_INVENTORY/slot:8"
    )
    stable = bool(
        target_valid
        and reconnect is not None
        and restart is not None
        and target["raw"] == reconnect["raw"] == restart["raw"]
    )
    return {
        "a_legacy_seed_marker_once": legacy_seed_text.count(PHASE_MARKERS["legacy-seed"]) == 1,
        "a_legacy_probe_marker_once": legacy_probe_text.count(PHASE_MARKERS["legacy-probe"]) == 1,
        "a_legacy_identity_complete": identity_valid,
        "a_target_status_exactly_one": target_single,
        "a_reconnect_status_exactly_one": reconnect_single,
        "a_restart_status_exactly_one": restart_single,
        "a_exact_uuid_preserved": bool(identity_valid and target_valid and legacy["uuid"] == target["uuid"]),
        "a_exact_version_plus_one": bool(
            identity_valid
            and target_valid
            and target["version"] == legacy["authority_version"] + 1
        ),
        "a_training_sword_definition": bool(
            identity_valid
            and target_valid
            and legacy["definition_id"] == SWORD_DEFINITION
            and target["definition_id"] == SWORD_DEFINITION
        ),
        "a_migrated_to_character_inventory": location_valid,
        "a_no_persistent_main_hand_status": bool(
            target_valid and "NATIVE_EQUIPPED/MAIN_HAND" not in target["raw"]
        ),
        "a_durability_120_120": bool(
            target_valid
            and target["durability"] == 120
            and target["maximum_durability"] == 120
        ),
        "a_target_reconnect_restart_byte_stable": stable,
    }


def evaluate_negative_evidence(
    negative_prep_text: str,
    negative_fill_text: str,
    negative_fail_text: str,
    negative_restart_text: str,
) -> dict[str, bool]:
    fail_marker = PHASE_MARKERS["negative-fail"]
    return {
        "a_negative_seed_marker_once": negative_prep_text.count(PHASE_MARKERS["negative-prep"]) == 1,
        "a_negative_full_inventory_marker_once": negative_fill_text.count(PHASE_MARKERS["negative-fill"]) == 1,
        "a_negative_target_fail_marker_once": negative_fail_text.count(fail_marker) == 1,
        "a_negative_restart_fail_marker_once": negative_restart_text.count(fail_marker) == 1,
        "a_negative_no_target_success_status": (
            PHASE_MARKERS["target-verify"] not in negative_fail_text
            and PHASE_MARKERS["target-stable"] not in negative_fail_text
            and PHASE_MARKERS["target-verify"] not in negative_restart_text
            and PHASE_MARKERS["target-stable"] not in negative_restart_text
        ),
    }


def _read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except FileNotFoundError:
        return ""


def _assert_port_free(core: Any) -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.settimeout(0.4)
        if probe.connect_ex(("127.0.0.1", 25565)) == 0:
            raise core.HarnessError("SECTION_A_PORT_IN_USE", "127.0.0.1:25565")


def _kill_tree(process: Any) -> None:
    if process is None or process.poll() is not None:
        return
    if os.name == "nt":
        subprocess.run(
            ["taskkill", "/PID", str(process.pid), "/T", "/F"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
    else:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()


def _prepare_run_directory(runtime_repo: Path) -> None:
    run_dir = runtime_repo / "mmo-bootstrap" / "run"
    run_dir.mkdir(parents=True, exist_ok=True)
    (run_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    properties = run_dir / "server.properties"
    if not properties.exists():
        properties.write_text(
            "online-mode=false\n"
            "server-port=25565\n"
            "spawn-protection=0\n"
            "max-players=1\n"
            "difficulty=normal\n",
            encoding="utf-8",
        )


def _head(core: Any, repo: Path) -> str:
    return core.git(repo, "rev-parse", "HEAD").stdout.strip()


def _assert_runtime_head(core: Any, repo: Path, expected: str) -> None:
    actual = _head(core, repo)
    if actual != expected:
        raise core.HarnessError(
            "SECTION_A_RUNTIME_REVISION_SUBSTITUTION",
            f"expected={expected} actual={actual}",
        )


def _add_fixed_worktree(core: Any, repo: Path, path: Path, sha: str) -> None:
    if path.exists():
        raise core.HarnessError("SECTION_A_FIXED_WORKTREE_EXISTS", str(path))
    cp = core.git(repo, "worktree", "add", "--detach", str(path), sha, check=False)
    if cp.returncode != 0:
        raise core.HarnessError(
            "SECTION_A_WORKTREE_ADD_FAILED", cp.stderr.strip() or cp.stdout.strip()
        )
    _assert_runtime_head(core, path, sha)


def _remove_fixed_worktree(core: Any, repo: Path, worker_root: Path, path: Path) -> None:
    expected = {
        (worker_root / POSITIVE_WORKTREE_NAME).resolve(),
        (worker_root / NEGATIVE_WORKTREE_NAME).resolve(),
    }
    if path.resolve() not in expected:
        raise core.HarnessError("SECTION_A_WORKTREE_PATH_SUBSTITUTION", str(path))
    if not path.exists():
        return
    cp = core.git(repo, "worktree", "remove", "--force", str(path), check=False)
    if cp.returncode != 0:
        raise core.HarnessError(
            "SECTION_A_WORKTREE_REMOVE_FAILED", cp.stderr.strip() or cp.stdout.strip()
        )


def _switch_runtime(core: Any, runtime_repo: Path, sha: str) -> None:
    cp = core.git(runtime_repo, "switch", "--detach", sha, check=False)
    if cp.returncode != 0:
        raise core.HarnessError(
            "SECTION_A_RUNTIME_SWITCH_FAILED", cp.stderr.strip() or cp.stdout.strip()
        )
    _assert_runtime_head(core, runtime_repo, sha)


def _client_phase(
    core: Any,
    task_repo: Path,
    client_dir: Path,
    result_dir: Path,
    phase: str,
    paper_log: Path,
    server: Any,
) -> tuple[str, list[str]]:
    marker = PHASE_MARKERS[phase]
    wrapper = client_dir / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not wrapper.is_file():
        raise core.HarnessError("SECTION_A_CLIENT_WRAPPER_MISSING", str(wrapper))
    client_log = result_dir / f"section-a-{phase}-client.log"
    client_env = core.gradle_env(task_repo)
    client_env["GRADLE_USER_HOME"] = str((task_repo.parent / "gradle-home-client").resolve())
    client_argv = [
        str(wrapper),
        "--no-daemon",
        "--console=plain",
        "runClientGameTest",
        f"-P{CLIENT_PHASE_PROPERTY}={phase}",
    ]
    join_offset = len(_read(paper_log))
    client = None
    player_name = None
    try:
        with client_log.open("w", encoding="utf-8") as client_out:
            client = core.subprocess.Popen(
                client_argv,
                cwd=client_dir,
                env=client_env,
                stdout=client_out,
                stderr=core.subprocess.STDOUT,
                text=True,
            )
            join_deadline = time.monotonic() + 300
            while time.monotonic() < join_deadline:
                suffix = _read(paper_log)[join_offset:]
                match = re.search(r"([A-Za-z0-9_]{1,16}) joined the game", suffix)
                if match is not None:
                    player_name = match.group(1)
                    break
                if client.poll() is not None:
                    raise core.HarnessError(
                        "SECTION_A_CLIENT_EXITED_BEFORE_JOIN",
                        f"phase={phase} exit={client.returncode}",
                    )
                if server.poll() is not None:
                    raise core.HarnessError(
                        "SECTION_A_PAPER_EXITED_DURING_CLIENT",
                        f"phase={phase} exit={server.returncode}",
                    )
                time.sleep(0.5)
            if player_name is None:
                raise core.HarnessError("SECTION_A_CLIENT_JOIN_TIMEOUT", phase)
            if server.stdin is None:
                raise core.HarnessError("SECTION_A_PAPER_CONSOLE_MISSING", phase)
            server.stdin.write(f"op {player_name}\n")
            server.stdin.write(f"experience set {player_name} 7 levels\n")
            server.stdin.flush()

            marker_deadline = time.monotonic() + (900 if phase == "negative-fill" else 300)
            while time.monotonic() < marker_deadline:
                text = _read(client_log)
                if marker in text:
                    break
                if client.poll() is not None:
                    raise core.HarnessError(
                        "SECTION_A_CLIENT_EXITED_BEFORE_MARKER",
                        f"phase={phase} marker={marker} exit={client.returncode}",
                    )
                if server.poll() is not None:
                    raise core.HarnessError(
                        "SECTION_A_PAPER_EXITED_DURING_CLIENT",
                        f"phase={phase} exit={server.returncode}",
                    )
                time.sleep(0.5)
            else:
                raise core.HarnessError(
                    "SECTION_A_CLIENT_MARKER_TIMEOUT", f"phase={phase} marker={marker}"
                )
            try:
                client_rc = client.wait(timeout=120)
            except core.subprocess.TimeoutExpired as exc:
                raise core.HarnessError(
                    "SECTION_A_CLIENT_STOP_TIMEOUT", f"phase={phase}"
                ) from exc
            if client_rc != 0:
                raise core.HarnessError(
                    "SECTION_A_CLIENT_NONZERO", f"phase={phase} exit={client_rc}"
                )
        return _read(client_log), client_argv
    finally:
        _kill_tree(client)


def _server_group(
    core: Any,
    task_repo: Path,
    runtime_repo: Path,
    result_dir: Path,
    label: str,
    phases: list[str],
) -> tuple[dict[str, str], dict[str, list[str]], str, list[str]]:
    _assert_port_free(core)
    _prepare_run_directory(runtime_repo)
    root_wrapper = Path(core.gradle_wrapper(runtime_repo))
    client_dir = task_repo / CLIENT_RELATIVE_PATH
    server_env = core.gradle_env(runtime_repo)
    server_argv = [
        str(root_wrapper),
        "--no-daemon",
        "--console=plain",
        ":mmo-bootstrap:runServer",
    ]
    paper_log = result_dir / f"section-a-{label}-paper.log"
    phase_texts: dict[str, str] = {}
    phase_argv: dict[str, list[str]] = {}
    server = None
    try:
        with paper_log.open("w", encoding="utf-8") as paper_out:
            server = core.subprocess.Popen(
                server_argv,
                cwd=runtime_repo,
                env=server_env,
                stdin=core.subprocess.PIPE,
                stdout=paper_out,
                stderr=core.subprocess.STDOUT,
                text=True,
            )
            deadline = time.monotonic() + 300
            while time.monotonic() < deadline:
                if "Done (" in _read(paper_log):
                    break
                if server.poll() is not None:
                    raise core.HarnessError(
                        "SECTION_A_PAPER_EXITED_BEFORE_READY",
                        f"label={label} exit={server.returncode}",
                    )
                time.sleep(1)
            else:
                raise core.HarnessError("SECTION_A_PAPER_READY_TIMEOUT", label)

            for phase in phases:
                phase_texts[phase], phase_argv[phase] = _client_phase(
                    core, task_repo, client_dir, result_dir, phase, paper_log, server
                )

            if server.stdin is None:
                raise core.HarnessError("SECTION_A_PAPER_CONSOLE_MISSING", label)
            server.stdin.write("stop\n")
            server.stdin.flush()
            try:
                server_rc = server.wait(timeout=90)
            except core.subprocess.TimeoutExpired as exc:
                raise core.HarnessError("SECTION_A_PAPER_STOP_TIMEOUT", label) from exc
            if server_rc != 0:
                raise core.HarnessError(
                    "SECTION_A_PAPER_NONZERO", f"label={label} exit={server_rc}"
                )
        return phase_texts, phase_argv, _read(paper_log), server_argv
    finally:
        if server is not None and server.poll() is None and server.stdin is not None:
            try:
                server.stdin.write("stop\n")
                server.stdin.flush()
                server.wait(timeout=15)
            except Exception:
                _kill_tree(server)
        _kill_tree(server)


def install(core: Any) -> None:
    def action_client_acceptance_legacy_main_hand_migration(repo, result_dir, manifest):
        started = time.monotonic()
        repo = Path(repo).resolve()
        worker_root = repo.parent.resolve()
        positive = worker_root / POSITIVE_WORKTREE_NAME
        negative = worker_root / NEGATIVE_WORKTREE_NAME
        if not fixed_runtime_paths_match(worker_root, positive, negative):
            raise core.HarnessError(
                "SECTION_A_WORKTREE_PATH_SUBSTITUTION",
                f"positive={positive} negative={negative}",
            )
        if not reviewed_revisions_match(LEGACY_RUNTIME_SHA, TARGET_RUNTIME_SHA):
            raise core.HarnessError(
                "SECTION_A_RUNTIME_REVISION_SUBSTITUTION",
                f"legacy={LEGACY_RUNTIME_SHA} target={TARGET_RUNTIME_SHA}",
            )
        required_client = repo / CLIENT_RELATIVE_PATH / "src" / "gametest" / "java" / "com" / "branz" / "mmorpg" / "acceptance" / "PhysicalLegacyMainHandClientGameTest.java"
        if not required_client.is_file():
            raise core.HarnessError(
                "SECTION_A_CLIENT_SOURCE_MISSING", str(required_client.relative_to(repo))
            )
        for sha in (LEGACY_RUNTIME_SHA, TARGET_RUNTIME_SHA):
            if core.git(repo, "cat-file", "-e", f"{sha}^{{commit}}", check=False).returncode != 0:
                raise core.HarnessError("SECTION_A_RUNTIME_COMMIT_MISSING", sha)
        if positive.exists() or negative.exists():
            raise core.HarnessError(
                "SECTION_A_FIXED_WORKTREE_EXISTS",
                f"positive_exists={positive.exists()} negative_exists={negative.exists()}",
            )

        positive_db = positive / DATABASE_RELATIVE_PATH
        negative_db = negative / DATABASE_RELATIVE_PATH
        record: dict[str, Any] = {
            "legacy_runtime_sha": LEGACY_RUNTIME_SHA,
            "target_runtime_sha": TARGET_RUNTIME_SHA,
            "positive_worktree": POSITIVE_WORKTREE_NAME,
            "negative_worktree": NEGATIVE_WORKTREE_NAME,
            "database_relative_path": DATABASE_RELATIVE_PATH.as_posix(),
        }
        positive_added = False
        negative_added = False
        try:
            _add_fixed_worktree(core, repo, positive, LEGACY_RUNTIME_SHA)
            positive_added = True
            legacy_group, legacy_argv, _, legacy_server_argv = _server_group(
                core,
                repo,
                positive,
                result_dir,
                "positive-legacy",
                ["legacy-seed", "legacy-probe"],
            )
            positive_db_after_legacy = positive_db.is_dir()
            if not positive_db_after_legacy:
                raise core.HarnessError(
                    "SECTION_A_POSITIVE_DB_MISSING", DATABASE_RELATIVE_PATH.as_posix()
                )
            _switch_runtime(core, positive, TARGET_RUNTIME_SHA)
            positive_db_after_switch = positive_db.is_dir()
            if not positive_db_after_switch:
                raise core.HarnessError(
                    "SECTION_A_POSITIVE_DB_LOST_ON_SWITCH", DATABASE_RELATIVE_PATH.as_posix()
                )
            target_group, target_argv, _, target_server_argv = _server_group(
                core,
                repo,
                positive,
                result_dir,
                "positive-target",
                ["target-verify", "target-stable"],
            )
            positive_db_after_target = positive_db.is_dir()
            restart_group, restart_argv, _, restart_server_argv = _server_group(
                core,
                repo,
                positive,
                result_dir,
                "positive-target-restart",
                ["target-stable"],
            )
            positive_db_after_restart = positive_db.is_dir()

            positive_checks = evaluate_positive_evidence(
                legacy_group["legacy-seed"],
                legacy_group["legacy-probe"],
                target_group["target-verify"],
                target_group["target-stable"],
                restart_group["target-stable"],
            )
            legacy_identity = parse_legacy_identity(legacy_group["legacy-probe"])
            target_status = _one_status(
                target_group["target-verify"], PHASE_MARKERS["target-verify"]
            )

            _add_fixed_worktree(core, repo, negative, LEGACY_RUNTIME_SHA)
            negative_added = True
            negative_legacy, negative_legacy_argv, _, negative_legacy_server_argv = _server_group(
                core,
                repo,
                negative,
                result_dir,
                "negative-legacy",
                ["negative-prep", "negative-fill"],
            )
            negative_db_after_legacy = negative_db.is_dir()
            if not negative_db_after_legacy:
                raise core.HarnessError(
                    "SECTION_A_NEGATIVE_DB_MISSING", DATABASE_RELATIVE_PATH.as_posix()
                )
            _switch_runtime(core, negative, TARGET_RUNTIME_SHA)
            negative_db_after_switch = negative_db.is_dir()
            if not negative_db_after_switch:
                raise core.HarnessError(
                    "SECTION_A_NEGATIVE_DB_LOST_ON_SWITCH", DATABASE_RELATIVE_PATH.as_posix()
                )
            negative_target, negative_target_argv, _, negative_target_server_argv = _server_group(
                core,
                repo,
                negative,
                result_dir,
                "negative-target",
                ["negative-fail"],
            )
            negative_db_after_failure = negative_db.is_dir()
            negative_restart, negative_restart_argv, _, negative_restart_server_argv = _server_group(
                core,
                repo,
                negative,
                result_dir,
                "negative-target-restart",
                ["negative-fail"],
            )
            negative_db_after_restart = negative_db.is_dir()
            negative_checks = evaluate_negative_evidence(
                negative_legacy["negative-prep"],
                negative_legacy["negative-fill"],
                negative_target["negative-fail"],
                negative_restart["negative-fail"],
            )

            path_and_runtime_checks = {
                "a_reviewed_revisions_exact": reviewed_revisions_match(
                    LEGACY_RUNTIME_SHA, TARGET_RUNTIME_SHA
                ),
                "a_fixed_runtime_paths_exact": fixed_runtime_paths_match(
                    worker_root, positive, negative
                ),
                "a_positive_db_preserved_after_legacy": positive_db_after_legacy,
                "a_positive_db_preserved_on_revision_switch": positive_db_after_switch,
                "a_positive_db_preserved_after_target": positive_db_after_target,
                "a_positive_db_preserved_after_restart": positive_db_after_restart,
                "a_negative_db_preserved_after_legacy": negative_db_after_legacy,
                "a_negative_db_preserved_on_revision_switch": negative_db_after_switch,
                "a_negative_db_preserved_after_failure": negative_db_after_failure,
                "a_negative_db_preserved_after_restart": negative_db_after_restart,
                "a_positive_target_head_exact": _head(core, positive) == TARGET_RUNTIME_SHA,
                "a_negative_target_head_exact": _head(core, negative) == TARGET_RUNTIME_SHA,
            }
            checks = dict(path_and_runtime_checks)
            checks.update(positive_checks)
            checks.update(negative_checks)
            passed = all(checks.values())
            record.update(
                {
                    "fixed_command_id": ACTION_IDENTITY,
                    "legacy_identity": legacy_identity,
                    "target_status": target_status,
                    "checks": checks,
                    "phase_client_argv": {
                        **legacy_argv,
                        **target_argv,
                        "positive-restart-target-stable": restart_argv["target-stable"],
                        **{f"negative-{k}": v for k, v in negative_legacy_argv.items()},
                        "negative-fail": negative_target_argv["negative-fail"],
                        "negative-restart-fail": negative_restart_argv["negative-fail"],
                    },
                    "server_argv": {
                        "positive-legacy": legacy_server_argv,
                        "positive-target": target_server_argv,
                        "positive-target-restart": restart_server_argv,
                        "negative-legacy": negative_legacy_server_argv,
                        "negative-target": negative_target_server_argv,
                        "negative-target-restart": negative_restart_server_argv,
                    },
                    "duration_seconds": round(time.monotonic() - started, 3),
                    "action_status": "PASS" if passed else "FAIL",
                }
            )
            return (
                0 if passed else 1,
                (
                    "PHYSICAL_CLIENT_ACCEPTANCE_LEGACY_MAIN_HAND_MIGRATION_V1_PASS\n"
                    if passed
                    else "PHYSICAL_CLIENT_ACCEPTANCE_LEGACY_MAIN_HAND_MIGRATION_V1_FAIL\n"
                ),
                "",
                record,
            )
        finally:
            # Only these exact action-owned fixed paths are eligible for removal.
            cleanup_error: Exception | None = None
            if negative_added:
                try:
                    _remove_fixed_worktree(core, repo, worker_root, negative)
                except Exception as exc:  # fail closed after attempting both fixed cleanups
                    cleanup_error = exc
            if positive_added:
                try:
                    _remove_fixed_worktree(core, repo, worker_root, positive)
                except Exception as exc:
                    cleanup_error = cleanup_error or exc
            if cleanup_error is not None:
                raise cleanup_error

    core.LEGACY_RUNTIME_SHA = LEGACY_RUNTIME_SHA
    core.TARGET_RUNTIME_SHA = TARGET_RUNTIME_SHA
    core.SECTION_A_POSITIVE_WORKTREE_NAME = POSITIVE_WORKTREE_NAME
    core.SECTION_A_NEGATIVE_WORKTREE_NAME = NEGATIVE_WORKTREE_NAME
    core.SECTION_A_DATABASE_RELATIVE_PATH = DATABASE_RELATIVE_PATH.as_posix()
    core.reviewed_section_a_revisions_match = reviewed_revisions_match
    core.fixed_section_a_runtime_paths_match = fixed_runtime_paths_match
    core.parse_section_a_legacy_identity = parse_legacy_identity
    core.section_a_target_statuses = target_statuses
    core.evaluate_section_a_positive_evidence = evaluate_positive_evidence
    core.evaluate_section_a_negative_evidence = evaluate_negative_evidence
    core.action_client_acceptance_legacy_main_hand_migration = (
        action_client_acceptance_legacy_main_hand_migration
    )
    core.ACTION_SPECS[ACTION_ID] = core.ActionSpec(
        action_client_acceptance_legacy_main_hand_migration,
        ACTION_IDENTITY,
    )
