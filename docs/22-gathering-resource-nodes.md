# Gathering and Resource Nodes

## Node types

- Common — personal extraction state, short recovery, base economy.
- Rich — limited high-value charges, Focus-efficient, shared discovery.
- Rare — world-shared, long recovery, authored regional material.
- Regional — biome/region-specific knowledge and tool requirements.
- Event — temporary city/world demand or crisis source.
- Corrupted — risky inputs with special processing and status pressure.

## Node representation

A node definition references a world anchor/region rule and owns server state. Vanilla blocks may provide visuals, but normal block break does not decide rewards. Node state persists as `available`, `reserved`, `depleted` or `recovering`.

## Gathering action

```text
validate tool, region and node
→ reserve node charge and tool durability
→ begin 1.5–6 second action
→ commit at declared point
→ roll deterministic server yield seed
→ grant lot(s), durability loss and evidence
```

Movement, damage or wrong tool cancels before commit. After commit, rewards persist even if recovery animation is interrupted.

## Disciplines

### Mining

Ore, stone, crystal and excavation seams. Control mitigates fragile-material loss. Rich veins may expose a short follow-up fracture interaction.

### Logging

Logs, heartwood, sap and resin. Tree visuals need not disappear globally for personal common nodes. Rare ancient trees are shared and announced through knowledge, not exact global coordinates.

### Foraging

Herbs, mushrooms, flowers, fibers and seeds. Regional season/weather may change available definitions without real-time spoilage.

### Butchering and Tanning

Eligible carcasses expose one harvest action after combat. Kill method can affect hide/meat state; fire may damage hide, poison may contaminate meat. One carcass has explicit owner/party rights and charge limits.

### Excavation

Relics, clay, fossils and buried reagents discovered through regional clues and tools. Excavation never edits protected terrain permanently.

## Yield

```text
base quantity
× node richness
× mastery yield curve
× Focus modifier
× tool specialization
+ authored byproducts/rare rolls
```

The random seed is persisted with the reservation where duplication risk exists. Rare rolls are bounded and auditable.

## Competition

Common nodes are effectively personal to reduce griefing. Rich/rare nodes are shared and create community competition, but reserve to the first valid actor for the short work duration. No player can hold an idle reservation longer than the action timeout.

## Respawn

Recovery uses wall-clock database state. Chunk unload/restart does not reset a node. Region activity may alter recovery within configured bounds.

## Anti-bot

Server timelines, path diversity, node rotation, impossible-input detection and telemetry identify automation. The game does not use intrusive CAPTCHAs for ordinary players.
