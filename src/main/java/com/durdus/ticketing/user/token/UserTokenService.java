package com.durdus.ticketing.user.token;

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

@Service
class UserTokenService {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();

	private final UserTokenRepository tokens;

	private final Clock clock;

	UserTokenService(UserTokenRepository tokens, Clock clock) {
		this.tokens = tokens;
		this.clock = clock;
	}

	record IssuedToken(String rawToken, Instant expiresAt) {

		// The raw token is a credential
		@Override
		public String toString() {
			return "IssuedToken[REDACTED]";
		}

	}

	/**
	 * Creates a new token and revokes the unused ones of the same user and type, so that resending an
	 * invitation invalidates the previous link. The raw token is returned only here and never stored.
	 */
	@Transactional
	IssuedToken issue(UUID userId, UserTokenType type, Duration timeToLive) {
		Instant now = clock.instant();
		tokens.revokeUnused(userId, type, now);
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant expiresAt = now.plus(timeToLive);
		tokens.save(new UserToken(userId, type, hash(rawToken), expiresAt, now));
		return new IssuedToken(rawToken, expiresAt);
	}

	/**
	 * Uses the token and returns the id of its user. Returns empty for an unknown, expired, already used or
	 * wrong-type token, without telling which of these it was.
	 */
	@Transactional
	Optional<UUID> consume(String rawToken, UserTokenType type) {
		String tokenHash = hash(rawToken);
		if (tokens.consume(tokenHash, type, clock.instant()) != 1) {
			return Optional.empty();
		}
		return tokens.findByTokenHash(tokenHash).map(UserToken::getUserId);
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
