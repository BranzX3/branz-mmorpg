# ADR 0001 — Single Authoritative Shard for V1

## Decision

V1 runs on one Paper shard with PostgreSQL. Modules are separated in code but not deployed as distributed services.

## Rationale

Combat, inventory, market and mount safety are easier to prove without cross-server ownership and distributed locks. Stable IDs, leases and provider interfaces leave a future migration path.

## Consequences

Capacity is bounded by the performance budget. Cross-shard market, party and travel are outside V1.
