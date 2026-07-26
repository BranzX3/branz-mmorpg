# Release Verification

Verified locally on 2026-07-26:

- Core and quest compiler/state/dialogue/cutscene/party/performance tests pass.
- Bundled game content and `branz:the_old_seal` compile together.
- Strict unknown-field, graph reachability, unbounded-cycle, stale-input,
  duplicate event, canonical query, party loot, and migration tests pass.
- Paper packaging smoke checks plugin metadata, bundled content, and the
  external Wallet ownership boundary.
- Local MariaDB 12.3 applied all Flyway migrations V1–V17 and exercised the
  quest world registry repository.
- Backup/restore rehearsal matched 43/43 tables and 17/17 successful migration
  rows; both rehearsal databases and the temporary dump were deleted.
- The local Quest Director production build succeeds.

The Testcontainers MySQL test is present and automatically skips when Docker is
unavailable. The local MariaDB integration gate is the executed fallback on this
machine; CI/release machines with Docker should also execute the MySQL 8.4
container test.

Final release gate:

    gradlew clean test shadowJar

The JAR is local-only and must not be remotely deployed by this workflow.
