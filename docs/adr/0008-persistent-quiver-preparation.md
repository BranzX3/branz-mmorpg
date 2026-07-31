# ADR 0008 — Persistent Quiver Preparation

## Decision

Quiver preparation is authoritative state owned by the equipped unique Quiver item UUID. The item
payload stores an ordered list of at most four stable ammo definition IDs and one selected index.
Every preparation edit or scroll-cycle replaces that payload through the PostgreSQL transaction
journal with version, owner, location and previous-payload compare-and-set expectations.

Item content declares an ammo family on each ammo lot definition. A Quiver declares capacity,
supported families, prepared-category limit and handling ticks. Startup compilation rejects invalid
or mutually incompatible item profiles. Bow release resolves and consumes the currently selected,
compatible prepared category before creating the server projectile.

Stationary sneak plus hotbar scroll owns ammo cycling while a Bow or Crossbow is equipped. The
proposed hotbar change is cancelled. Active draw/action state rejects the switch, and a successful
switch while engaged applies the Quiver's six-tick training handling lock. The cycle becomes visible
only after its journaled payload commit and snapshot refresh succeed.

## Rationale

Owning preparation on the Quiver item preserves the state when equipment is swapped or the
character reconnects and avoids creating character-global ammo state that can outlive its container.
Stable definition IDs keep the payload content-version independent while profile validation supplies
the current family rules. Reusing the value journal gives preparation edits the same crash, replay
and concurrency guarantees as equipment and ammo consumption.

## Compatibility and migration

No SQL migration is required because `item_instance.payload`, item version and transaction journal
already provide the required storage and CAS boundary. Existing Quiver payloads without a `quiver`
member decode as an empty preparation; malformed members fail the character load closed. The item
schema changes are additive. Older content without ammo or Quiver profiles remains valid unless it
uses the reserved `ammo.*` namespace, which now requires an ammo profile.

Rollback requires stopping active sessions before reverting code. The additive payload member may
remain in PostgreSQL and is preserved by generic payload readers. Runtime versions that do not know
the new ammo profiles must not be used to fire prepared-category Bow shots.

## Failure and recovery

Payload mutation, audit append and terminal journal state share one database transaction. A crash
before commit rolls back all three; retrying the same operation ID commits at most once. A stale
version, changed owner/location/payload, another in-flight character value mutation or reload failure
does not fabricate a local selection. PostgreSQL remains authoritative and reconnect reloads the
equipped Quiver payload.

This decision does not yet move or split commodity lots into Quiver capacity. Ammo remains in the
character inventory and capacity is compiled metadata until the dedicated transfer transaction is
implemented. Encounter-end ammo recovery, Pending Rewards overflow and Crossbow load binding are
also later slices.
