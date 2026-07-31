# ADR-0001: Single-shard V1

Status: Accepted

## Decision

V1 runs on one Paper game server with PostgreSQL. Architecture uses stable IDs and provider boundaries but does not implement distributed character locking, cross-server inventory or dungeon server transfer.

## Consequences

- Faster delivery and simpler transaction semantics.
- One character lease still exists to protect duplicate login and future expansion.
- Multi-server support requires a later ADR and migration.
