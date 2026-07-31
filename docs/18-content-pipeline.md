# Content Authoring and Dev-Server Pipeline

## Repositories

### `mmo-platform`

Java modules, schemas, compiler, migrations, tests and architecture docs.

### `mmo-content`

Git source of truth for gameplay definitions, Oraxen configuration, source assets, localization, registries and content migrations.

### `mmo-infrastructure`

Container images, environment configuration, deployment, monitoring, backup and CI templates.

## Tool ownership

```text
Oraxen Studio / Blockbench / audio tools
= authoring tools

mmo-content Git repository
= source of truth

CI content compiler
= validator and packager

Artifact registry
= deployable immutable outputs

Oraxen runtime
= asset/resource-pack provider
```

Oraxen Studio never deploys directly to production.

## `mmo-content` layout

```text
manifest.yml
registry/
  assets.yml
  glyphs.yml
  sounds.yml
  aliases.yml
gameplay/
  items/
  weapons/
  moves/
  techniques/
  forms/
  spells/
  statuses/
  consumables/
  quests/
  dialogue/
  loot/
oraxen/
resource-pack/
source-assets/
localization/th_TH/
localization/en_US/
migrations/
test-fixtures/
```

Generated pack output is not committed. Large source assets use Git LFS.

## Stable asset registry

Gameplay refers to stable `asset_id`:

```yaml
weapon.greatsword.iron_wolf:
  provider: ORAXEN
  provider_id: iron_wolf_greatsword
  category: WEAPON
  revision: 4
  status: APPROVED
```

Renames use alias/migration records. Provider IDs and file paths may change without changing gameplay identity.

## Content compiler stages

1. Parse YAML/JSON.
2. Schema validation.
3. Stable ID and uniqueness validation.
4. Cross-reference validation.
5. Gameplay invariant validation.
6. Asset/model/texture/glyph/sound validation.
7. Localization completeness validation.
8. Migration compatibility/diff.
9. Runtime smoke build in disposable server.
10. Package immutable artifacts and report.

Invariant examples:

- cosmetic cannot define combat stats/durability,
- enhancement cannot modify iframe/perfect-guard windows,
- HP-cost action cannot be lethal to user,
- all references exist and have compatible tags,
- reward/craft loops do not recursively create value,
- Scene UI glyphs do not collide,
- moveset branches are valid for family.

## Disposable pack builder

CI boots a temporary Paper environment with pinned:

- Paper target version,
- Oraxen version,
- Core content probe plugin,
- generated Oraxen/content configuration.

The job waits for successful plugin enable, asks Oraxen to generate the pack, runs item/glyph smoke tests, extracts the pack and shuts down. Production nodes do not build packs.

## Artifacts

```text
mmo-core-<version>.jar
mmo-content-<contentVersion>.zip
mmo-resourcepack-<contentVersion>.zip
mmo-content-manifest-<contentVersion>.json
mmo-validation-report-<contentVersion>.html
mmo-migration-plan-<contentVersion>.json
```

Manifest includes plugin compatibility, target game version, provider versions, schema version, Git commit and SHA-256 hashes.

## Environments

### Local developer

Minimal content, local PostgreSQL, one-command Paper run task.

### Content Dev

Auto-deploy from content development branch. Contains galleries, UI lab, Scene lab, mob/combat lab, free admin test tools and disposable player data.

### Integration

Full plugin stack and real database behavior. Tests provider adapters and end-to-end transactions.

### Staging

Production-like configuration and plugin pins. No hot reload. Runs migrations, load, rollback and pack-delivery tests.

### Production

Deploys only approved immutable artifacts. No file edits, automatic plugin updates or whole-server reload.

## Branch and promotion flow

```text
feature/content PR
 -> validation + preview artifact/server
 -> develop / Content Dev
 -> release candidate / Integration
 -> tagged RC / Staging
 -> manual approval / Production
```

The exact artifact hashes promoted from staging are deployed to production; production never rebuilds them.

## Hot reload policy

Content Dev may reload:

- localization,
- UI presentation,
- dialogue text,
- selected non-persistent definitions.

Controlled snapshot reload may replace item/move/spell/status definitions for new actions/encounters. Active actions and encounters retain their old snapshot.

Restart required for:

- Java code,
- database schema,
- item serialization,
- provider/plugin versions,
- state-machine structure,
- major content schema version.

## Code ownership

- Artists: source assets, resource-pack, Oraxen presentation.
- Content designers: gameplay definitions, quests, dialogue, loot and localization.
- Java developers: schema/compiler/runtime/migrations/integrations.
- Release manager: environment promotion and rollback.

Use `CODEOWNERS` and required reviews by directory.

## Pack distribution

Resource packs are stored in object storage and served through CDN using immutable versioned URLs. The active manifest supplies URL and SHA. Old packs remain available for rollback during the retention window.
