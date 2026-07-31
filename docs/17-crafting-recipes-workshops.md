# Crafting, Recipes and Workshops

## Recipe engine

All production uses one transactional recipe engine. A recipe declares:

```text
inputs and accepted variants
station and regional requirements
knowledge/rank requirements
batch size and duration
fuel/tool requirements
outputs and byproducts
quality/state rules
content snapshot
```

## Craft session

```text
validate station and permissions
→ reserve inputs, tool durability, fuel and fees
→ start cancellable work timeline/job
→ commit outputs once
→ record progression evidence
```

Cancellation before commit returns reserved inputs. Crash recovery resumes from the journal or returns reserves; it never produces both inputs and outputs.

## Stations

- Camp cooking fire
- Kitchen
- Alchemy table
- Smithing forge
- Processing bench
- City workshop
- Farming shed

Station tier controls recipe access and batch size, not arbitrary RNG success.

## Recipe knowledge

Recipes come from mentors, exploration, experiments, quests, factions, worker discoveries and regional workshops. Known recipes are permanent. Some recipes accept substitutions discovered as variants.

## Quality

V1 avoids random common/rare/epic output tiers. Output state is deterministic from recipe, input state, station and declared mastery threshold. Alchemy skill improves yield and batch efficiency rather than randomly changing potion tier.

## Workshops

Civic Influence leases city workshops. A workshop has type, tier, storage link, queue capacity and worker slots. Leases are refundable when relinquished after outstanding jobs finish. Workshop ownership grants service access, not land sovereignty.

## Commissions

The market commission system can reserve buyer materials and pay a crafter on successful recipe completion. The recipe engine verifies exact output requirements before transferring ownership.
