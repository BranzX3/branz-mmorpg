#!/usr/bin/env python3
"""Deterministic GitHub-controlled runner for Branz MMORPG.

Remote manifests select reviewed capabilities only. They cannot provide argv,
shell fragments, environment overrides, file inputs, or arbitrary executable paths.
"""
from __future__ import annotations

import fnmatch
import hashlib
import json
import os
import platform
import re
import signal
import subprocess
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

CONTROL_BRANCH = "HARNESS_MMORPG_CONTROL"
REMOTE = "origin"
HARNESS_DIR = ".mmorpg-harness"
ACTIVE_PATH = f"{HARNESS_DIR}/ACTIVE_TASK.json"
TASK_PREFIX = f"{HARNESS_DIR}/tasks/"
RESULT_PREFIX = f"{HARNESS_DIR}/results/"
TASK_BRANCH_PREFIX = "harness-task/"
PROTOCOL_VERSION = 1
TASK_ID_RE = re.compile(r"^MMO-[A-Z0-9][A-Z0-9._-]{2,63}$")
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
ALLOWED_MANIFEST_KEYS = {
    "protocol_version",
    "task_id",
    "task_branch",
    "base_commit",
    "action_id",
    "writable_paths",
}
COMMAND_LIKE_KEYS = {
    "commands", "command", "argv", "args", "shell", "script", "powershell",
    "python", "gradle_args", "java_args", "env", "environment", "executable",
    "working_directory", "url", "download_url",
}
EPHEMERAL_PATTERNS = (
    "**/__pycache__/**", "__pycache__/**", "**/*.pyc", "*.pyc",
    "**/.pytest_cache/**", ".pytest_cache/**",
)


class HarnessError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for block in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def run(argv: list[str], *, cwd: Path, timeout: int | None = None, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(argv, cwd=cwd, text=True, capture_output=True, timeout=timeout, env=env)
    except subprocess.TimeoutExpired as exc:
        stdout = exc.stdout or ""
        stderr = exc.stderr or ""
        if isinstance(stdout, bytes):
            stdout = stdout.decode("utf-8", errors="replace")
        if isinstance(stderr, bytes):
            stderr = stderr.decode("utf-8", errors="replace")
        raise HarnessError("COMMAND_TIMEOUT", f"timeout={timeout}s argv={argv!r}\n{stdout}\n{stderr}") from exc


def run_action(argv: list[str], *, cwd: Path, timeout: int, env: dict[str, str]) -> subprocess.CompletedProcess[str]:
    kwargs: dict[str, Any] = {
        "cwd": cwd,
        "text": True,
        "stdout": subprocess.PIPE,
        "stderr": subprocess.PIPE,
        "env": env,
    }
    if os.name == "nt":
        kwargs["creationflags"] = getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0)
    else:
        kwargs["start_new_session"] = True
    proc = subprocess.Popen(argv, **kwargs)
    try:
        stdout, stderr = proc.communicate(timeout=timeout)
    except subprocess.TimeoutExpired as exc:
        if os.name == "nt":
            subprocess.run(
                ["taskkill", "/PID", str(proc.pid), "/T", "/F"],
                text=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                check=False,
            )
        else:
            try:
                os.killpg(proc.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        try:
            stdout, stderr = proc.communicate(timeout=15)
        except subprocess.TimeoutExpired:
            proc.kill()
            stdout, stderr = proc.communicate()
        raise HarnessError(
            "ACTION_TIMEOUT",
            f"timeout={timeout}s argv={argv!r}\n{stdout}\n{stderr}",
        ) from exc
    return subprocess.CompletedProcess(argv, proc.returncode, stdout, stderr)


def git(repo: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    cp = run(["git", *args], cwd=repo)
    if check and cp.returncode != 0:
        raise HarnessError("GIT_COMMAND_FAILED", f"git {' '.join(args)} -> {cp.returncode}: {cp.stderr.strip() or cp.stdout.strip()}")
    return cp


def repo_root() -> Path:
    cp = subprocess.run(["git", "rev-parse", "--show-toplevel"], text=True, capture_output=True)
    if cp.returncode != 0:
        raise HarnessError("NOT_A_GIT_REPOSITORY", cp.stderr.strip())
    return Path(cp.stdout.strip()).resolve()


def remote_text(repo: Path, path: str) -> str:
    cp = git(repo, "show", f"{REMOTE}/{CONTROL_BRANCH}:{path}", check=False)
    if cp.returncode != 0:
        raise HarnessError("REMOTE_CONTROL_FILE_MISSING", f"{CONTROL_BRANCH}:{path}")
    return cp.stdout


def load_remote_json(repo: Path, path: str) -> tuple[dict[str, Any], str]:
    raw = remote_text(repo, path)
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise HarnessError("INVALID_REMOTE_JSON", f"{path}: {exc}") from exc
    if not isinstance(value, dict):
        raise HarnessError("REMOTE_JSON_NOT_OBJECT", path)
    return value, sha256_bytes(raw.encode("utf-8"))


def is_ephemeral(path: str) -> bool:
    norm = path.replace("\\", "/")
    return any(fnmatch.fnmatch(norm, pattern) for pattern in EPHEMERAL_PATTERNS)


def status_paths(repo: Path) -> list[dict[str, str]]:
    cp = git(repo, "status", "--porcelain=v1", "-z", "--untracked-files=all")
    chunks = cp.stdout.split("\0")
    result: list[dict[str, str]] = []
    i = 0
    while i < len(chunks):
        item = chunks[i]
        if not item:
            i += 1
            continue
        if len(item) < 4:
            raise HarnessError("STATUS_PARSE_ERROR", repr(item))
        xy = item[:2]
        path = item[3:].replace("\\", "/")
        rec = {"status": xy, "path": path}
        if xy[0] in "RC" or xy[1] in "RC":
            if i + 1 < len(chunks) and chunks[i + 1]:
                rec["source_path"] = chunks[i + 1].replace("\\", "/")
                i += 1
        result.append(rec)
        i += 1
    return result


def classify_worktree(repo: Path) -> dict[str, Any]:
    entries = status_paths(repo)
    substantive = [e for e in entries if not is_ephemeral(e["path"])]
    return {
        "entries": entries,
        "ephemeral": [e for e in entries if is_ephemeral(e["path"])],
        "substantive": substantive,
        "safe_to_continue": not substantive,
    }


def path_allowed(path: str, patterns: tuple[str, ...] | list[str]) -> bool:
    norm = path.replace("\\", "/")
    return any(fnmatch.fnmatch(norm, p.replace("\\", "/")) for p in patterns)


def result_dir_rel(task_id: str) -> str:
    return f"{RESULT_PREFIX}{task_id}"


def expected_writes(task_id: str) -> tuple[str, ...]:
    return (f"{result_dir_rel(task_id)}/**",)


def stage_evidence_files(repo: Path, result_dir: Path, allowed: tuple[str, ...] | list[str]) -> list[str]:
    """Force-stage only evidence files under the allowlisted result directory.

    Evidence intentionally includes stdout/stderr logs even when the implementation branch ignores
    ``*.log``. The harness capability boundary, not repository ignore rules, owns result staging.
    """
    paths = [
        path.relative_to(repo).as_posix()
        for path in sorted(result_dir.rglob("*"))
        if path.is_file()
    ]
    if not paths:
        raise HarnessError("NO_EVIDENCE_CHANGES", result_dir.relative_to(repo).as_posix())
    bad = [path for path in paths if not path_allowed(path, allowed)]
    if bad:
        raise HarnessError("UNAUTHORIZED_EVIDENCE_PATH", json.dumps(bad))
    for path in paths:
        git(repo, "add", "-f", "--", path)
    return paths


def validate_manifest(manifest: dict[str, Any]) -> tuple[str, str, str, str]:
    bad_command_keys = sorted(set(manifest) & COMMAND_LIKE_KEYS)
    if bad_command_keys:
        raise HarnessError("REMOTE_COMMANDS_FORBIDDEN", ",".join(bad_command_keys))
    unknown = sorted(set(manifest) - ALLOWED_MANIFEST_KEYS)
    if unknown:
        raise HarnessError("TASK_MANIFEST_UNKNOWN_KEYS", ",".join(unknown))
    missing = sorted(ALLOWED_MANIFEST_KEYS - set(manifest))
    if missing:
        raise HarnessError("TASK_MANIFEST_MISSING_KEYS", ",".join(missing))
    if type(manifest["protocol_version"]) is not int or manifest["protocol_version"] != PROTOCOL_VERSION:
        raise HarnessError("TASK_PROTOCOL_VERSION_MISMATCH", repr(manifest.get("protocol_version")))
    task_id = str(manifest["task_id"])
    branch = str(manifest["task_branch"])
    base = str(manifest["base_commit"])
    action_id = str(manifest["action_id"])
    if not TASK_ID_RE.fullmatch(task_id):
        raise HarnessError("TASK_ID_INVALID", task_id)
    if branch != f"{TASK_BRANCH_PREFIX}{task_id}":
        raise HarnessError("TASK_BRANCH_INVALID", branch)
    if not SHA_RE.fullmatch(base):
        raise HarnessError("BASE_COMMIT_INVALID", base)
    if action_id not in ACTION_SPECS:
        raise HarnessError("ACTION_NOT_ALLOWLISTED", action_id)
    writes = manifest["writable_paths"]
    if not isinstance(writes, list) or not all(isinstance(v, str) for v in writes):
        raise HarnessError("TASK_MANIFEST_TYPE_ERROR", "writable_paths")
    required_writes = list(expected_writes(task_id))
    if writes != required_writes:
        raise HarnessError("WRITE_POLICY_MISMATCH", json.dumps({"required": required_writes, "actual": writes}))
    return task_id, branch, base, action_id


def switch_existing_remote_branch(repo: Path, branch: str) -> None:
    remote_ref = f"refs/remotes/{REMOTE}/{branch}"
    if git(repo, "show-ref", "--verify", "--quiet", remote_ref, check=False).returncode != 0:
        raise HarnessError("TASK_BRANCH_MISSING", branch)
    local_ref = f"refs/heads/{branch}"
    if git(repo, "show-ref", "--verify", "--quiet", local_ref, check=False).returncode == 0:
        git(repo, "switch", branch)
    else:
        git(repo, "switch", "--track", "-c", branch, f"{REMOTE}/{branch}")
    cp = git(repo, "merge", "--ff-only", f"{REMOTE}/{branch}", check=False)
    if cp.returncode != 0:
        raise HarnessError("NON_FAST_FORWARD_LOCAL_BRANCH", cp.stderr.strip() or cp.stdout.strip())


def sanitized_remote(repo: Path) -> str:
    value = git(repo, "remote", "get-url", "origin", check=False).stdout.strip()
    if "@" in value and "://" in value:
        scheme, rest = value.split("://", 1)
        if "@" in rest:
            rest = rest.split("@", 1)[1]
        return scheme + "://" + rest
    return value


def environment_fingerprint(repo: Path) -> dict[str, Any]:
    def probe(argv: list[str], timeout: int = 60) -> dict[str, Any]:
        try:
            cp = run(argv, cwd=repo, timeout=timeout)
            return {"exit_code": cp.returncode, "stdout": cp.stdout.strip(), "stderr": cp.stderr.strip()}
        except HarnessError as exc:
            return {"error": exc.code, "detail": exc.message}

    wrapper = repo / ("gradlew.bat" if os.name == "nt" else "gradlew")
    java_home = os.environ.get("JAVA_HOME")
    fp: dict[str, Any] = {
        "captured_at_utc": utc_now(),
        "platform": platform.platform(),
        "machine": platform.machine(),
        "python": sys.version,
        "python_executable": sys.executable,
        "git": probe(["git", "--version"]),
        "java": probe(["java", "-version"]),
        "java_home": java_home,
        "cpu_count": os.cpu_count(),
        "repo_remote": sanitized_remote(repo),
        "repo_head": git(repo, "rev-parse", "HEAD").stdout.strip(),
        "runner_sha256": sha256_file(Path(__file__).resolve()),
    }
    if wrapper.exists():
        fp["gradle_wrapper"] = {
            "path": wrapper.name,
            "sha256": sha256_file(wrapper),
            "version_probe": probe([str(wrapper), "--no-daemon", "--version"], timeout=180),
        }
    for rel in ("settings.gradle.kts", "build.gradle.kts", "gradle/wrapper/gradle-wrapper.properties"):
        p = repo / rel
        fp.setdefault("project_files", {})[rel] = (
            {"sha256": sha256_file(p), "size": p.stat().st_size} if p.is_file() else {"missing": True}
        )
    control_from_env = os.environ.get("BRANZ_MMO_CONTROL_COMMIT")
    if control_from_env:
        fp["daemon_control_commit"] = control_from_env
    return fp


def gradle_env(repo: Path) -> dict[str, str]:
    env = dict(os.environ)
    env["CI"] = "true"
    env["GRADLE_USER_HOME"] = str((repo.parent / "gradle-home").resolve())
    for key in list(env):
        if key in {"GRADLE_OPTS", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"} or key.startswith("ORG_GRADLE_PROJECT_"):
            env.pop(key, None)
    return env


def gradle_wrapper(repo: Path) -> str:
    p = repo / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not p.is_file():
        raise HarnessError("GRADLE_WRAPPER_MISSING", str(p))
    return str(p)


def action_client_acceptance_compile(
    repo: Path, result_dir: Path, manifest: dict[str, Any]
) -> tuple[int, str, str, dict[str, Any]]:
    client_dir = repo / "acceptance" / "physical-client-26.2"
    wrapper = client_dir / ("gradlew.bat" if os.name == "nt" else "gradlew")
    required = (
        client_dir / "settings.gradle",
        client_dir / "build.gradle",
        client_dir / "gradle.properties",
        client_dir / "gradle" / "wrapper" / "gradle-wrapper.properties",
        client_dir / "gradle" / "wrapper" / "gradle-wrapper.jar",
        wrapper,
    )
    missing = [str(path.relative_to(repo)) for path in required if not path.is_file()]
    if missing:
        raise HarnessError("CLIENT_ACCEPTANCE_PROJECT_MISSING", json.dumps(missing))

    env = gradle_env(repo)
    env["GRADLE_USER_HOME"] = str((repo.parent / "gradle-home-client").resolve())
    argv = [str(wrapper), "--no-daemon", "--console=plain", "build"]
    started = time.monotonic()
    cp = run_action(argv, cwd=client_dir, timeout=1800, env=env)
    elapsed = round(time.monotonic() - started, 3)
    return cp.returncode, cp.stdout, cp.stderr, {
        "action_status": "PASS" if cp.returncode == 0 else "FAIL",
        "fixed_command_id": "PHYSICAL_CLIENT_ACCEPTANCE_COMPILE",
        "argv": argv,
        "working_directory": client_dir.relative_to(repo).as_posix(),
        "client_wrapper_sha256": sha256_file(wrapper),
        "client_wrapper_properties_sha256": sha256_file(
            client_dir / "gradle" / "wrapper" / "gradle-wrapper.properties"
        ),
        "exit_code": cp.returncode,
        "duration_seconds": elapsed,
    }


def action_client_acceptance_ingress(
    repo: Path,
    result_dir: Path,
    manifest: dict[str, Any],
    require_primary_input: bool = False,
) -> tuple[int, str, str, dict[str, Any]]:
    import re
    import shutil
    import socket

    client_dir = repo / "acceptance" / "physical-client-26.2"
    client_wrapper = client_dir / ("gradlew.bat" if os.name == "nt" else "gradlew")
    root_wrapper = Path(gradle_wrapper(repo))
    required = (
        client_wrapper,
        client_dir / "gradle" / "wrapper" / "gradle-wrapper.jar",
        repo / "mmo-bootstrap" / "build.gradle.kts",
    )
    missing = [str(path.relative_to(repo)) for path in required if not path.is_file()]
    if missing:
        raise HarnessError("CLIENT_ACCEPTANCE_PROJECT_MISSING", json.dumps(missing))

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.settimeout(0.4)
        if probe.connect_ex(("127.0.0.1", 25565)) == 0:
            raise HarnessError("CLIENT_ACCEPTANCE_PORT_IN_USE", "127.0.0.1:25565")

    server_run = repo / "mmo-bootstrap" / "run"
    if server_run.exists():
        shutil.rmtree(server_run)
    server_run.mkdir(parents=True)
    (server_run / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (server_run / "server.properties").write_text(
        "online-mode=false\n"
        "server-port=25565\n"
        "spawn-protection=0\n"
        "max-players=1\n"
        "difficulty=normal\n",
        encoding="utf-8",
    )

    paper_log = result_dir / "paper.log"
    client_log = result_dir / "client.log"
    server_env = gradle_env(repo)
    client_env = gradle_env(repo)
    client_env["GRADLE_USER_HOME"] = str((repo.parent / "gradle-home-client").resolve())
    server_argv = [
        str(root_wrapper),
        "--no-daemon",
        "--console=plain",
        ":mmo-bootstrap:runServer",
    ]
    if require_primary_input:
        server_argv.append("-PphysicalPrimaryInputAcceptance=true")
    client_argv = [
        str(client_wrapper),
        "--no-daemon",
        "--console=plain",
        "runClientGameTest",
    ]
    if require_primary_input:
        client_argv.append("-PphysicalPrimaryInputAcceptance=true")

    def read_log(path: Path) -> str:
        try:
            return path.read_text(encoding="utf-8", errors="replace")
        except FileNotFoundError:
            return ""

    def kill_tree(process: subprocess.Popen[str] | None) -> None:
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
    started = time.monotonic()
    player_name = None
    try:
        with paper_log.open("w", encoding="utf-8") as paper_out:
            server = subprocess.Popen(
                server_argv,
                cwd=repo,
                env=server_env,
                stdin=subprocess.PIPE,
                stdout=paper_out,
                stderr=subprocess.STDOUT,
                text=True,
            )
            deadline = time.monotonic() + 240
            while time.monotonic() < deadline:
                if "Done (" in read_log(paper_log):
                    break
                if server.poll() is not None:
                    raise HarnessError(
                        "CLIENT_ACCEPTANCE_PAPER_EXITED", f"exit={server.returncode}"
                    )
                time.sleep(1)
            else:
                raise HarnessError(
                    "CLIENT_ACCEPTANCE_PAPER_TIMEOUT", "Paper did not become ready"
                )

            with client_log.open("w", encoding="utf-8") as client_out:
                client = subprocess.Popen(
                    client_argv,
                    cwd=client_dir,
                    env=client_env,
                    stdout=client_out,
                    stderr=subprocess.STDOUT,
                    text=True,
                )
                join_deadline = time.monotonic() + 300
                while time.monotonic() < join_deadline:
                    paper_text = read_log(paper_log)
                    match = re.search(
                        r"([A-Za-z0-9_]{1,16}) joined the game", paper_text
                    )
                    if match is not None:
                        player_name = match.group(1)
                        break
                    if client.poll() is not None:
                        raise HarnessError(
                            "CLIENT_ACCEPTANCE_CLIENT_EXITED", f"exit={client.returncode}"
                        )
                    time.sleep(0.5)
                if player_name is None:
                    raise HarnessError(
                        "CLIENT_ACCEPTANCE_JOIN_TIMEOUT", "client did not join Paper"
                    )
                if server.stdin is None:
                    raise HarnessError(
                        "CLIENT_ACCEPTANCE_NO_CONSOLE", "Paper stdin unavailable"
                    )
                server.stdin.write(f"op {player_name}\n")
                server.stdin.write(f"experience set {player_name} 7 levels\n")
                server.stdin.flush()

                marker_deadline = time.monotonic() + 120
                while time.monotonic() < marker_deadline:
                    client_text = read_log(client_log)
                    if "PHYSICAL_AUTHORITY_STATUS_COMMAND_SENT_CLIENT" in client_text:
                        break
                    if client.poll() is not None:
                        raise HarnessError(
                            "CLIENT_ACCEPTANCE_CLIENT_EXITED", f"exit={client.returncode}"
                        )
                    time.sleep(0.5)
                else:
                    raise HarnessError(
                        "CLIENT_ACCEPTANCE_COMMAND_TIMEOUT", "client status marker missing"
                    )

                receipt_deadline = time.monotonic() + 20
                receipt = False
                while time.monotonic() < receipt_deadline:
                    paper_text = read_log(paper_log)
                    if "/mmo physical status" in paper_text:
                        receipt = True
                        break
                    time.sleep(0.5)
                if not receipt:
                    raise HarnessError(
                        "CLIENT_ACCEPTANCE_COMMAND_NOT_LOGGED",
                        "Paper did not log status command",
                    )

                server.stdin.write("stop\n")
                server.stdin.flush()
                try:
                    server_rc = server.wait(timeout=60)
                except subprocess.TimeoutExpired:
                    raise HarnessError(
                        "CLIENT_ACCEPTANCE_PAPER_STOP_TIMEOUT", "Paper did not stop"
                    )
                try:
                    client_rc = client.wait(timeout=60)
                except subprocess.TimeoutExpired:
                    raise HarnessError(
                        "CLIENT_ACCEPTANCE_CLIENT_STOP_TIMEOUT",
                        "client did not finish after disconnect",
                    )

        client_text = read_log(client_log)
        paper_text = read_log(paper_log)
        checks = {
            "server_handshake_client": "PHYSICAL_AUTHORITY_SERVER_HANDSHAKE_CLIENT"
            in client_text,
            "status_command_sent_client": "PHYSICAL_AUTHORITY_STATUS_COMMAND_SENT_CLIENT"
            in client_text,
            "status_command_logged_server": "/mmo physical status" in paper_text,
            "client_exit_zero": client_rc == 0,
            "server_exit_zero": server_rc == 0,
        }
        if require_primary_input:
            checks.update(
                {
                    "primary_stage_persisted_server": "PHYSICAL_AUTHORITY_PRIMARY_STAGE_PERSISTED_SERVER"
                    in paper_text,
                    "primary_stage_projected_server": "PHYSICAL_AUTHORITY_PRIMARY_STAGE_PROJECTED_SERVER"
                    in paper_text,
                    "primary_projection_ready_client": "PHYSICAL_AUTHORITY_PRIMARY_PROJECTION_READY_CLIENT"
                    in client_text,
                    "primary_mouse_sent_client": "PHYSICAL_AUTHORITY_PRIMARY_MOUSE_SENT_CLIENT"
                    in client_text,
                    "primary_intent_server": "PHYSICAL_AUTHORITY_PRIMARY_INTENT_SERVER"
                    in paper_text,
                    "primary_routed_server": "PHYSICAL_AUTHORITY_PRIMARY_ROUTED_SERVER"
                    in paper_text,
                }
            )
        passed = all(checks.values())
        return (
            0 if passed else 1,
            "PHYSICAL_CLIENT_INGRESS_PASS\n"
            if passed
            else "PHYSICAL_CLIENT_INGRESS_FAIL\n",
            "",
            {
                "action_status": "PASS" if passed else "FAIL",
                "fixed_command_id": (
                    "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_INPUT"
                    if require_primary_input
                    else "PHYSICAL_CLIENT_ACCEPTANCE_INGRESS"
                ),
                "server_argv": server_argv,
                "client_argv": client_argv,
                "player_name": player_name,
                "checks": checks,
                "client_wrapper_sha256": sha256_file(client_wrapper),
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


def action_client_acceptance_primary_input(
    repo: Path, result_dir: Path, manifest: dict[str, Any]
) -> tuple[int, str, str, dict[str, Any]]:
    return action_client_acceptance_ingress(
        repo, result_dir, manifest, require_primary_input=True
    )


ActionHandler = Callable[[Path, Path, dict[str, Any]], tuple[int, str, str, dict[str, Any]]]


@dataclass(frozen=True)
class ActionSpec:
    handler: ActionHandler
    identity: str


def action_canary(repo: Path, result_dir: Path, manifest: dict[str, Any]) -> tuple[int, str, str, dict[str, Any]]:
    required = ["settings.gradle.kts", "build.gradle.kts", "gradlew.bat"]
    missing = [p for p in required if not (repo / p).is_file()]
    settings = (repo / "settings.gradle.kts").read_text(encoding="utf-8", errors="replace") if (repo / "settings.gradle.kts").is_file() else ""
    project_ok = 'rootProject.name = "mmo-platform"' in settings
    status = "PASS" if not missing and project_ok else "FAIL"
    payload = {
        "status": status,
        "missing": missing,
        "mmo_platform_detected": project_ok,
        "head": git(repo, "rev-parse", "HEAD").stdout.strip(),
    }
    write_json(result_dir / "canary.json", payload)
    return (0 if status == "PASS" else 1, f"MMORPG_HARNESS_CANARY {status}\n", "", payload)


def action_gradle(repo: Path, result_dir: Path, manifest: dict[str, Any], argv_tail: tuple[str, ...], timeout: int, identity: str) -> tuple[int, str, str, dict[str, Any]]:
    argv = [gradle_wrapper(repo), "--no-daemon", "--console=plain", *argv_tail]
    started = time.monotonic()
    cp = run_action(argv, cwd=repo, timeout=timeout, env=gradle_env(repo))
    elapsed = round(time.monotonic() - started, 3)
    return cp.returncode, cp.stdout, cp.stderr, {
        "action_status": "PASS" if cp.returncode == 0 else "FAIL",
        "fixed_command_id": identity,
        "argv": argv,
        "exit_code": cp.returncode,
        "duration_seconds": elapsed,
    }


def make_gradle_action(argv_tail: tuple[str, ...], timeout: int, identity: str) -> ActionHandler:
    def handler(repo: Path, result_dir: Path, manifest: dict[str, Any]) -> tuple[int, str, str, dict[str, Any]]:
        return action_gradle(repo, result_dir, manifest, argv_tail, timeout, identity)
    return handler


def action_bootstrap_combat_acceptance(
    repo: Path, result_dir: Path, manifest: dict[str, Any]
) -> tuple[int, str, str, dict[str, Any]]:
    run_dir = repo / "mmo-bootstrap" / "run"
    run_dir.mkdir(parents=True, exist_ok=True)
    (run_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    return action_gradle(
        repo,
        result_dir,
        manifest,
        (":mmo-bootstrap:runServer", "-PcombatAcceptance=true"),
        600,
        "BOOTSTRAP_COMBAT_ACCEPTANCE",
    )


def action_content_validate_fixture(repo: Path, result_dir: Path, manifest: dict[str, Any]) -> tuple[int, str, str, dict[str, Any]]:
    fixture = (repo / "example-content" / "milestone-1").resolve()
    if not fixture.is_dir():
        raise HarnessError("CONTENT_FIXTURE_MISSING", str(fixture))
    fixed_arg = f"validate {fixture}"
    return action_gradle(
        repo,
        result_dir,
        manifest,
        (":mmo-content:run", f"--args={fixed_arg}"),
        900,
        "CONTENT_VALIDATE_MILESTONE1",
    )


ACTION_SPECS: dict[str, ActionSpec] = {
    "HARNESS_CANARY_V1": ActionSpec(action_canary, "HARNESS_CANARY_V1"),
    "MMO_GRADLE_VERIFY_V1": ActionSpec(make_gradle_action(("clean", "build"), 1800, "GRADLE_CLEAN_BUILD"), "GRADLE_CLEAN_BUILD"),
    "MMO_GRADLE_TEST_ALL_V1": ActionSpec(make_gradle_action(("test",), 1800, "GRADLE_TEST_ALL"), "GRADLE_TEST_ALL"),
    "MMO_GRADLE_TEST_COMBAT_V1": ActionSpec(make_gradle_action((":mmo-combat:test",), 900, "GRADLE_TEST_COMBAT"), "GRADLE_TEST_COMBAT"),
    "MMO_GRADLE_TEST_SCENES_V1": ActionSpec(make_gradle_action((":mmo-scenes:test",), 900, "GRADLE_TEST_SCENES"), "GRADLE_TEST_SCENES"),
    "MMO_GRADLE_TEST_PERSISTENCE_V1": ActionSpec(make_gradle_action((":mmo-persistence:test",), 1200, "GRADLE_TEST_PERSISTENCE"), "GRADLE_TEST_PERSISTENCE"),
    "MMO_GRADLE_TEST_CONTENT_V1": ActionSpec(make_gradle_action((":mmo-content:test",), 900, "GRADLE_TEST_CONTENT"), "GRADLE_TEST_CONTENT"),
    "MMO_GRADLE_TEST_ITEMS_V1": ActionSpec(make_gradle_action((":mmo-items:test",), 900, "GRADLE_TEST_ITEMS"), "GRADLE_TEST_ITEMS"),
    "MMO_GRADLE_TEST_QUESTS_V1": ActionSpec(make_gradle_action((":mmo-quests:test",), 900, "GRADLE_TEST_QUESTS"), "GRADLE_TEST_QUESTS"),
    "MMO_GRADLE_TEST_LIFESKILLS_V1": ActionSpec(make_gradle_action((":mmo-lifeskills:test",), 900, "GRADLE_TEST_LIFESKILLS"), "GRADLE_TEST_LIFESKILLS"),
    "MMO_GRADLE_TEST_MARKET_V1": ActionSpec(make_gradle_action((":mmo-market:test",), 900, "GRADLE_TEST_MARKET"), "GRADLE_TEST_MARKET"),
    "MMO_BOOTSTRAP_SHADOWJAR_V1": ActionSpec(make_gradle_action((":mmo-bootstrap:shadowJar",), 1200, "BOOTSTRAP_SHADOWJAR"), "BOOTSTRAP_SHADOWJAR"),
    "MMO_BOOTSTRAP_SMOKE_V1": ActionSpec(make_gradle_action((":mmo-bootstrap:runServer", "-PsmokeTest=true"), 600, "BOOTSTRAP_SMOKE"), "BOOTSTRAP_SMOKE"),
    "MMO_BOOTSTRAP_COMBAT_ACCEPTANCE_V1": ActionSpec(action_bootstrap_combat_acceptance, "BOOTSTRAP_COMBAT_ACCEPTANCE"),
    "MMO_BOOTSTRAP_INVALID_CONTENT_SMOKE_V1": ActionSpec(make_gradle_action((":mmo-bootstrap:runServer", "-PsmokeTest=true", "-PsmokeInvalidContent=true"), 600, "BOOTSTRAP_INVALID_CONTENT_SMOKE"), "BOOTSTRAP_INVALID_CONTENT_SMOKE"),
    "MMO_CONTENT_VALIDATE_FIXTURE_V1": ActionSpec(action_content_validate_fixture, "CONTENT_VALIDATE_MILESTONE1"),
    "MMO_CLIENT_ACCEPTANCE_COMPILE_V1": ActionSpec(
        action_client_acceptance_compile, "PHYSICAL_CLIENT_ACCEPTANCE_COMPILE"
    ),
    "MMO_CLIENT_ACCEPTANCE_INGRESS_V1": ActionSpec(
        action_client_acceptance_ingress, "PHYSICAL_CLIENT_ACCEPTANCE_INGRESS"
    ),
    "MMO_CLIENT_ACCEPTANCE_PRIMARY_INPUT_V1": ActionSpec(
        action_client_acceptance_primary_input, "PHYSICAL_CLIENT_ACCEPTANCE_PRIMARY_INPUT"
    ),
}


def existing_remote_result(repo: Path, branch: str, task_id: str) -> str | None:
    path = f"{result_dir_rel(task_id)}/result.json"
    if git(repo, "cat-file", "-e", f"{REMOTE}/{branch}:{path}", check=False).returncode != 0:
        return None
    cp = git(repo, "log", "-1", "--format=%H", f"{REMOTE}/{branch}", "--", path, check=False)
    return cp.stdout.strip() or "UNKNOWN"


def retry_unpushed_result(repo: Path, branch: str, task_id: str, base_commit: str) -> bool:
    local_ref = f"refs/heads/{branch}"
    remote_ref = f"refs/remotes/{REMOTE}/{branch}"
    if git(repo, "show-ref", "--verify", "--quiet", local_ref, check=False).returncode != 0:
        return False
    if git(repo, "show-ref", "--verify", "--quiet", remote_ref, check=False).returncode != 0:
        return False
    local_sha = git(repo, "rev-parse", branch).stdout.strip()
    remote_sha = git(repo, "rev-parse", f"{REMOTE}/{branch}").stdout.strip()
    if local_sha == remote_sha:
        return False
    parent = git(repo, "rev-parse", f"{local_sha}^", check=False)
    subject = git(repo, "show", "-s", "--format=%s", local_sha, check=False).stdout.strip()
    result_path = f"{result_dir_rel(task_id)}/result.json"
    has_result = git(repo, "cat-file", "-e", f"{local_sha}:{result_path}", check=False).returncode == 0
    if parent.returncode != 0 or parent.stdout.strip() != base_commit or not has_result or not subject.startswith(f"harness-result({task_id}):"):
        raise HarnessError("LOCAL_BRANCH_DIVERGED", json.dumps({"branch": branch, "local": local_sha, "remote": remote_sha}))
    cp = git(repo, "push", REMOTE, f"{local_sha}:refs/heads/{branch}", check=False)
    if cp.returncode != 0:
        raise HarnessError("RESULT_PUSH_RETRY_FAILED", cp.stderr.strip() or cp.stdout.strip())
    print(f"DONE {task_id} PUSH_RETRY {local_sha}")
    return True


def execute_task(repo: Path, manifest: dict[str, Any], manifest_sha: str, control_commit: str) -> int:
    task_id, branch, base_commit, action_id = validate_manifest(manifest)
    prior = existing_remote_result(repo, branch, task_id)
    if prior:
        print(f"IDLE {task_id} ALREADY_COMPLETED {prior}")
        return 0
    if retry_unpushed_result(repo, branch, task_id, base_commit):
        return 0

    before = classify_worktree(repo)
    if not before["safe_to_continue"]:
        raise HarnessError("DIRTY_WORKTREE", json.dumps(before["substantive"]))

    switch_existing_remote_branch(repo, branch)
    actual = git(repo, "rev-parse", "HEAD").stdout.strip()
    if actual != base_commit:
        raise HarnessError("BASE_COMMIT_MISMATCH", f"expected={base_commit} actual={actual}")
    clean = classify_worktree(repo)
    if not clean["safe_to_continue"]:
        raise HarnessError("DIRTY_TASK_BRANCH", json.dumps(clean["substantive"]))

    rel = result_dir_rel(task_id)
    result_dir = repo / rel
    result_dir.mkdir(parents=True, exist_ok=True)
    write_json(result_dir / "dirty_manifest.json", {"before_task_switch": before, "task_branch": clean})
    write_json(result_dir / "environment.json", environment_fingerprint(repo))

    started_utc = utc_now()
    started_mono = time.monotonic()
    execution_status = "PASS"
    blocker_code: str | None = None
    action_record: dict[str, Any] = {}
    stdout = ""
    stderr = ""
    exit_code = 1
    try:
        exit_code, stdout, stderr, action_record = ACTION_SPECS[action_id].handler(repo, result_dir, manifest)
        if exit_code != 0:
            execution_status = "FAIL"
            blocker_code = "ACTION_FAILED"
    except HarnessError as exc:
        execution_status = "FAIL"
        blocker_code = exc.code
        stderr = f"{exc.code}: {exc.message}\n"
        action_record = {"harness_error": exc.code}
        exit_code = 1
    except Exception as exc:
        execution_status = "FAIL"
        blocker_code = "ACTION_INTERNAL_ERROR"
        stderr = f"{type(exc).__name__}: {exc}\n"
        action_record = {"exception_type": type(exc).__name__}
        exit_code = 1

    (result_dir / "stdout.log").write_text(stdout, encoding="utf-8")
    (result_dir / "stderr.log").write_text(stderr, encoding="utf-8")

    allowed = expected_writes(task_id)
    after_action = classify_worktree(repo)
    unauthorized = [e for e in after_action["substantive"] if not path_allowed(e["path"], allowed)]
    if unauthorized:
        execution_status = "FAIL"
        blocker_code = "UNAUTHORIZED_WRITE"

    result = {
        "protocol_version": PROTOCOL_VERSION,
        "task_id": task_id,
        "execution_status": execution_status,
        "blocker_code": blocker_code,
        "control_branch": CONTROL_BRANCH,
        "control_commit": control_commit,
        "task_branch": branch,
        "task_manifest_sha256": manifest_sha,
        "base_commit_expected": base_commit,
        "starting_commit_actual": actual,
        "ending_commit_before_result_commit": git(repo, "rev-parse", "HEAD").stdout.strip(),
        "action_id": action_id,
        "action_identity": ACTION_SPECS[action_id].identity,
        "action_exit_code": exit_code,
        "action_record": action_record,
        "allowed_writes": list(allowed),
        "unauthorized_writes": unauthorized,
        "started_at_utc": started_utc,
        "finished_at_utc": utc_now(),
        "duration_seconds": round(time.monotonic() - started_mono, 3),
    }
    write_json(result_dir / "result.json", result)

    final = classify_worktree(repo)
    unauthorized = [e for e in final["substantive"] if not path_allowed(e["path"], allowed)]
    if unauthorized:
        result["execution_status"] = "FAIL"
        result["blocker_code"] = "UNAUTHORIZED_WRITE"
        result["unauthorized_writes"] = unauthorized
        write_json(result_dir / "result.json", result)
        final = classify_worktree(repo)

    hashes: dict[str, str] = {}
    for p in sorted(result_dir.rglob("*")):
        if p.is_file() and p.name != "hashes.json":
            hashes[p.relative_to(repo).as_posix()] = sha256_file(p)
    write_json(result_dir / "hashes.json", hashes)
    final = classify_worktree(repo)

    stage_evidence_files(repo, result_dir, allowed)
    staged = [p for p in git(repo, "diff", "--cached", "--name-only").stdout.splitlines() if p]
    bad = [p for p in staged if not path_allowed(p, allowed)]
    if bad:
        git(repo, "restore", "--staged", ".", check=False)
        raise HarnessError("UNAUTHORIZED_STAGE", json.dumps(bad))

    status = result["execution_status"]
    git(repo, "commit", "-m", f"harness-result({task_id}): {status}")
    result_commit = git(repo, "rev-parse", "HEAD").stdout.strip()
    pushed = git(repo, "push", REMOTE, branch, check=False)
    if pushed.returncode != 0:
        print(json.dumps({"status": "BLOCKED", "blocker_code": "PUSH_FAILED", "task_id": task_id, "local_result_commit": result_commit}, indent=2))
        return 22
    print(f"DONE {task_id} {status} {result_commit}")
    return 0 if status == "PASS" else 10


def command_status(repo: Path) -> int:
    git(repo, "fetch", REMOTE, "--prune")
    active, active_sha = load_remote_json(repo, ACTIVE_PATH)
    control_commit = git(repo, "rev-parse", f"{REMOTE}/{CONTROL_BRANCH}").stdout.strip()
    print(json.dumps({
        "protocol_version": PROTOCOL_VERSION,
        "control_branch": CONTROL_BRANCH,
        "control_commit": control_commit,
        "active_sha256": active_sha,
        "remote_active": active,
        "local_branch": git(repo, "rev-parse", "--abbrev-ref", "HEAD").stdout.strip(),
        "local_head": git(repo, "rev-parse", "HEAD").stdout.strip(),
        "worktree": classify_worktree(repo),
        "allowed_actions": sorted(ACTION_SPECS),
    }, indent=2))
    return 0


def command_run(repo: Path) -> int:
    git(repo, "fetch", REMOTE, "--prune")
    active, _ = load_remote_json(repo, ACTIVE_PATH)
    if set(active) != {"protocol_version", "state", "task_path"}:
        raise HarnessError("ACTIVE_TASK_SCHEMA_INVALID", json.dumps(sorted(active)))
    if type(active.get("protocol_version")) is not int or active["protocol_version"] != PROTOCOL_VERSION:
        raise HarnessError("PROTOCOL_VERSION_MISMATCH", repr(active.get("protocol_version")))
    state = active.get("state")
    if state in {"IDLE", "PAUSED"}:
        if active.get("task_path") not in (None, ""):
            raise HarnessError("INACTIVE_TASK_PATH_MUST_BE_NULL", repr(active.get("task_path")))
        print(f"IDLE {state}")
        return 0
    if state != "ACTIVE":
        raise HarnessError("INVALID_ACTIVE_STATE", repr(state))
    task_path = active.get("task_path")
    if not isinstance(task_path, str) or not task_path.startswith(TASK_PREFIX) or not task_path.endswith(".json") or ".." in Path(task_path).parts:
        raise HarnessError("ACTIVE_TASK_PATH_INVALID", repr(task_path))
    manifest, manifest_sha = load_remote_json(repo, task_path)
    manifest_task_id = manifest.get("task_id")
    if not isinstance(manifest_task_id, str) or task_path != f"{TASK_PREFIX}{manifest_task_id}.json":
        raise HarnessError("ACTIVE_TASK_MANIFEST_PATH_MISMATCH", repr({"path": task_path, "task_id": manifest_task_id}))
    control_commit = git(repo, "rev-parse", f"{REMOTE}/{CONTROL_BRANCH}").stdout.strip()
    return execute_task(repo, manifest, manifest_sha, control_commit)


def main() -> int:
    try:
        repo = repo_root()
        command = sys.argv[1] if len(sys.argv) > 1 else "run"
        if len(sys.argv) > 2:
            raise HarnessError("RUNNER_ARGV_FORBIDDEN", repr(sys.argv[2:]))
        if command == "run":
            return command_run(repo)
        if command == "status":
            return command_status(repo)
        print("usage: runner.py [run|status]", file=sys.stderr)
        return 2
    except HarnessError as exc:
        print(json.dumps({"status": "BLOCKED", "blocker_code": exc.code, "message": exc.message}, indent=2))
        return 20
    except Exception as exc:
        print(json.dumps({"status": "BLOCKED", "blocker_code": "HARNESS_INTERNAL_ERROR", "message": f"{type(exc).__name__}: {exc}"}, indent=2))
        return 99


if __name__ == "__main__":
    raise SystemExit(main())
