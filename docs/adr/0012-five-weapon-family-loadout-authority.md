# ADR 0012: Five-Weapon Family and Linked Off-Hand Authority

Status: Accepted

## Context

Milestone 5 requires Greatsword, Sword and Shield, Bow, Crossbow and Staff to complete a live
encounter and survive save/reload. The existing training blade used the legacy `SWORD` family and
all guards shared one hard-coded profile. Paper projected only the authoritative main hand, so a
Shield could not retain item UUID ownership in the native off hand.

## Decision

- Add stable `GREATSWORD` and `SWORD_SHIELD` weapon-family content without renaming or reusing the
  legacy training-blade IDs.
- Compile `offhand_policy` and guard tuning through Item Engine. Greatsword requires `EMPTY` and an
  item-owned weapon guard; Sword and Shield requires an authored `shield_profile` in `OFF_HAND`.
- Resolve Scene validation and live combat readiness through the same `WeaponLoadoutPolicy`.
- Treat main/off-hand changes as one journaled `item.move.batch` transaction with optimistic item
  versions. At most two linked equipment slots may change in this slice.
- Project the persisted `NATIVE_EQUIPPED/OFF_HAND` item as a signed Bukkit off-hand reference and
  reconcile stale signed projections on reconnect.
- A guard profile changes only when authoritative equipment changes. The live guard runtime resets
  on that boundary so stability cannot cross between incompatible guard authorities.

## Failure and recovery

An invalid main/off-hand pair cannot commit from Scene or start a combat action. Missing free space
rejects unequip without moving either item. All linked item moves and the transaction audit commit
together; a crash yields the complete old or complete new loadout. Reconnect and restart rebuild
both native slots from PostgreSQL.

## Migration impact

No SQL migration is required. Existing item locations already support `NATIVE_EQUIPPED` with a
slot reference. Public item schema gains additive `weapon_profile.offhand_policy`, authored guard
fields and `shield_profile.guard`. Older runtime versions must not operate on databases containing
an `OFF_HAND` projection they do not reconcile.
