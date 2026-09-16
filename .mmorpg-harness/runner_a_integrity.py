#!/usr/bin/env python3
"""Read-only Section A negative-case persistence integrity extension."""
from __future__ import annotations

import hashlib
import json
import socket
import struct
from pathlib import Path
from typing import Any

PG_USER = "postgres"
PG_DATABASE = "postgres"
EXPECTED_NEGATIVE_ROWS = 36
TRAINING_SWORD = "weapon.training_sword"
MAIN_HAND_TYPE = "NATIVE_EQUIPPED"
MAIN_HAND_REF = "MAIN_HAND"
INVENTORY_TYPE = "CHARACTER_INVENTORY"

# Fixed reviewed query only. No task input is interpolated into SQL.
ITEM_SNAPSHOT_SQL = (
    "SELECT item_uuid::text AS item_uuid, definition_id AS definition_id, "
    "COALESCE(owner_character_id::text,'') AS owner_character_id, "
    "location_type AS location_type, COALESCE(location_ref,'') AS location_ref, "
    "payload::text AS payload, content_version AS content_version, "
    "version::text AS version, last_transaction_id::text AS last_transaction_id "
    "FROM item_instance ORDER BY item_uuid"
)

_CAPTURE: dict[str, list[dict[str, str]]] = {}
_CONTEXT: dict[str, Any] = {}


def _recv_exact(sock: socket.socket, size: int) -> bytes:
    chunks: list[bytes] = []
    remaining = size
    while remaining:
        chunk = sock.recv(remaining)
        if not chunk:
            raise RuntimeError("PostgreSQL connection closed unexpectedly")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def _recv_message(sock: socket.socket) -> tuple[bytes, bytes]:
    kind = _recv_exact(sock, 1)
    length = struct.unpack("!I", _recv_exact(sock, 4))[0]
    if length < 4:
        raise RuntimeError(f"Invalid PostgreSQL message length: {length}")
    return kind, _recv_exact(sock, length - 4)


def _error_text(payload: bytes) -> str:
    fields = payload.split(b"\x00")
    rendered: list[str] = []
    for field in fields:
        if len(field) > 1:
            rendered.append(field[1:].decode("utf-8", errors="replace"))
    return " | ".join(rendered) or "unknown PostgreSQL error"


def _startup(sock: socket.socket) -> None:
    params = (
        b"user\x00" + PG_USER.encode() + b"\x00"
        + b"database\x00" + PG_DATABASE.encode() + b"\x00"
        + b"client_encoding\x00UTF8\x00"
        + b"\x00"
    )
    packet = struct.pack("!II", 8 + len(params), 196608) + params
    sock.sendall(packet)
    while True:
        kind, payload = _recv_message(sock)
        if kind == b"R":
            code = struct.unpack("!I", payload[:4])[0]
            if code != 0:
                raise RuntimeError(
                    f"Unexpected PostgreSQL authentication request {code}; fixed embedded trust auth required"
                )
        elif kind == b"E":
            raise RuntimeError(_error_text(payload))
        elif kind == b"Z":
            return


def _read_cstring(payload: bytes, offset: int) -> tuple[str, int]:
    end = payload.find(b"\x00", offset)
    if end < 0:
        raise RuntimeError("Malformed PostgreSQL cstring")
    return payload[offset:end].decode("utf-8", errors="strict"), end + 1


def _query_rows(port: int) -> list[dict[str, str]]:
    with socket.create_connection(("127.0.0.1", port), timeout=5.0) as sock:
        sock.settimeout(10.0)
        _startup(sock)
        encoded = ITEM_SNAPSHOT_SQL.encode("utf-8")
        sock.sendall(b"Q" + struct.pack("!I", len(encoded) + 5) + encoded + b"\x00")
        columns: list[str] = []
        rows: list[dict[str, str]] = []
        while True:
            kind, payload = _recv_message(sock)
            if kind == b"T":
                count = struct.unpack("!H", payload[:2])[0]
                offset = 2
                columns = []
                for _ in range(count):
                    name, offset = _read_cstring(payload, offset)
                    columns.append(name)
                    # table oid, attr, type oid, type size, modifier, format
                    offset += 18
            elif kind == b"D":
                if not columns:
                    raise RuntimeError("DataRow received before RowDescription")
                count = struct.unpack("!H", payload[:2])[0]
                if count != len(columns):
                    raise RuntimeError("PostgreSQL DataRow column count mismatch")
                offset = 2
                values: list[str] = []
                for _ in range(count):
                    length = struct.unpack("!i", payload[offset:offset + 4])[0]
                    offset += 4
                    if length == -1:
                        values.append("")
                    else:
                        raw = payload[offset:offset + length]
                        offset += length
                        values.append(raw.decode("utf-8", errors="strict"))
                rows.append(dict(zip(columns, values)))
            elif kind == b"E":
                raise RuntimeError(_error_text(payload))
            elif kind == b"Z":
                return rows
            # C (CommandComplete), S/N/K and other status messages are intentionally ignored.


def _embedded_port(data_dir: Path) -> int:
    pid_file = data_dir / "postmaster.pid"
    lines = pid_file.read_text(encoding="utf-8", errors="strict").splitlines()
    if len(lines) < 4:
        raise RuntimeError(f"Malformed PostgreSQL postmaster.pid: {pid_file}")
    port = int(lines[3])
    if not 1 <= port <= 65535:
        raise RuntimeError(f"Invalid embedded PostgreSQL port: {port}")
    return port


def snapshot_items(data_dir: Path) -> list[dict[str, str]]:
    rows = _query_rows(_embedded_port(data_dir))
    return sorted(rows, key=lambda row: row["item_uuid"])


def snapshot_digest(rows: list[dict[str, str]]) -> str:
    raw = json.dumps(rows, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


def _uuid_map(rows: list[dict[str, str]]) -> dict[str, dict[str, str]]:
    return {row["item_uuid"]: row for row in rows}


def _main_hand_rows(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    return [
        row
        for row in rows
        if row["definition_id"] == TRAINING_SWORD
        and row["location_type"] == MAIN_HAND_TYPE
        and row["location_ref"] == MAIN_HAND_REF
    ]


def evaluate_negative_integrity(
    baseline: list[dict[str, str]],
    failed: list[dict[str, str]],
    restarted: list[dict[str, str]],
) -> dict[str, bool]:
    baseline_ids = [row["item_uuid"] for row in baseline]
    failed_ids = [row["item_uuid"] for row in failed]
    restarted_ids = [row["item_uuid"] for row in restarted]
    baseline_map = _uuid_map(baseline)
    failed_map = _uuid_map(failed)
    restarted_map = _uuid_map(restarted)

    baseline_main = _main_hand_rows(baseline)
    failed_main = _main_hand_rows(failed)
    restarted_main = _main_hand_rows(restarted)

    baseline_training = [row for row in baseline if row["definition_id"] == TRAINING_SWORD]
    failed_training = [row for row in failed if row["definition_id"] == TRAINING_SWORD]
    restarted_training = [row for row in restarted if row["definition_id"] == TRAINING_SWORD]

    exact_rows_stable = baseline == failed == restarted
    id_set_stable = set(baseline_ids) == set(failed_ids) == set(restarted_ids)
    ids_unique = (
        len(baseline_ids) == len(set(baseline_ids))
        and len(failed_ids) == len(set(failed_ids))
        and len(restarted_ids) == len(set(restarted_ids))
    )
    main_uuid = baseline_main[0]["item_uuid"] if len(baseline_main) == 1 else None
    main_stable = bool(
        main_uuid
        and len(failed_main) == 1
        and len(restarted_main) == 1
        and failed_main[0]["item_uuid"] == main_uuid
        and restarted_main[0]["item_uuid"] == main_uuid
    )

    locations_stable = bool(
        id_set_stable
        and all(
            baseline_map[item_id]["location_type"] == failed_map[item_id]["location_type"]
            == restarted_map[item_id]["location_type"]
            and baseline_map[item_id]["location_ref"] == failed_map[item_id]["location_ref"]
            == restarted_map[item_id]["location_ref"]
            for item_id in baseline_map
        )
    )
    version_content_payload_stable = bool(
        id_set_stable
        and all(
            baseline_map[item_id]["version"] == failed_map[item_id]["version"]
            == restarted_map[item_id]["version"]
            and baseline_map[item_id]["content_version"] == failed_map[item_id]["content_version"]
            == restarted_map[item_id]["content_version"]
            and baseline_map[item_id]["payload"] == failed_map[item_id]["payload"]
            == restarted_map[item_id]["payload"]
            and baseline_map[item_id]["last_transaction_id"]
            == failed_map[item_id]["last_transaction_id"]
            == restarted_map[item_id]["last_transaction_id"]
            for item_id in baseline_map
        )
    )

    expected_shape = (
        len(baseline) == EXPECTED_NEGATIVE_ROWS
        and len(baseline_training) == EXPECTED_NEGATIVE_ROWS
        and len(baseline_main) == 1
        and sum(1 for row in baseline if row["location_type"] == INVENTORY_TYPE) == 35
    )

    return {
        "a_negative_integrity_expected_36_rows": expected_shape,
        "a_negative_integrity_uuid_set_stable": id_set_stable,
        "a_negative_integrity_uuid_unique": ids_unique,
        "a_negative_integrity_main_hand_uuid_stable": main_stable,
        "a_negative_integrity_no_duplicate_training_sword": (
            len(baseline_training)
            == len(failed_training)
            == len(restarted_training)
            == EXPECTED_NEGATIVE_ROWS
            and ids_unique
        ),
        "a_negative_integrity_no_row_loss_or_invention": (
            len(baseline) == len(failed) == len(restarted) == EXPECTED_NEGATIVE_ROWS
            and id_set_stable
        ),
        "a_negative_integrity_no_invented_destination": locations_stable,
        "a_negative_integrity_version_content_payload_stable": version_content_payload_stable,
        "a_negative_integrity_exact_rows_stable": exact_rows_stable,
    }


def _snapshot_key(label: str, phase: str) -> str | None:
    if label == "negative-legacy" and phase == "negative-fill":
        return "legacy_full"
    if label == "negative-target" and phase == "negative-fail":
        return "target_failed"
    if label == "negative-target-restart" and phase == "negative-fail":
        return "target_restart_failed"
    return None


def _write_snapshot(result_dir: Path, key: str, rows: list[dict[str, str]]) -> None:
    path = result_dir / f"section-a-negative-integrity-{key}.json"
    payload = {
        "kind": "SECTION_A_NEGATIVE_READ_ONLY_ITEM_SNAPSHOT",
        "query_sha256": hashlib.sha256(ITEM_SNAPSHOT_SQL.encode("utf-8")).hexdigest(),
        "row_count": len(rows),
        "rows_sha256": snapshot_digest(rows),
        "rows": rows,
    }
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def install(core: Any, runner_a: Any) -> None:
    original_server_group = runner_a._server_group
    original_client_phase = runner_a._client_phase
    original_evaluate_negative = runner_a.evaluate_negative_evidence
    original_action = core.action_client_acceptance_legacy_main_hand_migration

    def server_group(core_arg, task_repo, runtime_repo, result_dir, label, phases):
        previous = dict(_CONTEXT)
        _CONTEXT.clear()
        _CONTEXT.update(
            {
                "runtime_repo": Path(runtime_repo).resolve(),
                "result_dir": Path(result_dir).resolve(),
                "label": label,
            }
        )
        try:
            return original_server_group(
                core_arg, task_repo, runtime_repo, result_dir, label, phases
            )
        finally:
            _CONTEXT.clear()
            _CONTEXT.update(previous)

    def client_phase(
        core_arg,
        task_repo,
        client_dir,
        result_dir,
        phase,
        paper_log,
        server,
    ):
        value = original_client_phase(
            core_arg, task_repo, client_dir, result_dir, phase, paper_log, server
        )
        key = _snapshot_key(str(_CONTEXT.get("label", "")), phase)
        if key is not None:
            runtime_repo = Path(_CONTEXT["runtime_repo"]).resolve()
            expected_db = (runtime_repo / runner_a.DATABASE_RELATIVE_PATH).resolve()
            try:
                rows = snapshot_items(expected_db)
            except Exception as exc:
                raise core_arg.HarnessError(
                    "SECTION_A_NEGATIVE_INTEGRITY_SNAPSHOT_FAILED",
                    f"key={key} detail={exc}",
                ) from exc
            _CAPTURE[key] = rows
            _write_snapshot(Path(result_dir), key, rows)
        return value

    def evaluate_negative_evidence(
        negative_prep_text,
        negative_fill_text,
        negative_fail_text,
        negative_restart_text,
    ):
        checks = original_evaluate_negative(
            negative_prep_text,
            negative_fill_text,
            negative_fail_text,
            negative_restart_text,
        )
        baseline = _CAPTURE.get("legacy_full", [])
        failed = _CAPTURE.get("target_failed", [])
        restarted = _CAPTURE.get("target_restart_failed", [])
        checks.update(evaluate_negative_integrity(baseline, failed, restarted))
        return checks

    def action(repo, result_dir, manifest):
        _CAPTURE.clear()
        code, stdout, stderr, record = original_action(repo, result_dir, manifest)
        snapshots = {}
        for key in ("legacy_full", "target_failed", "target_restart_failed"):
            rows = _CAPTURE.get(key, [])
            snapshots[key] = {
                "row_count": len(rows),
                "rows_sha256": snapshot_digest(rows) if rows else None,
            }
        record["negative_integrity_snapshots"] = snapshots
        return code, stdout, stderr, record

    runner_a._server_group = server_group
    runner_a._client_phase = client_phase
    runner_a.evaluate_negative_evidence = evaluate_negative_evidence

    core.evaluate_section_a_negative_integrity = evaluate_negative_integrity
    core.SECTION_A_NEGATIVE_SNAPSHOT_QUERY_SHA256 = hashlib.sha256(
        ITEM_SNAPSHOT_SQL.encode("utf-8")
    ).hexdigest()
    core.action_client_acceptance_legacy_main_hand_migration = action
    core.ACTION_SPECS[runner_a.ACTION_ID] = core.ActionSpec(
        action,
        runner_a.ACTION_IDENTITY,
    )
