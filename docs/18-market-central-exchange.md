# Central Exchange and Market

## Market surfaces

1. Commodity Central Exchange.
2. Unique Item Market.
3. Crafting Commissions.
4. Market Watch in Scene Hub (read-only outside city).

Full deposit, withdrawal, listing and currency claim require a Market Clerk/Board in a major city.

## Commodity order book

Stackable commodities use buy and sell orders with price-time priority and partial fills. Buy orders reserve their full maximum currency. Sell orders escrow lots before activation.

When crossing prices match, execution uses the older resting order's price; unused buyer reservation returns automatically.

## Unique listings

Weapons, armor, tools, workwear, accessories, mounts and cosmetics list individually. The listing displays all persistent condition and history needed for informed purchase. Crossbows must unload ammunition; mounts must remove cargo and equipment before listing.

## Market Warehouse and Balance

Purchased goods arrive in Market Warehouse. Sale proceeds enter Market Balance. Both are safe from Death Pouch but usable/withdrawable only at market services. They are not remote inventory.

Base capacity:

- 40 warehouse slots;
- 2,000 weight units;
- 10 active sell orders;
- 10 active buy orders.

Civic/Broker progression can expand these within caps.

## Fees

- Listing deposit: 1%.
- Sale tax: 7.5%.
- Buyer pays the displayed execution price with no hidden fee.
- Cancelling a new listing during its minimum exposure period forfeits the deposit.

## Price guardrails

Each definition has a reference price based on filled trades, median, volume, production anchors and NPC floors/ceilings where appropriate. Commodity ranges default to ±25%; processed/consumables ±35%; unique items use wider stat-aware ranges. Reference price may move at most 5% per real day unless an audited economy intervention is deployed.

## Order states

```text
CREATED
ACTIVE
PARTIALLY_FILLED
FILLED
CANCEL_PENDING
CANCELLED
EXPIRED
QUARANTINED
```

All fills store order IDs, character IDs, lot/item IDs, price, tax, content version and risk flags.

## Crafting commissions

Two modes:

- Buyer-provided materials: inputs and payment escrow; eligible crafter completes at a station.
- Crafter-provided materials: buyer defines accepted output and maximum price; eligible crafter submits a matching item/lot.

Deadlines, rank, recipe, quantity, variant and item condition are explicit.

## Direct trade

V1 direct trade is limited to eligible party-loot transfers and low-value social items. High-value equipment, bulk commodities and currency exchange use market/commission systems for auditability.

## Risk controls

Detect self-trading, circular trades, linked-account anomalies, junk-item currency transfer, wash volume, rapid relisting and duplicate lineage. Suspicious fills pause in QUARANTINED without deleting value.
