# ADR 0051: Live Paper resource-node lab

## Status

Accepted.

## Context

The pure resource-node kernel and V0014 transaction boundary need one live Paper path that proves
authored work timing, durable tool reservation, Rank/Focus mutation, Pending Rewards output and
restart recovery together. A simulation-only command cannot prove value ownership or crash
semantics, and vanilla block drops must not become an alternate authority.

## Decision

The LOCAL/INTEGRATION development console now exposes a Resource Node Lab with these rules:

- the active content snapshot compiles the first authored node, its sharing/charge/timing rules,
  required tool tags, durable tool definition, output quantity, committed rank evidence and all
  thirty cumulative rank thresholds;
- the example Paper fixture supplies `equipment.training_pickaxe` as a signed, database-authority
  unique projection and renders it as an iron pickaxe;
- `/mmo node tool` grants that test-provenance tool through the normal value journal, `/mmo node
  harvest [0-5]` reserves the exact item and waits until the authored commit tick, and `/mmo node
  status` reads the durable node, tool, Rank and Focus state;
- the node instance ID is deterministic from the authored definition, and canonical codecs persist
  every personal/shared slot, reservation, wall-clock timestamp and exact processed operation;
- harvest uses the pure node, Rank and Focus engines, decrements the frozen durability cost and
  creates the deterministic yield lot in Pending Rewards through one V0014 compound transaction;
- exact harvest replay returns the committed node, tool, character and output without a second lot;
  and
- startup reconciliation releases every pre-commit reservation and exact tool, while a five-second
  wall-clock scan advances timeout, depletion and recovery independently of chunk load.

## Consequences

Milestone 8 now has an in-game acceptance path for the node reservation/harvest crash boundary.
The lab deliberately grants no vanilla drop and does not place a production world node; authored
world placement, tools/workwear breadth, Fishing, Hunting and processing remain later Milestone 8
slices.
