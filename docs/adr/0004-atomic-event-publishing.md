# ADR 0004: Atomic event publishing

Status: Accepted

## Context

Publishing an event creates one `event_seat` row per seat (for example 50,000). If this is interrupted halfway, the seat map and the sale would be wrong and unfair.

## Decision

Inventory creation runs in a single database transaction using a set-based `INSERT ... SELECT`, so it either completes fully or not at all. An event has a status (`DRAFT`, `ON_SALE`, ...) and is sellable only in `ON_SALE`. Retrying a failed publish is safe because `(event_id, seat_id)` is unique.

## Alternatives

- **Partial sale with background completion**: wrong seat map, unfair, extra job and failure cases.
- **Chunked, resumable job with a `PUBLISHING` status**: needed only for much larger inventories; can be adopted later.

## Consequences

- Simple and safe for the expected sizes. Revisit if an event grows to the point where one transaction is too long.
- The transaction time for large events must be measured in a test.
