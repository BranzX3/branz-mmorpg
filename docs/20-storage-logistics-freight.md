# Storage, Logistics and Freight

## Storage layers

### Personal inventory

Immediate carried items and hotbar. Slot 9 is reserved for Chronicle.

### City storage

Each major city has an independent item store. Items do not appear in another city automatically.

### Bank

Stores currency and explicitly bankable valuables globally. Bank is not bulk commodity storage and cannot accept regional cargo.

### Market Warehouse

Holds market deposits, purchases and claimable sales only.

### Mount cargo

General saddlebags and dedicated trade-cargo slots. Mount cargo is part of the mount state and cannot duplicate into player inventory during despawn.

### Overflow Claim

Recoverable system destination for quest rewards, failed delivery, mount recovery and crash reconciliation. It has a finite retention policy with prominent notifications; value is never silently deleted.

## Freight service

Players may ship normal items between discovered city storages:

- pay currency based on weight, distance and route;
- shipment takes real elapsed time;
- hazardous/unique categories may be restricted;
- regional trade cargo cannot use freight;
- shipment is database state, not a permanently loaded cart entity.

Freight states:

```text
BOOKED
IN_TRANSIT
DELAYED
ARRIVED
CLAIMED
QUARANTINED
```

World events can delay but not permanently destroy ordinary freight. Insurance is a currency sink that covers declared delay/damage categories.

## Inventory-full policy

Before committing a reward or withdrawal, the service reserves destination capacity. If capacity changes, value goes to Pending Rewards or Overflow Claim. It never drops at the player's feet as a fallback.

## Weight

Personal inventory uses slot limits plus a generous weight threshold for logistics items. Combat items are not micromanaged by weight beyond equipment load. City, market and mount stores enforce both slots and weight.

## Notifications

A notification inbox records market sales, commission completion, freight arrival, worker completion, expiring overflow and admin compensation. Inbox is not item storage; entries link to the appropriate claim service.
