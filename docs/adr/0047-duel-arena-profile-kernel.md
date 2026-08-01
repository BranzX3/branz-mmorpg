# ADR 0047: Duel and arena profile kernel

- Status: Accepted
- Date: 2026-08-02
- Owners: Social and Combat

## Context

Milestone 7 still requires explicit duel/arena profile hooks. ADR 0004 excludes open-world PvP, so
hostile permission cannot be inferred from two players being near each other or attacking. The live
adapter first needs one deterministic contract for consent, countdown, teams, defeat and safety
invariants.

## Decision

`PvpMatchEngine` owns consent-based duels and two-team arenas. Duel challenges expire after 600
ticks, acceptance starts a 100-tick countdown, and hostile permission exists only while the match is
`ACTIVE`, both characters are ready and their teams differ. Arena creation performs the same
admission checks and starts at countdown.

Admission requires a ready character outside Engagement, inside a safe PvP region and without an
active external value transaction. The canonical balance profile freezes damage 0.65, Flask healing
0.70, guard pressure 0.75, CC duration 0.60 and 30 ticks of hard-CC immunity. Flask is allowed;
external buffs applied after countdown are not. Durability loss and Death Pouch creation are
unconditionally disabled by the profile contract.

The LOCAL/INTEGRATION Paper adapter keeps matches process-local: restart cancels the match and the
normal durable character snapshot remains authoritative. `/mmo pvp` provides duel consent and a
two-team arena lab inside a configurable radius from the initiator. The adapter resets transient
combat state at activation/completion, grants hostile permission only through the kernel, converts
lethal damage to safe defeat, scales Flask healing, uses authored PvP damage profiles and bypasses
ammo/catalyst/crossbow mutations while the match is active. A production region/instance provider
may replace the local radius without changing the kernel contract.

Lethal defeat marks a participant defeated and completes when one whole team remains. Surrender and
boundary violation forfeit the actor's team. Disconnect grants 200 ticks to reconnect before the
same deterministic team-resolution rule applies. Every mutation accepts an operation UUID; exact
kind replay is unchanged and cross-kind reuse fails closed.

## Consequences

- no player-vs-player damage is legal before mutual consent and countdown completion;
- duel and arena share one balance/safety contract without enabling open-world PvP;
- arena team defeat supports one-to-five characters per side;
- the live adapter can ask one authoritative predicate for target permission;
- region, combat, death and command adapters remain effects outside the pure kernel.

## Failure and recovery

Invalid admission, team composition, authority, phase or participant state changes nothing. A
process restart currently abandons process-local matches; the live adapter must cancel them safely,
restore combat presentation and retain the no-value-loss guarantees. Durable ranked matchmaking is
not introduced by this kernel.

## Migration impact

None. The kernel adds immutable social-domain state only.
