# ADR 0028: Durable boss encounter state

- Status: Accepted
- Date: 2026-08-01
- Owners: World Loop and Persistence

## Context

The live Boss Encounter Lab can reach active, resetting, victory-pending and completed phases, but
ADR 0027 deliberately keeps those phases in process memory. Restart-safe reset and reward recovery
need an optimistic durable record that preserves the complete kernel payload and participates in the
platform transaction journal.

## Decision

V0009 adds one `boss_encounter_state` row per stable encounter UUID. The row stores the definition
ID, indexed lifecycle phase, canonical JSON payload, content version, optimistic version and last
transaction ID. Non-completed rows are covered by a partial recovery index.

All creates and replacements use system-owned transaction-journal requests with immutable operation
and idempotency IDs. Creation expects version zero; replacement compare-and-sets the current positive
version. Exact committed replay returns the current encounter row and marks the execution replayed.
A request whose identity or payload differs fails through the journal contract, while stale version
losers roll back their prepared journal entry.

Each successful state mutation appends an `ENCOUNTER` audit row in the same transaction. The
repository exposes exact-ID lookup and an ordered scan of every non-completed record for startup
reconciliation.

## Consequences

- encounter lifecycle state now has the same optimistic/journaled durability boundary as other
  authoritative values;
- startup can distinguish completed records from work requiring reset or reward reconciliation;
- side-effect adapters can persist a transition before executing Flask restore or reward grants;
- the stored JSON can evolve under an explicit schema version without widening SQL columns for each
  participant field.

## Failure and recovery

Invalid operation type, character-scoped request, stale version, changed idempotency payload or
database failure commits neither state nor audit. A callback loss is recovered by exact transaction
replay or by reloading the latest row. Invalid JSON is rejected by PostgreSQL and reported through
the stable transaction database error boundary.

## Migration impact

Forward-only V0009 adds `boss_encounter_state` and its partial recovery index. It references the
existing transaction journal and requires no backfill.
