# ADR 0046: Live owner-only Death Pouches

- Status: Accepted
- Date: 2026-08-01
- Owners: World Loop, Bootstrap and Economy

## Context

ADRs 0043-0045 provide deterministic pouch identities, a durable state ledger and a local carried
wallet, but no Paper event path creates or recovers a pouch. The live adapter must preserve
persist-before-effect ordering, avoid public map markers or stealable entities and resume ambiguous
wallet operations after restart.

## Decision

The Paper adapter observes a ready character's death. Boss-bound deaths and deaths attributed to
another player are suppressed before value mutation. An open-world death reads the durable carried
balance, plans a 10% pouch, commits `PENDING_DEBIT`, applies the stable wallet debit and only then
commits `ACTIVE`.

Active pouches are loaded per owner. Their marker is a particle packet sent only to that owner; no
world entity, public map coordinate or transferable item exists. Recovery requires the owner to be
in the same world and within four blocks. It commits `RECOVERING`, credits the stable recovery
operation and commits `RECOVERED` before reporting success.

Startup and a ten-second reconciler resume `PENDING_DEBIT` and `RECOVERING` rows and expire due
pouches. For an expired pending intention, the adapter queries the immutable wallet-operation ledger:
an unconfirmed debit expires without charging, while a confirmed debit is acknowledged and expires
without a second charge. One failed saga remains retryable without blocking unrelated pouches.

`/mmo pouch wallet`, `status` and `recover` expose the player flow. Environment-gated `fund` and
`simulate` commands use the same durable credit and death saga paths for local acceptance.

## Consequences

- open-world death and recovery are ready for local in-game testing;
- owner-only visualization reveals no pouch to other players and cannot be stolen;
- boss and player-killer deaths do not reduce the carried wallet;
- status output includes identity, amount and expiry but deliberately omits coordinates;
- explicit duel and arena runtime profiles and an external wallet adapter remain later integrations.

## Failure and recovery

A failure before debit leaves an invisible `PENDING_DEBIT` row. A failure after debit replays the
same operation and activates once. A failure after `RECOVERING` replays the same credit and reaches
`RECOVERED` once. Database query failure keeps new death processing closed until a later successful
scan; an individual saga failure stays durable and is retried without blocking the remaining scan.

## Migration impact

No new migration. The adapter composes V0012 Death Pouch state with V0013 carried-wallet operations
and the shared transaction journal/audit log.
