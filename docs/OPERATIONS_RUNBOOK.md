# Branz MMORPG Operations Runbook

This project runs locally on one Paper server. BranzWallet owns all currency;
MMORPG startup must fail closed when required storage or Wallet capabilities are
unavailable.

## Startup

1. Back up the configured MariaDB/MySQL database and `plugins/BranzMMORPG`.
2. Start BranzWallet, then BranzMMORPG. Never hot-swap either JAR.
3. Confirm `/branz status`, `/branz telemetry inspect`, and
   `/branz quest validate`.
4. Confirm content revision, database connection, scheduler queue depth, and
   that there are no pending reward failures.
5. Run the reference path in [QUEST_QA_PATH.md](QUEST_QA_PATH.md) after a release.

Flyway V1–V17 runs before gameplay services start. Migration failure disables
the plugin; do not repair `mmorpg_schema_history` manually.

## Clean shutdown

Use the Paper `stop` command. The plugin refuses new mutations, cancels Paper
tasks, flushes player state, drains bounded async work, persists recoverable
dialogue/cutscene sessions, and closes Hikari. Do not terminate the JVM while
the shutdown flush is active.

## Database outage

- Gameplay mutations fail closed; never grant substitute items or currency.
- Keep the database outage shorter than the configured connection recovery
  window and monitor pending session saves/reward operations.
- Restore database connectivity, then inspect `/branz status`.
- Run `/branz quest retry <online-player>` and the inventory/mailbox inspection
  commands. Durable operation IDs make retries safe.
- If a session save remains pending, perform a normal restart after connectivity
  is stable. Do not delete `pending-saves`.

## Bad content or reload

Reload compiles into a new immutable snapshot. Any error leaves the prior
revision active. Use `/branz quest validate`, `/branz quest graph [id]`, and
the local Quest Director. Fix the file/field diagnostic and reload again.
Never edit the active in-memory catalog or copy staging YAML into production
without validation.

## Repair procedures

All mutations require an operator reason and write `mmorpg_audit_log`.

- Quest version mismatch: inspect, then
  `/branz quest migrate <player> <quest> <reason>`.
- Stuck quest stage: `/branz quest stage <player> <quest> <stage> <reason>`.
- Objective repair:
  `/branz quest objective <player> <quest> <objective> <value> <reason>`.
- Irrecoverable test progress:
  `/branz quest reset <player> <quest> <reason>`.
- Pending quest action: `/branz quest retry <player>`.
- Mob/encounter: inspect before reset; use the stable instance ID.
- Equipment/inventory: inspect authoritative state and mailbox before revoke or
  repair. Never mint an ItemStack directly.
- Currency: use `/branz currency adjust ...`; the Wallet ledger is the sole
  authority.

## Backup and restore

Use a transaction-consistent database dump and retain plugin content/config with
the same release. Restore into a new database first, verify all 15 successful
Flyway rows, table counts, player/profile samples, inventories, active
encounters, quest progress, and pending operations, then switch configuration.
The 2026-07-26 local rehearsal restored the full schema and all V1–V17 migrations.

## Security

Do not expose the local Quest Director or a development server publicly. Keep
database/Wallet credentials out of logs and content. Player-facing `/branz`
access is limited to dialogue choice, personal journal, and own cutscene skip;
all other paths require `branz.admin`.
