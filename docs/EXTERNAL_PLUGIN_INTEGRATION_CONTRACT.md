# Branz MMORPG — External Plugin Integration Contract

Status: Locked baseline for cross-plugin surfaces
Runtime baseline: Paper 26.2, Java 25, Velocity front proxy
Related: DEVELOPMENT_OWNERSHIP_AND_CONTRACTS.md, PHASE_1_FOUNDATION.md, IMPLEMENTATION_ROADMAP.md

This document governs how Branz MMORPG talks to the three plugins it shares a
network and a database with. It does not describe their internals; it fixes the
boundary so both sides can change independently.

## 0. Participants

| Plugin | Role on the network | Deployed to |
|---|---|---|
| BranzWallet | Authoritative owner of all currency, the shared material warehouse, real-money top-ups, and Discord↔Minecraft account links | every backend |
| Branz Idle | Idle/base-building simulation. `mode: full` on the idle backend, `mode: remote` on the MMO backend | idle (full), mmo (remote) |
| BranzDiscord | Discord front-end (storefront, ranks, feed, tickets). One gateway per token | idle backend only |
| Branz MMORPG | This project | mmo backend |

MMORPG has no compile-time or runtime dependency on Idle or BranzDiscord.
It has an **optional** compile-time dependency on `WalletApi` only.

## 1. Direction of dependency

    mmorpg-paper  --compileOnly-->  BranzWallet (WalletApi, CommunityNotification)
    mmorpg-core / mmorpg-api / mmorpg-content / mmorpg-storage  -->  nothing external

Rules:

1. Only `mmorpg-paper` may reference `dev.branzx.wallet.*`. Every other module
   stays platform- and vendor-independent.
2. The dependency is `compileOnly` plus `testImplementation` of the same jar,
   matching how Branz Idle consumes it.
3. `WalletApi` is obtained from the Bukkit services manager and may be absent.
   Absence degrades features; it never fails plugin startup.
4. MMORPG never reads or writes a `wallet_*` or `idle_*` table directly, and
   never imports an implementation class from either plugin.

Resolution pattern (the only permitted one):

    WalletApi wallet = getServer().getServicesManager().load(WalletApi.class);

## 2. Currency ownership

**BranzWallet owns every unit of currency on this network. MMORPG owns none.**

| Currency | Owner | MMORPG access |
|---|---|---|
| Coin (gameplay currency, also the Vault economy) | BranzWallet | `WalletApi.coins`, `addCoins`, `hybridPay`, `recordCoinsEarned` |
| Credit (premium, bought with real money) | BranzWallet | `WalletApi.credits`, `adjustCredit` — admin/store paths only, never a gameplay reward |

Consequences that are binding on implementation:

1. `mmorpg-storage` must not contain a balance, wallet, currency, or ledger
   table. No Flyway migration may introduce one.
2. `QuestGamePort.grantCurrency(...)` is implemented as a thin adapter over
   `WalletApi.addCoins` / `hybridPay`. There is no MMORPG-side balance to
   reconcile, therefore no reconciliation job is ever needed.
3. Gameplay Coin income also calls `WalletApi.recordCoinsEarned` so the Hybrid
   Pay season offset cap stays accurate.
4. When `WalletApi` is unavailable, currency-granting operations fail closed:
   they return an unsuccessful `MutationResult` and route to the pending-claim
   mailbox (DEVELOPMENT_OWNERSHIP_AND_CONTRACTS §8). They never grant nothing
   silently and never grant from a local fallback balance.
5. MMORPG does not register a Vault `Economy` provider under any circumstances.

## 3. Idempotency: OperationId maps to the wallet transaction id

Wallet's idempotency key is `transactionId`, the primary key of the Credit
ledger; a replay aborts the transaction rather than minting twice. MMORPG's
`OperationId` maps onto it **one to one** — no second outbox is layered on top
of Wallet's.

Format:

    mmo:<subsystem>:<entity>:<playerUuid>:<discriminator>

Examples:

    mmo:quest:branz_broken_seal:9a1f...:reward
    mmo:mastery:branz_mining:9a1f...:milestone_25
    mmo:loot:branz_seal_guardian:9a1f...:enc_7c22

Rules:

1. An `OperationId` is derived deterministically from durable state, never from
   a clock, a random value, or an entity runtime UUID. Recomputing it after a
   restart must yield the same string.
2. The same string is passed verbatim as `transactionId` to `adjustCredit`, and
   as `idempotencyKey` to `hybridPay`.
3. Max length 128 characters; charset `[a-z0-9:_]`. `ContentId` colons are
   rewritten to `_` inside a segment so the segment separator stays unambiguous.
4. `addCoins` is *not* idempotent by itself. Any Coin grant that could be
   replayed (quest reward, milestone payout) must be guarded by an MMORPG-side
   operation record committed in the same transaction as the gameplay effect,
   before the Wallet call. Fire-and-forget trickle income (mob drops, gathering)
   does not need one.

## 4. Shared material warehouse

`WalletApi.warehouse*` is the cross-server item bridge: the idle base produces a
material, the MMO backend consumes it.

**Key format is not `ContentId.toString()`.** The live table
`wallet_warehouse.item_key` is populated by Idle with **upper-case Bukkit
Material names** (`IRON_ORE`, `DIAMOND`), normalised through
`toUpperCase(Locale.ROOT)` on every read and write.

Binding rules:

1. Vanilla-backed materials use the upper-case Bukkit Material name, so MMORPG
   and Idle address the same row.
2. MMORPG-defined materials that have no Bukkit equivalent are namespaced and
   upper-cased: `ContentId` `branz:seal_fragment` becomes key `BRANZ:SEAL_FRAGMENT`.
   A colon can never appear in a Bukkit Material name, so the two spaces cannot
   collide.
3. MMORPG applies `toUpperCase(Locale.ROOT)` before every warehouse call, to
   match Idle's normalisation exactly.
4. The mapping `ContentId <-> warehouse key` lives in exactly one class in
   `mmorpg-paper` and is unit-tested in both directions.
5. Warehouse writes are relative and commute across backends. MMORPG never
   writes a whole inventory, only deltas, and never assumes the row is
   unchanged since it was read.
6. `warehouseWithdraw` refuses to go negative and returns `false`. A `false`
   return is a normal gameplay outcome ("not enough material"), not an error.

## 5. Player identity

DEVELOPMENT_OWNERSHIP_AND_CONTRACTS §3 fixes UUID as the identity. This network
adds a caveat that the contract must state explicitly:

- Velocity runs in offline mode; FastLogin verifies premium accounts and
  **switches a premium player from an offline UUID to their Mojang UUID**.
- A player therefore may appear under two UUIDs across their lifetime.

Rules:

1. MMORPG keys all persistent player rows on the UUID Bukkit reports at that
   moment. It does not attempt its own UUID reconciliation.
2. Whatever migration policy Wallet applies to `wallet_*` rows is the network's
   policy. If a migration path is added, MMORPG exposes an admin command that
   moves its own rows for an explicit `(oldUuid, newUuid)` pair, invoked by an
   operator — never inferred automatically from a name match.
3. Display names are never an identity, a join key, or a lookup key.

## 6. Database sharing

Wallet, Idle, and MMORPG all point at MySQL database `branz` on the network.

| Concern | Rule |
|---|---|
| Table names | Every MMORPG table is prefixed `mmorpg_`. Already satisfied by `V1__foundation.sql`. |
| Flyway history | MMORPG configures an explicit history table `mmorpg_schema_history`. The default `flyway_schema_history` is a network-shared name and must not be claimed. |
| Migrations | Forward-only, unique ordered versions, MMORPG-owned files only. A migration never touches a `wallet_*` or `idle_*` table. |
| Connection pool | MMORPG owns its own Hikari pool (`BranzMMORPG`) sized independently. |
| Transactions | A transaction spanning MMORPG tables and a `WalletApi` call is impossible. Order the work so the MMORPG side commits first and the Wallet call is replay-safe (§3). |

## 7. Vault economy provider

Both Idle (`vault.provide`, registered at `ServicePriority.Highest`) and Wallet
publish Coin as the Vault `Economy`. Vault itself is not currently installed on
either backend, so the conflict is latent rather than live.

Rules:

1. On the MMO backend, BranzWallet is the sole Vault economy provider.
2. `mmo/plugins/Idle/config.yml` must set `vault.provide: false`. The MMO
   backend runs Idle in `mode: remote`; a remote window must not own the
   network economy.
3. MMORPG registers no Vault provider and reads balances through `WalletApi`
   rather than through Vault, so provider ordering cannot affect it.

## 8. Discord notifications

BranzDiscord runs on the idle backend only — MMORPG cannot call it, and must not
try to reach Discord itself.

The supported channel is Wallet's `CommunityNotification` Bukkit event, which
exists precisely so a game moment can reach Discord without the two plugins
depending on each other.

Rules:

1. MMORPG fires `CommunityNotification` for player-visible milestones: rare
   drop, boss first-kill, mastery milestone, season event.
2. Payload carries the player UUID, not the display name, as identity; the name
   field is presentation only.
3. Firing is best-effort. A missing listener (BranzDiscord absent from this
   backend, which is the normal case today) is not an error and never blocks or
   fails the gameplay transaction that produced it.
4. MMORPG opens no HTTP client, holds no bot token, and reads no Discord
   configuration. `WalletApi.discordIdFor` is blocking and, if ever needed, is
   called off the main thread.

## 9. What MMORPG must never do

1. Create a currency, balance table, or Vault economy provider.
2. Write to a `wallet_*` or `idle_*` table.
3. Import an implementation (non-`api`) class from Wallet, Idle, or Discord.
4. Require Idle or BranzDiscord to be installed.
5. Claim the default Flyway history table.
6. Grant a reward outside an idempotent operation when a replay is possible.
7. Identify a player by display name.

## 10. Milestone impact

| Milestone | External surface first touched | Must be settled by |
|---|---|---|
| M0–M4 | none | — |
| M5 (C8 gathering/crafting/economy) | Coin grants, warehouse keys | §2, §3, §4 |
| M6 (C10 boss) | `CommunityNotification` | §8 |
| M8 (C12 operations) | admin UUID migration command, audit reconciliation | §5, §6 |

Sections 2–4 must be implemented as written before the first Coin or warehouse
call is merged. Everything before M5 may proceed with no external dependency at
all.
