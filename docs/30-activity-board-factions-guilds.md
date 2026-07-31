# Activity Boards, Factions and Guilds

## Activity boards

Each city board generates a bounded rotating set from real system state:

- Hunt Contract
- Gathering Request
- Crafting Commission
- Delivery Contract
- Exploration Request
- Elite Bounty
- Regional Crisis
- Farming Supply Request
- Caravan Escort

Boards avoid compulsory daily streaks. Contracts last long enough for normal play and derive demand/rewards from city economy, encounter availability and content rules.

## Rewards

Contracts may grant currency, Renown, faction reputation, Civic Influence, recipe knowledge, regional materials and market demand changes. Reward budgets prevent contract loops from generating uncapped currency or rare inputs.

## Civic Influence

Visible, non-combat capacity used to lease workshops, farming plots, storage expansions, market privileges and worker slots. Influence is committed while leased and returns when the lease is safely released. It is earned through city quests, contracts, deliveries and reconstruction.

## Factions

V1 factions provide reputation, dialogue, mentors, recipes, services and authored conflict choices. Faction hostility may restrict local services but must provide a recovery path. V1 does not include faction war or territorial ownership.

## Basic guilds

V1 guild features:

- create, invite, leave, kick and roles;
- roster, message of the day and guild chat;
- banner/title presentation;
- activity listings and ready checks;
- small audited guild storage with role limits;
- guild contracts and shared cosmetic milestones.

No guild war, tax sovereignty, base ownership or uncapped treasury transfers.

## Guild storage safety

Every deposit/withdrawal records item/lot UUID, quantity, actor, role, timestamp and reason. High-value withdrawals require elevated permission and optional second approval. Guild storage cannot hold regional cargo, active quest items or market escrow.
