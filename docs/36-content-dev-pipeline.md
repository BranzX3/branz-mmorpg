# Content and Development Pipeline

## Repositories

### `mmo-platform`

Java modules, schemas, compiler, migrations, tests and architecture docs.

### `mmo-content`

Source assets, Oraxen exports, stable registries, gameplay definitions, localization, migrations and test fixtures.

### `mmo-infrastructure`

Containers, environment manifests, deployment, monitoring and backup configuration.

## Authority chain

```text
Oraxen Studio / Blockbench / audio tools
→ Git branch in mmo-content
→ validation and review
→ immutable artifacts
→ Content Dev
→ Integration
→ Staging
→ Production promotion
```

Oraxen Studio is an authoring/export tool. Git is the source of truth. Production never pulls directly from Studio.

## Stable asset registry

Gameplay definitions reference stable `asset_id` values. An adapter maps them to Oraxen provider IDs. Renaming a file/model/provider ID does not change the gameplay asset ID. Removed IDs require aliases/migrations.

## Authoring toolchain

The designer-facing CLI, generated schemas, catalog, in-game laboratories, simulations, test-scenario runner and PR preview rules are owned by `43-content-authoring-tools.md`. This document owns repository, artifact and environment promotion semantics.

## Content compiler

Validation stages:

1. syntax/schema;
2. reference types;
3. global invariants;
4. gameplay budgets;
5. asset paths/glyph/sound collisions;
6. migration compatibility;
7. disposable runtime boot test;
8. packaging and manifest.

## Disposable pack builder

CI boots a temporary Paper environment with pinned Oraxen and the build-probe plugin, generates the resource pack, captures startup errors, hashes outputs and destroys the environment. Production nodes never build packs.

## Artifacts

```text
mmo-platform-<version>.jar
mmo-content-<version>.zip
mmo-resourcepack-<version>.zip
content-manifest.json
validation-report.html
migration-plan.json
```

Artifacts are immutable. Staging and production use identical hashes.

## Environments

- Local: developer logic and small fixtures.
- Content Dev: asset gallery, UI/Scene lab, Combat lab, node/recipe lab, economy simulation fixtures and environment-gated dev tools.
- Integration: all providers and realistic database.
- Staging: production-equivalent config, migration and load rehearsal.
- Production: no live YAML edits, auto-updates or server `/reload`.

## Reload policy

Safe/controlled content reload may update localization, presentation, inactive definitions and future sessions. Active encounters/jobs retain their content snapshot. Java code, SQL schema, provider versions, serialization and state-machine changes require restart.

## UI validation

Test GUI scale 2/3/4, common aspect ratios, Thai/English, Force Unicode modes, pack upgrade/downgrade and missing enhanced-client extensions.
