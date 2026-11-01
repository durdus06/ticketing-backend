# ADR 0007: Password hashing with spring-security-crypto

Status: Accepted

## Context

Registration needs passwords stored only as salted hashes with a modern algorithm (NFR-7). The full Spring Security starter would also lock down every endpoint by default and requires the authentication design (ADR 0006), which is not decided yet.

## Decision

Add only the `spring-security-crypto` dependency (version managed by the Spring Boot BOM) and use `PasswordEncoderFactories.createDelegatingPasswordEncoder()`. It uses bcrypt by default and stores hashes with an algorithm prefix such as `{bcrypt}`, so the algorithm or cost can be changed later while old hashes keep working. Password policy: at least 10 characters and at most 72 bytes in UTF-8, because bcrypt cannot handle more than 72 bytes.

## Alternatives

- **Full `spring-boot-starter-security` now:** needed eventually, but forces security configuration before the authentication mechanism is chosen.
- **Argon2:** stronger against GPU attacks, but needs an additional library (Bouncy Castle). Can be adopted later through the delegating encoder.
- **Own implementation:** never roll your own cryptography.

## Consequences

- Hashes can be upgraded gradually. On a successful login, a hash with an outdated algorithm or cost can be re-encoded.
- The 72-byte limit is a known bcrypt constraint. If longer passwords or passphrases are wanted, switch to Argon2.
- Single-use tokens (invitation, password reset, account restore) are not passwords: they have 256 bits of randomness and are stored as SHA-256 hashes.
