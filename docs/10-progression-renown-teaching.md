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

## Renown

Renown is visible world recognition with no combat stats. It records notable deeds and unlocks:

- titles and cosmetic presentation;
- dialogue recognition;
- faction introductions;
- contracts and civic privileges;
- mentor trust.

Renown does not decay in V1. Faction reputation is separate and may be positive, neutral or hostile, but core gameplay families remain recoverable.
