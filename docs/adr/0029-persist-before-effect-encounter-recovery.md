# ADR 0029: Persist-before-effect encounter recovery

- Status: Accepted
- Date: 2026-08-01
- Owners: World Loop, Persistence and Bootstrap

## Context

ADR 0028 makes lifecycle documents durable, but the live controller must not restore a Flask or
publish victory before its transition is committed. It must also tolerate two participant events
arriving while PostgreSQL is acknowledging the first and resume non-terminal work after the Paper
process restarts. Server-relative ticks cannot be reused across that restart.

## Decision

The live controller serializes mutations through one FIFO per encounter. Each queued function is
evaluated against the latest acknowledged runtime, encoded canonically and compare-and-set through
V0009 on an asynchronous thread. Only the main-thread success callback publishes the new phase or
starts side effects. A failed database acknowledgement retains the queue head and retries the exact
transaction operation.

The V1 JSON document includes stable encounter/definition/checkpoint IDs, phase, attempt, starting
tick, sorted participants, availability/grace, sorted processed operations, active reset operation
and reward grant. Decode reconstructs the kernel record, so all record invariants are revalidated.

Startup blocks Encounter Lab commands until all non-completed V0009 rows load. Recovery behavior is:

- `ACTIVE`: persist `RESTART_RECOVERED`, preserve defeated characters and give every other
  participant a fresh 1,200-tick disconnect grace based on the new server clock;
- `WIPE_PENDING`: persist reset begin before restoring anything;
- `RESETTING`: reconstruct all participants as pending and replay deterministic per-character Flask
  restore operations; already-restored Flask state is accepted without another write;
- `VICTORY_PENDING`: keep reset disabled and wait for reward reconciliation;
- `COMPLETED`: excluded by the repository recovery query.

Reset completion is persisted before the next attempt is announced or health is refreshed. Reward
reconciliation is also persisted before participant locks are released.

## Consequences

- simultaneous deaths cannot lose one participant update or overwrite a newer phase;
- a crash before reset persistence performs no restore, while a crash after it safely resumes;
- a crash between individual Flask acknowledgements can replay without double restoration;
- reconnect grace has deterministic semantics even though Paper server ticks restart from zero.

## Failure and recovery

Invalid JSON or conflicting recovered participant ownership fails closed and is logged. Database
failure leaves the authoritative in-memory phase unchanged and retries the same queue head. Missing
or mismatched Flask snapshots leave `RESETTING` visibly blocked. A recovered victory never falls
back into wipe/reset.

## Migration impact

No new migration. This activates the V0009 repository from ADR 0028 and continues to compose V0008
per-character Flask commits.
