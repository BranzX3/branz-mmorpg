# Core MMO V1 Documentation

Status: **Implementation baseline**  
Target runtime: **Minecraft Java / Paper 26.2**  
Client policy: **Vanilla client supported; required resource pack; optional enhanced client visuals**

This folder is the authoritative V1 specification for the Core MMO plugin family. It is written to be consumed by humans and coding agents. When two documents appear to disagree, use this order of precedence:

1. `02-system-invariants.md`
2. The subsystem document that owns the behavior
3. `22-default-config.md`
4. Example files

A change that alters an invariant, persistent identity, transaction rule, or public content schema requires an ADR and a migration plan.

## Documentation map

| File | Purpose |
|---|---|
| `01-product-scope.md` | Product vision, V1 boundaries, non-goals |
| `02-system-invariants.md` | Rules that all modules must obey |
| `03-architecture.md` | Modules, providers, events, ownership boundaries |
| `04-combat-state-machine.md` | Engagement, weapon, action, UI and encounter states |
| `05-input-resolution.md` | LMB/RMB/F/Q/Shift routing, buffers and priority |
| `06-moves-hitboxes-targeting.md` | Data-driven moves, hit detection and targeting |
| `07-damage-defense-cc.md` | Damage, armor, guard, dodge, posture and CC |
| `08-builds-weapons-forms.md` | Classless build composition and movesets |
| `09-progression.md` | Mastery, conditioning, teaching and renown |
| `10-resources-consumables-status.md` | HP, stamina, mana, flask, potions and ailments |
| `11-magic-runtime.md` | Spell model, catalysts, targeting and interruption |
| `12-items-equipment-enhancement.md` | Item instances, durability, equipment and forging |
| `13-loot-crafting-economy.md` | Rewards, trade, crafting and economic safeguards |
| `14-party-encounters-pvp.md` | Party, encounters, threat, boss retry and V1 PvP |
| `15-quests-dialogue-npcs.md` | Quest runtime, dialogue, NPC profiles and cutscenes |
| `16-scene-ui-hud.md` | Slot 9 Scene Hub, wardrobe, HUD and accessibility |
| `17-persistence-transactions.md` | PostgreSQL, leases, journals, recovery and migration |
| `18-content-pipeline.md` | Oraxen Studio/Git/CI/dev server/release pipeline |
| `19-operations-security.md` | Admin tools, permissions, anti-exploit and observability |
| `20-testing-performance.md` | Test pyramid, acceptance suites and performance budgets |
| `21-implementation-roadmap.md` | Coding order, milestones and definition of done |
| `22-default-config.md` | Initial tunable values and balance targets |
| `23-onboarding-player-journey.md` | Resource-pack gate, tutorial and first-session flow |
| `24-spec-status.md` | Final audit, locked decisions and tuning policy |
| `examples/` | Example content and persistence contracts |
| `adr/` | Architecture decision records |

## V1 content target

V1 is a complete vertical MMO runtime with deliberately limited content:

- Five weapon families: Greatsword, Sword and Shield, Bow, Crossbow, Staff.
- One complete magic art plus one weapon-imbuement family.
- Approximately 30 combat techniques and 4 forms.
- Six normal enemy archetypes, three elites and two bosses.
- Six ailments and twelve alchemy recipes.
- One introduction arc, four mentor paths and one regional boss chain.
- Party PvE, duels and arena PvP. No open-world PvP.

The architecture may expose extension points for later features, but V1 code must not implement speculative systems that are outside this list unless they are required for safe persistence or migration.

## Coding-agent rules

- Do not invent missing gameplay rules. Propose a spec change when a required rule is not present.
- Treat identifiers as persistent contracts, not display names.
- Never perform item ownership changes outside `TransactionService`.
- Never call integration APIs directly from gameplay modules; use provider interfaces.
- Server state is authoritative. Client visuals never decide hits, resources, ownership or rewards.
- All scheduled gameplay work must be cancellable by character session, encounter and plugin shutdown.
- Avoid Bukkit `/reload`. Code changes and schema changes require restart.
