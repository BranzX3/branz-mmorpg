# Implementation Map

## Milestone 0 — first bounded task

Implemented:

- `docs/03-architecture.md` — Gradle module skeleton and provider boundaries.
- `docs/04-identifiers-content-contracts.md` — stable definition and instance ID value types.
- `docs/36-content-dev-pipeline.md` — immutable manifest model/parser and reproducible JAR settings.
- `docs/39-implementation-roadmap.md` — typed result/error contracts, CLI shell, formatting/static-analysis quality gate and empty Paper bootstrap.
- `docs/43-content-authoring-tools.md` — stable diagnostic-code catalog and `mmo-content` command shell.

Tests:

- stable-ID format and invalid-ID rejection;
- typed result success/failure propagation;
- valid, invalid and missing content manifests;
- CLI manifest validation;
- bootstrap enable/disable lifecycle.
- local Paper boot/shutdown smoke path (`runServer -PsmokeTest=true`).

Not implemented:

- definition schemas/registries and reference validation (Milestone 1);
- persistence, migrations, leases or transactions (Milestone 2);
- gameplay listeners, items, combat or provider adapters.

Migration impact: none. This task creates no persistent schema or runtime value.
