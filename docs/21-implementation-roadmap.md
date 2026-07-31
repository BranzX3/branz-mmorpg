# Implementation Roadmap

This order minimizes rework. A coding agent must not jump to full content before the owning runtime milestone passes its acceptance tests.

## Milestone 0 — Repository and build foundation

Deliver:

- Gradle multi-module structure.
- CI for formatting, unit tests and artifact build.
- Paper dev-server run task.
- PostgreSQL test container.
- Stable ID value types.
- Error/result/reason-code conventions.
- Content manifest skeleton.

Exit criteria:

- Plugin enables/disables cleanly.
- Test DB migrations run.
- One command starts local dev server.

## Milestone 1 — Persistence and character session

Deliver:

- Character/account schema.
- Session lease/token.
- Repository and async/thread handoff pattern.
- Transaction journal/outbox.
- Audit log.
- Login/logout/shutdown recovery.

Exit criteria:

- Duplicate login test passes.
- Restart preserves character and resolves stale lease.
- Stale async callback cannot mutate a new session.

## Milestone 2 — Content compiler and providers

Deliver:

- Schemas/registries.
- Content bundle loader/snapshot.
- AssetProvider and RegionProvider adapters.
- Disposable Oraxen pack-builder probe.
- Missing/invalid reference reports.

Exit criteria:

- Example content compiles.
- Invalid cosmetic stats and missing assets fail CI.
- Runtime loads one immutable snapshot.

## Milestone 3 — Item engine and inventory projection

Deliver:

- ItemDefinition/ItemInstance.
- Item UUID and locations.
- Projection/reconciliation.
- Slot 9 Chronicle protection.
- Basic equipment/native and virtual slots.
- Quarantine/admin inspect.

Exit criteria:

- Inventory event matrix cannot duplicate/move Chronicle.
- Missing/duplicate projections reconcile.
- Equip rollback leaves no partial ownership.

## Milestone 4 — Combat state and input router

Deliver:

- Orthogonal state machines.
- weapon draw/sheathe/item transitions.
- LMB/RMB/F/Q/Shift semantic routing.
- one-slot input buffer and rejection reasons.
- safe-zone/world-interaction ownership.

Exit criteria:

- State simulation passes randomized sequences.
- Scroll spam cannot bypass draw.
- Inventory remains usable while Engaged.

## Milestone 5 — Moves, hitboxes and damage

Deliver:

- Move schema/runtime.
- Movement/facing curves.
- Hitbox primitives and debug rendering.
- damage/armor/resistance.
- projectiles and basic Bow.

Exit criteria:

- Deterministic replay gives same results.
- No wall hits/tunneling in acceptance map beyond documented tolerance.
- Damage formula property tests pass.

## Milestone 6 — Defense and CC

Deliver:

- Dodge load profiles/i-frames.
- Guard Stability, chip and perfect guard.
- Parry technique behavior.
- enemy posture/player poise.
- CC hierarchy/immunity/DR.

Exit criteria:

- Same-tick hit/dodge/guard cases are deterministic.
- PvP DR cannot create infinite hard-CC chain.

## Milestone 7 — Build, progression and resources

Deliver:

- Moveset branch replacement.
- forms/attunement/rest context.
- stamina/mana/HP runtime.
- mastery/conditioning evidence.
- technique learning/teaching skeleton.

Exit criteria:

- Invalid builds cannot commit.
- anti-farm simulations suppress dummy loops.
- exact hidden values never leak through player API/UI.

## Milestone 8 — Flask, consumables, status and magic

Deliver:

- Flask profile/snapshot/use/refill.
- consumable categories/timelines.
- six ailments.
- spell runtime/catalysts/targeting.
- one complete Ember art.

Exit criteria:

- boss wipe restores prepared Flask only.
- status recursion cap holds.
- interrupted item/spell commit behavior is correct.

## Milestone 9 — Encounters, party and rewards

Deliver:

- parties and HUD data.
- encounter membership/snapshot/reset.
- threat integration.
- personal loot, PendingRewards and pity.
- death pouch.

Exit criteria:

- repeated completion/reset cannot duplicate rewards.
- support contribution qualifies.
- crash during reward/death pouch reconciles.

## Milestone 10 — Scene Hub and cosmetics

Deliver:

- PreviewActorProvider adapter.
- local placement/compact fallback.
- Scene pages/navigation.
- equipment/wardrobe preview and transactional commit.
- dye unlock/state.
- settings/accessibility.

Exit criteria:

- all close triggers clean actors/UI.
- preview never commits implicitly.
- resource-pack/preview failure uses safe fallback.

## Milestone 11 — Quest/dialogue/NPC

Deliver:

- quest state/objectives.
- dialogue conditions/actions.
- stationary NPC/service/mentor profiles.
- journal/markers/cutscene fallback.
- introduction and mentor content.

Exit criteria:

- active quest migration test passes.
- skip/disconnect executes required actions once.

## Milestone 12 — Trade, crafting, enhancement and PvP

Deliver:

- direct trade saga.
- blacksmith/alchemy crafting.
- durability/repair/enhancement/path/masterwork.
- duel and arena profile.

Exit criteria:

- wallet ambiguity freezes/reconciles safely.
- enhancement crash cannot duplicate/lose item.
- PvP causes no durability/pouch loss.

## Milestone 13 — Hardening and launch content

Deliver:

- performance profiling/optimization.
- admin and dashboards.
- backup/restore rehearsal.
- full V1 content target.
- localization/accessibility pass.
- migration/rollback runbook.

Exit criteria:

- all gates in `20-testing-performance.md` pass.
- staging artifact hashes equal production candidates.

## Definition of done for every subsystem

- schema and validation,
- domain API and implementation,
- persistence policy,
- failure/recovery behavior,
- unit/integration tests,
- admin inspection,
- metrics/logs,
- player feedback/localization keys,
- content examples,
- migration notes.
