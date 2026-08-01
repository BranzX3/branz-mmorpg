# ADR 0017: Live combat progression outcome boundary

- Status: Accepted
- Date: 2026-08-01
- Owners: Combat, Progression, Persistence and Bootstrap

## Context

The deterministic resolver and durable journal exist, but awarding at every Bukkit damage callback
would recreate XP-per-hit farming and multiply evidence for piercing projectiles, Zones or Channels.
Milestone 6 requires live server evidence without waiting for the Milestone 7 encounter director.

## Decision

Each live character session owns a bounded `CombatEvidenceAccumulator`. The first authoritative
successful action against a target creates a server encounter segment. Unique action UUIDs, move or
spell IDs and peak server-owned health/resource stress are accumulated; hit callbacks themselves do
not mutate persistent progression.

An authoritative target kill closes Victory. Player death closes Defeat, engagement exit closes
Retreat, and forced teleport or external target death closes Abandoned. Each used discipline composes
one stable Mastery and one mapped Conditioning candidate. A new segment receives a new encounter UUID
even when the same living entity is re-engaged, while candidate UUIDs remain deterministic within the
completed segment for retry safety.

The adapter classifies explicit anti-farm scoreboard tags and invulnerability before resolution,
derives challenge from server entity attributes, caps active targets/actions, and sends candidates
through the serialized Player Session persistence path in batches of at most 256. Normal feedback
contains qualitative readiness bands only.

## Consequences

- a projectile pierce, Zone pulse or Channel pulse cannot generate per-contact progression;
- misses after a target has been observed reduce execution quality for that discipline;
- weapon swapping may demonstrate several disciplines, but each is bounded to one pair of candidates
  per outcome segment;
- the future encounter director can replace target segmentation without changing the resolver or SQL
  contract.

## Failure and recovery

Completed candidates queue immediately. A busy Player Session or unavailable database keeps the
same immutable candidates and retries; exact repository replay cannot double-award. Invalid identity
conflicts fail visibly in the server log and are not retried forever. Open, uncompleted encounter
accumulators remain transient and are intentionally discarded on logout/restart.

## Migration impact

No SQL, content-schema or configuration-file migration is required. This slice consumes V0005 and
the fixed V1 balance targets documented in the default-config specification.
