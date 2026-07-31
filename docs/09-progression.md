# Mastery, Conditioning, Teaching and Renown

## Progression layers

1. **Body Conditioning** — hidden physical/mental adaptation.
2. **Discipline Mastery** — hidden competence with weapon, guard, mobility and magic families.
3. **Knowledge** — permanently learned techniques and forms.
4. **Expression** — current equipment, moveset and attunement.

Exact values are hidden. Players receive qualitative readiness, mentor dialogue and specific rejection reasons.

## Meaningful evidence

Progress is not awarded per raw click or damage event. Combat runtime emits evidence at action/encounter boundaries.

An evidence record includes:

```text
character
family/axis
encounter
challenge rating
novelty fingerprint
execution signals
stress ratio
result
content snapshot
idempotency key
```

### Evidence weight

```text
weight = base
       * challenge_factor
       * novelty_factor
       * execution_factor
       * repetition_factor
```

Bounds:

- Challenge factor: 0.0–1.5.
- Novelty factor: 0.25–1.25.
- Execution factor: 0.5–1.25.
- Repetition factor: decays toward 0.1 for identical low-risk farming.

No evidence is granted for training dummies beyond introductory familiarity, invulnerable targets, self-created trivial loops or encounters far below the character's demonstrated capability.

## Discipline mastery

Internal mastery is a continuous value with five readiness bands. Bands are not shown as numbers:

```text
UNFAMILIAR
PRACTICED
RELIABLE
REFINED
MASTERFUL
```

Mastery effects are bounded across the entire range:

- stamina/mana cost reduction: maximum 10%,
- recovery reduction: maximum 5%,
- posture/guard efficiency: maximum 8%,
- handling requirement contribution: meaningful but cannot replace Body Conditioning,
- access to advanced branches, forms and mentor trials.

Mastery does not provide more than 5% unconditional raw damage. Most power comes through unlocked options and execution.

Soft plateaus require a breakthrough condition such as mentor trial, boss technique, new form, difficult encounter pattern or exploration discovery. Progress evidence still accumulates into readiness but does not cross the plateau until the breakthrough completes.

## Body Conditioning axes

### Might

Heavy weapon control, force transfer, bow draw weight and recoil handling.

### Coordination

Momentum transitions, directional control, dual-action stability and precision of complex movement. It never adds aim assist.

### Endurance

Sustained stamina efficiency and recovery under repeated exertion.

### Fortitude

Poise, stability against force, guard resilience and hyper-armor support.

### Composure

Concentration and channel stability under pressure. It does not increase general intelligence or spell knowledge.

## Conditioning gain

Gain requires stress in a productive band:

- below 30% of demonstrated capacity: minimal gain,
- 30–75%: normal gain,
- 75–95%: high gain with risk,
- above safe capability without successful execution: low gain and likely failure.

Identical stimulus uses a rolling 30-minute repetition decay. Diverse actions and real encounters restore value. There is no hard daily cap, but diminishing returns prevent AFK/automation farming.

Maximum V1 effects from full conditioning range:

- handling requirement contribution: up to 20%,
- stamina efficiency: up to 8%,
- poise/guard stability contribution: up to 12%,
- channel interruption resistance contribution: up to 12%,
- no direct universal damage multiplier.

## Qualitative feedback

The system stores reason codes and maps them to player language:

- “Your stance is becoming reliable.”
- “Repeated practice here no longer challenges you.”
- “You understand the motion, but a mentor's breakthrough is still required.”
- “The weapon's weight still overwhelms your Might.”
- “Your Composure failed under the interruption.”

Feedback is event-driven and rate-limited; it does not spam every action.

## Technique learning

Prerequisite types:

```text
FOUNDATION
TECHNIQUE
FORM
MASTERY_READINESS
CONDITIONING_READINESS
QUEST_FLAG
MENTOR_TRIAL
ITEM_KNOWLEDGE
WORLD_DISCOVERY
```

Learning grants permanent knowledge. A newly learned technique must be useful immediately; mastery adds depth, connections and efficiency rather than fixing an intentionally weak baseline.

## Player teaching

V1 teaching is synchronous and in-person:

1. Teacher and student form a teaching session in a training region.
2. Teacher knows the technique and meets teaching readiness.
3. Student meets prerequisites except the knowledge source.
4. A short training challenge validates inputs/execution.
5. Success grants knowledge through an idempotent transaction.

Rules:

- Session timeout: 10 minutes.
- Disconnect cancels without consuming anything.
- Teacher receives small renown/mentor credit, subject to daily diminishing returns per student/technique.
- No offline teaching or trade-click instant learning.

## Renown

Renown is a visible account-character history and social recognition system.

- Global Renown records notable deeds.
- Faction reputation is separate and may be positive/negative.
- Neither grants direct combat stats.
- Uses: dialogue recognition, titles, cosmetic access, social permissions, service availability and narrative branches.
- No passive decay in V1.
- Repeated trivial tasks give sharply diminishing renown.

Titles are cosmetic/social only and may control prefix, suffix, glyph or subtle entrance effect.
