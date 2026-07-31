# Navigation and Travel Network

## Discovery map

The map reveals regions, roads, settlements and services through landmark discovery. It does not reveal every enemy, node, quest target or Death Pouch.

Discoverable categories:

- settlement and district;
- road and pass;
- stable, harbor and coach stop;
- sanctuary/camp anchor;
- market, blacksmith, mentor and workshop;
- dungeon entrance and boss landmark;
- broad fishing/resource/hunting region.

## Quest navigation

Quests use landmarks, descriptions and search areas. Direct exact markers are reserved for mundane urban tasks or accessibility settings. Death Pouches remain markerless by invariant.

## Travel layers

- walking: exploration and resource access;
- personal mount: fast regional travel;
- caravan: cargo transport;
- coach: discovered city-to-city passenger route;
- ferry: discovered water crossing/island route;
- dungeon transition: authored entrance only.

## Coach and ferry

Use only at stations. Destination must be discovered. Service costs currency and uses a short transition rather than simulating a long ride. Ordinary baggage may travel; regional cargo is rejected or must use an authored cargo service that reduces trade profit and takes freight time.

## Route state

Roads have safety/risk profiles and may be affected by world events. Navigation displays qualitative risk; higher Trading/Exploration knowledge reveals better estimates.

## No arbitrary teleport

V1 has no open-world waypoint teleport. Admin recovery and stuck handling are audited exceptions. Fast services preserve city/route geography.

## Stuck recovery

`/unstuck` requires out-of-combat, no cargo, no active encounter and a channel. It moves to the nearest safe anchor and has a cooldown. Geometry-detected mount/caravan recovery uses separate audited logic and cannot be used as travel.
