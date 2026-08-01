# Progression, Renown and Teaching

## Combat progression layers

1. **Body Conditioning** — hidden physical/mental adaptation.
2. **Discipline Mastery** — hidden proficiency per weapon, guard, mobility and magic discipline.
3. **Knowledge** — permanently learned techniques, forms, recipes and lore.
4. **Expression** — equipped build, gear, attunement and preparation.

## Mastery evidence

Mastery is not XP per hit. The server records bounded evidence from meaningful encounters:

```text
challenge rating
encounter outcome
move diversity
execution context
novelty decay
risk and resource pressure
mentor/trial milestones
```

Repeated hits on harmless targets rapidly reach zero evidence. Each discipline has a soft daily evidence curve, not a hard cap. Breakthrough trials unlock deeper capability bands after evidence readiness.

### V1 evidence resolution contract

The runtime emits one server-authored evidence candidate at an action-summary or encounter-outcome
boundary. A raw hit, client timing claim or presentation event is never an evidence candidate. Each
candidate owns an idempotency UUID, character and encounter UUID, stable `mastery.*` or
`conditioning.*` track, novelty fingerprint, immutable content version and the server-classified
target/outcome context.

Internal evidence is bounded from `0` to `1000`; a single candidate can award at most `100`. The
exact value remains hidden. The deterministic V1 formula is:

```text
award = base
      × challenge
      × outcome
      × diversity
      × execution
      × novelty
      × repetition
      × risk
      × daily_curve
```

- Challenge below `0.30` of demonstrated capability awards zero. Ratios `[0.30,0.60)`,
  `[0.60,0.90)`, `[0.90,1.15)`, `[1.15,1.50)` and `1.50+` use factors `0.50`, `0.85`, `1.00`,
  `1.25` and `1.50`.
- Victory, defeat, retreat and abandonment use `1.00`, `0.50`, `0.25` and `0.00`.
- Normalized move diversity and execution quality map linearly from `0.50` to `1.25`.
- A new 30-minute novelty fingerprint starts at `1.25`. Identical completions use repetition
  factors `1.00`, `0.75`, `0.50`, `0.25`, then `0.10`.
- Stress below `0.30` is minimal (`0.25`); productive `[0.30,0.75)` is `1.00`; risky
  `[0.75,0.95]` is `1.25`; unsafe stress above `0.95` is `0.75` only after victory and otherwise
  `0.25`.
- Accepted daily evidence below `100` uses `1.00`, `[100,250)` uses `0.50`, and `250+` uses
  `0.25`. A daily window is the current UTC calendar day. This curve never becomes a hard cap.

Training dummies can advance only introductory familiarity through evidence `25`. Invulnerable
targets, self-created loops, zero-risk interactions, duplicate evidence UUIDs, abandoned outcomes
and far-below-capability encounters award exactly zero with a stable suppression reason. The five
qualitative bands start at `0`, `100`, `300`, `600` and `850`: Unfamiliar, Developing, Reliable,
Refined and Exceptional. Crossing evidence readiness does not bypass an authored breakthrough or
knowledge prerequisite.

### Durable evidence batch contract

Accepted and suppressed candidates are written to the durable evidence journal before the result
is returned to a live character session. A batch contains between one and 256 candidates for one
character and resolves in list order inside one PostgreSQL transaction. The repository locks the
character, rebuilds the current track, 30-minute novelty and current-UTC-day context from database
truth, then evaluates the same pure resolver used by tests.

The evidence UUID is the idempotency key. Repeating the exact immutable candidate returns the
stored decision and does not advance a track version; reusing that UUID for different input rejects
and rolls back the whole batch. Suppressed evidence remains journaled for audit and anti-abuse
inspection even when no track row exists. A successful Player Session mutation reloads track state
from the database before publishing its new snapshot.

### Live combat evidence boundary

The Paper combat adapter starts one server-owned encounter segment on the first authoritative
successful action against a living target. It accumulates unique committed/successful action UUIDs,
distinct move or spell IDs and peak health/resource stress; repeated hitbox contacts, projectile
pierces, Zone pulses and Channel pulses sharing one action UUID do not create extra actions. No row
or award is produced at hit time.

The segment closes as Victory on authoritative target death, Defeat on player death, Retreat when
engagement returns to Exploration, or Abandoned on a forced teleport/external target death. Each
used discipline emits at most one Mastery and one mapped Body Conditioning candidate. A re-engage
against the same surviving entity receives a new server encounter UUID. One session tracks at most
64 active targets, each discipline summary counts at most 64 action UUIDs, and persistence splits
the output into batches of at most 256 candidates.

V1 maps Greatsword to Might, Sword and Shield to Fortitude, Bow/Crossbow to Coordination, Staff to
Composure and fallback families to Endurance. Target challenge is derived only from server entity
maximum health, attack damage and armor. Invulnerable entities and entities tagged
`branzmmo.training_dummy`, `branzmmo.self_created_loop` or `branzmmo.zero_risk` enter the matching
anti-farm classification before resolution.

## Mastery effects

Mastery may:

- unlock branches, cancels, forms and technique variants;
- reduce stamina/mana cost up to 15%;
- improve recovery or stability up to 10%;
- improve handling of heavy/restricted equipment;
- expose better qualitative combat feedback.

Mastery cannot increase generic raw health damage by more than 10% from novice to deep mastery. Most power comes from knowledge and execution.

## Body Conditioning

Hidden axes:

- Might — force and heavy handling.
- Coordination — momentum, balance and direction changes.
- Endurance — sustained effort and stamina efficiency.
- Fortitude — resistance to force, stagger and guard collapse.
- Composure — channel and ritual stability under pressure.

Conditioning responds to varied, appropriately difficult activity. It uses soft plateaus and mentor/trial breakthroughs. It does not increase aim accuracy and does not create exponential health/damage scaling.

## Feedback taxonomy

Exact values remain hidden, but the player sees one of:

- Unfamiliar
- Developing
- Reliable
- Refined
- Exceptional

Action rejection includes a reason code such as insufficient handling, unstable channel, missing knowledge, incorrect form, excessive load or unmet trial.

## Learning techniques

Sources:

- mentors and training trials;
- ruins, books and runes;
- boss knowledge;
- faction and regional quests;
- player teaching.

Learned knowledge is permanent. Story choices cannot permanently lock a core weapon or magic family.

## Player teaching

A teacher must own the technique and meet its teaching readiness. Student prerequisites are checked before starting. Both enter a training session with a short demonstration and execution challenge. Disconnect cancels safely; no knowledge is granted until challenge completion commits.

Teacher rewards are capped mentorship tokens, social renown or training evidence, never direct duplication of materials or currency.

### V1 learning and teaching contract

Knowledge identity is a stable pair of knowledge type and definition ID. The V1 types are
Foundation, Technique, Form, Spell, Recipe and Lore. A learning source resolves authored permanent
knowledge prerequisites first, then qualitative Mastery/Conditioning readiness, then world or trial
flags. Exact hidden evidence is never included in a learning rejection. Already learned knowledge
cannot be granted twice.

Player teaching supports Technique knowledge only in V1. Teacher and student must be different,
online characters. Before a session starts, the server verifies that the teacher owns the Technique,
meets its authored teaching readiness and that the student is not already learned and satisfies every
learning prerequisite. The session lasts 12,000 server ticks (10 minutes):

1. the teacher successfully demonstrates one server-resolved move;
2. the student executes that exact move successfully three times;
3. every successful execution must have a distinct server action UUID;
4. only then does the state machine produce an immutable completion intent keyed by teaching-session
   UUID for a later durable commit.

A repeated contact/action UUID does not advance the challenge. A miss, wrong move, wrong actor,
expiry or participant disconnect cannot produce a completion intent. Disconnect or expiry cancels
the complete session, including one whose challenge was ready but not yet committed. Knowledge and
the capped teacher reward must eventually commit atomically; the pure state machine and simulation
lab intentionally grant neither.

### Durable teaching completion contract

Migration V0006 stores permanent character Knowledge, the visible Renown projection, an immutable
Renown deed journal and an immutable teaching-completion journal. One completion transaction locks
teacher and student in UUID order, then either commits all of the following or none:

- the student's Technique Knowledge row;
- the server-authored mentorship deed and its resolved daily repetition result;
- the teacher's Renown projection when the deed awards a positive amount;
- the teaching-session UUID and its exact immutable participant/Technique/deed binding.

Exact replay of the same teaching-session and deed UUID returns the original result without a new
Knowledge row, deed, award or version. Reusing either UUID with different input, or teaching a
Technique the student already knows through another source, rejects and rolls back every new output.
After commit, both active Player Sessions reload database truth before success is published. If a
session changes during asynchronous completion, memory state is not patched; exact retry reloads the
already committed truth.

Production build resolution requires a selected Technique to exist in permanent Knowledge. V0006
grandfathers Technique/Form/Spell selections already committed before the migration by importing
their stable IDs as `LEGACY_BUILD_BACKFILL`; it never infers knowledge from post-migration GUI state.
Form and Spell learned-state gating remains with their authored acquisition-source slice.

### Live teaching action bridge

Each Technique definition authors `mastery_discipline`, `learning_readiness` and
`teaching_readiness`. Session start resolves those fields against the permanent Knowledge and
qualitative Mastery bands in both current Player Sessions. The two online players must be within 16
blocks in the same world, outside combat and between actions; one character can participate in only
one teaching session at a time.

After start, only a successful action emitted by the authoritative combat resolver can advance the
session. The teacher must land the Technique's authored move once, then the student must land the
same move with three distinct server action UUIDs. Multiple contacts from one swept or piercing
action remain one execution. Misses, other moves and the other participant's action do not advance
the current phase.

Separation, disconnect or ten-minute expiry before durable submission removes the transient
challenge without a grant. A ready challenge immediately submits its pre-created teaching-session
and mentorship-deed UUIDs to the V0006 transaction. Once submitted it cannot be cancelled locally;
participant/session change reports an uncertain live outcome and reconnect reloads PostgreSQL
truth. Temporary database or competing-mutation failure retries those exact IDs; permanent
rejection removes the session. `/mmo teaching start <student> <technique-id>`, `session` and
`cancel` expose this live path without enabling the developer simulation or direct-record fixture.

## Renown

Renown is visible world recognition with no combat stats. It records notable deeds and unlocks:

- titles and cosmetic presentation;
- dialogue recognition;
- faction introductions;
- contracts and civic privileges;
- mentor trust.

Renown does not decay in V1. Faction reputation is separate and may be positive, neutral or hostile, but core gameplay families remain recoverable.

### V1 Renown resolution contract

A server-authored deed owns an idempotency UUID, character ID, stable `renown.*` deed type, novelty
fingerprint, immutable content version and base award from 1 to 100. Renown never alters combat
statistics and never decays in V1. For the same novelty fingerprint in one UTC day, the first,
second and third accepted deed use factors `1.00`, `0.50` and `0.25`; later identical deeds award
zero. Replaying a deed UUID awards zero. The future durable resolver reconstructs repetition and
idempotency context from PostgreSQL rather than trusting client input.
