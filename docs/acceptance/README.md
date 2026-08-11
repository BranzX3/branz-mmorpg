# Live Acceptance Evidence

This directory stores repository-reviewed summaries of real local Paper client acceptance passes.

Evidence files are append-only acceptance records for an exact server commit and content version.
They must not contain credentials, local machine secrets, database passwords, or large raw server
logs. Keep only the minimum excerpts required to identify a failed or passed invariant.

For the physical gameplay item authority pass, follow
`../45-physical-gameplay-item-live-runbook.md` and the acceptance owner
`../44-physical-gameplay-item-acceptance.md`.

A feature status must not advance from `AUTOMATED_VERIFIED` to `LIVE_ACCEPTED` merely because an
evidence file exists. The owning acceptance checklist must actually pass on a real local client.
