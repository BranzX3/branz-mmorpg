# ADR 0018: Learning, teaching and Renown kernel boundaries

- Status: Accepted
- Date: 2026-08-01
- Owners: Progression, Social, Persistence and Bootstrap

## Context

Milestone 6 needs permanent learned knowledge, synchronous player teaching and visible recognition.
Granting from a GUI click, client action count or repeated mentorship deed would bypass authored
prerequisites and create replay/farming exploits. The durable multi-character transaction is not yet
present, so the first slice must define a testable authority boundary without pretending to persist.

## Decision

Learning resolves stable Knowledge keys against permanent knowledge, qualitative readiness and
world flags in deterministic order. Teaching is a pure 10-minute state machine: distinct online
participants, teacher ownership/readiness, student eligibility, one server-resolved demonstration
and three unique successful matching student action UUIDs. Completion returns an immutable intent
keyed by teaching-session UUID; it does not mutate either character.

Renown resolves bounded server-authored deed candidates. The same fingerprint awards at daily
factors 1.00, 0.50, 0.25 and then zero; a duplicate deed UUID awards zero. Renown has no combat-stat
field and no decay operation. Development commands execute these pure paths and explicitly persist
nothing.

## Consequences

- per-contact or client-declared teaching progress cannot complete a challenge;
- ready but uncommitted teaching can still be cancelled safely;
- all learning sources can share one prerequisite contract;
- mentorship and other repeatable deed rewards have deterministic abuse bounds;
- persistence can later add a transaction without changing the state-machine rules.

## Failure and recovery

Wrong actors, wrong/failed moves, expiry and disconnect produce no completion intent. A process
restart loses the transient teaching state and grants nothing. The future durable transaction must
atomically insert learned knowledge, the capped teacher reward and the deed/idempotency records, then
reload both live sessions before publishing success.

## Migration impact

No SQL, content-schema or configuration-file migration is included. The specification and default
balance document now fix the V1 duration, unique-success count, Renown award bound and daily factors.
