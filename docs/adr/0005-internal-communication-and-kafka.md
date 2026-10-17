# ADR 0005: Module communication and use of Kafka

Status: Accepted

## Context

Modules need to talk to each other (for example price changes from `event` to `reservation`). Kafka would add an outbox, eventual consistency, idempotent consumers and operational cost. We also want to learn Kafka where it solves a real problem.

## Decision

- Synchronous in-process calls through a module's public service or interface, or in-process application events, are used between modules. Operations that must be consistent, such as price changes on available seats, run in the same transaction.
- Kafka is introduced for email notifications (NFR-5): decoupling from the purchase flow, at-least-once delivery and failure isolation. The transactional outbox pattern is used when publishing.
- The Kafka dependency is added when the email feature is built.

## Alternatives

- **Kafka for every cross-module interaction**: adds dual-write and stale-price risks with no benefit inside one process.
- **No Kafka at all**: simpler, but email delivery then needs another mechanism for async handling and retries.

## Consequences

- Core consistency stays in the database.
- Email consumers must be idempotent because delivery is at least once.
