# Persistence, Transactions, Recovery and Migration

## Database

V1 uses PostgreSQL with a connection pool and explicit SQL/repository layer. Avoid storing core aggregates as opaque blobs except versioned snapshots used for recovery/debug.

Recommended isolation:

- ordinary reads: Read Committed,
- ownership and reward transactions: row locks/serializable logic as needed,
- optimistic version columns on character and item aggregates.

## Character lease

On login:

1. Acquire/update `character_session_lease` using account/character ID.
2. Reject or safely replace stale lease after configured timeout and server-instance check.
3. Generate `session_token`.
4. Load character aggregate and reconcile pending transactions/rewards.
5. Enter world only after required state is consistent.

Every async callback verifies the active session token.

## Persistence classes

### Immediate durable write

- item ownership/location,
- trade/crafting/enhancement/repair,
- reward grant and claim,
- wallet-linked mutation,
- death pouch create/recover/expire,
- quest completion and irreversible choice,
- technique/form learning,
- build commit,
- loaded crossbow persistent state.

### Batched durable write

- mastery/conditioning evidence,
- qualitative feedback cooldowns,
- analytics/telemetry,
- non-critical UI settings.

### Runtime only

- active action and buffer,
- hitboxes/projectiles that do not need crash resume,
- local Scene preview,
- transient threat.

Crash recovery resets runtime-only state safely.

## TransactionService

A transaction request contains:

```text
transaction_id
idempotency_key
type
actor_character_id
counterparty/service
expected_versions
operations
metadata/reason
```

Transaction flow:

1. Validate request and session.
2. Lock affected records in stable ID order.
3. Re-check ownership and versions.
4. Write intent/journal state.
5. Apply database mutations.
6. Write outbox events.
7. Commit.
8. Apply/reconcile Bukkit projections.
9. Mark presentation completion asynchronously.

Database truth wins over the inventory projection. Reconciliation repairs the projection after crash/desync.

## Inventory projection

- Each projected stack references `item_uuid` and a display revision.
- Inventory events validate protected/system/UI items.
- Reconciliation scans character-owned locations and expected slots.
- Duplicate projections do not create duplicate item instances; invalid copies are removed and audited.
- Missing projection is recreated from persistent state.

## Trade and wallet atomicity

External WalletProvider may not share the same database transaction. Use a saga with idempotent wallet reservations:

1. Reserve offered items and wallet amounts.
2. Persist transaction state.
3. Commit wallet transfer with provider idempotency key.
4. Commit item ownership.
5. Finalize reservations.

Compensation returns reservations if commit does not complete. Ambiguous provider response freezes the transaction for reconciliation rather than guessing.

## Reward idempotency

`reward_grant_id` has a unique constraint. Reward content and roll result are stored before delivery. Re-delivery only continues incomplete delivery steps.

## Death pouch persistence

Store:

- pouch UUID,
- owner character,
- wallet amount/reservation reference,
- world/location/fallback,
- created/expires timestamps,
- state: ACTIVE/RECOVERING/RECOVERED/EXPIRED,
- version.

Creation and wallet deduction are one saga. If wallet deduction cannot be confirmed, no spendable pouch appears.

## Scene recovery

Scene preview is not persisted. On close/crash:

- discard preview,
- clear synthetic UI items,
- restore Chronicle slot,
- set weapon/action/UI states to safe defaults,
- reconcile any transaction that reached durable commit.

## Content versioning

Persistent records store stable definition ID and relevant schema/definition revision. Content migration consists of:

- compatibility check,
- idempotent migration ID,
- dry-run report,
- batched execution,
- audit result,
- quarantine fallback.

IDs are redirected only through explicit aliases with removal version.

## Database migrations

Use ordered migration files. Production startup does not automatically run destructive migrations without deployment approval.

Rules:

- expand/contract for zero-loss changes,
- backfill before constraint tightening,
- migrations are repeat-safe where possible,
- rollback plan documented for each release,
- restore test performed on staging backup.

## Shutdown

On plugin/server shutdown:

1. Stop new logins, encounters and transactions.
2. Cancel runtime actions and scenes.
3. Allow in-flight durable transactions to finish or enter recoverable state.
4. Flush required evidence/journals.
5. Release character leases with server-instance token.
6. Emit shutdown health summary.

## Core tables

At minimum:

```text
characters
character_sessions
character_builds
character_knowledge
character_mastery
character_conditioning
items
item_rolls
item_locations
transactions
transaction_operations
outbox_events
pending_rewards
reward_grants
death_pouches
quests
quest_objectives
npc_memory
parties
encounter_records
content_migrations
audit_log
```
