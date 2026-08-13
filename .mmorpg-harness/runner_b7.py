#!/usr/bin/env python3
"""B7 broken-weapon restart extension for the immutable MMORPG harness."""
from __future__ import annotations

import os
import re
import socket
import subprocess
import time
from typing import Any


BROKEN_STATUS_RE = re.compile(
    r"ITEM uuid=([0-9a-fA-F-]{36}) def=weapon\.training_blade "
    r"loc=([^ ]+) ver=(\d+) durability=0/100 "
    r"tx=([0-9a-fA-F-]{36}) content=(\S+)"
)


def broken_status_lines(text: str) -> list[str]:
    return [match.group(0) for match in BROKEN_STATUS_RE.finditer(text)]


def evaluate_broken_restart_log_checks(
    before_client_text: str,
    restart_client_text: str,
    restart_paper_text: str,
) -> dict[str, bool]:
    before = broken_status_lines(before_client_text)
    after = broken_status_lines(restart_client_text)
    before_unique = len(before) >= 1 and len(set(before)) == 1
    after_unique = len(after) >= 1 and len(set(after)) == 1
    return {
        "broken_restart_pre_status_unique": before_unique,
        "broken_restart_post_status_unique": after_unique,
        "broken_restart_exact_authority": (
            before_unique and after_unique and before[0] == after[0]
        ),
        "broken_restart_server_handshake_client":
            "PHYSICAL_AUTHORITY_SERVER_HANDSHAKE_CLIENT" in restart_client_text,
        "broken_restart_projected_client":
            "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_RESTART_PROJECTED_CLIENT"
            in restart_client_text,
        "broken_restart_status_client":
            "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_RESTART_STATUS_CLIENT"
            in restart_client_text,
        "broken_restart_stable_client":
            "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_RESTART_STABLE_CLIENT"
            in restart_client_text,
        "broken_restart_status_command_once_server":
            restart_paper_text.count("/mmo physical status") == 1,
        "broken_restart_no_primary_restage_server":
            "PHYSICAL_AUTHORITY_PRIMARY_STAGE_PERSISTED_SERVER"
            not in restart_paper_text,
        "broken_restart_no_accelerated_wear_server":
            "PHYSICAL_AUTHORITY_BROKEN_ACCELERATED_WEAR_SERVER"
            not in restart_paper_text,
    }


def install(core: Any) -> None:
    """Install one reviewed two-phase B7 capability without changing runner_core.py."""

    def action_client_acceptance_primary_broken_restart(repo, result_dir, manifest):
        started = time.monotonic()
        phase1_code, phase1_stdout, phase1_stderr, phase1_record = (
            core.action_client_acceptance_primary_broken(repo, result_dir, manifest)
        )
        before_client_log = result_dir / "client.log"
        before_client_text = before_client_log.read_text(
            encoding="utf-8", errors="replace"
        ) if before_client_log.is_file() else ""
        before_statuses = broken_status_lines(before_client_text)

        if phase1_code != 0:
            checks = dict(phase1_record.get("checks", {}))
            checks["broken_restart_phase1_broken_pass"] = False
            return (
                phase1_code,
                phase1_stdout,
                phase1_stderr,
                {
                    "action_status": "FAIL",
                    "fixed_command_id": "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_BROKEN_RESTART",
                    "phase1_record": phase1_record,
                    "checks": checks,
                    "duration_seconds": round(time.monotonic() - started, 3),
                },
            )

        server_run = repo / "mmo-bootstrap" / "run"
        database_dir = server_run / "plugins" / "BranzMMO" / "embedded-postgres"
        if not database_dir.is_dir():
            raise core.HarnessError(
                "CLIENT_ACCEPTANCE_RESTART_DB_MISSING",
                "embedded PostgreSQL directory missing after B5 phase",
            )

        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
            probe.settimeout(0.4)
            if probe.connect_ex(("127.0.0.1", 25565)) == 0:
                raise core.HarnessError(
                    "CLIENT_ACCEPTANCE_RESTART_PORT_IN_USE", "127.0.0.1:25565"
                )

        client_dir = repo / "acceptance" / "physical-client-26.2"
        client_wrapper = client_dir / ("gradlew.bat" if os.name == "nt" else "gradlew")
        root_wrapper = core.Path(core.gradle_wrapper(repo))
        if not client_wrapper.is_file():
            raise core.HarnessError(
                "CLIENT_ACCEPTANCE_PROJECT_MISSING",
                str(client_wrapper.relative_to(repo)),
            )

        paper_restart_log = result_dir / "paper-restart.log"
        client_restart_log = result_dir / "client-restart.log"
        server_env = core.gradle_env(repo)
        client_env = core.gradle_env(repo)
        client_env["GRADLE_USER_HOME"] = str(
            (repo.parent / "gradle-home-client").resolve()
        )
        server_argv = [
            str(root_wrapper),
            "--no-daemon",
            "--console=plain",
            ":mmo-bootstrap:runServer",
        ]
        client_argv = [
            str(client_wrapper),
            "--no-daemon",
            "--console=plain",
            "runClientGameTest",
            "-PphysicalBrokenRestartAcceptance=true",
        ]

        def read_log(path):
            try:
                return path.read_text(encoding="utf-8", errors="replace")
            except FileNotFoundError:
                return ""

        def kill_tree(process):
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

        server = None
        client = None
        player_name = None
        server_rc = None
        client_rc = None
        database_preserved_before_restart = database_dir.is_dir()
        try:
            with paper_restart_log.open("w", encoding="utf-8") as paper_out:
                server = core.subprocess.Popen(
                    server_argv,
                    cwd=repo,
                    env=server_env,
                    stdin=core.subprocess.PIPE,
                    stdout=paper_out,
                    stderr=core.subprocess.STDOUT,
                    text=True,
                )
                deadline = time.monotonic() + 240
                while time.monotonic() < deadline:
                    if "Done (" in read_log(paper_restart_log):
                        break
                    if server.poll() is not None:
                        raise core.HarnessError(
                            "CLIENT_ACCEPTANCE_RESTART_PAPER_EXITED",
                            f"exit={server.returncode}",
                        )
                    time.sleep(1)
                else:
                    raise core.HarnessError(
                        "CLIENT_ACCEPTANCE_RESTART_PAPER_TIMEOUT",
                        "restarted Paper did not become ready",
                    )

                with client_restart_log.open("w", encoding="utf-8") as client_out:
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
                        paper_text = read_log(paper_restart_log)
                        match = re.search(
                            r"([A-Za-z0-9_]{1,16}) joined the game", paper_text
                        )
                        if match is not None:
                            player_name = match.group(1)
                            break
                        if client.poll() is not None:
                            raise core.HarnessError(
                                "CLIENT_ACCEPTANCE_RESTART_CLIENT_EXITED",
                                f"exit={client.returncode}",
                            )
                        time.sleep(0.5)
                    if player_name is None:
                        raise core.HarnessError(
                            "CLIENT_ACCEPTANCE_RESTART_JOIN_TIMEOUT",
                            "restart client did not join Paper",
                        )
                    if server.stdin is None:
                        raise core.HarnessError(
                            "CLIENT_ACCEPTANCE_RESTART_NO_CONSOLE",
                            "restarted Paper stdin unavailable",
                        )
                    server.stdin.write(f"op {player_name}\n")
                    server.stdin.write(f"experience set {player_name} 7 levels\n")
                    server.stdin.flush()

                    completion_marker = (
                        "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_RESTART_STABLE_CLIENT"
                    )
                    marker_deadline = time.monotonic() + 180
                    while time.monotonic() < marker_deadline:
                        client_text = read_log(client_restart_log)
                        if completion_marker in client_text:
                            break
                        if client.poll() is not None:
                            raise core.HarnessError(
                                "CLIENT_ACCEPTANCE_RESTART_CLIENT_EXITED",
                                f"exit={client.returncode}",
                            )
                        time.sleep(0.5)
                    else:
                        raise core.HarnessError(
                            "CLIENT_ACCEPTANCE_RESTART_STATUS_TIMEOUT",
                            "restart client stability marker missing",
                        )

                    receipt_deadline = time.monotonic() + 20
                    while time.monotonic() < receipt_deadline:
                        if "/mmo physical status" in read_log(paper_restart_log):
                            break
                        time.sleep(0.5)
                    else:
                        raise core.HarnessError(
                            "CLIENT_ACCEPTANCE_RESTART_COMMAND_NOT_LOGGED",
                            "restarted Paper did not log status command",
                        )

                    server.stdin.write("stop\n")
                    server.stdin.flush()
                    try:
                        server_rc = server.wait(timeout=60)
                    except core.subprocess.TimeoutExpired as exc:
                        raise core.HarnessError(
                            "CLIENT_ACCEPTANCE_RESTART_PAPER_STOP_TIMEOUT",
                            "restarted Paper did not stop",
                        ) from exc
                    try:
                        client_rc = client.wait(timeout=60)
                    except core.subprocess.TimeoutExpired as exc:
                        raise core.HarnessError(
                            "CLIENT_ACCEPTANCE_RESTART_CLIENT_STOP_TIMEOUT",
                            "restart client did not finish after server stop",
                        ) from exc

            restart_client_text = read_log(client_restart_log)
            restart_paper_text = read_log(paper_restart_log)
            checks = evaluate_broken_restart_log_checks(
                before_client_text,
                restart_client_text,
                restart_paper_text,
            )
            checks.update(
                {
                    "broken_restart_phase1_broken_pass": phase1_code == 0,
                    "broken_restart_database_preserved_before_restart":
                        database_preserved_before_restart,
                    "broken_restart_database_preserved_after_restart":
                        database_dir.is_dir(),
                    "broken_restart_client_exit_zero": client_rc == 0,
                    "broken_restart_server_exit_zero": server_rc == 0,
                }
            )
            after_statuses = broken_status_lines(restart_client_text)
            passed = all(checks.values())
            return (
                0 if passed else 1,
                "PHYSICAL_CLIENT_BROKEN_RESTART_PASS\n"
                if passed
                else "PHYSICAL_CLIENT_BROKEN_RESTART_FAIL\n",
                "",
                {
                    "action_status": "PASS" if passed else "FAIL",
                    "fixed_command_id": "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_BROKEN_RESTART",
                    "b7_fixed_phase1": "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_BROKEN",
                    "phase1_record": phase1_record,
                    "restart_server_argv": server_argv,
                    "restart_client_argv": client_argv,
                    "restart_player_name": player_name,
                    "broken_status_before_restart": (
                        before_statuses[0] if before_statuses else None
                    ),
                    "broken_status_after_restart": (
                        after_statuses[0] if after_statuses else None
                    ),
                    "checks": checks,
                    "duration_seconds": round(time.monotonic() - started, 3),
                },
            )
        finally:
            if server is not None and server.poll() is None and server.stdin is not None:
                try:
                    server.stdin.write("stop\n")
                    server.stdin.flush()
                    server.wait(timeout=15)
                except Exception:
                    kill_tree(server)
            kill_tree(client)
            kill_tree(server)

    core.broken_status_lines = broken_status_lines
    core.evaluate_broken_restart_log_checks = evaluate_broken_restart_log_checks
    core.action_client_acceptance_primary_broken_restart = (
        action_client_acceptance_primary_broken_restart
    )
    core.ACTION_SPECS["MMO_CLIENT_ACCEPTANCE_PRIMARY_BROKEN_RESTART_V1"] = (
        core.ActionSpec(
            action_client_acceptance_primary_broken_restart,
            "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_BROKEN_RESTART",
        )
    )
