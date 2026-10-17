# ADR 0003: Seat template in `event`, seat inventory in `reservation`

Status: Accepted

## Context

A seat has two aspects: its fixed layout (section, row, number, category, price) defined by the organizer, and its per-event availability (available, reserved, sold, holder, hold expiry), which changes constantly during a sale. NFR-1 requires that a seat is never sold or held twice, so the locking point must be small and well defined.

## Decision

- The `event` module owns the seat template (layout, category, price).
- The `reservation` module owns the inventory table `event_seat`: one row per event and seat, with status, holder, hold expiry, version and a copy of the price. `(event_id, seat_id)` is unique.
- `reservation` references events and seats by ID only, never by entity.
- Price changes by an organizer are propagated to `AVAILABLE` rows only. Reserved and sold rows keep their price.

## Alternatives

- **Everything in `event`**: locking and status transitions would live in the wrong module.
- **Everything in `reservation`**: `event` would depend on `reservation` to define layout and prices.

## Consequences

- Row locks and concurrency control apply only to `event_seat`; organizer edits of the template do not block sales.
- The price a customer reserved at is stable.
- Inventory must be created from the template when an event is published (ADR 0004).
- The exact concurrency mechanism (pessimistic lock, optimistic version, constraint) is a separate ADR.
