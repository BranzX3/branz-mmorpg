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
- [Combat and Skill Input Specification](docs/COMBAT_SKILL_INPUT_SPECIFICATION.md)
- [Permanent Character Class Specification](docs/PERMANENT_CHARACTER_CLASS_SPECIFICATION.md)
- [Class Compass and Skill Tree UI Specification](docs/CLASS_COMPASS_AND_SKILL_TREE_UI_SPECIFICATION.md)
- [Combat Mastery and Character Build Specification](docs/COMBAT_MASTERY_AND_CHARACTER_BUILD_SPECIFICATION.md)
- [Survival Skill Mastery (Life Skills) Specification](docs/SURVIVAL_SKILL_MASTERY_SPECIFICATION.md)
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

## Local test server

Start Paper 26.2 with the current shaded plugin:

    gradlew :mmorpg-paper:runServer

Then connect Minecraft to:

    localhost:25565

The local configuration starts with MySQL disabled, so foundation services and
content reload are available while persistent gameplay remains offline. Use
`/branz status` as an operator to inspect Core, content, and database health.

## MySQL integration tests

The real-MySQL suites are opt-in and are skipped during a normal build. Start a
disposable MySQL 8.4 instance with an empty test database, then run in PowerShell:

    $env:BRANZ_MYSQL_INTEGRATION = "true"
    $env:BRANZ_MYSQL_HOST = "127.0.0.1"
    $env:BRANZ_MYSQL_PORT = "3407"
    $env:BRANZ_MYSQL_DATABASE = "branz_mmorpg_test"
    $env:BRANZ_MYSQL_USERNAME = "root"
    $env:BRANZ_MYSQL_PASSWORD = ""
    .\gradlew.bat :mmorpg-storage:test :mmorpg-core:test --rerun-tasks

The suites apply Flyway migrations and cover the profile schema, JSON/content-ID
round trips, concurrent insert-if-absent, optimistic conflicts, database outage,
durable recovery journaling, and replay after reconnect.
