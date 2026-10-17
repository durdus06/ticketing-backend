# Security

Status: reviewed against the [OWASP Top 10:2025](https://owasp.org/Top10/2025/). Update this file whenever the design changes.

Legend for the status column: `Planned` (designed, not implemented), `In progress`, `Done` (implemented and verified by the evidence), `N/A` (not applicable, reason given).

## 1. Threat Overview

### Assets
- User accounts and credentials
- Tickets and reservations (who owns which seat)
- Payment status and refund state
- Admin functions (event management, bulk email)
- Availability of the system during a sale

### Actors
- Anonymous visitors (read-only access)
- Malicious customers (try to access other users' tickets, abuse limits)
- Bots (seat hoarding, scraping, credential stuffing)
- Compromised or careless admin accounts
- Attackers targeting dependencies or the build pipeline

### Trust boundaries
- Browser / client to API: everything coming from the client is untrusted
- API to database: only parameterized access
- API to external services (payment provider, email provider): responses are validated, failures are expected

## 2. OWASP Top 10:2025 Review

| ID | Category | Risk in this project | Mitigation | Evidence | Status |
|---|---|---|---|---|---|
| A01 | Broken Access Control | A customer reads or cancels another customer's ticket by changing an ID. A customer calls an admin endpoint. | Server-side authorization on every endpoint. Ownership check in every ticket and reservation query. Admin endpoints require the ADMIN role. Deny by default. | `TicketAccessIT`, `AdminEndpointsSecurityIT` | Planned |
| A02 | Security Misconfiguration | Exposed actuator endpoints, default credentials, verbose error pages, permissive CORS. | Only `health` and required metrics exposed, and protected where needed. No default credentials. Explicit CORS allow-list. Separate config per environment. Generic error responses. | `ActuatorExposureIT`, config review checklist | Planned |
| A03 | Software Supply Chain Failures | A vulnerable or malicious dependency, a compromised build or base image. | Dependabot enabled. Dependency vulnerability scan in CI. Pinned versions via the Spring Boot BOM. Minimal, official base images. Maven wrapper checksum kept in the repository. | CI workflow, Dependabot config | Planned |
| A04 | Cryptographic Failures | Weak password storage, tokens signed with weak keys, secrets in the repository, plain HTTP. | Passwords hashed with a modern adaptive algorithm (for example bcrypt or Argon2) through Spring Security. Strong signing keys loaded from the environment. TLS everywhere outside local development. No card data stored. | `PasswordHashingTest`, secret scanning in CI | Planned |
| A05 | Injection | SQL injection through filters and search, injection through logs or emails. | Only JPA and parameterized queries; no SQL built by string concatenation. Input validation with Bean Validation. Output encoding where user data is rendered. | `EventSearchInjectionIT`, code review rule | Planned |
| A06 | Insecure Design | Business logic abuse: seat hoarding, reservation races, replayed payments. | Threat modeling in this document. Reservation limits per customer. Time-boxed holds. Idempotent payment confirmation. Concurrency covered by tests (see NFR-1 and FR-16 in requirements). | `ConcurrentReservationIT`, `PaymentIdempotencyIT` | Planned |
| A07 | Authentication Failures | Credential stuffing, brute-force login, weak passwords, session or token theft. | Password policy. Rate limiting and lockout on login. Short-lived access tokens. Secure token storage on the client. Generic login error messages. Mechanism chosen in an ADR (OAuth2/JWT or sessions). | `LoginRateLimitIT`, `TokenExpiryTest` | Planned |
| A08 | Software or Data Integrity Failures | Tampered tokens, unsigned artifacts, unsafe deserialization, forged payment callbacks. | Signature verification on every token. Payment callbacks verified. No Java native deserialization of untrusted data. Artifacts built only in CI. | `TokenTamperingTest`, `PaymentCallbackVerificationIT` | Planned |
| A09 | Security Logging and Alerting Failures | An attack goes unnoticed: repeated failed logins, access denied bursts, admin actions without trace. | Structured logs with a correlation ID. Audit log for admin actions and authentication events. No secrets or personal data in logs. Alerts on abnormal rates (failed logins, 403/401 spikes). | `AuditLogIT`, alert rules | Planned |
| A10 | Mishandling of Exceptional Conditions | Errors leak internals, a failed payment leaves a seat locked, an email outage breaks a purchase, errors are swallowed silently. | Central exception handling returning safe error bodies. Fail closed on authorization errors. Expired holds released by a scheduled job. External service failures isolated from the purchase flow (see NFR-5). | `GlobalExceptionHandlerTest`, `HoldExpiryIT`, `EmailFailureDoesNotBreakPurchaseIT` | Planned |

## 3. Authentication and Authorization Design

To be filled in after the decision is made. Open points:
- Mechanism: OAuth2 with JWT access tokens, or server-side sessions. Recorded as an ADR (`docs/adr/`).
- Roles: `GUEST` (unauthenticated), `CUSTOMER`, `ADMIN`.
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
- A user's data export and deletion policy is an open question (see requirements).

## 7. Known Gaps and Accepted Risks

| Gap | Reason | Plan |
|---|---|---|
| Authentication mechanism not yet chosen | Security stage has not started | Decide with an ADR before implementing login |
| No bot protection beyond rate limiting | Out of scope for v1 | Revisit with waiting room / CAPTCHA decision |
| No external penetration test | Personal learning project | Run an automated scan (for example OWASP ZAP) before release |

## 8. Review Log

| Date | Change | Reviewer |
|---|---|---|
| 2026-10-17 | Initial version created against OWASP Top 10:2025 | Author |