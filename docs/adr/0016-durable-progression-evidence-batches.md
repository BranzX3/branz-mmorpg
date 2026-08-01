# ADR 0016: Durable progression evidence batches

- Status: Accepted
- Date: 2026-08-01
- Owners: Progression, Persistence and Bootstrap

## Context

ADR 0015 defines a pure deterministic resolver, but a live server must also preserve its novelty,
daily-curve and idempotency context across reconnects and restarts. Raw combat callbacks cannot
hold durable truth, and retrying a timed-out write must never advance Mastery twice.

## Decision

Migration V0005 creates an append-only `combat_progression_evidence` journal and the projected
`character_progression_track` state. One repository transaction accepts one to 256 ordered
candidates belonging to a single character, obtains a PostgreSQL transaction advisory lock for
that character, reconstructs resolver context from committed rows, and atomically writes all
decisions and track updates.

The evidence UUID is authoritative idempotency identity. Exact immutable retries return the stored
decision as a replay. A UUID collision with different input rejects the entire batch. Suppressed
decisions are also journaled so audit and anti-abuse systems can distinguish a rejected attempt from
an absent event.

Player Session owns live serialization. After a successful repository commit it reloads the
character snapshot, including qualitative progression tracks, before later queued mutations run.
Normal player status displays only readiness bands; exact evidence and factors remain restricted to
the environment-gated development tools.

## Consequences

- reconnect and database restart preserve progression and resolver context;
- concurrent or retried writers cannot silently double-award one evidence UUID;
- a failed multi-candidate batch exposes no partial track advancement;
- live combat may emit bounded batches without owning SQL or duplicating progression formulas.

## Failure and recovery

Database failure, invalid batch ownership, invalid persisted state or UUID conflict rolls back the
transaction. The active session retains its prior snapshot and receives a stable error. A successful
retry reloads database truth. No transient combat object is reconstructed from the evidence journal.

## Migration impact

Forward migration V0005 adds `character_progression_track`, `combat_progression_evidence` and
indexes for character-time and accepted novelty-window queries. No content-schema or configuration
change is required.
