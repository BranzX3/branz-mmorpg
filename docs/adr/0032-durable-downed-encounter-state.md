# ADR 0032: Durable downed encounter state

- Status: Accepted
- Date: 2026-08-01
- Owners: Persistence, Social and Bootstrap

## Context

The live downed adapter currently keeps revive allowance, deadlines, channels and protection only in
memory. Restarting Paper can therefore erase a consumed revive or abandon a downed transition while
the owning boss encounter itself recovers durably from V0009.

## Decision

V0010 adds one `downed_encounter_state` row per boss encounter UUID. It references the owning V0009
row with cascade deletion and stores the current attempt, a recoverable flag, canonical state JSON,
content version, optimistic version and last transaction identity. The row is replaced when the
boss attempt changes instead of accumulating stale attempt rows.

Every replacement is a system transaction using `encounter.downed.state.commit`. The repository
prepares the transaction journal, compare-and-sets the row, appends a `DOWNED_ENCOUNTER` audit entry
and commits the journal in one PostgreSQL transaction. Exact request replay returns the committed
row without a second version or audit entry.

Recovery scans only rows marked recoverable in stable update/encounter order. Marking a row
non-recoverable retains audit history while excluding completed encounter state from startup work.

## Consequences

- V0010 provides the storage boundary required for persist-before-effect lethal/revive handling;
- downed state cannot exist without its owning boss record;
- optimistic conflict prevents simultaneous live transitions from silently overwriting each other;
- JSON remains an opaque persistence payload whose schema is owned by the bootstrap codec.

## Failure and recovery

A stale expected version, mismatched operation, changed idempotent replay or missing owning boss row
rolls back the row, journal and audit together. A committed request can be replayed after callback
loss and startup can enumerate every recoverable row.

## Migration impact

Forward-only V0010 adds `downed_encounter_state` and a partial recovery index. It does not alter
V0009 rows.
