# Enhancement, Reforge and Repair

## Enhancement range

V1 uses +0 to +10.

- +1–3: bounded potency.
- +4–6: handling/efficiency.
- +7–9: forge-path specialization.
- +10: Masterwork trait choice.

Total generic raw damage increase from +0 to +10 is targeted at 18%, preventing enhancement from replacing execution or knowledge.

## Failure

A failed enhancement:

- never destroys the item;
- never reduces enhancement level;
- reduces maximum durability by a tier-defined amount;
- grants Forge Momentum to that item UUID.

Momentum is item-bound and transfers with the item so market inspection remains truthful. It resets on successful enhancement and is visible to the owner/listing inspector.

## Paths

Weapon paths:

- Heavy — posture/guard pressure, higher costs.
- Balanced — handling and recovery.
- Runic — mana/imbuement compatibility.

Armor paths:

- Fortified — poise and guard.
- Mobile — load/acceleration tradeoffs.
- Runed — conductivity and status resistance.

Path changes require reforge materials and may reset path-specific choices, never enhancement level.

## Masterwork

At +10 the player chooses one authored trait from the item's allowed pool. Masterwork traits are mechanics with tradeoffs, not unrestricted flat percentage stacks.

## Repair

Current durability repair uses currency plus common materials at a blacksmith; field kits repair a limited amount outside combat and cannot restore maximum durability.

Maximum durability restoration requires restoration materials tied to the item's tier and path. Restoration has no failure chance.

## Transaction flow

Enhancement, reforge and repair move the item into service escrow, reserve materials/currency, calculate once with an idempotency key, persist result and return the item. Disconnect or crash resumes or rolls forward the journal; the item is never simultaneously equipped and in service.
