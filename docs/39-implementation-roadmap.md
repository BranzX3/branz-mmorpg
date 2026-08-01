# Implementation Roadmap

The coding order prioritizes identity and recovery before gameplay breadth.

## Milestone 0 — Repository and quality gate

- Gradle multi-module project.
- Java toolchain, formatting, static analysis, unit-test framework.
- CI, local Paper runner and test fixtures.
- Error/result contracts and stable ID value types.
- `mmo-content` CLI shell, diagnostic codes and generated schema pipeline.

**Done when:** empty plugin boots/shuts down cleanly and CI packages reproducibly.

## Milestone 1 — Content foundation

- schemas and definition registries;
- content snapshot/manifest loader;
- validator and example content;
- stable-ID autocomplete, content catalog and reverse-reference index;
- validation HTML/JSON report;
- provider interfaces and health reporting;
- pinned integration adapters skeleton.

**Done when:** invalid references/invariants fail before player sessions.

## Milestone 2 — Persistence and transactions

- PostgreSQL migrations;
- character lease/session;
- transaction journal/idempotency;
- item/lot locations, Pending Rewards, Overflow and Quarantine;
- audit and reconciliation framework.

**Done when:** crash-point tests cannot duplicate or delete a test item/currency grant.

## Milestone 3 — Item/equipment and Scene shell

- Item Engine and asset adapter;
- hotbar slot 9 enforcement;
- native/virtual equipment;
- Local Scene session, preview actor interface and commit/cancel;
- resource-pack gate;
- environment-gated `/mmo dev`, Content Browser and Scene/UI Lab shell.

**Done when:** equipment/cosmetic preview survives disconnect/crash without persistent ghost changes.

## Milestone 4 — Combat kernel

- state machines and InputRouter;
- ActionTimeline, move registry, hitboxes and damage;
- stamina, dodge, guard, posture and CC;
- debug visualization, combat trace export/replay and deterministic simulations.

**Done when:** one weapon test kit passes latency/state/interrupt acceptance tests.

## Milestone 5 — Weapon families and magic

- five V1 weapon runtimes;
- quiver/ammo and crossbow persistence;
- mana, spell runtime, Ember art and Runic Imbuement;
- techniques, forms and attunement.

**Done when:** each family completes a full encounter and save/reload cycle.

**Implementation status (`newmmo`): Complete.** Greatsword, Sword and Shield, Bow, Crossbow and
Staff have integrated executable-action coverage. Linked equipment, Quiver/ammo, Crossbow
checkpoint, catalyst durability and character build/attunement have PostgreSQL reconnect/restart
coverage. Ember Direct/Projectile/Zone/Channel and Runic Imbuement share the authoritative spell
commit boundary; transient encounter effects are intentionally not reconstructed after restart.

## Milestone 6 — Progression and consumables

- Mastery/conditioning evidence;
- teaching and renown;
- Flask, consumable categories and six ailments;
- Rest Context and boss Flask snapshot.

**Done when:** anti-dummy tests, boss retry and qualitative feedback pass.

**Implementation status (`newmmo`): In progress.** The deterministic Mastery/Body Conditioning
evidence kernel and local Progression Evidence Lab are implemented with bounded factors,
idempotency input, soft repetition/daily decay, five qualitative readiness bands and explicit
anti-dummy suppression. Durable batching, live encounter emission, learning/teaching/Renown,
Flask/consumables/ailments and boss snapshot restore remain before Milestone 6 completion.

## Milestone 7 — Encounters, rewards and party

- territory/population/encounter controller;
- personal rewards, Death Pouch and eligibility;
- party, LFG, downed/revive;
- duel/arena profile hooks.

**Done when:** party boss wipe/victory/reconnect/reward flows are idempotent.

## Milestone 8 — Lifeskill kernel

- Rank/Mastery/Focus;
- tools/workwear;
- nodes, Gathering, Fishing, Hunting;
- processing recipe engine;
- Node/Recipe Lab and declarative crash-point fixtures.

**Done when:** node reservation and harvest remain correct through chunk unload/crash.

## Milestone 9 — Production, farming and workers

- Cooking, Alchemy, Smithing;
- workshops and Civic Influence leases;
- farming plots;
- worker reservation/offline completion.

**Done when:** 24-hour offline simulation produces exactly one result and no unreserved input.

## Milestone 10 — Market and city economy

- Central Exchange matching;
- unique listings and commissions;
- city profiles, demand and regional cargo;
- Market Warehouse/Balance and risk controls;
- economy simulator scenarios and market/city inspection tools.

**Done when:** partial fills, cancellation, linked crash points and cargo exclusion tests pass.

## Milestone 11 — Mounts, storage and travel

- mount UUID projection and stable;
- cargo/caravan/recovery;
- city storage, freight and overflow;
- map discovery, coach and ferry.

**Done when:** mount logout/crash and freight arrival never duplicate cargo.

## Milestone 12 — Narrative and live loop

- quests, dialogue, NPC services and cutscenes;
- activity boards, factions and guild basics;
- onboarding and first regional arc;
- notification inbox;
- quest/dialogue graph validation and test-state editor.

**Done when:** a fresh player completes the full V1 loop through first boss and market sale.

## Milestone 13 — Hardening and release

- load tests to budget;
- observability dashboards and alerts;
- staging migration/restore rehearsal;
- security/anti-exploit review;
- accessibility and localization matrix;
- production runbooks;
- authoring-tool acceptance matrix and PR preview expiry/security review.

## Dev-tool delivery rule

Tooling is delivered with the subsystem it validates rather than postponed until the end. Each milestone must expose enough inspection, fixtures and deterministic tests to diagnose its state. `43-content-authoring-tools.md` is authoritative for tool behavior and environment restrictions. A full drag-and-drop web editor remains a compatible extension, not a V1 release blocker.

## Coding prompt discipline

Each coding task must cite owning documents, list invariants, define failure/recovery behavior and include automated tests. Do not ask an agent to implement “the MMO system” in one change. Each PR should have one bounded domain and migration impact statement.
