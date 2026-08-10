# Documentation Index

Status: **V1 Full implementation baseline**  
Runtime target: **Minecraft Java / Paper 26.2**  
Client policy: **Vanilla client supported; required resource pack; optional enhanced visual extensions**

## Precedence

When documents disagree, apply this order:

1. `02-system-invariants.md`
2. The document that owns the subsystem
3. `38-default-config.md`
4. Example files

Any change to persistent identity, input ownership, transaction semantics, state transitions or public content schemas requires an ADR and a migration plan.

## Product and platform

| File | Purpose |
|---|---|
| `01-product-scope.md` | V1 product definition, full loop and explicit non-goals |
| `02-system-invariants.md` | Non-negotiable rules shared by every module |
| `03-architecture.md` | Module boundaries, providers, events and ownership |
| `04-identifiers-content-contracts.md` | Stable IDs, definition snapshots and compatibility |

## Combat and character

| File | Purpose |
|---|---|
| `05-combat-state-machine.md` | Engagement, weapon, action, UI and encounter states |
| `06-input-resolution.md` | LMB/RMB/F/Q/Shift ownership, buffering and interaction priority |
| `07-moves-hitboxes-targeting.md` | Move timelines, hitboxes, projectiles and soft targeting |
| `08-damage-defense-cc.md` | Damage, armor, dodge, guard, parry, posture, poise and CC |
| `09-weapons-builds-forms.md` | Weapon families, movesets, forms and attunement |
| `10-progression-renown-teaching.md` | Mastery, conditioning, teaching and renown |
| `11-resources-flask-consumables.md` | HP, stamina, mana, Flask and consumable use |
| `12-status-effects.md` | Ailments, buildup, resistance and cleansing |
| `13-magic-runtime.md` | Spells, catalysts, targeting, channels and summons |

## Items, rewards and economy

| File | Purpose |
|---|---|
| `14-items-equipment-durability.md` | Item instances, equipment, quiver and durability |
| `15-enhancement-reforge-repair.md` | Enhancement, forge paths, pity, repair and restoration |
| `16-loot-rewards-ownership.md` | Personal loot, eligibility, pending rewards and anti-duplication |
| `17-crafting-recipes-workshops.md` | General recipe engine and station transactions |
| `18-market-central-exchange.md` | Commodity order book, unique listings and commissions |
| `19-city-economy-regional-trade.md` | City profiles, demand, cargo and trade routes |
| `20-storage-logistics-freight.md` | Inventory, city storage, bank, freight and overflow |

## Lifeskills and world loop

| File | Purpose |
|---|---|
| `21-lifeskill-overview.md` | Rank, mastery, Life Focus and profession rules |
| `22-gathering-resource-nodes.md` | Mining, logging, foraging, harvesting and node lifecycle |
| `23-fishing-hunting.md` | Active/relaxed fishing and hunting encounters |
| `24-processing-production.md` | Processing, cooking, alchemy and smithing |
| `25-farming-workers.md` | Farming plots and bounded background workers |
| `26-mounts-caravans-stables.md` | Horse, camel, mule, llama caravan and Training |
| `27-rest-camp-checkpoints.md` | Sanctuaries, field camps and boss retry checkpoints |
| `28-navigation-travel-network.md` | Discovery map, roads, coaches, ferries and route rules |
| `29-encounters-spawn-ecology.md` | Territories, spawn populations, leash and encounter reset |
| `30-activity-board-factions-guilds.md` | Repeatable activities, civic influence, faction and basic guilds |

## Social, narrative and presentation

| File | Purpose |
|---|---|
| `31-party-lfg-downed-pvp.md` | Parties, LFG, downed state, duels and arena PvP |
| `32-quests-dialogue-npcs.md` | Quest runtime, dialogue, NPCs and cutscenes |
| `33-scene-ui-hud-accessibility.md` | Slot 9 Local Scene Hub, wardrobe, HUD and accessibility |
| `34-onboarding-player-journey.md` | First login through the first regional boss |

## Engineering and release

| File | Purpose |
|---|---|
| `35-persistence-transactions.md` | PostgreSQL, leases, journals, reconciliation and migration |
| `36-content-dev-pipeline.md` | Oraxen Studio, Git, CI, dev servers and immutable artifacts |
| `37-operations-security-testing.md` | Admin tools, anti-exploit, observability, tests and budgets |
| `38-default-config.md` | Initial tunable values and balance targets |
| `39-implementation-roadmap.md` | Milestones, coding order and definition of done |
| `40-spec-status.md` | Final completeness audit and change policy |
| `41-cross-system-acceptance.md` | End-to-end acceptance and crash-recovery matrix |
| `42-ai-coding-handoff.md` | Coding-agent prompt/PR contracts and stop conditions |
| `43-content-authoring-tools.md` | Content CLI, in-game labs, simulations, authoring UX and PR previews |
| `44-physical-gameplay-item-acceptance.md` | Physical hotbar/offhand/consumable authority live acceptance and evidence gate |

## V1 content target

V1 implements every platform above with a bounded content set:

- Five weapon families: Greatsword, Sword and Shield, Bow, Crossbow and Staff.
- One complete magic art and one weapon-imbuement family.
- Approximately 30 combat techniques, four forms, six ailments, ten normal enemy archetypes, four elites and three bosses.
- Five economic cities, four gathering regions, three hunting ecosystems, six fishing waters and one farming district per major city.
- Gathering, Fishing, Hunting, Processing, Cooking, Alchemy, Smithing, Trading, Training and Farming.
- Central Exchange, unique-item listings, crafting commissions, regional cargo and city storage.
- Party PvE, LFG, limited downed state, duels and arena. No open-world PvP.

## Coding-agent rules

- Never invent a missing rule. Emit a specification defect with the exact document and section required.
- Content-authoring tasks must also follow `43-content-authoring-tools.md`; tools write to local/test environments and never directly to Production.
- Treat stable IDs as persistent contracts, never as display names.
- All ownership and currency changes go through `TransactionService`.
- Gameplay modules call provider interfaces, never vendor APIs directly.
- Server state is authoritative; visuals never decide damage, resources, ownership or rewards.
- Scheduled work must be cancellable by character session, encounter, world unload and plugin shutdown.
- Bukkit/Paper `/reload` is unsupported. Code, schema and provider-version changes require restart.
