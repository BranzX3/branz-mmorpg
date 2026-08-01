# ADR 0043: Death Pouch saga planning

- Status: Accepted
- Date: 2026-08-01
- Owners: World Loop and Economy

## Context

Milestone 7 requires open-world PvE death to move 10% of the current carried wallet into a separate
owner-only pouch, while boss-suppressed and PvP deaths lose nothing. Wallet value is externally
owned, so the server needs stable pouch/debit identities before it can build a restart-safe saga.
Repeated deaths must never merge or calculate from the pre-death balance.

## Decision

`DeathPouchEngine` is a pure planner. It receives one immutable death event UUID, owner, resolved
valid world position, death profile, carried-wallet amount and creation timestamp. Open-world PvE
uses integer floor division by ten. Amounts below ten currency units create no pouch. Boss profiles
that suppress loss, duels and arenas return explicit suppression reasons.

Each planned pouch derives its pouch UUID and wallet-debit operation UUID from the death event UUID.
The draft expires exactly seven real-time days after creation. A different death event always has a
different identity, so later deaths calculate against the caller's already reduced carried balance
and cannot merge with an older pouch.

## Consequences

- retrying the same death input produces the same pouch/debit identities and amount;
- bank and Market Balance remain outside the planner because only carried wallet is supplied;
- no wallet provider call or spendable world entity occurs in the pure layer;
- the next slice must persist a non-spendable debit intention before contacting the wallet provider.

## Failure and recovery

Negative wallet amounts, non-finite coordinates and invalid timestamps fail before a draft exists.
Suppressed profiles and zero calculated loss are explicit successful decisions with no value effect.
Persistence and wallet ambiguity remain fail-closed responsibilities of the saga adapter.

## Migration impact

None for the pure planner. Durable pouch state and recovery indexes require the next forward-only
migration.
