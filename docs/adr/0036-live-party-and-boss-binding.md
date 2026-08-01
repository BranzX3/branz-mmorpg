# ADR 0036: Live party commands and boss binding

- Status: Accepted
- Date: 2026-08-01
- Owners: Social and Bootstrap

## Context

ADR 0035 defines deterministic party membership, but the Paper runtime still requires explicit
participant names when starting a boss encounter. V1 parties must own invitation, leadership,
ready-check and reconnect behavior while preserving the rule that ordinary parties do not survive a
server restart. An active checkpoint encounter is the only restart exception.

## Decision

One main-thread `PartyController` owns live in-memory party runtimes and exposes create, status,
invite, accept, decline, leader transfer, kick, leave and ready-check commands under `/mmo party`.
Character readiness gates every command. Quit enters the kernel's 6,000-tick reconnect grace;
Player Session readiness reconnects a returning member, and a one-second clock task advances invite,
ready-check and disconnect deadlines.

Starting a boss encounter without explicit participant names resolves the actor's ready, online
party members in stable UUID order. Explicit participant arguments remain available to the
environment-gated encounter lab. This keeps party authority separate from encounter state while
allowing the existing V0009/V0010 persist-before-effect path to own the admitted attempt.

After V0009 recovery completes, every active multi-player boss checkpoint without an installed
party is reconstructed as a deterministic checkpoint party. Online ready participants are marked
online and unavailable participants enter reconnect grace. The recovered encounter participant set
is authoritative; general process-local parties are intentionally not restored.

## Consequences

- normal party commands are gameplay commands and are not restricted to development environments;
- a leader can start the existing boss lab for all ready online party members with one command;
- offline, unready and grace members remain in the party but are not admitted to a new encounter;
- restarting outside an active checkpoint dissolves the party as required by the V1 specification;
- recovered checkpoint parties regain leadership, membership and reconnect handling without adding
  a second durable party record that could disagree with the encounter snapshot.

## Failure and recovery

Kernel failures leave the current runtime unchanged and report the stable error code. The controller
installs a replacement only after a successful transition. Boss admission still fails atomically on
participant availability or character readiness. Recovery waits for the boss controller, skips
participants already claimed by another recovered party and never invents members outside the
durable encounter participant set.

## Migration impact

None. Active-checkpoint recovery reads participant membership from the existing V0009 encounter
payload. Ordinary parties are deliberately process-local.
