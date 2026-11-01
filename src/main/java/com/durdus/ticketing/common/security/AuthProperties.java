package com.durdus.ticketing.common.security;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Authentication settings (ADR 0006).
 *
 * @param jwtSecret HS256 signing key, at least 32 bytes. Comes from the environment, never from the repository.
 * @param issuer value of the {@code iss} claim, checked on every request
 * @param accessTokenTtl lifetime of an access token. Also the delay before a suspension takes full effect.
 * @param refreshTokenTtl lifetime of a refresh token
 * @param refreshCookieSecure whether the refresh cookie is marked {@code Secure}. Only {@code false} for plain-HTTP development.
 * @param corsAllowedOrigins origins allowed to call the API cross-origin. Empty means same-origin only.
 */
@ConfigurationProperties("ticketing.auth")
public record AuthProperties(String jwtSecret, @DefaultValue("ticketing-backend") String issuer,
		@DefaultValue("10m") Duration accessTokenTtl, @DefaultValue("7d") Duration refreshTokenTtl,
		@DefaultValue("true") boolean refreshCookieSecure, @DefaultValue List<String> corsAllowedOrigins) {

	static final int MINIMUM_SECRET_BYTES = 32;

	public AuthProperties {
		if (jwtSecret == null || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
			throw new IllegalStateException(
					"ticketing.auth.jwt-secret must be set to at least " + MINIMUM_SECRET_BYTES + " bytes (TICKETING_AUTH_JWT_SECRET)");
		}
	}

	// The secret must never appear in logs or actuator output.
	@Override
	public String toString() {
		return "AuthProperties[jwtSecret=REDACTED, issuer=" + issuer + "]";
	}

}
