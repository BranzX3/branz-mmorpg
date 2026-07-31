# Operations, Security, Testing and Performance

## Admin tools

```text
/mmo health
/mmo character inspect
/mmo item inspect <uuid>
/mmo transaction inspect <id>
/mmo content status|validate|diff|references|test
/mmo dev
/mmo scene test
/mmo encounter inspect|reset
/mmo market order inspect|quarantine
/mmo mount recover
/mmo worker inspect
/mmo reconcile <domain>
```

Every value-changing admin action requires permission, reason and audit entry. Content Dev actions also record test provenance and cannot move test-created value into normal market, trade or guild flows. The full authoring-tool contract is in `43-content-authoring-tools.md`.

## Security and anti-exploit

- Validate semantic input rate and state, not only packet frequency.
- Verify item PDC/visual data against database identity.
- Treat client-provided NBT, price, damage, position and completion as untrusted.
- Exempt legitimate custom velocity/dodge from anti-cheat through scoped tokens, not global bypass.
- Detect duplicated item/lot lineage, replayed transaction IDs and stale session actions.
- Restrict market, guild storage and worker operations through role/ownership checks.
- Never log secrets or full authentication data.

## Observability

Structured logs include character/session, transaction, item/lot, encounter, content version and result code where relevant. Metrics include:

- MSPT/TPS and scheduler delay;
- active combatants, projectiles and hitboxes;
- scene sessions and preview actors;
- DB latency/pool saturation;
- transaction retries/quarantine;
- market fill volume/spread;
- node harvest/yield and currency creation/sinks;
- worker/freight backlog;
- pack acceptance/failure.

## Test pyramid

### Unit

Formulas, resolvers, state transitions, content validation, price matching, yield curves, capability checks and deterministic content simulations.

### Integration

PostgreSQL transactions, lease conflict, Oraxen/Packet/Mob adapters, market escrow, crafting/worker recovery and migrations.

### Simulation

Combat timelines, latency inputs, encounter reset, death/revive, repeated crash points, market partial fills, cargo/mount recovery and offline job completion.

### In-game acceptance

Real client behavior for inventory events, hotbar protection, Scene close, resource pack, GUI scales, mount/caravan pathing, node interactions and party boss retry.

## Initial performance budget

Target production baseline:

- 100 concurrent players on one shard;
- 50 simultaneously engaged combatants;
- 250 active MMO projectiles;
- 500 active hitbox evaluations per tick averaged below 2 ms;
- 25 active Scene sessions;
- 20 active boss/elite encounter groups;
- 2,000 persisted resource nodes without per-tick iteration;
- 10,000 open market orders handled by indexed DB queries/background matching;
- 5,000 worker/freight jobs using timestamp evaluation, not tick loops.

Sustained server target is below 40 ms MSPT at the expected peak; alerts begin before 50 ms.

## Degradation

Under pressure, reduce cosmetic particles, debug visuals, non-essential HUD updates and spawn formation frequency. Never degrade transaction validation, damage authority or item ownership safety.
