# Requirements

Status: Draft v1

## 1. Purpose and Scope

The platform sells tickets for seated events such as concerts, theatre plays and cinema screenings. Customers browse events and seat availability, reserve seats and pay for them. Organizers manage their own events. Admins run the platform and can step in on any event.

In scope for v1:
- Event and seat management by organizers (own events) and admins (any event, audited)
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
| **Organizer** | Owns events. Creates, updates and cancels only their own events and requests emails to the ticket holders of those events (sent after admin approval). Account is created by an admin. |
| **Admin** | Site administrator. Creates organizer accounts, approves organizer emails and can manage or cancel any event, for example in case of abuse or customer harm. Every admin and organizer action on an event is written to an audit log. |

Each account has exactly one role. A person who needs to be both a customer and an organizer uses two separate accounts.

An organizer owns an event from the moment it is created. Ownership is checked on every organizer operation (see NFR-8).

All event times are stored as UTC instants and interpreted and displayed in a single timezone, `Europe/Istanbul` (as Türkiye currently uses one time for the whole country).

## 3. Functional Requirements

### 3.1 Accounts
- FR-1: A guest can register as a customer with an email and a password. Self-registration never creates an organizer or admin account.
- FR-2: A registered user can log in and log out.
- FR-23: An admin can create organizer accounts. There is no self-registration for organizers. Organizer creation is recorded in the audit log (FR-20).

### 3.2 Event management (Organizer and Admin)
- FR-3: An organizer can create an event with name, type, venue, start time and a seat layout with a price per seat category.
- FR-4: An organizer can update their own event (time, venue, description, prices of unsold seats). A change after tickets were sold has refund consequences for ticket holders (see FR-22).
- FR-5: An organizer can cancel their own event. Cancelling an event with sold tickets triggers a 100% refund of every sold ticket. Payment provider fees on these refunds are borne by the organizer. Events with sold tickets are never hard-deleted.
- FR-6: When an event is updated or cancelled, every customer holding a ticket for it receives an email describing the change.
- FR-7: An organizer can request an email to all ticket holders of their own event by choosing one of the predefined email templates; free-text emails are not possible. The request is sent only after an admin approves it. A rejected or unapproved email is never sent. Approval and rejection are audited (FR-20).
- FR-28: Email templates are predefined and managed by admins. A template has a fixed text with named placeholders (for example event name, date, venue). The organizer can only choose a template and fill in its placeholders; the placeholder values are validated and escaped, and the admin sees the final rendered email when approving.
- FR-19: An admin can perform every event operation of FR-3 to FR-7 on any event, regardless of ownership.
- FR-20: Every admin and organizer action on an event (create, update, cancel, email request, email approval or rejection) is recorded in an audit log with the acting user and their role, the target event, the action, a timestamp and the changed values (before and after). A reason is mandatory for admin actions and optional for organizer actions. Audit entries cannot be modified or deleted through the application. Entries are kept for `audit.retention-years` (default 5). Removal or archiving after that period is done by a separately managed job, not by the application.
- FR-21: An organizer cannot access or modify events owned by another organizer.

### 3.3 Browsing (Guest and Customer)
- FR-8: Anyone can list upcoming events and filter them by type and date.
- FR-9: Anyone can view the seat map of an event with the status of each seat: available, reserved or sold.

### 3.4 Reservation and purchase (Customer)
- FR-10: A customer must be logged in to reserve. A guest who tries is redirected to login and returned to the same event afterwards.
- FR-11: A customer can reserve up to N seats (`reservation.max-seats`, default 6) for one event in a single reservation.
- FR-27: A customer can have at most `reservation.max-active-seats` (default 6) seats on hold at the same time, counted across all events. A request that would exceed the limit is rejected. The check must hold under concurrent requests from the same customer.
- FR-12: A reserved seat is held exclusively for that customer for 10 minutes. No other user can reserve or buy it during that time.
- FR-13: If the hold expires without a successful payment, the seats are released automatically and become available again.
- FR-14: If payment fails or is abandoned, the seats are released no later than the end of the hold period.
- FR-15: After successful payment the seats become sold and the customer receives a confirmation email with the ticket.
- FR-24: If a payment succeeds after the hold has expired and a seat is no longer available to that customer (for example taken by another customer), no ticket is issued. The customer is told that an unexpected error occurred, and the full amount is refunded immediately. The refund is recorded and must be idempotent (see FR-16).
- FR-16: A payment request submitted more than once (retry, double click, network replay) must result in at most one charge and one ticket.
- FR-17: A customer can view their tickets and reservations.
- FR-18: A customer can cancel their own ticket and receives a partial refund of the ticket price. The refund rate is `refund.customer-cancel-rate` (default 80%). The rate in effect at the moment of cancellation applies; changing it never affects refunds already made. The seat becomes available again.
- FR-25: A customer can cancel a ticket only until `refund.customer-cancel-cutoff-hours` (default 48) before the event start. After that point cancellation is not possible.
- FR-22: If the start time or the venue of an event changes after tickets were sold, each ticket holder can request a 100% refund instead of keeping the ticket. Holders are notified by email (see FR-6). The request window depends on when the organizer made the change (see FR-26).
- FR-26: Organizer change and refund windows, both configurable:
  - An organizer can change the event time or venue until `event.organizer-change-cutoff-hours` (default 72) before the event start.
  - If the change is made before that cutoff, ticket holders can request the 100% refund until `refund.time-change-request-cutoff-hours` (default 24) before the event start.
  - If the organizer changes the time or venue inside the last 72 hours, ticket holders can request the 100% refund without that limit (until the event starts).

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
- NFR-14: Reservation and login endpoints are rate limited per client (IP and, when authenticated, per user). Requests over the limit get HTTP 429 with a `Retry-After` header. A waiting room or queue is deliberately not part of v1 (see docs/adr/0001-no-waiting-room-in-v1.md).
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

## 6. Configurable Parameters

These values are read from configuration and can be changed without a code change. A change applies only to operations performed after it (for example, a new refund rate never alters refunds already made).

| Key | Default | Used by |
|---|---|---|
| `reservation.max-seats` | 6 | FR-11 |
| `reservation.max-active-seats` | 6 | FR-27 |
| `audit.retention-years` | 5 | FR-20 |
| `refund.customer-cancel-rate` | 0.80 | FR-18 |
| `refund.customer-cancel-cutoff-hours` | 48 | FR-25 |
| `event.organizer-change-cutoff-hours` | 72 | FR-26 |
| `refund.time-change-request-cutoff-hours` | 24 | FR-22, FR-26 |

## 7. Decisions on Former Open Questions

- Organizer accounts are created by an admin (FR-23). There is no self-registration or approval flow.
- Customer cancellation is possible only until 48 hours before the event, with no cancellation afterwards (FR-18, FR-25).
- Time and venue change refund windows are identical and defined in FR-26.
- One account has one role. Customer and organizer accounts are separate (section 2).
- A payment that succeeds after the hold expired and the seat was lost is refunded immediately (FR-24).
- A single reservation holds up to 6 seats, configurable (FR-11). A customer can have at most 6 seats on hold at once across all events, configurable (FR-27).
- v1 uses rate limiting only; a waiting room is deferred (NFR-14, ADR 0001).
- Audit log entries are kept 5 years by default, configurable; cleanup is a separate managed job (FR-20).
- All events use the `Europe/Istanbul` timezone (section 2).
- Payment provider fees on refunds are borne by the organizer. Ignored in v1 with the fake payment provider.
- Organizer actions on events are audited as well as admin actions (FR-20).
- Organizer emails use predefined templates only and need admin approval (FR-7, FR-28).

## 8. Open Questions

1. Which email templates exist in v1, and which placeholders may an organizer fill in? What does the approval flow need beyond approve or reject (rejection reason, notification to the organizer)?
