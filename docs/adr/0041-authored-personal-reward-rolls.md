# ADR 0041: Authored deterministic personal reward rolls

- Status: Accepted
- Date: 2026-08-01
- Owners: Content, World Loop and Bootstrap

## Context

ADR 0040 stores durable personal grants, but a frozen grant still needs a validated reward pool,
an order-independent roll and a canonical payload before the live boss controller can deliver any
value safely. Reward content must not reference missing or non-stackable items, and a retry must
never select a different item, quantity or lot identity.

## Decision

Encounter definitions author an eligibility profile and a weighted personal reward pool. Content
validation constrains weights, quantities, idle windows and late-join ratios, and resolves every
reward item reference. Bootstrap compilation additionally requires every referenced reward item to
be a `STACKABLE_LOT` before exposing an immutable `EncounterRewardTable`.

`PersonalRewardRollEngine` derives the weighted cursor and quantity only from the frozen per-player
roll seed. It derives the lot UUID from the grant UUID. The result therefore has no dependency on
iteration order, wall time or mutable random state.

The canonical payload codec stores grant identity, frozen eligibility evidence, optional rolled
outcome and optional delivery receipt. Payload shape must match the durable ledger state exactly:
`FROZEN` has neither outcome nor receipt, `ROLLED` has an outcome only and `DELIVERED` has both.
Decoding a durable row also verifies that all identity fields equal its indexed columns.

## Consequences

- malformed encounter reward content fails during content load;
- a grant replay produces the same item, quantity and lot UUID;
- payload/version or state-shape mismatches fail closed before delivery;
- live boss integration can persist the roll before applying the value transaction;
- authored content currently supports one weighted stackable-lot outcome per personal grant.

## Failure and recovery

An unsupported payload schema, invalid evidence, impossible state shape or durable identity mismatch
is rejected without an inventory effect. Once a `ROLLED` payload is committed, recovery uses that
stored outcome rather than recalculating it. The later delivery transaction can use its stable lot
identity to make retries idempotent.

## Migration impact

None. This slice uses the V0011 personal reward grant ledger and adds validated encounter content.
