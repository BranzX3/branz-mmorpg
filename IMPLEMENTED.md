# Implementation Map

## Milestone 0 — first bounded task

Implemented:

- `docs/03-architecture.md` — Gradle module skeleton and provider boundaries.
- `docs/04-identifiers-content-contracts.md` — stable definition and instance ID value types.
- `docs/36-content-dev-pipeline.md` — immutable manifest model/parser and reproducible JAR settings.
- `docs/39-implementation-roadmap.md` — typed result/error contracts, CLI shell, formatting/static-analysis quality gate and empty Paper bootstrap.
- `docs/43-content-authoring-tools.md` — stable diagnostic-code catalog and `mmo-content` command shell.

Tests:

- stable-ID format and invalid-ID rejection;
- typed result success/failure propagation;
- valid, invalid and missing content manifests;
- CLI manifest validation;
- bootstrap enable/disable lifecycle.
- local Paper boot/shutdown smoke path (`runServer -PsmokeTest=true`).

Not implemented:

- definition schemas/registries and reference validation (Milestone 1);
- persistence, migrations, leases or transactions (Milestone 2);
- gameplay listeners, items, combat or provider adapters.

Migration impact: none. This task creates no persistent schema or runtime value.

## Milestone 1 — registry and reference-validation slice

Implemented:

- `docs/04-identifiers-content-contracts.md` — immutable `ContentSnapshot`, typed
  `DefinitionRegistry` and startup-before-session validation result.
- `docs/36-content-dev-pipeline.md` — YAML parsing through one compiled definition model.
- `docs/43-content-authoring-tools.md` — schema-owned reference rules, direct/reverse reference
  index, source-located diagnostics and `mmo-content references`.

The first schema slice recognizes item, move, spell, status, scene, city, trade-good,
lifeskill-node, node-region, mount, worker-job and trait namespaces. Type-aware references are
compiled for item traits, node yields, city trade goods, and worker city/node/item inputs and
outputs.

Tests:

- immutable snapshot/body behavior;
- valid direct and reverse references;
- missing references and wrong reference types;
- malformed stable IDs inside references;
- duplicate definition IDs;
- CLI directory validation and reference lookup.

Not implemented in this slice:

- full field/range/budget schemas for every public definition;
- JSON Schema generation and editor autocomplete;
- HTML/JSON validation reports and content catalog service;
- alias/migration compatibility analysis.

Migration impact: none. Content schema version remains `1`; no persistent data is written.

## Milestone 1 — schema generation and validation-report slice

Implemented:

- shared field/type/range/enum/unit metadata used by both runtime validation and schema generation;
- documented constraints for item, move, spell, status, Scene, city, node, worker and mount
  definitions;
- generated Draft 2020-12 JSON Schemas and a combined editor schema;
- checked-in VS Code YAML schema associations;
- JSON and self-contained HTML validation report writers;
- `mmo-content schema` and `mmo-content report` commands;
- Gradle `:mmo-content:generateContentSchemas` task.

Documented constraints include the move target cap, node action duration, city authored-list
sizes and demand range, worker offline cap, Scene preview distance, supported hitbox/spell/node
enums, and V1 mount death/combat invariants. Values without an authoritative bound remain
unbounded rather than receiving invented limits.

Tests:

- required fields, types, ranges, enums and units in generated schemas;
- field-range rejection before snapshot activation;
- boolean V1 invariants in generated schema;
- deterministic combined schema;
- JSON report structure and HTML escaping.

Not implemented in this slice:

- recipe, quest/dialogue and encounter schemas (their definition models are not implemented yet);
- localization and asset-provider catalog validation;
- schema compatibility diff and migration classification;
- catalog HTTP service.

Migration impact: none. Existing content schema version remains `1`.

## Milestone 1 — content catalog and completion slice

Implemented:

- immutable catalog entries derived from the activated `ContentSnapshot`;
- deterministic catalog ordering by stable ID;
- search across stable ID, definition type, source, status, owning team, asset ID, authored tags,
  localization locales and compiled textual metadata;
- direct and reverse references from the shared runtime `ReferenceIndex`;
- optional catalog fields for status, owner, revision, localization coverage and alias history when
  definitions declare them;
- `content-catalog.json` and `stable-id-completions.json` exports;
- `mmo-content search` and `mmo-content catalog` commands.

Tests:

- catalog lookup and stable ordering;
- search by authored value and definition type;
- reverse-reference catalog data;
- deterministic stable-ID completion export;
- CLI search and catalog artifact generation.

Alias/migration validation is intentionally not implemented here because the current authoritative
documents require aliases and migration windows but do not define their source-file schema. No
alias file shape or migration behavior was invented.

Migration impact: none. Catalogs are read-only generated artifacts.

## Milestone 1 — local catalog HTTP service slice

Implemented:

- `mmo-content serve-catalog [path] [port]`, defaulting to port `8765`;
- loopback-only binding with no remote or production deployment behavior;
- one immutable startup snapshot shared with CLI validation and catalog generation;
- canonical JSON serialization shared by artifact export and HTTP responses;
- read-only health, full-catalog, search, definition and reference endpoints under `/api/v1`;
- stable JSON errors for invalid IDs, missing definitions and unknown routes;
- HTTP `405 Method Not Allowed` rejection for every mutation attempt.

The local API exposes:

- `GET /api/v1/health`;
- `GET /api/v1/catalog`;
- `GET /api/v1/search?q=<query>`;
- `GET /api/v1/definitions/<stable-id>`;
- `GET /api/v1/definitions/<stable-id>/references`.

Tests:

- health and immutable-snapshot metadata;
- catalog search, definition lookup and reverse references;
- invalid ID, missing definition and unknown-route responses;
- mutation rejection and `Allow: GET`;
- CLI port validation.

Failure/recovery behavior: invalid content prevents the server from starting, an unavailable port
returns a command error, and the process must be restarted after source changes. No endpoint writes
source content or persistent state.

Migration impact: none. The service is a local read-only projection of compiled content schema
version `1`.

## Milestone 1 — provider readiness and startup acceptance closure

Authoritative contracts:

- `docs/02-system-invariants.md` — immutable runtime content snapshots;
- `docs/03-architecture.md` — typed provider health, optional degradation and required-provider
  maintenance;
- `docs/04-identifiers-content-contracts.md` — incompatible content fails before players load;
- `docs/36-content-dev-pipeline.md` — provider pins belong to immutable content artifacts;
- `docs/39-implementation-roadmap.md` — provider reporting, adapter skeletons and Milestone 1
  acceptance gate.

Implemented:

- deterministic immutable `ProviderRegistry` and aggregate `ProviderHealthReport`;
- `READY`, `DEGRADED` and `MAINTENANCE` provider readiness;
- fail-safe conversion of provider health exceptions to `UNAVAILABLE`;
- exact-version capability skeletons for Oraxen, MythicMobs, PacketEvents, WorldGuard and a generic
  wallet plugin;
- additive `providerVersions` pins in `content-manifest.json`;
- Bukkit plugin-capability capture on the main thread before async startup work;
- asynchronous content parse/validation with main-thread snapshot activation;
- a login gate registered before content loading begins;
- safe maintenance for invalid content, required-provider failure or startup exceptions;
- degraded startup for optional-provider failure;
- valid and deliberately invalid Milestone 1 example content;
- valid and invalid Paper smoke-test modes.

The adapter skeletons perform presence, enabled-state and exact-version capability checks only.
Vendor-owned item, mob, packet, region and wallet operations remain deferred to their owning
runtime milestones; gameplay modules still do not call vendor APIs directly.

Acceptance tests:

- healthy, degraded, unavailable, throwing and duplicate provider cases;
- missing, disabled and version-mismatched plugin capabilities;
- missing required provider pin;
- invalid reference blocks session activation;
- invalid V1 mount invariant blocks session activation;
- optional provider failure allows degraded sessions;
- required provider failure blocks sessions;
- startup lifecycle rejects sessions during loading, maintenance and shutdown.

Failure/recovery behavior:

- source content is never activated when parsing or validation fails;
- startup exceptions expose only their type and fail closed;
- enabled providers without an artifact pin report unavailable;
- changing content/schema/provider versions requires restart; Bukkit `/reload` remains unsupported;
- no startup path writes ownership, currency or persistent gameplay state.

Schema/config impact: `providerVersions` is an additive immutable manifest map. Existing schema
version `1` manifests still parse; an enabled integration without its pin becomes unavailable
instead of receiving a guessed version.

Migration impact: none. No database or persistent player data exists in this milestone.

## Milestone 1 status

Milestone 1 is complete. Invalid references and invariants are rejected by the bootstrap startup
gate before character sessions can activate. All remaining vendor operations, persistence and
gameplay behavior belong to later roadmap milestones.

## Milestone 2 — PostgreSQL migration foundation slice

Authoritative contracts:

- `docs/35-persistence-transactions.md` — PostgreSQL, explicit forward migrations, character lease
  and transaction journal fields;
- `docs/37-operations-security-testing.md` — real PostgreSQL integration testing;
- `docs/39-implementation-roadmap.md` — PostgreSQL migrations before lease/repository runtime;
- `docs/42-ai-coding-handoff.md` — migration runner precedes provider/player event integration.

Implemented:

- deterministic classpath migration index and forward-only version ordering;
- SHA-256 checksum recording and verification for every applied migration;
- rejection of modified and unknown applied migrations;
- one PostgreSQL transaction-scoped advisory lock for concurrent startup serialization;
- atomic DDL, migration-history insertion and rollback for the complete pending batch;
- stable typed migration error codes without database credentials or SQL text in failure details;
- `V0001__lease_and_transaction_foundation.sql`;
- real disposable PostgreSQL integration tests using pinned embedded server/driver dependencies.

Migration `V0001` creates:

- `character_leases` keyed by character UUID, with server/session identity, optimistic version,
  acquisition/heartbeat/expiry timestamps and expiry index;
- `mmo_transaction_state` with `PREPARED`, `COMMITTED`, `ROLLED_BACK` and `QUARANTINED`;
- `transaction_journal` with unique idempotency key, character/session context, operation type,
  state, JSONB reservations/outputs, content version and timestamps;
- state/update index for reconciliation scans.

Tests:

- deterministic default catalog and duplicate-version rejection;
- first apply and idempotent second apply;
- exact checksum enforcement and unknown-applied-version rejection;
- failed SQL rolls back earlier DDL and migration history;
- concurrent migration runners serialize through the advisory lock and install once;
- PostgreSQL enum, JSONB-compatible schema and expected tables are created.

Failure/recovery behavior:

- connection failure returns `MIGRATION_DATABASE_UNAVAILABLE`;
- lock failure returns `MIGRATION_LOCK_FAILED`;
- checksum drift or an unknown database version fails closed before new SQL executes;
- any failed pending migration rolls back the whole batch;
- production rollback is forward-fix only; applied migration files must not be edited.

Threading: the runner is synchronous/blocking and is not called on a Paper server thread. Bootstrap
database wiring will run it in the asynchronous startup phase in the lease/session slice.

Non-goals for this slice: character lease acquisition/heartbeat, journal state transitions,
item/lot repositories, grants, reconciliation actions and player login database access.

Migration impact: forward migration `V0001` adds only new PostgreSQL types, tables and indexes. It
does not modify content schema or existing player data.

## Milestone 2 — character lease repository slice

Implemented contracts from `docs/35-persistence-transactions.md` and
`docs/41-cross-system-acceptance.md`:

- durable `CharacterId` and transient `SessionId` value types;
- immutable character lease model with server instance, session, optimistic version and
  acquisition/heartbeat/expiry timestamps;
- PostgreSQL-authoritative lease time to avoid cross-server JVM clock disagreement;
- idempotent acquire retry for the same live server/session;
- explicit live conflict outcome for a competing session;
- explicit recovery-required outcome for every expired lease;
- version-checked expired-lease reassignment;
- ownership/version-checked heartbeat that cannot revive an expired lease;
- ownership/version-checked release with idempotent already-released result;
- unique session-ID enforcement across characters;
- stable typed failure codes without SQL text or connection credentials.

State transitions:

```text
missing -> ACQUIRED(version=1)
same live owner -> ALREADY_HELD(no mutation)
different live owner -> CONFLICT(no mutation)
expired -> RECOVERY_REQUIRED(no mutation)
RECOVERY_REQUIRED + expected version -> ACQUIRED(new owner, version+1)
live owner heartbeat + expected version -> live lease(version+1)
live owner release + expected version -> missing
```

Recovery invariants:

- expiry never transfers ownership automatically;
- an old session cannot heartbeat or release a recovered lease;
- optimistic-version mismatch requires reload rather than overwrite;
- database/connection ambiguity returns failure and never falls back to an in-memory lease;
- repository calls are blocking and must execute outside Paper server threads.

Integration tests on disposable PostgreSQL cover:

- same-session acquire retry and competing live-session conflict;
- expired inspection, stale recovery rejection and successful versioned recovery;
- stale heartbeat and expired-heartbeat rejection;
- wrong-owner release, successful release and idempotent release retry;
- simultaneous acquisition producing exactly one owner;
- session UUID collision across characters.

Schema impact: none in this slice; it uses `character_leases` created by `V0001`.

Non-goals: Bukkit login wiring, heartbeat scheduling, character record loading and expired-session
domain reconciliation. These follow after the repository semantics are proven.

## Milestone 2 — transaction, item/lot location and reconciliation slice

Implemented contracts from `docs/02-system-invariants.md`, `docs/35-persistence-transactions.md`
and `docs/42-ai-coding-handoff.md`:

- typed transaction requests, journal entries, states and stable error codes;
- JSONB-backed reserved-input and intended-output evidence;
- prepare-by-idempotency semantics that compare character/session context, operation, JSON value
  and content version while returning the original transaction for an equivalent retry;
- immutable terminal journal states with idempotent same-state retry;
- one PostgreSQL transaction around journal prepare, item/lot mutation, audit append and journal
  commit;
- globally unique item UUID and lot UUID rows with exactly one inline authoritative location;
- optimistic version, expected owner and expected location compare-and-set on every move;
- foundational `CHARACTER_INVENTORY`, `PENDING_REWARDS`, `OVERFLOW_CLAIM` and `QUARANTINE`
  destinations;
- immutable item payload and lot lineage JSON plus content version and last transaction identity;
- read-only audit inspection by transaction;
- read-only reconciliation detection for stale prepared transactions, non-committed value rows,
  unknown locations, missing owners and zero-quantity lots.

State and retry behavior:

```text
new idempotency key -> PREPARED -> mutation + audit -> COMMITTED
equivalent terminal retry -> prior journal result, no mutation
same key + different request -> IDEMPOTENCY_CONFLICT, no mutation
mutation/CAS failure -> whole database transaction rolls back
crash before commit -> journal, value and audit all roll back together
stale version/owner/location -> reload-required typed failure, never overwrite
```

Migrations:

- `V0002__item_and_lot_locations.sql` adds `item_instance` and `commodity_lot`, owner/location
  indexes, JSONB payload/lineage, optimistic versions and journal foreign keys.
- `V0003__audit_log.sql` adds append-only transaction/subject audit rows and inspection indexes.

Integration tests on disposable PostgreSQL cover:

- semantic JSONB idempotency despite field order/whitespace differences;
- idempotency-key reuse rejection and immutable terminal states;
- invalid JSON rollback;
- simulated crash after journal prepare and after value mutation;
- exactly-once item/lot grants after retry;
- item and lot move replay without a second version increment;
- stale-version and wrong-source rejection;
- two concurrent item moves producing exactly one destination;
- quantity and lineage preservation;
- audit rollback/append behavior;
- reconciliation finding coverage with proof that scanning does not move or delete value.

Failure/recovery behavior:

- database or ambiguous SQL failure returns a typed failure and never falls back to local state;
- a visible `PREPARED` retry is frozen for reconciliation rather than executed speculatively;
- reconciliation is detection-only; it never guesses a repair;
- repository and transaction calls are blocking and must execute outside Paper server threads.

Non-goals:

- Bukkit/login event wiring and character aggregate loading;
- external `branz-wallet` currency reservation/commit integration, pending its MMO adaptation plan;
- equipment, escrow, world-drop and storage locations owned by later runtime milestones;
- automatic reconciliation scheduling or admin repair commands;
- Pending/Overflow retention and notification policy.

Migration impact: forward-only migrations `V0002` and `V0003` add new tables and indexes. Existing
lease and journal rows are unchanged. No content schema, wallet database or authentication data is
modified.

## Milestone 3 — Item Engine and in-game Scene shell slice

Implemented contracts from `docs/14-items-equipment-durability.md`,
`docs/16-scene-ui-hud.md`, `docs/33-scene-ui-hud-accessibility.md` and
`docs/43-content-authoring-tools.md`:

- immutable Item Engine compiled from the already validated `ContentSnapshot`;
- typed unique/durable and stackable-lot item classes, stable asset IDs and cosmetic durability
  rejection;
- immutable `ItemInstance` state with typed authoritative locations, optimistic relocation,
  enhancement bounds and atomic durability invariants;
- immutable native, virtual and cosmetic equipment-slot loadouts;
- provider-neutral item presentation adapter with a safe barrier fallback;
- signed projection references covering value UUID/type, definition, slot, quantity, authority
  version, display revision, content version and optional test provenance;
- deterministic inventory projection reconciliation that removes unknown, tampered, stale,
  misplaced and duplicate copies before materializing a missing authoritative projection;
- character-owned item/lot repository queries in stable location order for the future login
  projection handoff;
- atomic multi-item location moves ordered by UUID for native/virtual equipment swaps; any stale
  owner, location or version rolls the whole transaction and journal back;
- preview-only `SceneSessionManager` with stale-session tokens, modes, back/discard, explicit
  commit boundary and close reasons;
- compact 2D preview provider used when no packet preview actor is available;
- permanent Chronicle system-item marker and hotbar slot 9 reconciliation;
- protection against click, number-key swap, drag, drop, off-hand swap and consume paths;
- death/respawn restoration and duplicate Chronicle cleanup;
- a pure placement planner that refuses to overwrite a full inventory and can swap a displaced
  value into an existing Chronicle slot without deleting it;
- resource-pack admission states with URL/SHA-256 validation against the active manifest;
- inventory-based Scene Hub shell with all V1 root pages and protected synthetic buttons;
- Scene interruption on damage, movement, teleport, world change, death, disconnect and plugin
  disable;
- environment/permission-gated `/mmo dev`, item Content Browser, safe test-projection spawner and
  Scene/UI tester shell;
- non-authoritative dev projections with explicit test provenance; all transfer/use paths are
  blocked and the projection is removed on death/logout/disable;
- `/mmo health` runtime/content/item/pack inspection.
- verified Paper player UUID as the V1 `CharacterId` boundary (one account maps to one character);
- asynchronous PostgreSQL session admission before the player inventory is unlocked;
- live character lease acquisition, expired-lease reconciliation, heartbeat, release and
  conflicting-session rejection;
- embedded PostgreSQL for `LOCAL`/`INTEGRATION` with a persistent plugin data directory, plus
  configurable external PostgreSQL for other environments;
- character-owned item/lot loading and deterministic signed Bukkit inventory reconciliation on
  login, reconnect and respawn;
- persisted and audited `/mmo dev` item/lot grants with test provenance;
- Scene Equipment main-hand preview followed by one atomic database transaction on Confirm;
- native main-hand projection to hotbar slot 1, including safe relocation of a displaced vanilla
  stack and database-authoritative reconstruction after reconnect or server restart;
- `/mmo health` character database readiness, character UUID and lease version.

Runtime sequence:

```text
validated snapshot
  -> compile Item Engine
  -> verify configured pack SHA-256 against manifest
  -> enable player admission
  -> pack ready/disabled-local
  -> safely reconcile Chronicle slot 9
  -> Chronicle right-click opens compact Local Scene Hub

PostgreSQL character-owned rows
  -> signed expected projections
  -> remove invalid/stale/duplicate observed copies
  -> materialize each missing database-authoritative value once

verified proxy identity / Paper UUID
  -> acquire character lease asynchronously
  -> reconcile incomplete journal evidence before expired-lease recovery
  -> load inventory and equipment truth
  -> apply signed projection
  -> unlock the player and heartbeat the lease

Scene equipment preview
  -> no database mutation
  -> Confirm
  -> atomic inventory/equipment location move
  -> reload character snapshot
  -> reconcile native main hand
```

Tests:

- Item Engine compiles durable and lot definitions and rejects cosmetic durability;
- equipment loadouts remain immutable;
- Scene close/disconnect discards preview state;
- rejected Scene commit cannot alter committed equipment;
- stale callbacks cannot mutate a newer Scene session;
- Chronicle placement preserves occupied slot value, handles full-inventory swap, removes only
  duplicate system items and fails safely when no destination exists;
- projection reconciliation keeps one exact authoritative copy and repairs missing, duplicate,
  stale, misplaced, unknown and tampered projections;
- projection HMAC changes when slot, quantity, revision or provenance changes;
- PostgreSQL owner queries exclude another character and return a deterministic location order;
- a two-item equipment swap leaves both original locations/version numbers unchanged when either
  compare-and-set is stale, then commits/replays exactly once with valid expectations;
- item relocation checks optimistic version and durability remains an atomic valid pair.
- a persisted dev grant survives clean lease release and reconnect;
- a unique item moves from character inventory to native main hand and survives both reconnect and
  complete embedded-PostgreSQL restart;
- a second live session for the same character is rejected while the first lease is valid.

Failure/recovery behavior:

- invalid Item Engine content or enabled pack configuration mismatch enters maintenance before
  sessions are accepted;
- local development may explicitly disable pack delivery; enabled environments require a manifest
  SHA-256 match;
- missing packet preview features degrade to compact 2D Scene rather than disabling ownership;
- the player remains locked while its resource-pack and PostgreSQL character session load;
- database startup/migration failure enters maintenance and admits no MMO session;
- Scene Cancel/Back/disconnect discards preview state without a database write;
- Scene Confirm applies one equipment-slot change atomically and reloads database truth before the
  client projection changes;
- `/mmo dev` writes authoritative test-provenance rows and an audit journal, while gameplay
  transfer/use paths remain blocked;
- every interruption discards the in-memory preview and closes its preview handle.

Non-goals in this slice:

- provisioning/configuring the proxy authentication stack in this repository; the Paper boundary
  consumes the UUID already verified by the proxy;
- Oraxen item-stack resolution (the Paper codec currently renders the safe barrier fallback);
- equipment UI for every native/virtual/cosmetic slot (the live slice exposes main hand first);
- full-body owner-only packet actor and validated placement candidates;
- real resource-pack hosting and cross-client visual acceptance;
- external wallet reservation/commit integration.

Migration impact: no new migration beyond Milestone 2's `V0001`-`V0003`. This slice activates
those tables in the Paper runtime and adds only local database/session configuration.

## Milestone 4 — combat state and input-router kernel slice

Authoritative contracts:

- `docs/02-system-invariants.md` — server authority, no client-declared hits and explicit weapon
  timelines;
- `docs/05-combat-state-machine.md` — orthogonal engagement, weapon, action, UI and encounter
  state;
- `docs/06-input-resolution.md` — semantic input ownership, priority, deduplication and one-slot
  buffering;
- `docs/41-cross-system-acceptance.md` — scroll-spam safety and inventory availability while
  Engaged.

Implemented in `mmo-combat`:

- immutable engagement, weapon, action, UI and encounter state types;
- login/session transient reset to Exploration, Sheathed/Idle and no exclusive UI;
- typed rejection when Scene, Market, Crafting or blocking Dialogue attempts to open while
  Engaged;
- danger and hard-control transitions that close danger-sensitive UI without closing ordinary
  inventory merely because combat is Engaged;
- deterministic weapon draw/sheathe machine with configured integer tick durations;
- latest-target-only hotbar selection, full draw restart when the desired weapon changes during
  Drawing and no intermediate weapon execution during scroll spam;
- Chronicle/non-combat selection sheathing and hard-control draw cancellation;
- four-way dominant-axis direction snapshot with forward/back winning diagonal ties;
- semantic input policy for LMB/RMB/F/Q/Shift, including combat-vs-hard-world-interaction RMB
  ownership and vanilla fallback while Sheathed;
- deterministic same-frame priority, two-tick duplicate-observation collapse and monotonically
  assigned request sequence;
- one buffered primary/secondary request, higher-priority replacement, same-branch refresh,
  twelve-tick expiry and explicit invalidation reasons.

Tests:

- randomized 20,000-step hotbar/tick simulation never creates an illegal weapon snapshot;
- scroll spam sheaths once, draws only the latest slot and cannot skip either duration;
- changing weapon during Drawing restarts the complete draw timeline;
- Knockdown cancels Drawing;
- same-frame Dodge wins over defense, techniques and attacks regardless of collection order;
- duplicate Bukkit/packet observations collapse inside the two-tick window;
- equal/lower buffer replacement is rejected, directional priority may replace neutral, same
  branch refreshes and stale input expires;
- Engaged RMB owns a targeted world interaction while Ready-but-not-Engaged yields to the hard
  interactable;
- Q/F are combat-owned only while Ready;
- Drawing permits one buffered opener and still allows Dodge;
- vanilla inventory remains compatible with Engaged combat while Scene input is rejected.

Failure/recovery behavior:

- illegal UI/action transitions return typed failures and do not mutate the immutable snapshot;
- no transient combat action or buffer is restored on login/session replacement;
- duplicate/stale/locked input is rejected without executing a fallback combat action;
- hard CC, death, weapon/session/world changes, Scene open and encounter reset have explicit
  buffer-clear reasons for the runtime adapter.

Non-goals for this bounded slice:

- packet-level enhanced input capture beyond the Bukkit training adapter;
- authoritative world hitbox collision and damage application;
- guard, posture, CC duration and debug rendering;
- weapon-family content and presentation.

## Milestone 4 — move compiler, ActionTimeline and Paper training slice

Implemented:

- complete typed move contract compiled atomically from `ContentSnapshot`, including semantic input
  branch, integer phases, commit tick, resource/setup costs, movement/facing, hitboxes, impact
  outputs, cancel/chain windows, interrupt profile, presentation archetype and PvE/PvP profiles;
- expanded generated move JSON Schema with field types, bounds, enums and units;
- rejection of commit ticks outside the action, hitboxes outside Active, invalid resource/setup
  costs and invalid cancel/chain windows before Move Engine activation;
- immutable health/stamina/mana ledger with reservation, non-lethal HP cost validation, commit and
  pre-commit refund;
- explicit setup-stamina cost retained on pre-commit cancellation;
- deterministic Windup -> Active -> Recovery -> Complete timeline and exact authored hitbox-open
  trace events;
- committed costs remain spent after interruption, while uncommitted reservations release;
- chain/dodge window queries use the current authoritative server tick;
- canonical combat trace export, deterministic simulation/replay and tamper/divergence detection;
- example `move.training_blade.primary_1` content and manifest count;
- main-thread Paper combat session created only after character/database projection readiness;
- persisted main-hand training blade drives Draw/Sheathe state from hotbar selection;
- arm-swing input routes through the semantic InputRouter, supports one opener buffered during
  Drawing and starts the compiled training move when Ready;
- vanilla entity damage is cancelled while the training combat path owns the weapon; client swing
  never declares an MMO hit;
- action-bar phase/tick/stamina feedback, `/mmo health` combat inspection and `/mmo dev` Training
  Move Tester;
- exploration stamina regeneration after the one-second spend delay.

Automated tests prove:

- complete move compilation and typed invalid timeline/hitbox rejection;
- insufficient stamina rejects before an action exists;
- cancellation before commit refunds all non-setup cost;
- setup cost is the only pre-commit spend;
- cancellation after commit preserves the full spend;
- the hitbox opens once and only on its authored Active tick;
- full trace order is reserve -> commit -> hitbox -> complete;
- identical inputs produce byte-identical canonical traces;
- replay reproduces every event/resource result and rejects a tampered trace.

Failure/recovery behavior:

- Move Engine failure or a missing required training move enters startup maintenance before player
  sessions;
- logout/session reprojection creates a fresh transient combat session; no action, buffer or
  reservation resumes;
- slot change cancels the current timeline, applies pre/post-commit resource rules and clears the
  attack buffer;
- plugin shutdown cancels the tick task and drops transient combat sessions;
- the training adapter cancels vanilla damage until authoritative world hit resolution exists.

Remaining Milestone 4 work:

- shape-specific dimensions/resolvers for CAPSULE/BOX/SPHERE/RAY/PROJECTILE plus
  region/friendly-provider filters;
- persistent MMO health application and death/encounter integration;
- persistent combatant status/health integration and provider-authored posture/CC profiles;
- additional rejection-reason overlays plus persisted/inspectable multi-action trace history;

Schema/config impact: move schema gains the complete runtime fields. Local config adds positive
`combat.weapon-draw-ticks` and `combat.weapon-sheathe-ticks`. Migration impact: none; combat
sessions/resources remain transient.

## Milestone 4 — authoritative training ARC and damage-resolution slice

Implemented:

- pure horizontal ARC resolver with authored range, angle, vertical bounds and target cap;
- eligibility and line-of-sight gates before contact;
- stable limited-target ordering by weak point, distance, angle and entity UUID;
- collection-order-independent resolution proven across shuffled candidate inputs;
- deterministic physical damage resolver using weapon power, move coefficient, flat technique
  power, armor, capped penetration, flat penetration, resistance, conditional advantage and
  PvE/PvP profile;
- canonical 70% armor mitigation, 60% penetration-percent and -30%/60% resistance bounds;
- non-random conditional advantage combination: strongest full, second strongest half-excess,
  total capped at 1.60;
- optional typed item `weapon_profile` compiled by Item Engine; the training blade declares
  `family: SWORD` and `power: 100`;
- Paper Active-tick scan of nearby living entities using Bukkit bounding boxes and server
  line-of-sight;
- players and armor stands are excluded from the current PvE training profile;
- server-resolved MISS/HIT target count and damage feedback plus last resolution in `/mmo health`;
- isolated 1,000-HP training-target accounting without trusting or applying vanilla attack
  damage.

Tests:

- out-of-range, outside-cone, vertical, ineligible and occluded candidates cannot hit;
- weak-point/distance/angle/UUID ordering and authored target cap;
- 100 shuffled input orders produce the same targets;
- exact armor/resistance/advantage breakdown;
- penetration, mitigation and resistance caps;
- 10,000 randomized formula cases prove deterministic repeat, armor monotonicity and penetration
  monotonicity;
- Item Engine compiles the training weapon family/power.

Failure/recovery behavior:

- malformed weapon profiles fail Item Engine activation;
- missing/mismatched training blade profile enters maintenance before sessions;
- Paper cancels vanilla attack damage and reports server ARC resolution only;
- no persistent health/reward/value is mutated by this training slice.

Non-goals:

- applying internal MMO HP to persistent player/enemy combatants;
- PvP/party/region ownership policy and weak-point provider data;
- capsule, box, sphere, ray, projectile and swept-arc runtime;
- knockback, posture, guard, status and encounter rewards.

Schema impact: item schema adds optional `weapon_profile.family` and positive
`weapon_profile.power`. Config/migration impact: none.

## Milestone 4 — live engagement clock slice

Implemented:

- immutable deterministic engagement runtime driven by monotonic server ticks;
- `EXPLORATION -> ALERT` when a hostile mob acquires the player without a committed exchange;
- offensive resource commit and accepted incoming entity damage enter/refresh `ENGAGED`;
- loss of threat enters `DISENGAGING`, with the canonical 160-tick quiet window before
  `EXPLORATION`;
- active threat ownership, encounter hard lock and downed state hold engagement;
- Paper threat ownership follows mob target acquisition/change/death without scanning every world
  entity each tick;
- incoming hostile damage closes an open Scene inventory;
- live engagement state now feeds the semantic input policy instead of a hard-coded
  `EXPLORATION`;
- `/mmo health` exposes engagement state and remaining exit ticks.

Tests:

- alert clears if no hostile exchange occurs;
- disengagement requires the complete quiet window;
- threat, hard lock and downed conditions hold or restore engagement;
- hostile activity refreshes the exit clock;
- 100,000 seeded randomized transitions produce identical runtimes with bounded countdowns.

Failure/recovery behavior:

- invalid/non-positive exit duration enters startup maintenance;
- stale or dead mob threat owners are pruned on the main thread;
- logout and session replacement discard the transient clock and threat set;
- server-tick regression is rejected by the pure engagement runtime.

Non-goals:

- persistent threat tables or encounter hard-lock integration;
- dodge, guard, posture, poise and crowd control;
- out-of-combat HP regeneration and persistent combat health.

Config impact: `combat.engagement-exit-ticks` defaults to `160`. Migration impact: none;
engagement remains transient and is never restored after login.

## Milestone 4 — directional dodge and i-frame training slice

Implemented:

- immutable load-tier dodge profiles and server-tick runtime phases for Light, Medium, Heavy and
  Overloaded;
- canonical stamina costs `25/30/35/40` and i-frame lengths `6/4/2/0` with one startup tick;
- load-scaled training travel/recovery baselines and four authoritative movement steps;
- six-tick Shift candidate window, three-tick movement grace and five-tick stationary crouch
  threshold;
- directional input captured from Paper's current input snapshot with deterministic dominant-axis
  resolution;
- exploration retains vanilla sneak while Alert/Engaged/Disengaging combat routes directional
  Shift through the semantic input router;
- dodge may overlay weapon Drawing, and may cancel an attack only at or after the move's authored
  `dodge_from_tick`;
- dodge clears the one-slot attack buffer and spends stamina without consuming resources reserved
  by another action;
- player movement uses captured facing and a collision-checked path; solid world collision stops
  the remaining step instead of allowing wall traversal;
- accepted entity attacks are cancelled only during the server i-frame phase; same-tick startup
  hits remain valid and Overloaded never gains invulnerability;
- normal attacks remain locked through dodge recovery and stamina regeneration observes the
  post-spend delay;
- `/mmo health` exposes the configured training load and current dodge phase.

Tests:

- exact profile costs, i-frame lengths, travel and total timing;
- startup, invulnerable, recovery and completion boundaries;
- non-dodgeable and Overloaded hits never receive i-frame immunity;
- neutral input, insufficient stamina and active recovery reject deterministically;
- Shift movement grace, crouch hold, release and expiry boundaries;
- direct stamina spend cannot consume reserved stamina;
- Exploration versus Engaged Shift ownership.

Failure/recovery behavior:

- invalid training load enters startup maintenance;
- insufficient stamina and closed cancel windows reject without cancelling the current action;
- logout/session replacement drops dodge, movement and pending Shift state;
- solid collision stops movement while the dodge timeline/recovery continues;
- vanilla environmental damage is never cancelled by this slice.

Non-goals:

- equipment-derived dynamic load calculation;
- PvP recovery tuning and provider-authored undodgeable attack tags;
- animation/root-motion presentation, dodge follow-up techniques and entity/boss phasing policy;
- guard, posture, poise and crowd control.

Config impact: `combat.training-dodge-load` defaults to `MEDIUM`. Migration impact: none; dodge
runtime is transient.

## Milestone 4 — weapon guard and perfect-guard training slice

Implemented:

- immutable weapon-guard runtime with active, perfect, normal, broken and inactive phases;
- deterministic 120-degree front cone including the 60-degree boundary on either side;
- canonical four-tick perfect-guard startup window;
- training weapon guard blocks 80% physical damage; perfect guard produces zero chip;
- normal pressure/stamina applies at 100%, while perfect guard applies 50% pressure/stamina;
- Guard Stability starts at 100, recovers after 30 quiet ticks at 20/s inactive or 8/s active,
  breaks for 24 ticks and returns at 35;
- the same-tick defense resolver used by Paper applies `dodge -> perfect guard -> guard -> hit`;
- non-dodgeable attacks fall through dodge to guard and non-perfect-guardable attacks fall through
  the perfect window to normal guard;
- Paper Engaged RMB toggles the training weapon guard through the semantic defensive-response
  route; another RMB releases it;
- attack, weapon swap, dodge and leaving Engaged state release or block guard as appropriate;
- guarded entity hits spend stamina, apply chip, refresh engagement and report outcome/stability;
- normal stamina regeneration pauses while guard is active;
- `/mmo health` exposes guard phase and Guard Stability.

Tests:

- exact perfect/normal timing, chip, stamina and pressure values;
- front cone boundary, rear hit, unblockable, exhausted and non-perfect-guardable behavior;
- stability depletion, Guard Break duration/reset and active/inactive regeneration rates;
- release/restart creates a new fixed perfect window;
- same-tick dodge precedence and startup/non-dodgeable fallthrough to perfect guard;
- inactive guard falls through to an ordinary hit.

Failure/recovery behavior:

- invalid/non-positive training pressure enters startup maintenance;
- insufficient stamina leaves the hit unguarded without negative resources;
- Guard Break disables guard until its server-tick recovery completes;
- logout/session replacement discards transient guard/stability state;
- input duplication and action locks reject without restarting the perfect window.

Non-goals:

- packet-level RMB release detection; the local Paper-only training adapter uses an explicit
  right-click toggle until the optional packet provider supplies held/release edges;
- shield profiles, elemental leakage, unblockable/provider attack tags and PvP tuning;
- shield profiles, equipment-derived poise and provider-authored attack tags;
- persistent player HP; normal guard chip still flows through the current vanilla training event.

Config impact: `combat.training-incoming-guard-pressure` defaults to `10.0`. Migration impact:
none; guard runtime is transient.

## Milestone 4 — posture, poise and crowd-control training slice

Implemented:

- immutable normal-enemy posture runtime with 100 maximum posture, a 60-tick recovery delay,
  25 posture/second recovery and a 60-tick broken window;
- authored training-slash posture damage is applied after health damage, so the breaking hit does
  not receive the posture-break bonus while later hits during the window receive the canonical
  `POSTURE_BREAK` 1.35 advantage;
- perfect guard applies eight posture damage to a non-player attacker and reports its resulting
  posture state;
- hidden player poise accumulates for ten ticks against a threshold of 30, decays at 30% of the
  threshold per second and accepts a 0..1 hyper-armor multiplier in the pure runtime;
- ordinary unguarded training entity hits apply 35 poise damage and request a six-tick `FLINCH`;
  defended hits never also apply poise;
- the complete physical CC hierarchy is encoded as
  `FLINCH < STAGGER < HEAVY_STAGGER < KNOCKBACK < KNOCKDOWN < LAUNCH < GRAB`;
- stronger CC replaces weaker CC, equal/lower active CC is rejected unless explicitly marked as a
  combo continuation, and continuation duration is halved;
- hard CC grants exact 24-tick PvE or 30-tick PvP post-control immunity at the active end tick;
  stronger categories may break through weaker-category immunity;
- PvP CC uses a 0.60 duration multiplier and same-category 100%/50%/immune diminishing returns
  within 160 ticks;
- Guard Break applies a 24-tick `HEAVY_STAGGER`; accepted CC cancels action/dodge/guard and buffered
  input, interrupts weapon drawing and owns the player action state until expiry;
- `/mmo health` exposes active CC and remaining ticks but intentionally does not expose the exact
  player-poise accumulator.

Tests:

- exact posture damage, break, recovery-delay and recovery boundaries;
- exact poise threshold, resistance, reset, hyper-armor and decay behavior;
- CC replacement, rejection, continuation, immunity and stronger-category breakthrough;
- PvP duration scaling, repeat decay and exact repeat-window reset;
- 100,000 seeded posture transitions and 100,000 seeded CC transitions preserve bounded valid
  runtimes.

Failure/recovery behavior:

- non-finite/negative posture or poise impacts and non-positive CC durations are rejected;
- server-tick regression is rejected by every pure runtime;
- dead, invalid or unloaded training targets discard isolated health/posture state;
- logout/session replacement discards transient poise, CC and posture ownership;
- rejected CC leaves the current action/control state intact.

Non-goals:

- persistent MMO HP/death, encounter rewards and boss-specific posture phases/immunity;
- equipment-derived poise, provider-authored hyper armor and attack-specific CC/poise tags;
- launch/knockback movement, grab pairing, animations and client-side posture-bar presentation.

Config impact: `combat.training-incoming-poise-damage`,
`combat.training-incoming-cc-severity`, `combat.training-incoming-cc-ticks` and
`combat.training-perfect-guard-posture-damage` add local training defaults. Migration impact: none;
all runtimes and isolated target accounting remain transient.

## Milestone 4 — viewer debug and live trace export slice

Implemented:

- pure deterministic ARC outline geometry with bounded arc/radial sample density;
- `/mmo combat debug [player]` toggles inspection for the viewer/target pair only;
- each authoritative ARC opening renders its outline to subscribed viewers with selected,
  non-selected and line-of-sight-rejected target markers; no particles are broadcast globally;
- debug ownership is removed when either the inspecting viewer or target player disconnects;
- live action capture retains the latest completed or cancelled `ActionTimeline`, its initial/final
  resources, cancellation command and content version;
- `/mmo combat trace export [player]` replays the captured trace against the active Move Engine and
  refuses export on divergence;
- verified canonical traces are written beneath the plugin's fixed local `combat-traces` directory
  with create-new UUID filenames;
- debug and export commands reuse the environment-gated `branzmmo.dev` access policy, so production
  cannot enable them through command input alone.

Tests:

- exact deterministic ARC outline endpoints, center ray and sample count;
- invalid debug density is rejected;
- canonical trace bytes are written beneath the configured directory;
- existing complete/cancel simulation fixtures prove byte-identical export, exact replay and
  tamper rejection.

Failure/recovery behavior:

- missing/offline target sessions and missing completed traces return typed player feedback;
- replay divergence fails closed before filesystem output;
- export I/O failures are logged and reported without changing combat state;
- trace filenames cannot include player-controlled path segments and exports cannot escape the
  configured directory;
- logout/session replacement discards transient debug ownership and latest live trace.

Non-goals:

- production observability streaming, database trace retention or cross-server trace lookup;
- persistent multi-action history and full damage/posture/CC packet capture;
- swept-path/projectile visualization and detailed per-candidate rejection text.

Config/migration impact: none. Inspection remains gated by the existing `dev-tools.enabled`,
environment allowlist and `branzmmo.dev` permission.

## Milestone 4 — latency/jitter and training-weapon acceptance kit

Implemented:

- deterministic synthetic input emissions carry a unique client sequence, emission tick, base
  latency, signed jitter, semantic input, captured direction, branch and deduplication key;
- effective simulated delay is bounded to 0..40 ticks and zero-delay input is delivered on the
  next server scheduling boundary rather than retroactively in its emission tick;
- delivery groups inputs into ascending server-tick frames and orders same-frame observations by
  client sequence, independent of fixture collection order;
- jitter can reorder later emissions ahead of earlier ones while InputRouter still assigns
  authoritative server-session sequences and semantic priorities;
- the training-weapon acceptance kit composes weapon draw, delayed opener buffering,
  ActionTimeline completion, canonical trace replay, same-frame dodge priority, guard timing and
  hard-CC interruption through the real pure-domain engines;
- the kit proves a delayed guard starts its perfect window at authoritative arrival, not at the
  untrusted client emission tick.

Tests:

- zero-delay next-tick delivery and positive/negative jitter boundaries;
- stable frames across 1,000 shuffled emission collections;
- delayed Bukkit/packet duplicates still collapse inside InputRouter's two-tick window;
- invalid delays and duplicate synthetic client sequences fail closed;
- a delayed attack buffers during six-tick weapon Drawing, executes on Ready, spends the exact
  authored stamina and replays byte-identically;
- jittered Attack/Dodge in one frame resolves Dodge regardless of arrival-list order;
- delayed guard hits the exact perfect/normal boundary, and pre-commit heavy stagger cancels with
  a full reservation refund while locking further attack input.

Failure/recovery behavior:

- negative or over-cap effective delay, invalid sequence/tick and duplicate client sequence reject
  before any server frame exists;
- simulator output is immutable and contains no wall clock, random clock or network dependency;
- these fixtures never mutate a live Paper session or trust a client timestamp for gameplay.

Non-goals:

- rollback hit detection, client-authoritative lag compensation or acceptance of stale world state;
- a production packet/network emulator and live artificial-lag command;
- bow/crossbow/magic family kits, which belong to Milestone 5.

Config/schema/migration impact: none; this is a headless deterministic acceptance facility.

## Milestone 4 — swept ARC contact and teleport interruption slice

Implemented:

- deterministic ARC sweep between the prior and current server-owned player transform for each
  authoritative hitbox-open tick;
- translation is sampled at no more than 0.125 blocks/segment and shortest-yaw rotation at no more
  than two degrees/segment, with both endpoints always present;
- sampling is bounded at 128 segments; the Paper adapter rejects impact entirely when motion would
  exceed that bound instead of accepting a coarse teleport-path hit;
- the Paper broad phase expands around the midpoint of both transforms, so targets near the prior
  pose are not omitted merely because the attacker moved before impact;
- contacts across every sample are deduplicated by entity UUID and retain their best deterministic
  weak-point/distance/angle metrics before the authored target cap is applied;
- viewer-only combat debug now renders prior/current ARC outlines and sampled sweep origins in
  addition to target selection markers;
- non-dodge teleport/world-change events cancel the action, clear buffered input, release guard,
  clear dodge/pending direction and rebuild the weapon transition from the selected slot;
- authoritative dodge movement uses a narrow in-process teleport token, so unrelated plugin
  teleports cannot masquerade as a permitted dodge step.

Tests:

- linear motion hits a target missed by both endpoint ARC queries;
- shortest-yaw rotation hits an intermediate-angle target missed at either endpoint;
- a target intersected by many samples resolves once, and 1,000 shuffled candidate collections
  preserve identical global ordering;
- extreme motion reports a capped sweep while retaining exact prior/current endpoints.

Failure/recovery behavior:

- a sweep whose shape parameters change between endpoints is rejected at construction;
- motion beyond 16 blocks in one tick exceeds the 128-segment linear bound and produces no Paper
  combat impact;
- cross-world transforms never interpolate; the teleport listener cancels/reset transient combat
  before the next action tick;
- logout, cancellation and terminal action completion discard the prior transform.

Non-goals:

- `CAPSULE`, `BOX`, `SPHERE` and `RAY` collision semantics until the content schema declares their
  required radius/width/depth/offset dimensions instead of overloading ARC fields;
- projectile simulation, provider weak-point volumes and region/party filters;
- rollback/rewind against client-reported historical transforms.

Config/schema/migration impact: none; the existing ARC contract and local training content remain
compatible.
