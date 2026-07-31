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

- swept world hitbox collision, deterministic target ordering and region/friendly filters;
- damage/armor/advantage calculation and health application;
- live engagement timers, dodge movement/i-frames, guard/posture/poise/CC;
- viewer-scoped debug rendering and in-game trace export controls;
- latency/jitter acceptance fixtures and a complete weapon test kit.

Schema/config impact: move schema gains the complete runtime fields. Local config adds positive
`combat.weapon-draw-ticks` and `combat.weapon-sheathe-ticks`. Migration impact: none; combat
sessions/resources remain transient.
