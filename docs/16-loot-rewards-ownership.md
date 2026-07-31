# Loot, Rewards and Ownership

## Personal rewards

Normal encounters and bosses use personal reward rolls. World visuals may show a shared chest, but each eligible character receives an independent idempotent grant.

## Eligibility

Eligibility is based on meaningful participation, not last hit:

- damage and posture contribution;
- guard/control contribution;
- effective healing/support;
- objective actions;
- encounter presence and survival/return rules.

Joining near the end requires a minimum participation threshold. A dead/downed player remains eligible if contribution and encounter membership are valid. AFK proximity is insufficient.

## Reward destinations

1. Inventory when capacity allows.
2. Pending Rewards when full or offline.
3. Overflow Claim when a transactional destination is unavailable.

Valuable rewards are never dropped on the ground as the only fallback.

## World drops

Common world drops may use owner protection and a short lifetime. Unique items receive an item UUID before appearing. Pickup is a location transaction from WORLD_DROP to inventory.

## Boss rewards

Boss grants freeze at `VICTORY_PENDING`, create a reward grant ID per character and commit once. Retries cannot duplicate grants. Boss checkpoint Flask restoration does not restore ordinary potions, ammunition beyond encounter recovery rules or dropped cargo.

## Pity and duplicate protection

Pity is content-specific and recorded per character/reward pool. Duplicate protection may bias knowledge/cosmetic drops but does not guarantee unrestricted best-in-slot items. Pity counters are visible only when the content intentionally exposes them.

## Party loot trade window

A unique reward may be traded without market listing only to party members who were eligible in the same encounter, for two hours, before use, enhancement, dye or resale. Currency cannot be added to this transfer.

## Death pouch

PvE death transfers 10% of current carried wallet into a separate owner-only pouch at the nearest valid point to death. Bank and Market Balance are safe. Pouches persist seven real-time days including offline time, never merge, have no map marker and may receive only vague one-at-a-time hints from designated NPCs.
