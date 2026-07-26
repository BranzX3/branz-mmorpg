# C8 material faucet/sink simulation

Reference launch loop:

- `branz:aether_deposit` yields 1–3 Aether Ore (mean 2) every 60–70 seconds.
- `branz:aether_ingot_recipe` consumes 3 Aether Ore and 5 Coins for one Aether Ingot.
- One permanently harvested deposit therefore produces about 102–120 Ore/hour,
  supporting 34–40 ingot crafts/hour.
- The corresponding sink is 102–120 Ore/hour and 170–200 Coins/hour.

The deposit is an active MMO source, so Idle/warehouse output is additive and
never the only acquisition path. Recipe validation computes reachability from
gathering sources through recipe outputs and rejects self-supporting cycles.

Coin and Credit ownership remains in BranzWallet. MMORPG creates no balance or
currency-ledger table. A paid craft first escrows materials in MMORPG storage,
then uses Wallet's idempotent checkout, then transactionally delivers output.
Wallet outage leaves escrow pending; insufficient Coins cancels and refunds;
retry after either process crashes cannot consume inputs or create output twice.

Balance tuning should preserve:

- finite node throughput from persisted respawn timers;
- no premium Credit path to mastery, random rolls, boss components, or power cap;
- a visible Coin sink on repeatable production;
- output overflow routed to pending claim rather than world drops.
