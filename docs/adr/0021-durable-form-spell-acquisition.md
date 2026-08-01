# ADR 0021: Durable Form and Spell acquisition

- Status: Accepted
- Date: 2026-08-01
- Owners: Progression, Content, Persistence and Bootstrap

## Context

V0006 stores/backfills Form and Spell Knowledge, but production build resolution previously gated
only Techniques. A character could therefore select any authored Form or Spell without completing
its learning source. Granting source completion directly into `character_knowledge` also lacks a
global immutable event identity for crash retry and conflict detection.

## Decision

Each Form and Spell authors one acquisition policy: server source type, stable source definition,
qualitative Mastery readiness and optional permanent Knowledge/world prerequisites. The pure
resolver matches observed source identity before it evaluates prerequisites. Build preview, commit
and combat activation require Form and Spell Knowledge in addition to Technique Knowledge.

V0007 adds `CONTENT_ACQUISITION` as a Character Knowledge source and an immutable
`knowledge_acquisition_journal`. One acquisition UUID binds the complete character, target,
source and content-version input. The repository locks the character, inserts Knowledge and journal
inside one PostgreSQL transaction, and reloads the Player Session before publication.

## Consequences

- catalyst/equipment ownership does not imply Spell/Form Knowledge;
- exact retry after a lost callback returns the original grant without duplication;
- UUID reuse with changed input and a second UUID for already-owned Knowledge both reject;
- mentor, discovery, boss and quest systems can deliver acquisition later through one shared
  boundary;
- the local command is an environment/permission-gated fixture and is not a production learning
  source.

## Failure and recovery

Source/prerequisite rejection writes nothing. SQL failure rolls back both rows. If PostgreSQL commits
but the live Player Session changes, memory is not patched; reconnect reloads database truth and an
exact acquisition UUID replays. Concurrent grants serialize on the character advisory lock.

## Migration impact

Forward-only V0007 replaces the `character_knowledge.source_type` check to admit
`CONTENT_ACQUISITION`, adds `knowledge_acquisition_journal` and one character/time query index. It
does not rewrite legacy or teaching Knowledge.
