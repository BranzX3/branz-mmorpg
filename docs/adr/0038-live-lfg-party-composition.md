# ADR 0038: Live LFG directory and party composition

- Status: Accepted
- Date: 2026-08-01
- Owners: Social and Bootstrap

## Context

ADR 0037 defines LFG listing and approval semantics, but a kernel acceptance is not itself party
membership. The Paper runtime needs a testable directory that uses Player Session readiness and
composes admission with the live party authority without presenting a successful LFG join when the
party has become full, changed leader or otherwise rejected the applicant.

## Decision

One main-thread `LfgController` owns a process-local directory exposed through `/mmo lfg`. A ready
player can publish activity, region, language, descriptive role, one-line experience note, public
requirements and an optional automatic policy. Publishing creates a solo party when necessary and
requires the current party leader. Browse supports optional activity, region, language and role
filters plus applicant-supplied public eligibility tags.

Applicants request a stable listing UUID with their descriptive role, note and public eligibility
tags. Approval remains the default. A leader can inspect requests, accept/decline by online player
name (or UUID for an offline decline), inspect status and close the listing. Applicants can cancel a
pending request. Automatic listings use the same request command.

For an accepted effect, the controller first computes the immutable LFG transition, then asks the
live `PartyController` to perform a kernel invite plus accept on the current main-thread party
snapshot. The LFG replacement is installed only if party admission succeeds. This makes the party
the final membership authority and prevents an LFG success message on capacity/readiness failure.

Before every directory command, active listings reconcile against current party identity, leader,
size and previously accepted applicants. A mismatched listing closes rather than guessing new slot
or authority state. Offline/unready leaders are excluded from browse. Closed listings are removed
from the live directory.

## Consequences

- live LFG and party mutations have one observable admission outcome;
- ordinary party and LFG state share the same process-local restart boundary;
- a leader/membership change requires publishing a fresh listing, avoiding stale capacity repair;
- public test commands accept raw stable IDs/tags until a later UI supplies authored selections;
- no hidden Mastery data is read or displayed by the adapter.

## Failure and recovery

Invalid filters, profiles, listing IDs, requirements, authority and readiness mutate nothing. If
party invite or acceptance fails, the pending LFG request remains and no accepted effect is
installed. Server restart clears all listings. An active checkpoint may reconstruct its party under
ADR 0036, but never reconstructs an obsolete LFG advertisement.

## Migration impact

None. Live LFG is intentionally process-local with ordinary party state.
