# ADR 0049: Resource-node reservation and harvest kernel

## Status

Accepted.

## Context

Gathering rewards cannot be decided by vanilla block breaking. A node must own persistent
availability, reserve a charge and exact tool before work begins, emit value once at the authored
commit point and recover from wall-clock state rather than loaded chunks. Common nodes need
independent personal state; Rich and Rare nodes need first-valid-actor shared competition.

## Decision

`mmo-lifeskills` owns a pure resource-node state machine with these rules:

- Common definitions are personal; Rich and Rare definitions are shared. Regional, Event and
  Corrupted definitions author their sharing mode explicitly.
- An absent personal/shared slot means all authored charges are available. Reservations freeze the
  server-generated reservation ID, owner, exact tool instance, durability/Focus costs, work commit
  tick, wall-clock timeout and a deterministic yield seed.
- Admission requires authoritative region/action eligibility, all required tool tags and enough
  durability. The first valid actor moves a slot from `AVAILABLE` to `RESERVED`.
- Cancel, timeout or restart before commit returns the charge and emits the released reservation;
  ordinary chunk unload does not release a live reservation before its timeout.
- Commit is rejected before the authored work tick or at/after timeout. A successful commit emits
  one frozen harvest intent, decrements one charge and moves the last charge to `DEPLETED` with its
  wall-clock recovery timestamp.
- Reconciliation advances `DEPLETED` to `RECOVERING`, releases stale reservations and restores all
  charges when the timestamp is due. Chunk load is never an availability source.
- Reserve, cancel, commit and explicit recovery operations are exact by operation UUID. Exact replay
  is unchanged and emits no harvest; cross-kind or changed-input reuse fails closed.

## Consequences

The kernel proves personal independence, shared competition and crash-side commit semantics without
owning inventory value. The persistence adapter must atomically journal the node transition with
tool durability, Focus, yield lots and rank evidence. A live Node Lab can then exercise the same
state machine without granting value from simulated runs.
