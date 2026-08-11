# Live Acceptance Evidence

This directory stores repository-reviewed summaries of real local Paper client acceptance passes.

Evidence files are append-only acceptance records for an exact accepted **runtime revision** and
content version. A later documentation-only evidence commit may have a different Git SHA without
changing the runtime revision that was actually exercised. Any runtime source, migration,
configuration-default or active-content change requires a new runtime revision and a fresh owning
acceptance pass.

Evidence must not contain credentials, local machine secrets, database passwords, raw item payload
JSON or large raw server logs. Keep only the stable authority fields and minimum excerpts required to
identify a failed or passed invariant.

For the physical gameplay item authority pass, follow
`../45-physical-gameplay-item-live-runbook.md` and the acceptance owner
`../44-physical-gameplay-item-acceptance.md`. On the accepted runtime use `/mmo physical status` for
item/lot authority evidence; section A additionally uses the old runtime's signed held-item
projection evidence as described by the runbook.

A feature status must not advance from `AUTOMATED_VERIFIED` to `LIVE_ACCEPTED` merely because an
evidence file exists. The owning acceptance checklist must actually pass on a real local client.
