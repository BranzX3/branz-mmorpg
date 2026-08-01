# ADR 0019: Durable teaching, Knowledge and Renown transaction

- Status: Accepted
- Date: 2026-08-01
- Owners: Progression, Persistence, Social and Bootstrap

## Context

A ready teaching challenge affects two characters and three durable concepts. Committing student
Knowledge separately from the teacher reward can create free rewards, missing learning or replay
duplication. Enabling learned-Technique gating without migration handling can also invalidate builds
committed before Knowledge storage existed.

## Decision

V0006 stores character Knowledge, a Renown projection, an immutable deed journal and an immutable
teaching-completion journal. Completion locks teacher and student by sorted UUID and writes the
student Technique, resolved mentorship deed, positive Renown projection change and exact session
binding in one PostgreSQL transaction.

Teaching-session and deed UUIDs are immutable idempotency keys. Exact replay returns stored rows;
mismatched reuse and already-learned input roll back. Both Player Sessions reload database truth
before the application publishes success. Build preview, commit and combat activation require each
selected Technique to appear in permanent Knowledge.

V0006 imports stable Technique, Form and Spell IDs found in pre-migration build JSON with
`LEGACY_BUILD_BACKFILL`. This grandfathering runs once during migration and cannot be triggered by a
post-migration build edit.

## Consequences

- student learning and teacher recognition cannot diverge during a successful transaction;
- a fourth identical daily deed may still teach but awards zero Renown;
- old committed builds remain valid, while new Technique selection requires learning;
- Form/Spell knowledge is persisted/backfilled here; ADR 0021 subsequently owns their authored
  acquisition source and production build gates.

## Failure and recovery

Any SQL, UUID conflict or already-learned rejection rolls back every new row. If the transaction
commits but either live session changes before callback, no stale snapshot is installed. Retrying the
same immutable input replays and reloads both current sessions. Participant advisory locks use stable
ordering to prevent cross-teaching deadlocks.

## Migration impact

Forward-only V0006 adds `character_knowledge`, `character_renown`, `renown_deed_journal` and
`teaching_completion_journal`, plus three query indexes and the one-time legacy build import. No
content-schema or configuration change is required.
