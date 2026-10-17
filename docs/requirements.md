# Requirements

Status: Draft v1

## 1. Purpose and Scope

The platform sells tickets for seated events such as concerts, theatre plays and cinema screenings. Customers browse events and seat availability, reserve seats and pay for them. Admins manage events.

In scope for v1:
- Event and seat management by admins
- Public browsing of events and seat availability
- Seat reservation with a time limit, payment and ticket issuing
- Cancellation and refund
- Email notifications about purchases and event changes

Out of scope for v1: see section 5.

## 2. User Roles

| Role | Description |
|---|---|
| **Guest** | Not logged in. Can browse events and see seat availability. Cannot reserve or buy. |
| **Customer** | Registered and logged in. Can reserve seats, pay, view and cancel own tickets. |
| **Admin** | Manages events. Can notify ticket holders of an event. |

An "Organizer" role (an event owner who manages only their own events) is intentionally left out of v1. See Open Questions.

## 3. Functional Requirements

### 3.1 Accounts
- FR-1: A guest can register with an email and a password.
- FR-2: A registered user can log in and log out.

### 3.2 Event management (Admin)
- FR-3: An admin can create an event with name, type, venue, start time and a seat layout with a price per seat category.
- FR-4: An admin can update an event (time, venue, description, prices of unsold seats).
- FR-5: An admin can cancel an event. Cancelling an event with sold tickets triggers refunds. Events with sold tickets are never hard-deleted.
- FR-6: When an event is updated or cancelled, every customer holding a ticket for it receives an email describing the change.
- FR-7: An admin can send a custom email to all ticket holders of a specific event.

### 3.3 Browsing (Guest and Customer)
- FR-8: Anyone can list upcoming events and filter them by type and date.
- FR-9: Anyone can view the seat map of an event with the status of each seat: available, reserved or sold.

### 3.4 Reservation and purchase (Customer)
- FR-10: A customer must be logged in to reserve. A guest who tries is redirected to login and returned to the same event afterwards.
- FR-11: A customer can reserve up to N seats (N is configurable, default 6) for one event in a single reservation.
- FR-12: A reserved seat is held exclusively for that customer for 10 minutes. No other user can reserve or buy it during that time.
- FR-13: If the hold expires without a successful payment, the seats are released automatically and become available again.
- FR-14: If payment fails or is abandoned, the seats are released no later than the end of the hold period.
- FR-15: After successful payment the seats become sold and the customer receives a confirmation email with the ticket.
- FR-16: A payment request submitted more than once (retry, double click, network replay) must result in at most one charge and one ticket.
- FR-17: A customer can view their tickets and reservations.
- FR-18: A customer can cancel a ticket according to the refund policy. The seat becomes available again.

## 4. Non-Functional Requirements

### 4.1 Consistency and availability (key architectural trade-off)
- NFR-1 (hard invariant): A seat must never be sold or held for two customers at the same time. This is not a probability to be minimized; it must hold under every load and failure scenario.
- NFR-2: Under overload or partial failure, the system prefers rejecting or delaying a reservation request over risking a double sale. Seat allocation therefore favors consistency over availability.
- NFR-3: Browsing (event list, seat map) favors availability. It may show slightly stale seat status (up to 2 seconds), because the reservation step is the source of truth.

### 4.2 Performance (initial targets, to be verified by load tests)
Assumptions: an event with 50,000 seats goes on sale and 20,000 users arrive within the first minute. Each user opens the seat map a few times and attempts one or two reservations.

| Operation | Target |
|---|---|
| Event list and seat map reads | 2,000 requests/s, p95 < 200 ms |
| Reservation attempt | 500 requests/s, p95 < 500 ms |
| Payment confirmation | p95 < 1 s (excluding the external payment provider) |

These numbers are assumptions chosen for design purposes. They will be refined once real measurements exist.

### 4.3 Reliability
- NFR-4: Expired holds are released within 30 seconds of expiry, even if the application restarts.
- NFR-5: Emails are delivered at least once, and a failure of the email provider must never block or roll back a purchase.
- NFR-6: Availability target for browsing: 99.9% monthly.

### 4.4 Security
- NFR-7: Passwords are stored only as salted hashes using a modern algorithm.
- NFR-8: Authentication and authorization are enforced on the server for every protected operation. Admin operations require the Admin role.
- NFR-9: The application is reviewed against the OWASP Top 10 before release.
  Each category is either mitigated or documented as not applicable in docs/security.md.
- NFR-10: No card data is stored by the platform.

### 4.5 Operability
- NFR-11: The system exposes health checks, metrics and structured logs with a correlation ID per request.
- NFR-12: Database schema changes are versioned and applied automatically at deployment.
- NFR-13: Every feature is covered by automated tests, including integration tests against a real PostgreSQL instance.

## 5. Out of Scope (v1)

- Real payment provider integration (a fake payment provider is used)
- General admission events without assigned seats
- Dynamic pricing, discount codes, resale or ticket transfer
- Mobile application
- QR code scanning at the venue entrance
- Multi-language and multi-currency support
- Email template design (simple text templates are enough)

## 6. Open Questions

1. Who can cancel an event, and what happens to sold tickets: full refund always, or only if the organizer cancels?
2. What is the refund policy for customer cancellations (until when, how much)?
3. If an admin changes the event time, can customers get a refund, or only be notified?
4. Do we need an Organizer role in a later version, with ownership of events?
5. What happens when a payment succeeds after the hold has already expired and the seat was taken by another customer?
6. How many seats can one customer hold across all events at the same time, to prevent hoarding?
7. Should the system use a waiting room or queue for very popular sales, or only rate limiting?
8. Which timezone rules apply to events in different cities?