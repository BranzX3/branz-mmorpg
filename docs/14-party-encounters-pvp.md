# Party, Encounters, Threat and PvP

## Party V1

- Maximum size: 5.
- One leader; leadership transfers automatically on leave/disconnect timeout.
- Invite, accept, decline, kick, leave and disband.
- No raid conversion in V1.
- Party membership is persistent across short disconnects for 5 minutes, but not across server restart unless the group is in an active checkpoint encounter.
- Friendly fire is off in PvE.
- Party HUD shows HP, primary resource state, status warnings and dead/disconnected state. There is no downed state; dead is dead.

## Quest sharing

Objectives declare sharing policy:

- `INDIVIDUAL`
- `PARTY_NEARBY`
- `PARTY_ENCOUNTER`
- `PARTY_ALL_PRESENT`

Quest acceptance and narrative choices remain individual unless the quest explicitly declares a group decision.

## Encounter membership

An encounter has:

- stable encounter definition,
- runtime encounter ID,
- content snapshot,
- region/arena,
- participants and join timestamps,
- threat state,
- checkpoint,
- eligibility ledger,
- reset/completion idempotency keys.

Joining may occur by region entry, hostile action or explicit start interaction. Boss encounters freeze their primary participant set at the authored lock point but may allow reconnect/re-entry.

## Boss checkpoints and wipe

- A checkpoint stores party participants, prepared Flask snapshots and encounter-specific state.
- A wipe occurs when all valid participants are dead, outside the arena beyond grace, or the encounter declares failure.
- Wipe resets boss/mobs, clears encounter statuses/projectiles/zones and restores the prepared Flask snapshot.
- Durability loss from the failed attempt remains; this is the equipment sink.
- No reward or mastery completion grant occurs on wipe, though limited execution evidence may still be accepted.
- Re-entry grace after disconnect: 90 seconds.

## Death and respawn

V1 has no downed/crawl/revive state and no combat resurrection.

### Open-world PvE death

- Create Death Pouch equal to 10% of current carried wallet.
- Banked currency is safe.
- Inventory/equipment remain owned.
- Respawn at bound sanctuary or nearest configured regional sanctuary.
- Repeated deaths take 10% of the remaining carried wallet and create separate pouches.

### Death Pouch

- Owner-only visual and interaction.
- Exact death position with nearest-valid fallback.
- Persists through restart/chunk unload.
- Expires after 7 real-time days including offline time.
- No map marker, waypoint or exact coordinate UI.
- Nearby owner-only effects and optional vague NPC hint for one pouch at a time.
- Recovery is idempotent and journaled.

### Boss/arena death

- Boss encounter death follows checkpoint rules and does not create an open-world pouch when the encounter profile suppresses it.
- Duel/arena death has no pouch, durability loss or open-world currency loss.

## Threat

Threat is owned by encounter/MobProvider integration, using Core MMO signals.

Sources:

- damage dealt,
- healing/shielding Engaged participants,
- authored guard/provoke actions,
- objective mechanics,
- proximity baseline.

Threat is not a visible numeric meter in V1. Bosses may use fixation mechanics that override normal threat temporarily. Death removes active threat but reconnect/re-entry may restore a bounded portion according to encounter profile.

## Leash and reset

- Normal mobs use region/source-defined leash.
- Encounter mobs cannot be dragged outside encounter bounds.
- Reset clears combat runtime effects, restores authored health/posture, destroys encounter projectiles/zones and invalidates old hit contexts.
- Reset never rolls or grants loot.

## PvP scope

V1 supports:

1. Consent-based duel.
2. Instanced/region arena match.

V1 does not support open-world flagging or criminal systems.

## Duel

- Challenge and accept while both players are outside Engaged state and in allowed region.
- Countdown owns hostile permission.
- Leaving boundary, disconnecting beyond grace or using forbidden commands forfeits.
- Duel ends at lethal damage interception; loser is set to safe HP and no actual death pipeline runs.
- Consumable policy is configured per duel profile; default allows Flask but not external crafted buffs applied after countdown.

## Arena

- Dedicated PvP balance profile from `07-damage-defense-cc.md`.
- Match inventory is a snapshot of permitted equipped build and consumables.
- Arena end restores external state and clears arena-only status/cooldowns.
- Rewards are granted through the normal idempotent reward pipeline.
- Spectators cannot interact and are isolated from combat packets.

## Anti-stall rules

- Arena may apply escalating boundary/anti-stall pressure after authored inactivity.
- Healing reduction and CC DR prevent indefinite loops.
- No sudden hidden damage scaling; all escalation is communicated.
