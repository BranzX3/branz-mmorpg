# Phase 1 — Project and Content Foundation

## Locked runtime

- Paper 26.2
- Java 25
- Gradle Kotlin DSL with Shadow and run-paper
- MySQL 8 through HikariCP
- Flyway-owned SQL migrations
- YAML content compiled through Jackson into immutable definitions

## Architectural rules

1. `mmorpg-api` contains stable, platform-independent contracts only.
2. `mmorpg-content` owns parsing, validation, duplicate detection, and atomic catalog replacement.
3. `mmorpg-storage` owns database lifecycle and schema migration.
4. `mmorpg-core` owns game rules and may depend on content and storage.
5. `mmorpg-paper` is the only module allowed to depend on Paper.
6. Branz MMORPG has no required dependency on Branz Idle. A future bridge may grant registered material IDs through the public API.
7. A failed content reload never replaces the last valid snapshot.
8. Database schema changes are forward-only Flyway migrations.

## Phase acceptance criteria

- `gradlew clean test shadowJar` succeeds.
- The shaded Paper plugin starts with database disabled.
- Database-enabled startup applies migrations before gameplay services start.
- Content IDs are namespaced and validated.
- Invalid or duplicate content produces actionable diagnostics.
- Content snapshots are immutable and replaced atomically.
- `/branz status` reports foundation state and `/branz reload` reloads content safely.
