# ADR 0045: Durable local carried-wallet authority

- Status: Accepted
- Date: 2026-08-01
- Owners: Persistence and Economy

## Context

The Death Pouch saga requires idempotent debit and credit acknowledgement, but the configured
`WalletProvider` integration is currently only a health capability and exposes no transaction API.
Using process memory or guessing an external provider balance would make crash recovery unsafe and
would prevent meaningful local in-game acceptance.

## Decision

Forward migration V0013 adds a local PostgreSQL carried-wallet account and immutable operation
ledger. `CarriedWalletService` is the authoritative local transaction boundary for carried currency
until an external wallet adapter implements the same semantics. Every positive credit or debit uses
its operation UUID as the shared transaction UUID, commits account state, immutable operation,
transaction journal and audit row atomically, and returns exact retry acknowledgement.

The account begins virtually at zero. Each character is serialized by a transaction-scoped
PostgreSQL advisory lock before its account row is read, including concurrent first credits where no
row exists yet. Debits cannot produce a negative balance. The bank and Market Balance remain separate
future authorities and are not represented by this table.

## Consequences

- Death Pouch debit and recovery can use durable stable operation UUIDs during local acceptance;
- restart replay cannot apply the same debit or credit twice;
- concurrent first operations cannot overwrite one another;
- development funding must use the same journaled credit operation rather than direct SQL or memory;
- a future external wallet adapter must preserve these idempotency and acknowledgement guarantees.

## Failure and recovery

Database failure rolls back the journal, balance, operation and audit together. Insufficient funds,
overflow, operation drift and changed transaction reuse fail without changing the account. A
committed retry validates the immutable operation before returning the current durable account.

## Migration impact

Forward-only V0013 adds `carried_wallet_account`, `carried_wallet_operation` and the operation-kind
enum. Existing characters require no backfill because a missing account is defined as zero balance.
