# ADR 0044: Durable Death Pouch saga ledger

- Status: Accepted
- Date: 2026-08-01
- Owners: Persistence and Economy

## Context

ADR 0043 freezes deterministic pouch and wallet operation identities, but an external wallet debit
cannot be atomically committed in the same PostgreSQL transaction. A crash must not expose value
before debit confirmation, lose a confirmed debit, credit recovery twice or resurrect an expired
pouch.

## Decision

Forward migration V0012 creates one `death_pouch` row per pouch UUID and unique death event. Identity
columns store owner, amount, debit/credit operation UUIDs, resolved world position and real-time
creation/expiry timestamps. Mutable state is journaled and optimistic:

`PENDING_DEBIT → ACTIVE → RECOVERING → RECOVERED`

`PENDING_DEBIT` and `ACTIVE` may instead advance to terminal `EXPIRED`. Exact same-state checkpoint
updates are allowed; backward or cross-branch transitions fail. New rows must start
`PENDING_DEBIT`, which is intentionally absent from active owner queries and therefore cannot render
or pay out before the wallet confirms its debit.

Recovery lookup returns `PENDING_DEBIT` and `RECOVERING` sagas. Active owner lookup returns only
spendable pouches. Expiry lookup returns due pending/active rows in stable order. Every commit shares
the system transaction journal and appends a `DEATH_POUCH` audit record.

## Consequences

- wallet debit and recovery adapters can retry stable external operation UUIDs;
- ambiguous debit never creates a visible pouch;
- ambiguous recovery remains non-interactable until credit acknowledgement;
- repeated deaths cannot merge because `death_id` and pouch UUID are unique;
- V0012 contains no world entity state; rendering remains a live adapter concern.

## Failure and recovery

Database failure rolls back journal, pouch mutation and audit together. Exact transaction replay
returns current durable truth. Stale versions, changed identities, duplicate death events and illegal
transitions commit nothing. Expiry is terminal and cannot become active or recovered later.

## Migration impact

Forward-only V0012 adds the Death Pouch enum, table and owner/recovery/expiry partial indexes. No
existing data is rewritten.
