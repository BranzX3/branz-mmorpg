# ADR 0050: Durable resource-node compound transactions

## Status

Accepted.

## Context

The resource-node kernel freezes an exact tool, durability/Focus costs and yield seed when work is
reserved. Persisting its state independently from the tool, character Lifeskill state or output
would allow a crash to duplicate rewards, consume value without a reward or leave a reservation
that cannot be recovered. Restart recovery also needs to discover non-available nodes without
depending on chunk load.

## Decision

Forward migration V0014 adds versioned `resource_node_state` and `character_lifeskill_state`
documents. `JdbcResourceNodeStateRepository` commits one journaled compound operation with these
rules:

- reserve, cancel, harvest and recovery compare the exact expected node version and replace its
  content-versioned JSON document in the same database transaction;
- actor operations compare and update the exact durable tool instance; harvest additionally
  compares and updates the actor's Lifeskill/Focus document and creates all frozen yield lots in
  Pending Rewards;
- recovery is system-authored, can release the durable tool reservation and cannot create yield or
  progression value;
- a successful transaction records one `RESOURCE_NODE` audit row and one committed transaction
  journal; any SQL, validation or injected runtime failure rolls the entire compound operation back;
- an exact committed operation replay returns the already-written node, character, tool and output
  records without mutating them, while changed-input transaction reuse fails closed; and
- `findRecoverable` returns every persisted phase other than `AVAILABLE`, allowing startup to
  reconcile reservations and wall-clock depletion without loaded chunks.

## Consequences

Node state, tool state, Focus/rank evidence and output ownership now share one durable value
boundary. Crash-point integration tests prove rollback and exactly-once retry after partial node,
tool and output writes. A live Paper adapter still needs to encode the pure kernel documents and
drive reservation/recovery through this repository.
