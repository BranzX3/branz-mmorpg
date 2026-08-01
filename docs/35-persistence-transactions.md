# Persistence and Transactions

## Database

V1 uses PostgreSQL with a connection pool and explicit SQL migrations. Repositories expose domain records and optimistic versions; gameplay modules do not write tables directly.

## Character lease

On login, the server acquires a lease keyed by character ID with server instance, session UUID, version and expiry heartbeat. A competing live lease blocks duplicate activation. Expired leases require recovery checks before reassignment.

## Write policy

### Immediate transactional

- item/lot/mount/worker ownership and location;
- currency and escrow;
- equipment and committed build changes;
- market fills;
- rewards and death pouch;
- crafting/worker reservations and outputs;
- quest branch/reward commits.

### Batched with durable evidence journal

- combat Mastery evidence;
- Body Conditioning evidence;
- lifeskill evidence;
- telemetry and non-critical presentation preferences.

Combat progression uses `combat_progression_evidence` as its durable, append-only decision journal
and `character_progression_track` as the current per-character projection. The repository accepts
one to 256 candidates for one character, obtains a transaction-scoped PostgreSQL advisory lock for
that character and resolves candidates sequentially against database truth. Track updates and all
accepted or suppressed journal rows commit atomically.

An exact retry of an existing evidence UUID returns the stored decision without another track
update. Reusing an evidence UUID with different immutable input is an idempotency conflict and
rolls back the batch. Repetition context is reconstructed from accepted matching fingerprints in
the preceding 30 minutes; per-track daily context is reconstructed from accepted awards since
00:00 UTC.

### Multi-character teaching completion

A durable teaching completion locks teacher and student with transaction-scoped advisory locks in
sorted UUID order. One transaction inserts the student's permanent Technique Knowledge, the
teacher's immutable mentorship deed, any positive Renown projection change and the exact
teaching-session binding. The session UUID and deed UUID are immutable idempotency keys: exact
retry returns stored truth, while mismatched reuse or an independently learned Technique rejects
and rolls back every new row. Both active Player Sessions reload database truth before success is
published; session replacement during the asynchronous operation never receives a stale snapshot.

### Derived/cache

- calculated stats;
- market views;
- HUD state;
- content indices.

## Transaction journal

Every value-changing operation has:

```text
transaction_id
idempotency_key
character/session context
operation type
state: PREPARED / COMMITTED / ROLLED_BACK / QUARANTINED
reserved inputs
intended outputs
content version
created/updated timestamps
```

A retry with the same idempotency key returns the prior result.

## Item versioning

Each unique item and lot row has a version. Location changes use compare-and-set. A failure to match means the operation reloads and rejects; it never overwrites a newer location.

## Pending destinations

- Pending Rewards: granted value awaiting player claim.
- Overflow Claim: operational fallback and recovery.
- Quarantine: inconsistent or unknown value requiring review.

None is used as unlimited convenient storage; retention, notifications and claim rules apply.

## Reconciliation

Startup and scheduled reconciliation verify:

- item/lot exists in one location;
- live mount entity matches active mount record;
- escrow matches active orders/services;
- reserved currency equals open buy orders/jobs;
- completed job/reward has one output grant;
- Scene previews own no persistent item;
- stale leases and sessions are closed safely.

## Migrations

Migrations are forward-only in production, idempotent where possible and rehearsal-tested against staging copies. Content migrations and SQL migrations are versioned separately but deployed in a compatibility manifest.

## Backups

- continuous WAL/archive policy appropriate to hosting;
- daily full backup;
- regular restore rehearsal;
- audit and transaction journals retained longer than ordinary telemetry;
- secrets and personal data excluded from content repositories.
