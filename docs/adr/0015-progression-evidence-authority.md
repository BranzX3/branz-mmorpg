# ADR 0015: Server-authored progression evidence authority

- Status: Accepted
- Date: 2026-08-01
- Owners: Progression, Combat, Persistence and Devtools

## Context

`docs/02-system-invariants.md` forbids progression from unchallenged dummy loops, repeated
zero-risk interactions and client timing claims. `docs/10-progression-renown-teaching.md` requires
hidden combat Mastery and Body Conditioning values derived from meaningful evidence rather than XP
per hit. Milestone 6 needs one deterministic boundary before persistence or live combat integration
can safely grant progression.

## Decision

`mmo-progression` owns a pure evidence resolver. Combat and encounter systems may emit one immutable
server-authored candidate only at an action-summary or encounter-outcome boundary. Candidates carry
an idempotency UUID, character/encounter UUIDs, immutable content version, novelty fingerprint and a
stable `mastery.*` or `conditioning.*` track.

The resolver applies the bounded V1 factors and qualitative thresholds specified in
`docs/10-progression-renown-teaching.md`. It returns an explicit suppression reason for duplicates,
completed dummy familiarity, invulnerable targets, self-created loops, zero-risk interactions,
far-below-capability encounters and abandoned outcomes. Exact values and factor diagnostics are
internal; normal player presentation receives only qualitative bands and explainable reason text.

The local Progression Evidence Lab is a simulation projection. It may display internal factors to a
permitted developer in a non-production environment, but it cannot write evidence or character
state.

## Consequences

- Raw hits and client packets cannot directly award progression.
- Identical server inputs always produce the same award or suppression result.
- Repetition and daily curves diminish toward a positive factor rather than creating a hard cap.
- Training dummies can teach introductory familiarity but cannot reach a readiness threshold.
- Persistence can batch decisions without duplicating the gameplay formula.

## Failure and recovery

Invalid candidates fail during construction and no state is mutated. A suppressed candidate is a
successful zero-award decision with a stable audit reason. The resolver performs no I/O, so restart
or cancellation cannot create partial progression.

## Migration impact

None in this slice. No database row is written. The follow-up durable batching slice must add an
idempotent forward migration for per-track state and evidence UUID history; old runtimes must not
write that schema after activation.
