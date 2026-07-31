# V1 Specification Status

Status: **Ready to begin implementation**

## Audit result

All architecture-blocking decisions identified during pre-production review now have a V1 baseline:

- hotbar ownership and Scene Chronicle;
- combat/weapon/action/UI/encounter state graphs;
- LMB/RMB/F/Q/Shift ownership and ammo switching;
- move timeline, hitboxes, projectiles and soft targeting;
- deterministic damage, armor, conditional advantage and PvP profile;
- dodge, guard, perfect guard, parry, posture, poise and CC;
- weapon families, moveset branches, forms, Rest Context and attunement;
- mastery, body conditioning, player teaching and renown;
- HP, stamina, mana, Flask, consumables and six ailments;
- spell runtime, catalysts, targeting, resonance and safety caps;
- item UUID, equipment, quiver, durability, enhancement and trade inspection;
- personal loot, PendingRewards, bank, crafting and economy safeguards;
- party, encounter, boss retry, threat, death pouch, duel and arena;
- quest, dialogue, NPC and cutscene runtime;
- local Scene Hub, preview actor, wardrobe, HUD and resource-pack failure;
- PostgreSQL, leases, transactions, reconciliation and migrations;
- Oraxen/Git/CI/dev-server pipeline;
- operations, anti-exploit, observability, testing and performance budgets;
- onboarding and first-player journey.

There are no unresolved decisions that should force an implementation rewrite if these documents are followed.

## What remains intentionally tunable

The following are balance/content tuning rather than missing architecture:

- exact move timings and coefficients;
- individual weapon powers and armor values;
- resource costs within global caps;
- boss posture/recovery and encounter-specific rules;
- reward probabilities and economic prices;
- enhancement chances and material quantities;
- mastery evidence rates and qualitative thresholds;
- UI art, copy, sound and animation assets.

Defaults are provided in `22-default-config.md`; telemetry and playtests should tune them without changing invariants.

## Change policy

### Normal content/config change

Allowed when it does not alter identity, state graph, transaction semantics or player control ownership. Requires validation and normal review.

### Spec/ADR change

Required for changes to:

- persistent identifiers/schema;
- item ownership/location rules;
- input ownership or hotbar allocation;
- state-machine transitions;
- random accuracy/critical policy;
- item destruction/downgrade policy;
- reward ownership/idempotency;
- Scene commit/cancel/teleport policy;
- PvP scope;
- server topology.

### Migration-required change

Any change that affects existing characters, items, active quests, content IDs, reward grants or stored build data requires an idempotent migration and staging rehearsal.

## Recommended first coding prompt

Start with Milestones 0–2 from `21-implementation-roadmap.md`. Require the coding agent to read, in order:

1. `02-system-invariants.md`
2. `03-architecture.md`
3. `17-persistence-transactions.md`
4. `18-content-pipeline.md`
5. the milestone-specific subsystem documents

The first implementation pull request should create only the module skeleton, stable IDs, result/error contracts, test infrastructure, PostgreSQL migrations, character lease and content manifest loader. It should not implement combat moves before persistence and content snapshot foundations pass their tests.
