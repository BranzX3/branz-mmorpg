# ADR 0013: Persistent Character Build and Rest-Context Authority

Status: Accepted

## Context

Milestone 5 requires Techniques, Forms and Magic Attunement to be more than local menu state. A
character build must survive reconnect/restart, remain compatible with the equipped weapon family
and reject over-capacity or conflicting supernatural effects. Build changes are preparation, not a
combat-time hot swap.

## Decision

- `mmo-progression` compiles `technique.*`, `form.*` and attunable `spell.*` definitions into one
  immutable `BuildEngine` for the active content snapshot.
- One character-owned build selects at most one Technique per moveset branch, one optional Form and
  a bounded set of attuned effects. Resolution validates weapon-family compatibility, attunement
  load/capacity and declared tag conflicts before preview and again before commit.
- A valid Technique replaces or augments its authored move branch. An active Form applies bounded
  stamina/mana cost multipliers; it does not grant an unrestricted damage multiplier.
- The build is stored as versioned JSON in `character_build_state`. `character.build.commit` uses
  the transaction journal, expected-version compare-and-set and a character audit row in the same
  PostgreSQL transaction.
- Chronicle may preview and commit build changes only while the combat session is `EXPLORATION`
  and the player is inside an authored Rest Context. The local training fixture treats the world
  spawn radius as that context until regional sanctuary content is introduced.
- Session open/reload validates the persisted build against the active content snapshot. Unknown,
  incompatible or corrupt build data blocks the character session instead of silently rewriting it.

## Failure and recovery

Scene preview never changes database truth. A stale version, invalid family, capacity overflow,
tag conflict or lost Rest Context rejects without a partial build. Journal replay returns the
original terminal result and cannot spend the same expected version twice. Reconnect and process
restart decode the committed JSON and restore its exact version.

Equipment and build remain separate atomic boundaries: an equipment preview may be evaluated
against a build preview, but each commit revalidates the complete resulting pair. Transient combat
actions continue using the last committed session snapshot until the build commit reloads.

## Compatibility and migration

Migration `V0004__character_build_state.sql` adds one character-keyed table. The content contract
adds `TECHNIQUE` and `FORM` definition types plus optional spell attunement tags/conflicts. Existing
characters have no row and resolve to the empty build with six capacity. Older runtimes must not
host gameplay against a database after non-empty build rows are written because they cannot enforce
those selections.
