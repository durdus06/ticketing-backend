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

## Open points

- Exact access token lifetime and refresh token lifetime.
- Refresh token transport and storage on the client (to be recorded in the frontend ADR).
- Whether to add a per-request account state check if the revocation gap proves too long.
