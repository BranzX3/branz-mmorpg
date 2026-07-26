# Branz MMORPG

Standalone, modular MMORPG framework for Paper 26.2 and Java 25.

## Modules

- mmorpg-api: Stable platform-independent contracts.
- mmorpg-content: Typed YAML loading, validation, and atomic content snapshots.
- mmorpg-storage: MySQL/HikariCP lifecycle and Flyway migrations.
- mmorpg-core: Platform-independent game rules.
- mmorpg-paper: Paper bootstrap, commands, listeners, and UI.
- mmorpg-quest-api/core/storage/paper: Quest, dialogue, cutscene, migration,
  persistence, and Paper presentation.
- tools/quest-editor: Local-only visual YAML graph/timeline editor.

## Documentation

- [System Specification](docs/SPECIFICATION.md)
- [Phase 1 Foundation](docs/PHASE_1_FOUNDATION.md)
- [Development Ownership and Contracts](docs/DEVELOPMENT_OWNERSHIP_AND_CONTRACTS.md)
- [Core MMO Specification](docs/CORE_MMO_SPECIFICATION.md)
- [Life Skill Mastery Specification](docs/SURVIVAL_SKILL_MASTERY_SPECIFICATION.md)
- [Quest, Dialogue, and Cutscene Specification](docs/QUEST_DIALOGUE_CUTSCENE_SPECIFICATION.md)
- [Parallel Implementation Roadmap](docs/IMPLEMENTATION_ROADMAP.md)
- [External Plugin Integration Contract](docs/EXTERNAL_PLUGIN_INTEGRATION_CONTRACT.md)
- [Operations Runbook](docs/OPERATIONS_RUNBOOK.md)
- [Public API Guide](docs/PUBLIC_API.md)
- [Reference Quest QA Path](docs/QUEST_QA_PATH.md)
- [Release Verification](docs/RELEASE_VERIFICATION.md)

## Build

    gradlew clean test shadowJar

The plugin artifact is produced under mmorpg-paper/build/libs.
