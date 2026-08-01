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
evidence kernel, durable PostgreSQL evidence batches and Player Session reload path are implemented
with bounded factors, exact idempotent replay, atomic conflict rollback, soft repetition/daily
decay, five qualitative readiness bands and explicit anti-dummy suppression. The local dev tools
can simulate without mutation or persist a fixed evidence UUID to verify reconnect/restart behavior.
Live melee, projectile and spell outcomes now emit bounded Mastery/Conditioning batches without
per-hit awards. The permanent-learning prerequisite resolver, synchronous player-teaching state
machine and non-combat Renown resolver are implemented. V0006 atomically persists student Technique
Knowledge, teacher Renown/deed and exact teaching completion, reloads both Player Sessions and gates
selected Techniques on learned state; environment-gated simulation and durable two-player fixtures
expose the path in game. Live teaching now consumes successful server combat actions, enforces
authored learning/teaching Mastery bands and atomically commits the completed challenge. Form/Spell
acquisition policies now gate preview/commit/combat and V0007 persists their idempotent source
completion. The deterministic Flask allocation/refill/mercy/consumption kernel, consumable
commit/category state machines and six-ailment buildup/decay/reapplication kernel are implemented
with an in-game simulation lab. The `.4` content bundle now authors all five consumable categories,
Infusion Stock and all six core ailments; startup compiles and requires the complete ailment set.
V0008 persists one canonical restart-safe Flask/effect/ailment document through the shared
transaction journal and reloads it with the Player Session; the environment-gated Consumable Lab
can write and inspect the durable fixture. The same document now carries a backward-compatible
prepared boss Flask snapshot bound to one checkpoint-instance UUID; capture/confirmed-wipe restore
is journaled and rejects ordinary death or checkpoint mismatch. The live party-wipe trigger belongs
to the Milestone 7 encounter controller. The live Flask hotbar representation and durable use path
are now implemented with commit-before-restoration, interrupt/recovery and reconnect behavior.
Atomic Rest allocation/refill is now available through Chronicle with exact Infusion Stock CAS,
Mercy fallback, stale rollback and restart coverage. Normal signed consumable lots now use authored
timelines, shared combat action ownership and an atomic lot/effect commit with rare-replacement and
bounded duration checkpoints. Milestone 6 implementation is ready for its in-game acceptance pass;
the live party-wipe trigger remains owned by the Milestone 7 encounter controller.

## Milestone 7 — Encounters, rewards and party

- territory/population/encounter controller;
- personal rewards, Death Pouch and eligibility;
- party, LFG, downed/revive;
- duel/arena profile hooks.

**Done when:** party boss wipe/victory/reconnect/reward flows are idempotent.

Implementation status: the pure boss lifecycle kernel now owns locked participant availability,
1,200-tick reconnect/boundary grace, confirmed wipe, reset-attempt and victory/reward operation
boundaries. An environment-gated live Paper lab now captures the shared prepared-Flask checkpoint,
feeds real death/quit/rejoin signals into that kernel and restores only the Flask after confirmed
wipe. Durable encounter storage/restart recovery is next; rewards, Death Pouch, party/LFG,
downed/revive and PvP hooks remain.

V0009 and its journaled optimistic repository now provide canonical encounter records, exact replay,
audit and ordered non-completed recovery lookup. Wiring the live controller to persist-before-effect
and resume `RESETTING`/`VICTORY_PENDING` work is the next restart-safety slice.

The live controller now serializes participant events, persists every phase before effects and
recovers `ACTIVE`, `WIPE_PENDING`, `RESETTING` and `VICTORY_PENDING` records on startup. Active grace
is rebased to the new server clock and reset operations replay safely. The boss wipe/retry/reconnect
boundary is ready for in-game restart acceptance; authored rewards and party systems remain.

The pure party-PvE downed kernel now owns the one-revive encounter allowance, 15-second downed
window, four-second interruptible channel, 25% revive and Execute/solo/second-defeat death rules.
The Paper adapter now binds those rules to real lethal damage in multi-player boss attempts and
provides `/mmo downed` acceptance controls. V0010 now supplies a journaled optimistic downed-state
repository and recovery index. Its canonical codec/store now preserves remaining tick durations and
operation identities across restart. Activating persist-before-effect recovery in the live adapter,
then general party membership, are the next social slices.

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
