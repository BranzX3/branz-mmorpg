# City Economy and Regional Trade

## Five V1 economic cities

### Frostpeak

Produces metal, stone, tools, heavy components and preserved mountain food. Imports timber, herbs, medicine, textiles, fish oil and luxury food.

### Red Harbor

Produces fish, salt, oil, rope, sailcloth and spice blends. Imports metal, timber, stone, weapon components and preservatives.

### Elderwood

Produces timber, sap, resin, mushrooms, herbs and dye plants. Imports metal tools, salt, glassware, stone and preserved food.

### Riverlands

Produces grain, vegetables, meat, leather, freshwater fish and cooking ingredients. Imports tools, salt, medicine, lumber and defenses.

### Ashen Reach

Produces cinder crystal, volcanic glass, sulfur, magical ash and forge catalysts. Imports food, water supplies, medicine, protective textiles, timber and cooling reagents.

## Economic profile

Every city defines:

- 3–5 primary production categories;
- 3–5 import needs;
- 2–4 authored city trade goods;
- workshop specialties;
- category demand indices;
- event modifiers and accepted/rejected goods.

## Trade goods

Trade cargo is created from real materials plus processing, a city workshop, packaging fee and origin stamp. Example:

```text
Iron ingots + timber + leather straps
→ Frostpeak Toolworks
→ Frostpeak Reinforced Tool Shipment
```

Cargo cannot be unpacked back into ingredients, enter Central Exchange/Bank or use unrestricted fast travel.

## Demand

Demand index ranges 0–200:

- 0–50 saturated;
- 51–90 low;
- 91–110 normal;
- 111–150 high;
- 151–200 shortage.

Server-wide deliveries reduce city/category demand; demand recovers over time. A soft personal repetition modifier prevents one unthinking loop without invalidating specialization.

## Pricing

```text
sale = baseValue × demand × distance × routeRisk × condition × saturation × contractBonus
```

Distance alone cannot make a route dominant. Multipliers are capped and visible as qualitative information until Trading rank unlocks detailed estimates.

## City events

Mine collapse, festival, mobilization, epidemic, reconstruction and caravan disruption alter demand and activity boards. Events are announced diegetically through NPCs, boards and merchant news.

## Cargo condition

```text
PRISTINE
WELL_PACKED
STANDARD
DAMAGED
```

Damage may result from mount incapacitation, falls, environmental mismatch or contract failure. Packaging such as reinforced, waterproof or cooled containers mitigates declared risks.

## Cargo capacity

Trade cargo uses dedicated mount/player cargo slots and weight. Light packs may be carried; heavy and bulk shipments require mule, camel or llama caravan. Teleport/coach rules inspect cargo before travel.
