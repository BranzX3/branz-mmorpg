# Cross-System Acceptance Matrix

This matrix defines end-to-end scenarios that must pass before V1 release. Unit success inside one module is insufficient.

## Character and Chronicle

- Login with valid pack acquires one lease and creates exactly one Chronicle in slot 9.
- Attempts to move, swap-hand, drop, container-transfer or number-key-swap Chronicle are rejected without inventory desync.
- Selecting a physical weapon from hotbar 1–8 draws it; selecting Chronicle sheaths it; right-click opens Scene only under allowed conditions.
- Damage during Scene closes it, removes Preview Actor and preserves all unconfirmed virtual equipment/cosmetic/build state.
- Chronicle can inspect selected weapon, native off-hand and armor, but ordinary weapon/shield/armor handling does not require Chronicle confirmation.

## Combat and inventory

- A dev-granted Training Sword is an MMO-owned durable item immediately when granted; it does not become a different logical item after a Scene equip operation.
- The same Training Sword UUID may be moved to any hotbar slot 1–8, selected, drawn and used without a persistent `MAIN_HAND` equip transaction.
- Moving the selected sword between hotbar slots preserves UUID, durability, enhancement and other item payload state.
- An MMO-owned Training Sword can damage and kill an ordinary vanilla cow through exactly one authoritative combat path; no training-only hidden 1000-HP runtime owns that cow.
- Vanilla melee damage is suppressed only when the MMO runtime actually claims the attack; an unclaimed physical item interaction does not silently become a zero-damage swing.
- Player opens normal inventory while ENGAGED; enemies continue and may damage the player.
- Moving a potion into hotbar and closing inventory allows normal consumable timeline; no snapshot/loadout restriction exists.
- Moving a valid shield to off-hand performs the authoritative native-equipment validation/commit and enables the compatible guard style; removing it physically disables that style after reconciliation.
- Moving valid armor through native armor slots commits authoritative equipment without requiring Chronicle.
- The character-bound Flask representation cannot leave gameplay hotbar slots, spends its durable selected charge at commit and never restores that charge after a post-commit interrupt.
- Flask refill/allocation is initiated from valid Sanctuary/Camp/Checkpoint Rest interaction rather than requiring Chronicle as the primary preparation surface.
- Weapon scroll spam cannot skip sheathe/draw or retain an illegal buffered attack.
- Dodge, Perfect Guard, Parry and hard CC resolve in the documented priority under same-tick input tests.
- Disconnect/reconnect and a full server/database restart reconstruct the same physical sword/shield/armor/consumable ownership without duplicate projections or ghost `MAIN_HAND` equipment.

## Boss retry

- Party prepares Flask at checkpoint, spends Flask in fight and wipes.
- Encounter resets once, Flask snapshot restores once, ordinary potion/ammo/durability do not restore.
- Disconnect/reconnect during wipe cannot duplicate restore or rewards.
- Victory creates one reward grant per eligible player and disables further checkpoint restore for that completed encounter.
- In a development environment, start the encounter lab, record independent category evidence with
  `/mmo encounter contribute <damage|guard|support|objective> <amount> [player]`, then run
  `/mmo encounter victory`; eligible players receive one lot in Pending Rewards while rejected
  players receive the stable eligibility reason.
- Restart once before victory and once during `VICTORY_PENDING`; contribution totals, frozen roll,
  lot UUID and delivered quantity remain identical, and V0011 has no pending row after completion.

## Lifeskill harvest

- Two players use personal common node independently.
- Shared rare node reserves to one valid actor and releases on timeout/cancel.
- Crash before commit returns node/tool reservation; crash after commit preserves exactly one yield and durability cost.
- Life Focus improves declared output but zero Focus still permits base work.

## Farming and workers

- Offline crop progress is derived from timestamps and does not require loaded chunks.
- Worker job reserves all inputs/wages/food before start.
- Restart before completion resumes; restart after completion grants exactly once.
- Missing content definition pauses/quarantines job and returns no fabricated output.

## Market

- Buy and sell orders partially fill in price-time priority.
- Currency reservation equals remaining exposure; cancellation returns unused reserve exactly once.
- Unique item leaves player location before listing and cannot be equipped while escrowed.
- Regional cargo is rejected from Central Exchange, Bank, freight and prohibited travel.
- Risk quarantine holds both sides without deleting value.

## Mount and cargo

- One mount UUID never produces two live entities after chunk unload/restart.
- Logout with caravan snapshots all cargo once and restores at valid anchor/stable.
- Mount incapacitation transfers cargo to Stable Claim and reduces trade-cargo condition once.
- Selling a mount is rejected until equipment/cargo are removed.

## Storage and freight

- Quest/market/reward with full destination goes to correct Pending/Overflow location.
- Freight reserves items at origin; they are unavailable until arrival/cancel resolution.
- Arrival grants once after offline time; no regional cargo can enter freight.

## Party, quest and downed

- Downed player revives once per encounter; second defeat is death.
- Revive interruption does not consume the encounter revive until commit.
- Shared quest objective updates only eligible nearby members under its policy.
- Unique quest item is not copied through party join/leave.

## Content deployment

- Active encounter continues on old snapshot after new content activation.
- New sessions use new snapshot.
- Removed persistent ID without alias fails validation before production.
- Staging and production artifact hashes are identical.

## Content authoring tools

- Scaffolded item, move, node, recipe and quest definitions validate without manual schema repair.
- Stable-ID completion and reverse-reference lookup return the same types/references as the runtime compiler.
- A deliberately missing asset, recipe cycle and unreachable dialogue node fail CI with stable diagnostic codes.
- Combat trace export/replay produces the same authoritative outcomes for the same seed, inputs and content snapshot.
- Economy simulation detects a deliberately inserted positive-value recipe loop without creating live items or currency.
- PR preview uses an isolated database/pack and expires without altering Staging or Production.
- Test-created items and currency cannot enter normal market, trade or guild flows.
- No CLI, catalog, lab or preview action can deploy or mutate Production directly.

## Operational recovery

- Database loss blocks value-changing actions and does not fall back to local guesses.
- Provider failure degrades only owned presentation/domain behavior.
- Reconciliation detects duplicate location, stale escrow, stale lease and missing Preview Actor cleanup.
- Every admin recovery action has actor, reason and audit record.
