# ADR 0031: Live party-PvE downed adapter

- Status: Accepted
- Date: 2026-08-01
- Owners: Bootstrap, Combat, Social and World Loop

## Context

The deterministic downed kernel needs a live authority that can replace a real lethal combat result
without allowing Bukkit death to run early. It also needs one concrete encounter-membership source
so its one-revive allowance is scoped to an attempt rather than to a player session.

## Decision

An active multi-player boss-lab attempt supplies the encounter UUID, attempt number and locked
participant set. The live adapter keys one downed runtime by encounter UUID plus attempt. A lethal
combat result is applied to the kernel first. `DOWNED` replaces Bukkit death with a one-percent
internal health sentinel and hard action/movement lock; a kernel death continues to ordinary Bukkit
death so the durable boss lifecycle receives its existing death event.

The adapter advances every server tick. It applies the kernel's 25% revive output to combat health,
turns expiry into real death and rejects incoming combat damage during the 60-tick revive-protection
window. Successful hostile combat ends protection. Movement, incoming damage and disconnect cancel
an owned revive channel without consuming the target's encounter revive.

The environment-gated `/mmo downed` lab exposes status, lethal, Execute, revive, interruption and
hostile-action inputs. It is an acceptance surface, not a client-authoritative gameplay protocol.

## Consequences

- normal mob/environment lethal damage now exercises the same deterministic downed state as lab
  commands;
- a second lethal event, Execute or timer expiry still reaches `PlayerDeathEvent`, preserving the
  durable boss wipe/Flask path;
- a new boss attempt receives a fresh one-revive allowance;
- the adapter currently covers boss-lab party membership only; general party and LFG membership
  remain separate work.

## Failure and recovery

Invalid commands or mismatched encounter attempts change nothing. A server restart currently loses
an in-progress downed timer/channel, so durable downed recovery remains required before this feature
is production-safe. Boss encounter state itself continues to use its existing durable boundary.

## Migration impact

None. Durable downed state will require a later forward-only migration.
