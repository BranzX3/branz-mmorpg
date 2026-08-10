# Implementation Roadmap

The coding order prioritizes identity and recovery before gameplay breadth.

Milestones below are planning groups only. Their historical implementation notes describe code
coverage, not delivery completion. Work proceeds as a single active player-facing vertical slice,
and a feature is complete only after the full gate in `42-ai-coding-handoff.md`: usable runtime
entry/exit, connected Paper/content/persistence paths, actionable failure handling, automated tests
and a real local client acceptance pass including reconnect/restart. Until that evidence exists,
the feature remains `IN_PROGRESS` or `AUTOMATED_VERIFIED` even when a note below says its
implementation is complete or ready for acceptance.

## Current single-feature delivery queue

1. **Physical gameplay item authority refactor — `IN_PROGRESS`.** Replace the temporary
   `MAIN_HAND`/Scene-deck interaction model with the authored physical model from ADR 0025: weapons
   and consumables live in hotbar 1–8, selected hotbar weapon drives combat authority, shield/armor
   use native physical slots, Chronicle owns build/virtual/cosmetic state, and production world mobs
   never use training-only hidden health. Migrate legacy main-hand locations idempotently while
   preserving item UUID/payload/durability and reconnect/restart truth.
2. **Local Character Scene/build configuration — `NOT_STARTED` for renewed acceptance (blocked by
   feature 1).** Preserve the already-tested world-backed Scene lifecycle/recovery, but remove
   ordinary weapon/shield/native-armor/consumable equip from the Scene acceptance target. Re-verify
   Chronicle after its Character & Equipment page becomes inspection plus virtual/build management.
3. **Training Sword physical combat loop — `NOT_STARTED` (blocked by features 1–2).** A player with
   a dev-granted MMO-owned sword must move it to any hotbar slot 1–8, select/draw it, hit/miss, kill
   an ordinary world cow through one authoritative health path, defend with a physical off-hand
   shield, take MMO damage, die/respawn and reconnect without a Chronicle weapon-equip transaction.
4. **Additional weapon families, magic, progression, consumables, encounters/social and
   lifeskills — `NOT_STARTED` for delivery purposes.** Existing kernels and automated coverage will
   be audited and completed one player-facing feature at a time; historical Milestone 0–8 labels
   do not waive live acceptance.

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
Atomic Rest allocation/refill persistence is implemented with exact Infusion Stock CAS; its player-facing preparation entry is being moved from Chronicle to the Rest interaction under ADR 0025,
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
operation identities across restart. The live adapter now serializes V0010 before gameplay effects,
checkpoints active timers and restores matching attempts after V0009 recovery. The boss-party
downed path is ready for restart acceptance; general party membership is the next social slice.

The pure party kernel now owns five-member capacity, invitation lifecycle, leader transfer,
five-minute reconnect grace, leave/kick/disband and bounded ready checks with operation replay.
The live Paper adapter now exposes those transitions through `/mmo party`, binds ready online party
members to boss admission and reconstructs membership only for active durable checkpoint encounters.
The pure LFG kernel now owns stable party listings, public activity/region/language/role matching,
bounded experience notes, leader approval by default, optional automatic joining and admission-time
capacity. Hidden Mastery cannot enter its public requirement tags. The live process-local directory
now exposes those flows through `/mmo lfg` and installs acceptance only after PartyEngine admission.
Party/LFG/boss/downed flows are ready for in-game acceptance; personal rewards are the next
Milestone 7 slice.

The pure personal-reward freeze now owns category-based participation floors, cutoff/membership/AFK
checks, stable rejection reasons and deterministic per-character grant identity/roll seeds. Durable
V0011 now stores one journaled grant per encounter/attempt/character and advances it monotonically
through frozen, rolled and delivered recovery states. Encounter content now supplies validated
eligibility and weighted stackable-lot reward tables; deterministic rolling derives item quantity
and lot identity without mutable RNG. A canonical fail-closed payload codec preserves frozen
evidence, rolled outcome and delivery receipt. The live boss adapter now checkpoints category
evidence in V0009 schema V2, freezes a stable victory tick, persists V0011 before each roll/value
effect and delivers independent lots to Pending Rewards before marking the encounter complete.
Restart and exact reconciliation replay cannot duplicate a grant or lot. Milestone 7's remaining
work is Death Pouch and duel/arena profile hooks.

The pure Death Pouch planner now freezes the open-world 10% carried-wallet loss, seven-day expiry,
stable pouch/debit saga identities and explicit boss/PvP/zero-loss suppression. Durable pouch state,
wallet-provider reconciliation and live owner-only recovery remain the next Death Pouch slices.
V0012 now stores the non-spendable debit intention, active pouch, recovery credit and terminal expiry
as journaled optimistic transitions with stable recovery/expiry queries. The wallet capability and
live owner-only world adapter are next.
V0013 now supplies a durable local carried-wallet authority with atomic journal/audit operations,
exact debit/credit replay, insufficient-funds rejection and serialized concurrent first writes. It
is the local acceptance boundary while the external wallet capability remains deferred; live death,
owner-only rendering, recovery and expiry are next.
The live Paper adapter now turns eligible open-world deaths into persist-before-debit pouches,
renders markers only to their owner, requires proximity for recovery and reconciles debit, credit
and expiry ambiguity across restart. Boss and player-killer deaths are suppressed, and local durable
fund/simulate/status/recover commands make the complete Death Pouch slice ready for in-game
acceptance. Milestone 7's remaining work is explicit duel/arena profile integration.
The pure PvP match kernel now owns duel consent/expiry, countdown-gated hostile permission,
two-team arena elimination, surrender, boundary forfeits, disconnect grace and exact operation
replay. Its canonical profile freezes separate damage/healing/guard/CC values and forbids durability
or Death Pouch loss. Live Paper combat, command and safe-defeat wiring is the final Milestone 7
slice.
The environment-gated Paper PvP Lab now wires that kernel to `/mmo pvp` duel and two-team arena
commands, a configurable local safe-region radius, countdown/boundary/disconnect handling and
opponent-only hostile permission across melee, projectile and Staff spell targeting. Lethal damage
becomes safe defeat, Flask healing uses the PvP profile, external consumables are rejected and
ammo/catalyst/crossbow state remains on the pre-match durable snapshot. Death Pouch and progression
evidence are suppressed. Milestone 7 is implementation-complete and ready for its in-game
acceptance pass; Milestone 8 is the next planned development boundary.

## Milestone 8 — Lifeskill kernel

- Rank/Mastery/Focus;
- tools/workwear;
- nodes, Gathering, Fishing, Hunting;
- processing recipe engine;
- Node/Recipe Lab and declarative crash-point fixtures.

**Done when:** node reservation and harvest remain correct through chunk unload/crash.

**Implementation status (`newmmo`): In progress.** The pure progression kernel now owns stable
`lifeskill.*` identities, all thirty visible Rank labels, content-authored cumulative promotion
thresholds and exact committed-evidence replay. Visible Mastery composes the six declared sources,
clamps at 1000 and follows diminishing curves bounded by the published work-speed, basic-yield and
relative rare-yield caps. Life Focus now recovers from an offline-safe wall-clock anchor, clamps at
100, permits normal work at zero and spends one to five points through an exact work-operation
boundary. Durable node reservation/harvest is the next Milestone 8 slice.
The pure resource-node kernel now separates personal Common slots from first-actor shared Rich/Rare
slots, freezes exact tool/durability/Focus/yield inputs at reservation, enforces authored work and
timeout boundaries, emits one harvest intent at commit and recovers charges from wall-clock state.
Restart releases pre-commit reservations while committed depletion survives; exact operation replay
cannot emit a second harvest. V0014 now journals the exact node document, actor Lifeskill/Focus
document, durable tool payload and Pending Rewards output lots in one PostgreSQL transaction.
Committed replay is read-only, partial node/tool/output crash checkpoints roll back and retry once,
and startup can query every non-available node for wall-clock reconciliation. The LOCAL/INTEGRATION
Paper Node Lab now compiles the authored Common iron node, thirty rank thresholds, exact durable
pickaxe and iron-ore yield from the active content snapshot. Its environment-gated commands reserve
the signed tool, wait for the authored commit tick, atomically spend durability/Focus, advance Rank
and place one deterministic lot in Pending Rewards. Exact replay creates no second lot; startup and
five-second wall-clock reconciliation release pre-commit reservations and advance recovery without
chunk state. This node slice is ready for its in-game acceptance pass; tools/workwear breadth,
Fishing, Hunting and processing remain planned Milestone 8 work.

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
