# ADR 0037: LFG listing and approval kernel

- Status: Accepted
- Date: 2026-08-01
- Owners: Social and API

## Context

Live party membership is available, but players still need a safe directory for finding an activity
group. The LFG contract requires activity, region, language, descriptive role preference,
experience note and entry requirements. Its default flow requests leader acceptance, optional
automatic joining must obey the same capacity boundary, and hidden Mastery values must never enter
the public directory model.

## Decision

A stable UUID `LfgListingId` identifies one listing tied to a `PartyId` and leader. The immutable
runtime carries stable activity and region definition IDs, a normalized language tag, the leader's
descriptive profile, public entry-requirement tags, join policy, available slots, pending requests,
accepted applicants and processed operation identities.

The default join policy is `LEADER_APPROVAL`. A qualifying applicant creates one pending request;
only the listing leader accepts or declines it. `AUTOMATIC` is an explicit alternative and emits the
same accepted-applicant effect immediately. Capacity is checked at admission, not only at listing
creation. Applicants can cancel pending requests and leaders can close a listing.

Role preferences are `FRONTLINE`, `GUARD_CONTROL`, `DAMAGE`, `SUPPORT` and `FLEXIBLE`; they remain
descriptive and confer no combat authority. Experience notes are single-line and bounded to 160
characters. Entry requirements and applicant eligibility are normalized public tags with bounded
count and length. Any tag containing `mastery` is rejected so a caller cannot encode hidden Mastery
numbers or thresholds through this contract.

Search matching is pure and checks open capacity, optional activity/region filters, normalized
language, descriptive role and public eligibility. Every state-changing command uses an operation
UUID; exact command replay is unchanged and cross-command reuse fails closed.

## Consequences

- directory presentation cannot depend on hidden character progression values;
- leader approval remains the safe default while authored/configured flows may opt into auto-join;
- accepted applicants are explicit effects for a live adapter to compose with party invitations;
- listing search and admission share the same public requirement predicate;
- party membership remains authoritative after an LFG acceptance effect.

## Failure and recovery

Closed/full listings, missing requests, leader misuse, duplicate applications and unmet public
requirements mutate nothing. Accepted capacity is retained in the immutable runtime. The pure
kernel performs no external party change; a later live adapter must acknowledge party membership
before presenting a successful join.

## Migration impact

None for the pure kernel. A durable cross-restart LFG directory would require a forward-only
migration and expiry policy; neither is introduced here.
