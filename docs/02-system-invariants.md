# System Invariants

These rules are mandatory. Code and content that violate them are invalid even if they appear to work in a local test.

## Identity and ownership

1. `character_id`, `item_uuid`, `encounter_id`, `transaction_id` and stable content IDs are immutable identities.
2. Display names, Oraxen IDs, file paths and localized text are not persistent identities.
3. An item instance has exactly one ownership location at a time.
4. Every item ownership mutation occurs inside `TransactionService` and writes a transaction journal entry.
5. Cosmetic items have no combat stats and no durability.
6. Item data from client-visible NBT/PDC is treated as a reference, not unquestioned authority. Persistent state is verified against the server repository.
7. Unknown or incompatible items are quarantined, never silently deleted.

## Character and build

1. One account owns one active persistent character in V1.
2. No class, character level or gear score gates core content.
3. Exact mastery and conditioning values are hidden from players, but rejection reasons and qualitative readiness are visible.
4. Learning breadth has no permanent numeric penalty.
5. Active power is constrained by equipment, moveset branches, form, attunement and resources.
6. Build changes that alter moves, form or attunement require a Rest Context. Equipment and consumable hotbar changes may occur outside combat according to their own rules.

## Hotbar and inputs

1. Hotbar slots 1–8 are gameplay slots.
2. Hotbar slot 9 contains the server-owned Scene Chronicle item.
3. The Chronicle cannot be moved, dropped, traded, stored, consumed or used as a crafting ingredient.
4. Selecting slot 9 does not open the Scene. Main-hand use opens it when eligibility checks pass.
5. F means the client's swap-hand action; Q means the client's drop action. Tutorials display current keybind names, not hard-coded letters.
6. In Combat Ready or Engaged state, Q is owned by the combat input router and never drops the held gameplay item.
7. In Exploration state, Q uses vanilla drop behavior except for protected system items.
8. Shift is sneak in Exploration. In Combat Ready/Engaged, directional tap is dodge; stationary hold is crouch/brace.
9. The input buffer contains at most one future action.

## Combat authority

1. The server decides action start, resource cost, hit, damage, status, interruption and reward eligibility.
2. A move definition is immutable for the lifetime of an active action and encounter content snapshot.
3. No random miss or hidden accuracy roll exists.
4. V1 has no random critical chance. Critical-like bonuses are explicit conditional advantages.
5. Combat actions cannot reduce the user below 1 HP when the action declares a self-HP cost.
6. Durability does not decay from PvP.
7. Enhancement never improves dodge invulnerability or perfect-guard timing.
8. Combat state and weapon state are separate dimensions.

## Scene and UI

1. The slot 9 Scene Hub opens only while not Engaged and while the character is in a stable physical state.
2. The daily Scene Hub is local: the real player is not teleported.
3. The preview actor is visible only to its owner and has no collision, AI, damage or world persistence.
4. Taking damage, entering Engaged state, teleporting, changing world, mounting, falling, plugin disable or disconnect closes the Scene and discards uncommitted preview changes.
5. Preview state is never committed implicitly by closing a menu.
6. Resource-pack or presentation failure may degrade visuals but must not corrupt gameplay state.

## Economy and rewards

1. Base HP, mana and stamina recovery cannot be monopolized by the player economy.
2. Player-crafted consumables focus on cures, prevention, specialization, utility and tradeoffs.
3. Boss and encounter rewards are personal in V1.
4. Eligible rewards are persisted before presentation. Inventory overflow enters `PendingRewards`.
5. A player cannot receive a reward twice for the same reward grant ID.
6. NPC price ceilings exist for essential recovery and basic remedies.

## Persistence and failure

1. Item ownership, wallet-linked transactions, enhancement results, reward grants and death pouches require immediate durable writes.
2. Mastery evidence and telemetry may be batched, but accepted evidence cannot be applied twice.
3. Only one server session may hold a character lease.
4. Every asynchronous result must verify the character session token before mutating live Bukkit state.
5. Bukkit/entity/inventory mutations run on the correct server or region thread.
6. Plugin shutdown cancels active actions, closes scenes, freezes new transactions and flushes required journals.
7. Production never uses Bukkit `/reload` and never edits content files directly on the server.

## Content and integrations

1. Git is the source of truth. Oraxen Studio is an authoring tool.
2. Gameplay definitions refer to stable `asset_id`, not provider-specific IDs.
3. Gameplay modules access Oraxen, MythicMobs, PacketEvents, WorldGuard and wallet systems only through provider interfaces.
4. Content bundles and resource packs are immutable, versioned artifacts.
5. An encounter uses one content snapshot from start through completion/reset.
6. A content migration must be idempotent and recorded.
