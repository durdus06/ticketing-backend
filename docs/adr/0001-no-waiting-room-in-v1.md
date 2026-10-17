# ADR 0001: No waiting room in v1, rate limiting only

Status: Accepted (deferred)

## Context

A popular sale can bring a large burst of users (see the assumptions in requirements section 4.2). Rejected users tend to retry, which adds load. Seat correctness does not depend on traffic control, because NFR-1 and NFR-2 are enforced in the database.

## Decision

v1 protects the system with per-client rate limiting (NFR-14) and the active hold limit per customer (FR-27). No waiting room or queue is built. Over-limit requests receive HTTP 429 with a `Retry-After` header.

## Alternatives

- **Waiting room / queue:** best fairness and load smoothing, but needs an extra component, state management and UI. Too much complexity for v1.
- **Admission control** (cap on concurrent reservation requests, fast 429 beyond it): a middle ground. Candidate follow-up if load tests show overload.

## Consequences

- Simple to build and operate.
- Under extreme bursts, users may see 429 responses and retry manually; fairness is not guaranteed.
- Revisit after load tests against the targets in section 4.2. The modular monolith allows adding admission control or a queue in front of the reservation module later.
