# Operations, Security, Admin and Observability

## Operational principles

- Production state changes are performed through versioned deployments, migrations or journaled admin commands.
- No direct database edits during normal operation.
- Every valuable mutation includes actor, reason, transaction ID and content version.
- Fail closed for ownership/wallet ambiguity; fail degraded for optional presentation.

## Permissions

Permission groups:

```text
mmo.player
mmo.moderator.inspect
mmo.gm.encounter
mmo.admin.items
mmo.admin.characters
mmo.admin.content
mmo.admin.transactions
mmo.admin.dangerous
```

Dangerous commands require explicit reason text and, for production, optional two-person approval through deployment/admin workflow.

## Required commands

```text
/mmo health
/mmo character inspect <player>
/mmo combat debug <player>
/mmo item inspect <uuid|held>
/mmo item reconcile <player>
/mmo item quarantine <uuid>
/mmo transaction inspect <id>
/mmo transaction reconcile <id>
/mmo rewards inspect <player>
/mmo rewards redeliver <grantId>
/mmo pouch inspect <player>
/mmo scene close <player>
/mmo encounter inspect <id>
/mmo encounter reset <id>
/mmo content status
/mmo content diff
/mmo content validate
/mmo pack resend <player>
```

Grant/repair commands must produce transaction/audit records. Raw item spawning is disabled on production except through an audited reason and recipient.

## Structured logging

Every log record should include relevant fields:

- timestamp,
- severity,
- server instance,
- character/account,
- session token hash,
- encounter ID,
- transaction ID,
- item UUID,
- content/plugin version,
- error code,
- provider.

Do not log full chat, private dialogue choices or secrets by default.

## Metrics

### Runtime

- tick time/MSPT percentiles,
- active characters,
- active combatants/actions/hitboxes/projectiles,
- encounter and Scene counts,
- packets/effects emitted by subsystem,
- scheduler backlog.

### Database

- pool usage,
- query latency,
- transaction success/failure/retry,
- lease conflicts,
- outbox lag,
- pending reward count,
- quarantine count.

### Economy

- currency faucets/sinks by source,
- repair/enhancement spend,
- Flask stock consumption,
- reward expected vs actual value,
- item creation/destruction/salvage,
- trade volume.

### Gameplay

- action rejection reasons,
- dodge/guard/perfect-guard rates,
- boss wipe/completion,
- status application/cleanse,
- weapon family usage,
- mastery evidence suppression reasons.

## Alerts

Alert when:

- p95 MSPT exceeds target for 5 minutes,
- transaction ambiguity or duplicate constraint failure occurs,
- wallet provider is unavailable,
- item quarantine spikes,
- outbox lag exceeds 60 seconds,
- resource-pack failure rate spikes,
- content/provider version mismatch,
- encounter cannot reset,
- Scene sessions leak beyond timeout,
- database backup/restore verification fails.

## Security and anti-exploit

### Item security

- Server repository validates every item UUID and location.
- PDC/display data is never trusted as ownership proof.
- Inventory click/drag, number-key, off-hand swap, drop, death, container and creative events are covered.
- Chronicle and UI items use protected markers and are removed outside valid contexts.
- Duplicate projection detection retains one valid projection and audits removals.

### Input and packet abuse

- Rate-limit semantic action requests.
- Deduplicate hand/event duplicates.
- Validate state, timing and resource server-side.
- Reject impossible slot/weapon/action transitions.
- Preview actor interaction packets are viewer/session scoped.
- No client timestamp determines a hit.

### Movement and anti-cheat integration

Dodge/lunge/knockback creates signed movement exemptions with:

- character/session,
- source action,
- expected duration,
- maximum displacement,
- expiry.

Anti-cheat exemptions are narrow and never global. Unexpected movement still triggers correction/logging.

### Economy abuse

- Idempotency keys and unique constraints for rewards/transactions.
- Craft graph validation for recursive value.
- Wallet reservations for trade/death pouch.
- AFK/repetition suppression for progression and rewards.
- Admin grants separated from normal economy metrics.

## Backups

- PostgreSQL continuous backup plus daily snapshot.
- Artifact and content manifests retained with database backups.
- Restore rehearsal at least monthly on staging.
- Restore verification includes item ownership count, transaction journal integrity, pending rewards and death pouches.

## Incident modes

### Database degraded

- Block login progression after safe lobby.
- Freeze valuable transactions.
- Existing players may be moved to safe shutdown path.

### Wallet degraded

- Block trade currency, death pouch and paid services.
- Combat remains available where it does not create ambiguous currency mutation.

### Oraxen/pack degraded

- Block new affected item projection and normal world entry if required pack unavailable.
- Preserve persistent state.

### Packet/preview degraded

- Disable advanced Scene actor/camera and use compact UI.
- Combat core remains authoritative.

## Privacy

Store only information required for account-character mapping, gameplay state, moderation/audit and operations. Define retention for IP/security logs separately from gameplay records. Exports/deletion must preserve legally/operationally required transaction audit through pseudonymization where applicable.
