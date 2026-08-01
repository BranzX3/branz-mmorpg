# ADR 0039: Personal encounter reward eligibility freeze

- Status: Accepted
- Date: 2026-08-01
- Owners: World Loop and API

## Context

Boss victory currently freezes into `VICTORY_PENDING` but completes with one empty lab grant UUID.
V1 requires independent personal grants based on meaningful participation rather than last hit or a
damage leaderboard. Support, guard/control and objective play must qualify independently; late join,
AFK proximity, invalid membership and previously committed completion grants must fail visibly.

## Decision

`EncounterRewardEngine` is a pure freeze resolver. Each participant supplies joined/last-active
ticks, whether they joined before the authored eligibility cutoff, whether encounter
membership/recovery remains valid, whether a completion grant already exists, and non-negative
auditable totals for damage/posture, guard/control, healing/support and objective actions.

An authored `RewardEligibilityProfile` provides a positive floor for every category plus maximum
idle ticks. Meeting any one category floor is meaningful participation. Eligibility also requires
pre-cutoff join, valid membership/recovery, activity within the idle window and no prior completion
grant. Life state is deliberately absent: a dead or downed participant can qualify through the same
membership, activity and contribution evidence.

The freeze emits one personal grant per eligible character. Its UUID is deterministically derived
from encounter UUID, attempt and character UUID. Its roll seed is deterministically mixed from the
stored encounter seed and character UUID, so retry order cannot change or duplicate a roll. Every
ineligible participant receives one stable reason: cutoff, membership, inactivity, insufficient
contribution or already granted.

## Consequences

- eligibility is category-based and does not privilege last hit or raw damage;
- eligible participants can roll independently in any iteration order;
- retrying the same frozen evidence produces identical grant UUIDs and seeds;
- attempt changes create new grant identities while keeping an encounter's per-character roll stream
  stable until a durable seed policy is attached;
- live contribution capture, reward-table rolling and durable delivery remain separate layers.

## Failure and recovery

Negative contribution/ticks, invalid profiles and duplicate participant evidence fail closed before
any freeze result exists. A participant cannot appear in both grant and rejection maps. The resolver
does not write inventory, currency or Pending Rewards; those effects require durable grant rows and
transaction-journal acknowledgement.

## Migration impact

None for the pure kernel. Durable personal grants, evidence snapshots, roll audit and delivery state
require a forward-only migration in the next slice.
