# Farming and Workers

## Farming scope

V1 farming uses leased instanced plots attached to major-city farming districts and selected rural hubs. It does not permit arbitrary protected-world block ownership.

## Plot model

A character leases plot capacity using Civic Influence. A plot has:

```text
plot ID and city/region
soil type and fertility
water state
temperature/exposure
crop slots
lease state
```

Crops progress by wall-clock stages stored in PostgreSQL; chunks need not remain loaded. Growth caps when care requirements are unmet but crops do not vanish merely because the player is offline.

## Farming actions

- prepare soil;
- plant seed;
- water/fertilize;
- prune/treat disease;
- harvest;
- breed/select seed variants through authored recipes.

Farming Rank and Mastery improve care efficiency, seed recovery, batch harvest and access to regional crops. Output scaling follows the same bounded yield rules as gathering.

## Crop states

Crops do not use real-time spoilage after harvest. While growing they may become stressed, diseased or overripe according to long, forgiving windows. Notifications warn before meaningful loss.

## Workers

Workers are hired city contracts represented by database records, not permanent simulated NPC entities.

Worker roles:

- Gatherer — discovered civic resource nodes.
- Processor — workshop processing recipes.
- Crafter — approved workshop recipes.
- Farmhand — watering/care/harvest assistance.
- Porter — city storage/workshop transfer within one city.

## Worker job transaction

Before start, the system reserves all inputs, wages, food, workshop slot and output capacity. Progress is calculated from start time, worker stats and job definition. Completion grants outputs once.

## Offline behavior

- Jobs continue while the owner is offline.
- Queue duration is capped at 24 hours of work.
- Workers cannot generate rare boss/event resources.
- Worker yields are below active expert play and primarily provide baseline materials/convenience.
- No job starts without reserved costs.

## Worker stats

Speed, Stamina, Aptitude and Trait. Traits are authored bounded specializations, not wide RNG affix pools. Workers gain experience from completed jobs and can be reassigned after current work.

## Limits

Base character limit: two workers, expanded through Civic Influence and city access to a V1 cap of six. Each workshop/node has explicit worker capacity. This prevents invisible exponential production empires.

## Failure and reconciliation

Worker jobs use idempotent job IDs. On crash, elapsed progress recalculates from persisted timestamps. Missing definitions pause the job and quarantine reserves rather than deleting inputs.
