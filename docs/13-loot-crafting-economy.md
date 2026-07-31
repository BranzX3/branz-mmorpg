# Loot, Crafting, Trade and Economy

## Reward philosophy

V1 uses personal rewards to avoid party conflict and reduce exploit complexity. Content may still create visible world props/chests, but reward ownership is per eligible character.

## Encounter eligibility

A character is eligible when all are true:

- joined the encounter before the final eligibility cutoff,
- remained in the encounter region or valid recovery state,
- contributed meaningful damage, healing, guard/support, mechanics or objective actions,
- did not remain inactive beyond the AFK threshold,
- has not already received the completion grant ID.

Contribution uses category floors, not a single damage leaderboard. Support characters can qualify independently.

Late join cutoff defaults to boss reaching 20% HP or the final phase start, whichever occurs first. Encounters may override.

## Reward grant pipeline

1. Encounter completes and creates deterministic `reward_grant_id` per character.
2. Reward table rolls using server RNG and stored seed/audit metadata.
3. Durable reward record is committed.
4. Currency/materials transfer directly when possible.
5. Item rewards enter inventory through TransactionService.
6. Overflow enters persistent `PendingRewards`.
7. Presentation/UI occurs after persistence.

Retrying the pipeline with the same grant ID cannot duplicate rewards.

## Normal mob loot

- Common currency/materials may transfer directly.
- Physical item drops are owner-only for 120 seconds.
- If not collected, valuable registered drops move to PendingRewards; trash/common drops may expire.
- Party members cannot take another member's owner-only item.

## Pending rewards

PendingRewards is a persistent claim list accessible from the Scene Hub/Journal and designated reward NPCs.

- Default retention: 30 days.
- Claim is transactional.
- Items are previewable before claim.
- Capacity is bounded; when near capacity, the system warns and blocks optional reward generation before data loss.
- Quest-critical rewards never expire.

## Duplicate protection and pity

Pity applies only to explicitly declared boss/key reward pools.

- Pity is stored per character and reward pool.
- It increments on eligible completion without the target reward.
- Receiving the target resets it.
- Exact pity may be hidden, but qualitative feedback may indicate rising certainty.
- Pity cannot be traded with an item.

Duplicate protection may prefer unknown techniques/cosmetics before duplicates when the table declares it.

## Trade

V1 supports direct two-player trade only.

Trade stages:

```text
OPEN
OFFERING
LOCKED_FOR_REVIEW
BOTH_CONFIRMED
COMMITTING
COMPLETED/CANCELLED
```

- Items move into logical escrow but remain visibly in the trade UI.
- Any offer change clears both confirmations.
- Character disconnect cancels before commit or completes/reconciles from the transaction journal after commit begins.
- Wallet transfer occurs in the same business transaction boundary through WalletProvider idempotency keys.
- Bound quest items, Chronicle, Flask profile and system representations cannot be traded.
- Gameplay equipment remains tradeable after use unless its definition explicitly declares narrative binding. Binding is exceptional, not default.

## Bank and storage

V1 includes world-bound personal item storage accessed through bank NPCs/objects.

- Base capacity: 54 item slots, expandable through non-combat progression/economy.
- Bank is not remotely accessible from the Scene Hub.
- Deposit/withdraw uses TransactionService and persistent item locations.
- Equipment may be stored while enhanced, dyed, damaged or loaded; all state remains on the item UUID.
- Chronicle, active Flask representation, active quest-bound items and transaction-reserved items cannot be deposited.
- Currency banking is handled by WalletProvider; banked currency is excluded from Death Pouch calculation.
- Disconnect during deposit/withdraw reconciles from database truth.

Shared guild/account banks are outside V1.

## Crafting access

There are no permanent profession locks. Any character may learn all crafts over time. Active recipes and station access provide practical specialization.

V1 stations:

- Blacksmith forge
- Alchemy table
- General workbench
- Cooking/camp preparation may use vanilla-adjacent behavior but is not a major profession system

## Recipe definition

A recipe declares:

- stable recipe ID,
- station tags,
- knowledge prerequisites,
- ingredients and accepted substitutes,
- output definition and quantity,
- fixed quality/roll policy,
- batch limit,
- craft time,
- byproducts,
- economy classification.

Crafting is server-side and transactional. Inputs are reserved at start and consumed at commit. Disconnect after commit does not lose output; it enters inventory or PendingRewards.

## Alchemy

Alchemy skill affects:

- yield efficiency,
- access to specialized recipes,
- reduced waste/byproduct chance,
- controlled preparation options.

It does not create random quality tiers for finished potions. A recipe produces a known result.

V1 alchemy market pillars:

- specialized remedies,
- preventive wards/tonics,
- weapon coatings,
- utility bombs,
- tradeoff elixirs,
- Infusion Stock production efficiency.

NPCs provide weak essentials and price ceilings. Players provide specialization and regional efficiency.

## Blacksmith economy

Blacksmith services create sinks through:

- current durability repair,
- max durability restoration,
- enhancement materials,
- reforge/path change,
- Masterwork selection.

Player-crafted materials and services may reduce cost or add options, but an NPC fallback prevents permanent progression deadlock.

## Regional materials

Materials may have region/source tags that affect recipes and trade routes. V1 does not implement artificial per-region market servers or dynamic taxation. Regional identity comes from source availability and recipes.

## Economic safeguards

- Essential recovery has NPC price ceilings.
- Currency faucets and sinks are tagged and measurable.
- Reward tables declare expected value bands.
- Admin grants are journaled with reason and operator.
- No direct production edits to player balances/items.
- Craft loops are validated for positive-output recursion.
- Salvage cannot return more expected value than consumed inputs without an explicit time/content gate.

## Destruction and salvage

Players may intentionally salvage eligible items at a station after confirmation.

- Artifact/Relic items require typed confirmation or extra UI step.
- Salvage is transactional and irreversible after commit.
- Quest-critical/system/quarantined items cannot be salvaged.
- Salvage returns materials, never the full enhancement investment.
