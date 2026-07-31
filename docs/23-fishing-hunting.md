# Fishing and Hunting

## Fishing waters

Each water region has species, abundance, weather/time modifiers, danger and pressure state:

```text
ABUNDANT
NORMAL
PRESSURED
DEPLETED
SEASONAL
DANGEROUS
```

Server-wide catch pressure lowers local abundance temporarily and encourages movement without making a spot permanently useless.

## Active fishing

Flow:

```text
cast
→ bite signal
→ hook timing
→ tension/direction phase
→ land or lose catch
```

Active fishing provides the best rare and large-fish chance. Timing windows are forgiving enough for normal latency and never require one-tick precision.

## Relaxed fishing

Available only while online, in allowed safe/low-risk fishing regions and with the player stationary. It is slower, has reduced rare chance and stops on full inventory, broken rod, movement, damage, logout or region invalidation. It never continues offline.

## Catch identity

Common fish are commodity lots. Trophy fish are unique UUID items carrying species, size, origin water and catch time; they may be listed, displayed or used in authored recipes.

## Hunting

Hunting is distinct from normal mob farming:

```text
find trace
→ identify species/quality
→ follow trail
→ enter hunting encounter
→ use hunting weapon/trap
→ harvest carcass
```

Tracking clues are owner/party scoped. Targets have flee, alert and habitat behavior. Hunting encounters use Combat Engine with a hunting profile and reward eligibility.

## Kill quality

Damage tags influence outputs:

- precise weak-point kill preserves hide;
- fire may damage hide and cook/ruin meat;
- poison may contaminate food;
- excessive blunt damage may damage trophy parts.

No single kill method is mandatory for basic material progression.

## Hunting weapons

V1 supports hunting crossbow/rifle-like visual content only if implemented through allowed weapon definitions; otherwise Bow/Crossbow hunting techniques provide the gameplay. Mounted combat remains disabled.

## Trophy and contracts

Rare tracks, trophy size and clean harvest feed Hunting rank, city boards, cooking, tanning and cosmetic crafting. Trophy claims are idempotent and party ownership is explicit.
