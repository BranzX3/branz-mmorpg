# Testing, Acceptance and Performance

## Test pyramid

### Unit tests

Required for:

- state transition guards,
- input priority/buffer,
- damage/mitigation calculations,
- guard/dodge/CC rules,
- attunement/build validation,
- mastery evidence weighting,
- item location invariants,
- reward idempotency,
- recipe graph validation,
- content schema validation.

Use deterministic clocks and seeded RNG.

### Property tests

Properties:

- damage is never negative after valid calculation,
- armor mitigation is monotonic and respects cap,
- one item UUID cannot occupy two locations,
- transaction retry does not duplicate output,
- self-HP cost never kills the user,
- buffer never contains more than one request,
- content references resolve or validation fails,
- reward expected value remains within declared bounds.

### Integration tests

Run with PostgreSQL and provider test doubles/real test instances:

- login lease conflict,
- item projection/reconciliation,
- trade saga with wallet timeout,
- enhancement crash after commit,
- reward overflow and re-delivery,
- death pouch create/recover/expire,
- Oraxen item/glyph creation,
- resource-pack manifest/hash,
- Mythic encounter signals,
- WorldGuard/region rule mapping.

### Simulation tests

Headless/domain simulations:

- thousands of combat state/input sequences,
- simultaneous hit/guard/dodge ticks,
- CC DR chains,
- encounter wipe/reset cycles,
- mastery anti-farm patterns,
- economy faucet/sink Monte Carlo,
- content hot snapshot handoff.

### In-game acceptance

Scripted human/bot checklists on real Paper:

- slot 9 protection through every inventory path,
- Scene actor spawn/compact fallback/cleanup,
- disconnect during Scene preview and commit,
- weapon swap scroll spam,
- inventory open during Engaged combat,
- bow draw/strain/ammo cycle,
- crossbow loaded persistence,
- guard/perfect guard under latency,
- resource-pack decline/failure/retry,
- Thai/English UI at supported GUI scales.

## Performance target

Initial V1 production target:

- 100 concurrent online players.
- 40 simultaneous active combatants.
- 10 simultaneous encounter groups.
- 20 active local Scene sessions.
- 250 active server-simulated projectiles.
- 500 active transient hitboxes/impact checks per tick across server.

Server target on production hardware:

- p95 MSPT <= 35 ms.
- p99 MSPT <= 45 ms.
- no sustained tick over 50 ms under target load.
- combat input application within the next server tick when received before scheduling cutoff.

## Subsystem budgets

At 40 combatants:

| Subsystem | p95 budget/tick |
|---|---:|
| Input/state/actions | 2 ms |
| Hitbox/projectile collision | 6 ms |
| Damage/status/CC | 2 ms |
| Encounter/Mob integration | 3 ms |
| HUD/packet presentation | 4 ms |
| Misc Core MMO main-thread work | 3 ms |

Database and content work remain asynchronous, but callbacks must not create unbounded main-thread bursts.

## Query and write budgets

- Login aggregate load: p95 <= 250 ms from lease to ready, excluding pack download.
- Immediate transaction DB commit: p95 <= 100 ms, p99 <= 250 ms.
- No per-tick database query.
- Mastery evidence batch every 5–15 seconds or encounter end.
- Outbox processing lag p95 <= 5 seconds.

## Spatial optimization

- Use encounter/entity spatial indices, not world-wide scans.
- Hitboxes query bounded nearby candidates.
- Status/projectile tasks are bucketed across ticks where gameplay permits.
- Owner-only effects are sent only to relevant viewers.
- Scene actors have no server AI/tick when packet-side.

## Load test scenarios

1. **Combat field:** 40 players, 80 normal mobs, mixed melee/projectiles/status.
2. **Boss:** two parties, high telegraph/effect load, repeated wipe/reset.
3. **Scene burst:** 20 simultaneous Scene opens and wardrobe previews.
4. **Reward burst:** 100 encounter rewards with inventory overflow.
5. **Login wave:** 50 reconnects after restart with reconciliation.
6. **Content deployment:** new snapshot while active encounters remain on old snapshot.

## Release acceptance gates

A release candidate fails if:

- any ownership/reward duplication test fails,
- any required migration lacks dry-run/rollback evidence,
- p99 target is missed under baseline hardware without documented exception,
- Scene or transaction leaks persist after disconnect,
- resource-pack/content hash mismatch is possible,
- high-severity action rejection or combat desync has no diagnostic reason code.

## Balance acceptance

Before V1 launch, playtests must verify:

- normal enemies allow expressive chain play without stun-locking players,
- elite/boss telegraphs are readable with reduced effects settings,
- Light/Medium/Heavy load all have viable defensive identity,
- mastery/conditioning differences are noticeable but not overwhelming,
- Flask economy supports exploration and affordable boss learning,
- no weapon family dominates all single-target, AoE, defense and mobility roles.
