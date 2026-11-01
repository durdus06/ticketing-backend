# ADR 0006: Authentication mechanism

Status: Accepted

## Context

The API needs to know who is calling and enforce roles (NFR-8). Accounts can be suspended by an admin (FR-35) or deleted by their owner (FR-31). The application is a single deployable modular monolith with PostgreSQL as its only required store. Registration (FR-1) and password hashing (ADR 0007) are already in place; login is not.

The web client is a separate project (`ticketing-frontend`) served from a different origin than the API, so the mechanism must work cross-origin without relying on third-party cookies for every request.

## Decision

Short-lived stateless JWT access tokens plus long-lived refresh tokens.

- **Access token:** signed JWT sent in the `Authorization: Bearer` header. Lifetime is short (minutes). It carries the user id and role.
- **Refresh token:** opaque random value, only its hash is stored in PostgreSQL. It is rotated on every use, and reuse of an already-used token revokes the whole token family (theft detection).
- **Account state:** the refresh endpoint checks that the user still `canLogin()`. Suspension and deletion therefore stop new access tokens immediately and existing ones when they expire.
- **Client storage:** the access token is kept in memory only. Where the refresh token lives (HttpOnly cookie or response body) is decided together with the frontend ADR, since it depends on the deployment origins.

## Alternatives

- **Server-side sessions (Spring Session JDBC) with a cookie:** immediate revocation and the simplest model, but cross-origin use needs `SameSite=None`, CORS with credentials and CSRF handling. Rejected because the frontend is a separate project and bearer tokens are preferred.
- **Opaque access tokens stored in the database:** immediate revocation like sessions, but one database read per request and a custom mechanism to maintain. A valid option if the JWT revocation gap below becomes unacceptable.
- **External identity provider (OAuth2/OIDC):** best practice for real products, but a separate system is out of proportion for v1.

## Consequences

- Suspension and deletion take effect when the current access token expires, not on the next request. This is a deliberate relaxation of the "immediate" expectation in FR-35 and FR-31. Mitigations if it is not acceptable: a very short access token lifetime, a per-request `canLogin()` check, or a deny-list. The last two give up part of the stateless benefit.
- Refresh token rotation, family revocation and cleanup of expired tokens must be implemented and tested (a `refresh_tokens` table with a Liquibase migration).
- Signing key management is required: key source, rotation and the algorithm (asymmetric keys keep verification possible without sharing the signing secret if services are split later).
- The API needs CORS configuration for the frontend origins.
- Rate limiting and lockout on login (A07) are still required and are implemented separately.

## Decisions made at implementation

- **Refresh token transport:** an `HttpOnly`, `Secure`, `SameSite=Strict` cookie limited to `Path=/api/auth`. Scripts cannot read it, and it is never sent on cross-site requests, which is also the CSRF protection for `/refresh` and `/logout`. The frontend is served same-origin (Vite proxy in development, a reverse proxy for `/api` in production), so CORS is off unless `ticketing.auth.cors-allowed-origins` lists origins.
- **Signing:** HS256 with a secret of at least 32 bytes from `TICKETING_AUTH_JWT_SECRET`. The application refuses to start without it. This is simpler than asymmetric keys; switching to RS256/EdDSA is needed before verification is split into another service.
- **Lifetimes:** access token 10 minutes, refresh token 7 days (both configurable).
- **Claims:** `sub` (user id), `role`, `iss`, `iat`, `exp`. No personal data.
- **Errors:** all login failures (unknown email, wrong password, suspended, deleted) return the same `401 INVALID_CREDENTIALS`. A dummy password check runs for unknown emails to even out response times.

## Open points

- Rate limiting and lockout on login (NFR-14, A07) are not implemented yet.
- A scheduled job must call `RefreshTokenService.deleteExpired()`; none exists yet.
- Whether to add a per-request account state check if the revocation gap proves too long.
