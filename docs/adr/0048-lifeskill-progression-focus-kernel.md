# ADR 0048: Lifeskill progression and Life Focus kernel

## Status

Accepted.

## Context

Milestone 8 begins with visible Lifeskill Rank, visible Mastery and Life Focus. The product contract
defines thirty named ranks, mastery sources and hard effect caps, plus a 100-point Focus pool that
recovers one point per ten real minutes including offline time. It does not define one global rank
evidence curve; disciplines and content progression must remain authorable without changing code.

Work completion and Focus spending cross future durable node/tool/output transactions. Their pure
decisions therefore need stable operation identities before a persistence adapter is introduced.

## Decision

`mmo-lifeskills` owns a pure progression package with these boundaries:

- a discipline is a stable `lifeskill.*` definition identity;
- the rank domain is exactly Trainee I through Grandmaster V;
- each discipline supplies a strictly increasing, thirty-entry cumulative evidence table beginning
  at zero;
- only already-approved committed-work evidence enters the rank engine, and an operation UUID may
  apply it once; exact replay is unchanged and changed reuse fails closed;
- mastery combines the six declared server-authored sources, clamps visibly to 0–1000 and applies a
  normalized quadratic diminishing-return curve to the 35% work-speed, 60% basic-yield and 30%
  relative rare-yield caps;
- normal work commits with zero Focus, focused work costs one to five, and both are idempotent by
  operation UUID;
- Focus recovery uses an absolute wall-clock anchor, grants one point per ten complete minutes,
  includes offline elapsed time, clamps at 100 and discards elapsed time accumulated while full;
- a backward wall clock, malformed runtime/input or changed operation replay fails closed.

The operation maps are pure-kernel replay evidence. A later persistence slice must store equivalent
journal identities transactionally with node, tool and output state; it must not treat these in-memory
maps as the durable authority.

## Consequences

Balance authors can change promotion pacing without altering rank identity. Mastery cannot exceed
the published V1 output caps even when several equipment/knowledge sources sum above 1000. Offline
Focus recovery is deterministic and cannot be banked beyond the cap. Node reservation, repetition
evidence, durable journals and live Paper tools remain separate Milestone 8 slices.
