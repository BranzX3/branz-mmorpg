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
  `0.25`. This curve never becomes a hard cap.

Training dummies can advance only introductory familiarity through evidence `25`. Invulnerable
targets, self-created loops, zero-risk interactions, duplicate evidence UUIDs, abandoned outcomes
and far-below-capability encounters award exactly zero with a stable suppression reason. The five
qualitative bands start at `0`, `100`, `300`, `600` and `850`: Unfamiliar, Developing, Reliable,
Refined and Exceptional. Crossing evidence readiness does not bypass an authored breakthrough or
knowledge prerequisite.

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
