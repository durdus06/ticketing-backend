# Security

Status: reviewed against the [OWASP Top 10:2025](https://owasp.org/Top10/2025/). Update this file whenever the design changes.

Legend for the status column: `Planned` (designed, not implemented), `In progress`, `Done` (implemented and verified by the evidence), `N/A` (not applicable, reason given).

## 1. Threat Overview

### Assets
- User accounts and credentials
- Tickets and reservations (who owns which seat)
- Payment status and refund state
- Admin and organizer functions (event management, bulk email with admin approval, event cancellation with refunds, organizer account creation)
- The audit log of admin and organizer actions on events
- Refund configuration (refund rate and cutoff values): changing it changes money flows
- Availability of the system during a sale

### Actors
- Anonymous visitors (read-only access)
- Malicious customers (try to access other users' tickets, abuse limits)
- Bots (seat hoarding, scraping, credential stuffing)
- Malicious organizers (try to manage another organizer's events)
- Compromised or careless admin and organizer accounts
- Attackers targeting dependencies or the build pipeline

### Trust boundaries
- Browser / client to API: everything coming from the client is untrusted
- API to database: only parameterized access
- API to external services (payment provider, email provider): responses are validated, failures are expected

## 2. OWASP Top 10:2025 Review

| ID | Category | Risk in this project | Mitigation | Evidence | Status |
|---|---|---|---|---|---|
| A01 | Broken Access Control | A customer reads or cancels another customer's ticket by changing an ID. A customer calls an admin endpoint. An organizer reads, edits or cancels another organizer's event. | Server-side authorization on every endpoint. Ownership check in every ticket and reservation query. Event operations by an organizer check that the organizer owns the event. Admin endpoints (organizer creation, email approval, configuration) require the ADMIN role. Self-registration can only create CUSTOMER accounts. Deny by default. | `TicketAccessIT`, `AdminEndpointsSecurityIT`, `OrganizerEventOwnershipIT`, `RegistrationRoleIT` | Planned |
| A02 | Security Misconfiguration | Exposed actuator endpoints, default credentials, verbose error pages, permissive CORS. | Only `health` and required metrics exposed, and protected where needed. No default credentials. Explicit CORS allow-list. Separate config per environment. Generic error responses. | `ActuatorExposureIT`, config review checklist | Planned |
| A03 | Software Supply Chain Failures | A vulnerable or malicious dependency, a compromised build or base image. | Dependabot enabled. Dependency vulnerability scan in CI. Pinned versions via the Spring Boot BOM. Minimal, official base images. Maven wrapper checksum kept in the repository. | CI workflow, Dependabot config | Planned |
| A04 | Cryptographic Failures | Weak password storage, tokens signed with weak keys, secrets in the repository, plain HTTP. | Passwords hashed with a modern adaptive algorithm (for example bcrypt or Argon2) through Spring Security. Strong signing keys loaded from the environment. TLS everywhere outside local development. No card data stored. | `PasswordHashingTest`, secret scanning in CI | Planned |
| A05 | Injection | SQL injection through filters and search, injection through logs or emails. | Only JPA and parameterized queries; no SQL built by string concatenation. Input validation with Bean Validation. Output encoding where user data is rendered. Organizer emails use predefined templates with validated and escaped placeholders (FR-28). | `EventSearchInjectionIT`, `EmailTemplatePlaceholderIT`, code review rule | Planned |
| A06 | Insecure Design | Business logic abuse: seat hoarding, reservation races, replayed payments. | Threat modeling in this document. Reservation limits per customer (`reservation.max-seats` per reservation, `reservation.max-active-seats` across all events, FR-27). Rate limiting on reservation endpoints (NFR-14). Time-boxed holds. Idempotent payment confirmation and refunds. Late payment after hold expiry is refunded immediately (FR-24). Refund and cancellation rules enforced in the domain layer with server-side time. Concurrency covered by tests (see NFR-1 and FR-16 in requirements). | `ConcurrentReservationIT`, `ActiveHoldLimitIT`, `ReservationRateLimitIT`, `PaymentIdempotencyIT`, `LatePaymentRefundIT`, `RefundWindowIT` | Planned |
| A07 | Authentication Failures | Credential stuffing, brute-force login, weak passwords, session or token theft. | Password policy. Rate limiting and lockout on login. Short-lived access tokens. Secure token storage on the client. Generic login error messages. Mechanism chosen in an ADR (OAuth2/JWT or sessions). | `LoginRateLimitIT`, `TokenExpiryTest` | Planned |
| A08 | Software or Data Integrity Failures | Tampered tokens, unsigned artifacts, unsafe deserialization, forged payment callbacks. | Signature verification on every token. Payment callbacks verified. No Java native deserialization of untrusted data. Artifacts built only in CI. | `TokenTamperingTest`, `PaymentCallbackVerificationIT` | Planned |
| A09 | Security Logging and Alerting Failures | An attack goes unnoticed: repeated failed logins, access denied bursts, admin or organizer actions on events without trace, an organizer mass-mailing without approval. | Structured logs with a correlation ID. Audit log for admin and organizer actions on events (mandatory reason for admins, FR-20). Organizer emails are sent only after admin approval (FR-7) and for authentication events. No secrets or personal data in logs. Alerts on abnormal rates (failed logins, 403/401 spikes). | `AuditLogIT`, `AdminEventOverrideAuditIT`, `OrganizerEventAuditIT`, `OrganizerEmailApprovalIT`, alert rules | Planned |
| A10 | Mishandling of Exceptional Conditions | Errors leak internals, a failed payment leaves a seat locked, an email outage breaks a purchase, errors are swallowed silently. | Central exception handling returning safe error bodies. Fail closed on authorization errors. Expired holds released by a scheduled job. External service failures isolated from the purchase flow (see NFR-5). | `GlobalExceptionHandlerTest`, `HoldExpiryIT`, `EmailFailureDoesNotBreakPurchaseIT` | Planned |

## 3. Authentication and Authorization Design

To be filled in after the decision is made. Open points:
- Mechanism: OAuth2 with JWT access tokens, or server-side sessions. Recorded as an ADR (`docs/adr/`).
- Roles: `GUEST` (unauthenticated), `CUSTOMER`, `ORGANIZER`, `ADMIN`. Each account has exactly one role; a person who needs two roles uses two accounts.
- Event authorization rules: an `ORGANIZER` can act only on events they own. An `ADMIN` can act on any event. Admin and organizer actions on events are audited (FR-19, FR-20).
- Organizer accounts are created only by an admin (FR-23). Public registration creates `CUSTOMER` accounts only.
- Organizer emails to ticket holders require admin approval before sending (FR-7).
- Token lifetime and refresh strategy.
- Where authorization is enforced: at the HTTP layer (route rules) and at the method/service layer for ownership checks.

## 4. Secrets and Configuration

- No secret is ever committed to the repository. Secret scanning runs in CI.
- Local development uses throwaway credentials from `compose.yaml`.
- Other environments receive secrets through environment variables or a secret manager (decided at the deployment stage).
- Different keys and credentials per environment.

## 5. Dependency and Supply Chain

- Dependabot opens update pull requests for Maven dependencies, GitHub Actions and Docker images.
- CI fails on known high-severity vulnerabilities in dependencies.
- Container images are built in CI from a minimal official base image and scanned before deployment.

## 6. Data Protection

- Personal data stored: email, password hash, ticket history. Collected only when needed.
- Passwords and tokens are never logged.
- Audit log entries are kept for `audit.retention-years` (default 5) and hold user IDs rather than personal data where possible. A user's data export and deletion policy is an open question (see requirements).

## 7. Known Gaps and Accepted Risks

| Gap | Reason | Plan |
|---|---|---|
| Authentication mechanism not yet chosen | Security stage has not started | Decide with an ADR before implementing login |
| No bot protection beyond rate limiting and the active hold limit | Waiting room deferred (ADR 0001) | Revisit with load test results and a CAPTCHA decision |
| No external penetration test | Personal learning project | Run an automated scan (for example OWASP ZAP) before release |

## 8. Review Log

| Date | Change | Reviewer |
|---|---|---|
| 2026-10-17 | Initial version created against OWASP Top 10:2025 | Author |
| 2026-10-17 | Added the Organizer role, event ownership checks and the admin audit log | Author |
| 2026-10-17 | Updated for answered open questions: admin-created organizers, single role per account, organizer audit, email approval, refund rules and late-payment refund | Author |
| 2026-10-17 | Added active hold limit, reservation rate limiting, audit retention and waiting room deferral | Author |
| 2026-10-17 | Organizer emails restricted to predefined templates with admin approval | Author |
