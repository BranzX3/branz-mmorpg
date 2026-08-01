# ADR 0020: Live teaching uses authoritative successful combat actions

- Status: Accepted
- Date: 2026-08-01
- Owners: Progression, Combat, Content and Bootstrap

## Context

A teaching challenge must distinguish a successful move from a click, animation, miss or repeated
contact. It also needs authored learning/teaching readiness without exposing exact hidden evidence.
Allowing a client event or per-target damage contact to advance teaching would permit false or
multi-hit completion, while generating durable IDs only after database failure would make safe
retry impossible.

## Decision

Technique content authors a Mastery discipline and qualitative learning/teaching readiness bands.
The live adapter resolves permanent Knowledge and current readiness from both Player Sessions before
opening one ten-minute session. Participants must be online, idle/out of combat, in the same world
and within 16 blocks; a participant can own only one live teaching session.

Combat emits an observation only after its authoritative resolver accepts a successful hit. The
observation carries the actor, stable move ID, server action UUID and server tick. A deterministic
router accepts the Technique's authored move from the teacher once and then three unique actions
from the student. Contacts sharing an action UUID remain one execution.

The adapter creates the teaching-session and mentorship-deed UUIDs before challenge completion.
When ready it submits those exact IDs to the V0006 atomic transaction. Transient persistence or
competing-mutation failures retry the same IDs; permanent rejection removes the live session.

## Consequences

- client clicks, misses, wrong moves and per-target contacts cannot advance teaching;
- a student can practice the authored fallback move without prematurely owning the Technique;
- disconnect, separation, explicit cancellation and expiry before durable submission grant nothing;
- exact retry cannot duplicate Knowledge or Renown;
- content authors can raise readiness by band without Java changes or exact evidence thresholds.

## Failure and recovery

Live challenge state is intentionally transient. Restart or participant disconnect cancels it before
durable submission, even when ready but not submitted. Once the transaction is in flight it is not
locally cancellable. If PostgreSQL committed before a Player Session changed, V0006 remains
authoritative and reconnect reloads the durable result; the live adapter reports that recovery path
instead of claiming failure. A retry uses the immutable session/deed IDs and therefore replays rather
than awards again.

## Migration impact

No SQL migration is required. Technique schema version 1 now requires `mastery_discipline`,
`learning_readiness` and `teaching_readiness`; content must be updated and validated before runtime
admission. The example snapshot advances to `v1.milestone-1.example.2`. ADR 0019 continues to own
the durable transaction and replay contract.
