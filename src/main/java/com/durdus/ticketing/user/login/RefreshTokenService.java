package com.durdus.ticketing.user.login;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.durdus.ticketing.common.security.AuthProperties;

@Service
class RefreshTokenService {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();

	private final RefreshTokenRepository tokens;

	private final AuthProperties properties;

	private final Clock clock;

	RefreshTokenService(RefreshTokenRepository tokens, AuthProperties properties, Clock clock) {
		this.tokens = tokens;
		this.properties = properties;
		this.clock = clock;
	}

	record IssuedRefreshToken(String rawToken, UUID userId, UUID familyId, Duration timeToLive) {

		// The raw token is a credential
		@Override
		public String toString() {
			return "IssuedRefreshToken[REDACTED]";
		}

	}

	/** Starts a new family, used at login. */
	@Transactional
	IssuedRefreshToken startFamily(UUID userId) {
		return issue(userId, UUID.randomUUID());
	}

	/**
	 * Exchanges a refresh token for a new one in the same family. Returns empty for an unknown, expired or
	 * revoked token. Presenting a token that was already exchanged is treated as theft: the whole family is
	 * revoked, so both the thief and the real user have to log in again.
	 */
	@Transactional
	Optional<IssuedRefreshToken> rotate(String rawToken) {
		String tokenHash = hash(rawToken);
		Instant now = clock.instant();
		if (tokens.markUsed(tokenHash, now) == 1) {
			RefreshToken used = tokens.findByTokenHash(tokenHash).orElseThrow();
			return Optional.of(issue(used.getUserId(), used.getFamilyId()));
		}
		// Not usable. If it exists and was used before, someone replayed it. A normal return (not an exception)
		// is needed here so that the revocation is committed.
		tokens.findByTokenHash(tokenHash)
				.filter(token -> token.getUsedAt() != null)
				.ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), now));
		return Optional.empty();
	}

	/** Revokes the family of the given token (logout). Unknown tokens are ignored. */
	@Transactional
	void revokeFamilyOf(String rawToken) {
		tokens.findByTokenHash(hash(rawToken))
				.ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), clock.instant()));
	}

	/** Removes tokens that expired more than a day ago. Meant to be called by a scheduled job. */
	@Transactional
	int deleteExpired() {
		return tokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
	}

	private IssuedRefreshToken issue(UUID userId, UUID familyId) {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant now = clock.instant();
		tokens.save(new RefreshToken(userId, familyId, hash(rawToken), now.plus(properties.refreshTokenTtl()), now));
		return new IssuedRefreshToken(rawToken, userId, familyId, properties.refreshTokenTtl());
	}

	// A fast hash is enough here: the token has 256 bits of randomness, unlike a password.
	private static String hash(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

}
