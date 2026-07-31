# Content Authoring and Development Tools

## Purpose

The V1 authoring toolchain exists to let designers, artists and QA create, validate and test content without editing production servers or requiring Java changes for ordinary content work.

The toolchain does not replace Git, code review, migrations or immutable release artifacts. Every tool writes to a local working tree, test database or disposable environment. No authoring tool may deploy directly to production.

## Authority and boundaries

```text
Oraxen Studio / Blockbench / audio editor
→ visual source assets and provider exports

Content CLI and generated schemas
→ gameplay definitions, validation, build and reports

In-game Dev Console and laboratories
→ runtime inspection and gameplay testing

Git pull request
→ reviewable source of truth

CI and disposable servers
→ authoritative build and acceptance gate
```

The MMO platform owns stable IDs, schemas, validation, simulation and runtime behavior. Oraxen owns presentation assets and pack assembly through the `AssetProvider` integration boundary.

## V1 delivery scope

V1 must ship the following development tools:

1. Content CLI.
2. Generated JSON Schema and editor autocomplete.
3. Content catalog and reverse-reference index.
4. In-game Dev Console.
5. Item, move, spell, status, node and recipe browsers.
6. Combat sandbox and debug overlay.
7. Scene/UI laboratory.
8. Quest/dialogue validation and graph reports.
9. Economy and progression simulators.
10. Disposable resource-pack builder and validation report.
11. PR preview deployment support.
12. Test-scenario runner and regression fixtures.

A full drag-and-drop web editor is not a V1 release blocker. The schemas, catalog API and command contracts must be designed so one can be added without changing content identity or build semantics.

## Users and permissions

### Artist

May author source assets, Oraxen exports, glyphs, sounds, models and UI textures. May inspect mappings and preview presentation. Cannot change gameplay values without the required content-owner review.

### Content Designer

May author definitions, recipes, loot, encounters, quests, dialogue, city profiles and localization. May run simulations and spawn fixtures in Content Dev.

### Combat Designer

May edit moves, hitboxes, costs, weapon kits, enemies and encounter fixtures. May use deterministic combat replay and latency simulation.

### Economy Designer

May edit recipes, yields, sinks, city demand, worker jobs and market anchors. May run economy simulations and compare release snapshots.

### QA and Release Manager

May run acceptance suites, create clean test profiles, inspect transactions and promote immutable artifacts. Production value-changing actions remain separately audited.

## Tool architecture

```text
mmo-devtools
├─ devtools-api
├─ content-cli
├─ schema-generator
├─ content-catalog
├─ content-test-runner
├─ simulation-engine
├─ validation-report
├─ ingame-dev-console
├─ combat-lab
├─ scene-ui-lab
├─ economy-lab
└─ preview-deployer
```

Tooling consumes the same compiled definition model as the runtime. A tool must not maintain a second interpretation of a definition or formula.

Shared libraries must expose:

- typed stable IDs;
- schema metadata;
- definition parsers;
- reference graph;
- formula/resolver implementations;
- deterministic random sources;
- content snapshot loader;
- validation diagnostics;
- test fixture factories.

## Content CLI

The executable name is `mmo-content`.

### Required commands

```text
mmo-content validate [path]
mmo-content build [path]
mmo-content diff <from> <to>
mmo-content scaffold <type> <stable-id>
mmo-content references <stable-id>
mmo-content search <query>
mmo-content simulate <scenario>
mmo-content test [suite|path]
mmo-content migrate <from-schema> <to-schema>
mmo-content pack
mmo-content report
mmo-content serve-catalog
```

### Validate

Runs syntax, schema, reference, invariant, budget, localization, asset and migration checks. Exit code must be non-zero when any error exists. Warnings may be promoted to errors by CI policy.

Diagnostics use stable codes:

```text
CONTENT_SCHEMA_REQUIRED_FIELD
CONTENT_REFERENCE_NOT_FOUND
CONTENT_REFERENCE_WRONG_TYPE
CONTENT_ID_DUPLICATE
CONTENT_ALIAS_CYCLE
CONTENT_BUDGET_EXCEEDED
CONTENT_LOCALIZATION_MISSING
CONTENT_ASSET_NOT_FOUND
CONTENT_RECIPE_CYCLE
CONTENT_UNREACHABLE_NODE
CONTENT_MIGRATION_REQUIRED
```

Each diagnostic includes:

- file and source location;
- stable definition ID;
- code and severity;
- plain-language explanation;
- related definition IDs;
- suggested repair when deterministic.

### Scaffold

Creates a minimal valid definition using schema defaults and comments. It never invents final balance values. Generated files are placed in the correct domain directory and include a test-fixture stub when that content type supports one.

Example:

```text
mmo-content scaffold move move.greatsword.wolf_cleave
```

### Diff

Produces both machine-readable JSON and human-readable Markdown/HTML. It classifies changes as:

- presentation-only;
- tuning-only;
- behavior-compatible;
- persistent migration required;
- breaking contract;
- removed/aliased.

The diff identifies reverse references and active persistent data affected by the change.

### Pack

Builds source content into the runtime content bundle, invokes the disposable Oraxen pack builder, emits hashes and refuses to overwrite an immutable artifact version.

## Schemas and editor integration

The schema generator produces JSON Schema for every public definition type and a combined catalog for IDE completion.

Required editor features:

- field documentation and examples;
- enum completion;
- stable-ID completion;
- type-aware references;
- numeric ranges and units;
- deprecation warnings;
- localization-key completion;
- asset-ID completion;
- inline diagnostic codes.

The repository includes recommended VS Code settings and schema associations. IntelliJ users receive the same JSON Schema files. YAML remains the normal authoring format; tools may internally convert through a canonical model.

Fields representing time, distance, angle, chance, currency, weight or quantity must declare units in schema metadata. Ambiguous untyped numbers are prohibited in new public schemas.

## Content catalog

The catalog indexes every compiled definition and reverse reference.

For each stable ID it exposes:

- type and source file;
- status: draft, review, approved, deprecated;
- owning team;
- current revision and content version;
- provider/asset mapping;
- direct references;
- reverse references or “used by” list;
- localization coverage;
- migration/alias history;
- available test fixtures.

The catalog may be served locally as read-only HTTP for browsing, but Git files remain authoritative. The HTTP service must not edit production or bypass pull requests.

## In-game Dev Console

Command:

```text
/mmo dev
```

It opens an inventory-based tool hub available only on Content Dev and explicitly authorized Integration/Staging accounts.

Required modules:

```text
Content Browser
Item Spawner
Character Profile Editor
Move and Spell Tester
Mob and Encounter Spawner
Scene/UI Tester
Resource Node Tester
Recipe/Production Tester
Quest State Editor
Market and City Simulator
Mount/Caravan Tester
Time/Weather/Region Controls
Debug Overlay Controls
Test Profile Reset
```

Production defaults to the console being disabled. Enabling it requires an environment flag and audited permission.

### Safe test identities

Items, currency and progression created by dev tools are marked with a test provenance record. Test objects cannot enter production artifacts, normal market matching, guild storage or trade flows. Content Dev databases may be reset freely; Staging test data remains isolated from production identities.

## Content browser

The browser supports search by:

- stable ID;
- display/localization text;
- definition type;
- tag;
- owning module;
- status;
- asset/provider ID;
- content version.

Actions include:

- inspect compiled definition;
- inspect source path;
- spawn a test instance;
- list references and usages;
- open associated recipe/loot/move/scene;
- compare revisions;
- run the default fixture;
- copy stable ID;
- show asset preview metadata.

It never edits database ownership directly. Spawning value-bearing test content uses the dev provenance transaction path.

## Combat sandbox

The Combat Lab provides a dedicated arena with deterministic reset.

Configurable inputs:

- player weapon kit and build;
- target profile and armor;
- enemy AI profile;
- party size;
- content snapshot;
- simulated latency and packet jitter;
- PvE/PvP balance profile;
- resource regeneration mode;
- invulnerability or infinite resources.

Required visualizations:

- engagement, weapon and action states;
- input queue and rejection reason;
- windup, active, recovery and cancel windows;
- hitbox shapes and swept paths;
- facing/soft-target assist;
- damage, posture, guard and status breakdown;
- threat table;
- projectile ownership and collision;
- encounter eligibility.

The lab can export a combat trace containing inputs, ticks, state transitions, resolver outputs, random seed and content version. The test runner can replay a trace and compare deterministic outcomes.

## Scene and UI laboratory

The Scene/UI Lab tests Local Scene Hub, Wardrobe, HUD, dialogue and inventory UI presentation.

Required controls:

- GUI scale 2, 3 and 4;
- common 16:9, 16:10 and ultrawide profiles;
- Thai and English localization;
- Force Unicode on/off;
- vanilla/enhanced visual capability;
- light, darkness, rain and underwater backgrounds;
- preview actor skin/equipment/cosmetics;
- blocked local-scene placement;
- damage, knockback, teleport and disconnect interruption;
- pack accepted, declined, failed and stale states.

Automated screenshot comparison may be used as a warning system, but manual acceptance remains required because Minecraft rendering and fonts can vary by client environment.

## Resource-node and recipe laboratory

The Node Lab can create personal, shared, rich, rare and regional nodes with forced lifecycle states. It exposes reservation owner, remaining charges, respawn timestamp, yield seed and anti-abuse signals.

The Recipe Lab can:

- grant exact inputs;
- select station, Rank, Mastery, Focus and tool profile;
- execute once or simulate many iterations;
- show output distribution, byproducts and waste;
- show input valuation and current reference price;
- inspect recipe dependency graph;
- detect circular or value-amplifying loops;
- test crash at reserve, commit and delivery points.

Simulated results never create live inventory value.

## Quest and dialogue tools

The validator builds a graph for quests, dialogue and cutscene transitions.

It reports:

- unreachable nodes;
- dead ends without intentional terminal metadata;
- missing localization;
- impossible condition sets;
- duplicate one-time rewards;
- permanent core-feature lockouts;
- missing abandon/retry paths;
- party-sharing conflicts;
- migration risk for active quest states.

The in-game Quest State Editor may set test characters to a selected node, objective or branch. It must use a test-only state mutation record and cannot alter normal production characters.

The generated HTML report includes a navigable graph. A full drag-and-drop graph editor may be added later without changing the runtime schema.

## Economy simulator

The simulator runs outside the live server using the same yield, recipe, worker, market, tax, demand and sink resolvers.

Scenario inputs include:

- simulated population and archetypes;
- playtime distribution;
- gathering/production preferences;
- city routes;
- market liquidity assumptions;
- worker utilization;
- event schedules;
- content snapshot and random seed;
- duration in simulated hours/days.

Outputs include:

- currency sources and sinks;
- commodity creation and consumption;
- price, spread and fill volume estimates;
- city demand and saturation;
- route profit distribution;
- worker output versus active play;
- recipe profitability;
- shortages, gluts and dead content;
- concentration by player archetype.

Simulation does not assert the final economy truth. It detects obvious feedback loops and provides hypotheses for in-game telemetry and playtests.

## Progression simulator

The progression simulator evaluates expected hours and evidence distributions for:

- weapon Mastery;
- Body Conditioning;
- Lifeskill Rank and Mastery;
- Renown and Civic Influence;
- teaching prerequisites;
- mount Training.

It must flag paths that can be completed through trivial repetition, dummy farming or offline workers when active play is intended.

## Test-scenario runner

Scenarios are declarative fixtures executed against unit simulations, integration environments or in-game test harnesses.

A scenario declares:

- starting content version;
- character/items/currency/state;
- environment and provider capabilities;
- ordered actions;
- optional crash/disconnect points;
- expected state, transactions, metrics and diagnostics.

Required scenario domains:

- item ownership and location;
- market escrow and partial fills;
- crafting/worker reservation;
- combat state and deterministic trace;
- Scene interruption/recovery;
- node reservation/chunk unload;
- mount/cargo recovery;
- quest migration;
- resource-pack failure.

Every production bug involving loss, duplication or invalid state must add a regression scenario before closure.

## Validation report

CI produces `validation-report.html` and machine-readable diagnostics.

The report contains:

- build/content/schema versions;
- errors and warnings by owner/domain;
- changed definitions;
- compatibility classification;
- missing localization/assets;
- reference graph deltas;
- budget violations;
- simulation summaries;
- test results;
- disposable server boot/pack-generation logs;
- links to PR preview instructions.

The report must avoid embedding secrets, private player data or unrestricted admin tokens.

## PR preview environments

A pull request may request a disposable preview environment containing:

- the PR platform/content artifact;
- isolated database and test profiles;
- generated resource pack;
- Content Dev permissions;
- fixture worlds/labs;
- automatic expiry.

Preview environments are never promoted directly. A merge produces a fresh immutable artifact through the normal CI pipeline.

## Reload and snapshot behavior

Tools may request a controlled content reload only in Local or Content Dev.

Rules:

- parse and validate the complete candidate snapshot first;
- never mutate the active snapshot in place;
- active encounters, transactions, jobs and Scene commits retain their original snapshot;
- switch eligible new sessions atomically;
- report definitions blocked by migration or active-use constraints;
- retain the previous snapshot for rollback within the environment policy.

Code, SQL, provider versions, serialization and state-machine changes require restart.

## Performance and safety

Development overlays and traces are opt-in per viewer and bounded by rate/retention limits. Debug particles, packet capture and simulations must not run by default on Production.

The catalog and simulator may cache compiled definitions but must invalidate by content manifest hash. Tools must use deterministic seeds where reproducibility matters.

## V1 acceptance criteria

The authoring toolchain is complete for V1 when:

- a designer can scaffold, validate, build and test a new item, move, node, recipe and quest without changing Java;
- stable-ID autocomplete and reverse-reference lookup work in the repository;
- invalid references and invariant violations fail before runtime deployment;
- a combat trace can be exported and deterministically replayed;
- Scene/UI presentation can be tested across the required client matrix;
- recipe/economy simulations identify a deliberately inserted infinite-value loop;
- crash-point fixtures prove no duplication through crafting, market and worker flows;
- CI generates immutable content/pack artifacts and a readable validation report;
- no authoring tool can directly modify or deploy Production content.

## Future extensions

The following are compatible extensions, not V1 blockers:

- drag-and-drop combat timeline editor;
- full quest/dialogue graph editor;
- structured local web form editor;
- collaborative review annotations;
- automated visual-diff approval;
- remote telemetry exploration dashboards;
- controlled live-event authoring with signed release bundles.
